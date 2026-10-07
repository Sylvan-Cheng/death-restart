package local.sylvan.deathreset.client;

import java.util.Optional;
import java.util.OptionalLong;
import local.sylvan.deathreset.DeathResetMod;
import local.sylvan.deathreset.ResetNoticePayload;
import local.sylvan.deathreset.ResetRequest;
import local.sylvan.deathreset.WorldArchive;
import net.fabricmc.api.ClientModInitializer;
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

public final class DeathResetClient implements ClientModInitializer {
    private final GuestReconnect reconnect = new GuestReconnect();
    private ResetRequest pendingPublish;
    private IntegratedServer pendingServer;
    private long newSeed;
    private long publishDeadline;
    private long lastPublishAttempt;
    private boolean portWarningShown;

    @Override
    public void onInitializeClient() {
        DeathResetMod.setResetHandler(request -> Minecraft.getInstance().execute(() -> resetWorld(request)));
        ClientPlayNetworking.registerGlobalReceiver(ResetNoticePayload.TYPE,
                (payload, context) -> reconnect.arm(context.client()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reconnect.onDisconnect();
            if (pendingServer != null) {
                pendingPublish = null;
                pendingServer = null;
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reconnect.onJoin());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            publishWhenReady(client);
            reconnect.tick(client);
        });
    }

    private void resetWorld(ResetRequest request) {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != request.server()) return;
        WorldArchive.Transaction transaction = null;
        LevelStorageSource.LevelStorageAccess newAccess = null;
        try {
            // Vanilla disconnect waits for server shutdown and releases the world lock before any move.
            client.disconnect(new GenericMessageScreen(Component.literal("正在保存旧世界，新世界生成中…")), false);
            var storage = client.getLevelSource();
            String levelId = request.worldPath().toAbsolutePath().normalize().getFileName().toString();
            var flows = client.createWorldOpenFlows();
            WorldCreationContext context;
            try (var oldAccess = storage.validateAndCreateAccess(levelId)) {
                context = flows.recreateWorldData(oldAccess).getSecond();
            }

            transaction = WorldArchive.prepare(storage.getBaseDir(), request.worldPath(),
                    client.gameDirectory.toPath().resolve("death-reset-backups"));
            do {
                newSeed = WorldOptions.randomSeed();
            } while (newSeed == request.previousSeed());
            var options = context.options().withSeed(OptionalLong.of(newSeed));
            var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
            var registries = context.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
            var worldData = new PrimaryLevelData(request.settings(), dimensions.specialWorldProperty(), dimensions.lifecycle());
            var settings = new LevelDataAndDimensions.WorldDataAndGenSettings(worldData,
                    new WorldGenSettings(options, context.selectedDimensions()));

            newAccess = storage.validateAndCreateAccess(levelId);
            pendingPublish = request;
            pendingServer = null;
            publishDeadline = 0;
            lastPublishAttempt = 0;
            portWarningShown = false;
            DeathResetMod.LOGGER.info("Archived old world at {}; creating seed {}", transaction.backupPath(), newSeed);
            flows.createLevelFromExistingSettings(newAccess, context.dataPackResources(), registries,
                    settings, Optional.of(request.gameRules()));
            pendingServer = client.getSingleplayerServer();
            // The integrated server now owns the access and closes it on shutdown.
            newAccess = null;
        } catch (Exception failure) {
            pendingPublish = null;
            pendingServer = null;
            DeathResetMod.LOGGER.error("Could not reset the LAN world", failure);
            if (client.getSingleplayerServer() != null) {
                client.disconnect(new GenericMessageScreen(Component.literal("正在恢复旧世界…")), false);
            }
            if (newAccess != null) newAccess.safeClose();
            String recovery = "旧世界仍保留在原存档目录。";
            if (transaction != null) {
                try {
                    transaction.restore();
                    recovery = "旧世界已恢复，重新进入原存档即可继续。";
                } catch (Exception restoreFailure) {
                    DeathResetMod.LOGGER.error("Restore failed; previous world remains in {}", transaction.backupPath(), restoreFailure);
                    recovery = "旧世界备份路径：" + transaction.backupPath();
                }
            }
            client.setScreen(new AlertScreen(() -> client.setScreen(new TitleScreen()),
                    Component.literal("世界重置失败"), Component.literal(recovery + "\n" + failure.getMessage())));
        }
    }

    private void publishWhenReady(Minecraft client) {
        if (pendingPublish == null || client.player == null || client.level == null || client.getConnection() == null) return;
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || server != pendingServer || !server.isReady()) return;
        long now = System.nanoTime();
        if (publishDeadline == 0) publishDeadline = now + 10_000_000_000L;
        if (now - lastPublishAttempt < 1_000_000_000L) return;
        lastPublishAttempt = now;
        boolean published = server.isPublished() || server.publishServer(
                pendingPublish.lanGameType(), pendingPublish.allowCommands(), pendingPublish.port());
        if (published) {
            client.player.sendSystemMessage(Component.literal(
                    "新世界已就绪！种子：" + newSeed + " · 端口：" + server.getPort()).withStyle(ChatFormatting.GREEN));
            DeathResetMod.LOGGER.info("New round ready: seed {}, LAN port {}", newSeed, server.getPort());
            pendingPublish = null;
            pendingServer = null;
        } else if (now >= publishDeadline && !portWarningShown) {
            client.player.sendSystemMessage(Component.literal(
                    "新世界已生成，正在等待原端口 " + pendingPublish.port() + " 释放，稍后自动重试…")
                    .withStyle(ChatFormatting.RED));
            DeathResetMod.LOGGER.warn("LAN port {} unavailable; continuing to retry the same port", pendingPublish.port());
            portWarningShown = true;
        }
    }
}
