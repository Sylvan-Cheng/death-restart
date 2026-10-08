package local.sylvan.deathrestart.client;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import local.sylvan.deathrestart.DeathRestartConfig;
import local.sylvan.deathrestart.DeathRestartMod;
import local.sylvan.deathrestart.ResetNoticePayload;
import local.sylvan.deathrestart.ResetRequest;
import local.sylvan.deathrestart.WorldArchive;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

/** Rebuilds the host world after server shutdown, then restores its LAN settings. */
public final class DeathRestartClient implements ClientModInitializer {
    private final GuestReconnect reconnect = new GuestReconnect();
    private ResetRequest pendingPublish;
    private LanSettings pendingLanSettings;
    private IntegratedServer pendingServer;
    private long newSeed;
    private long publishDeadline;
    private long lastPublishAttempt;
    private boolean portWarningShown;
    private boolean commitWarningShown;
    private WorldArchive.Transaction pendingTransaction;
    private Component recoveryMessage;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(this::recoverInterruptedResets);
        DeathRestartMod.setResetHandler(request -> Minecraft.getInstance().execute(() -> resetWorld(request)));
        ClientPlayNetworking.registerGlobalReceiver(ResetNoticePayload.TYPE,
                (payload, context) -> reconnect.arm(context.client()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reconnect.onDisconnect();
            if (pendingServer != null) clearPendingReset();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reconnect.onJoin());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (recoveryMessage != null && ScreenCompatibility.current(client) instanceof TitleScreen) {
                Component message = recoveryMessage;
                recoveryMessage = null;
                ScreenCompatibility.set(client, new AlertScreen(() -> ScreenCompatibility.set(client, new TitleScreen()),
                        Component.translatable("deathrestart.recovery.title"), message));
            }
            publishWhenReady(client);
            reconnect.tick(client);
        });
    }

    private static Path backupRoot(Minecraft client) {
        return client.gameDirectory.toPath().resolve("death-restart-backups");
    }

    private void recoverInterruptedResets(Minecraft client) {
        try {
            var recovery = WorldArchive.recoverIncomplete(client.getLevelSource().getBaseDir(), backupRoot(client));
            if (recovery.restoredWorlds().isEmpty() && recovery.failures().isEmpty()) return;
            recoveryMessage = Component.translatable("deathrestart.recovery.restored", recovery.restoredWorlds().size());
            if (!recovery.failures().isEmpty()) {
                recoveryMessage = recoveryMessage.copy().append("\n")
                        .append(Component.translatable("deathrestart.recovery.failures", recovery.failures().size(),
                                backupRoot(client).toString()));
            }
            DeathRestartMod.LOGGER.info("Reset recovery: restored {}, failures {}",
                    recovery.restoredWorlds(), recovery.failures());
        } catch (Exception failure) {
            DeathRestartMod.LOGGER.error("Could not check interrupted world resets", failure);
            recoveryMessage = Component.translatable("deathrestart.recovery.failures", 1, backupRoot(client).toString());
        }
    }

    private void resetWorld(ResetRequest request) {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != request.server()) return;
        WorldArchive.Transaction transaction = null;
        LevelStorageSource.LevelStorageAccess newAccess = null;
        try {
            LanSettings lanSettings = LanCompatibility.capture(client.getSingleplayerServer());
            // Vanilla disconnect waits for server shutdown and releases the world lock before any move.
            client.disconnect(new GenericMessageScreen(Component.translatable("deathrestart.reset.saving")), false);
            var storage = client.getLevelSource();
            String levelId = request.worldPath().toAbsolutePath().normalize().getFileName().toString();
            var flows = client.createWorldOpenFlows();
            WorldCreationContext context;
            try (var oldAccess = storage.validateAndCreateAccess(levelId)) {
                context = flows.recreateWorldData(oldAccess).getSecond();
            }

            transaction = WorldArchive.prepare(storage.getBaseDir(), request.worldPath(), backupRoot(client));
            do {
                newSeed = WorldOptions.randomSeed();
            } while (newSeed == request.previousSeed());
            var options = context.options().withSeed(OptionalLong.of(newSeed));
            var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
            var registries = context.worldgenRegistries().replaceFrom(
                    RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
            var worldData = new PrimaryLevelData(request.settings(), dimensions.specialWorldProperty(),
                    dimensions.lifecycle());
            var settings = new LevelDataAndDimensions.WorldDataAndGenSettings(
                    worldData, new WorldGenSettings(options, context.selectedDimensions()));

            newAccess = storage.validateAndCreateAccess(levelId);
            pendingPublish = request;
            pendingLanSettings = lanSettings;
            pendingServer = null;
            pendingTransaction = transaction;
            publishDeadline = 0;
            lastPublishAttempt = 0;
            portWarningShown = false;
            commitWarningShown = false;
            DeathRestartMod.LOGGER.info("Archived old world at {}; creating seed {}",
                    transaction.backupPath(), newSeed);
            flows.createLevelFromExistingSettings(newAccess, context.dataPackResources(), registries,
                    settings, Optional.of(request.gameRules()));
            pendingServer = client.getSingleplayerServer();
            // The integrated server now owns the access and closes it on shutdown.
            newAccess = null;
        } catch (Exception failure) {
            handleResetFailure(client, transaction, newAccess, failure);
        }
    }

    private void handleResetFailure(Minecraft client, WorldArchive.Transaction transaction,
                                    LevelStorageSource.LevelStorageAccess newAccess, Exception failure) {
        clearPendingReset();
        DeathRestartMod.LOGGER.error("Could not reset the LAN world", failure);
        if (client.getSingleplayerServer() != null) {
            client.disconnect(new GenericMessageScreen(Component.translatable("deathrestart.reset.restoring")), false);
        }
        if (newAccess != null) newAccess.safeClose();

        Component recovery = Component.translatable("deathrestart.reset.old_world_kept");
        if (transaction != null) {
            try {
                transaction.restore();
                recovery = Component.translatable("deathrestart.reset.old_world_restored");
            } catch (Exception restoreFailure) {
                DeathRestartMod.LOGGER.error("Restore failed; previous world remains in {}",
                        transaction.backupPath(), restoreFailure);
                recovery = Component.translatable("deathrestart.reset.backup_path", transaction.backupPath().toString());
            }
        }
        ScreenCompatibility.set(client, new AlertScreen(() -> ScreenCompatibility.set(client, new TitleScreen()),
                Component.translatable("deathrestart.reset.failed"),
                recovery.copy().append("\n").append(String.valueOf(failure.getMessage()))));
    }

    private void publishWhenReady(Minecraft client) {
        if (pendingPublish == null || client.player == null || client.level == null
                || client.getConnection() == null) return;
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || server != pendingServer || !server.isReady()) return;
        long now = System.nanoTime();
        if (publishDeadline == 0) publishDeadline = now + 10_000_000_000L;
        if (now - lastPublishAttempt < 1_000_000_000L) return;
        lastPublishAttempt = now;
        boolean published = server.isPublished() || LanCompatibility.publishLan(server,
                pendingLanSettings, pendingPublish.port());
        if (published) {
            completeReset(client, server);
        } else if (now >= publishDeadline && !portWarningShown) {
            client.player.sendSystemMessage(Component.translatable(
                    "deathrestart.reset.port_wait", pendingPublish.port())
                    .withStyle(ChatFormatting.RED));
            DeathRestartMod.LOGGER.warn("LAN port {} unavailable; continuing to retry the same port",
                    pendingPublish.port());
            portWarningShown = true;
        }
    }

    private void completeReset(Minecraft client, IntegratedServer server) {
        // Commit before retention: recovery must never refer to a backup that cleanup deleted.
        try {
            pendingTransaction.complete();
        } catch (Exception commitFailure) {
            if (!commitWarningShown) {
                DeathRestartMod.LOGGER.error(
                        "Could not commit the world reset; keeping the recovery journal", commitFailure);
                client.player.sendSystemMessage(Component.translatable("deathrestart.recovery.commit_failed")
                        .withStyle(ChatFormatting.RED));
                commitWarningShown = true;
            }
            return;
        }
        Path backups = backupRoot(client);
        String worldName = pendingTransaction.worldPath().getFileName().toString();
        int keep = DeathRestartConfig.get().backupRetentionCount();
        client.player.sendSystemMessage(Component.translatable(
                "deathrestart.reset.ready", newSeed, server.getPort()).withStyle(ChatFormatting.GREEN));
        DeathRestartMod.LOGGER.info("New round ready: seed {}, LAN port {}", newSeed, server.getPort());
        clearPendingReset();
        cleanupBackups(backups, worldName, keep);
    }

    private static void cleanupBackups(Path backups, String worldName, int keep) {
        if (keep <= 0) return;
        CompletableFuture.runAsync(() -> {
            try {
                int removed = WorldArchive.cleanupBackups(backups, worldName, keep);
                if (removed > 0) {
                    DeathRestartMod.LOGGER.info("Removed {} old world backup(s) for {}", removed, worldName);
                }
            } catch (Exception cleanupFailure) {
                DeathRestartMod.LOGGER.warn("Could not clean old world backups", cleanupFailure);
            }
        });
    }

    private void clearPendingReset() {
        pendingPublish = null;
        pendingLanSettings = null;
        pendingServer = null;
        pendingTransaction = null;
    }
}
