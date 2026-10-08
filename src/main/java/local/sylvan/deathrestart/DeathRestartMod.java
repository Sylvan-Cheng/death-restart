package local.sylvan.deathrestart;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns countdowns and command confirmations on the integrated-server thread. */
public final class DeathRestartMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("deathrestart");
    private static final long COMMAND_CONFIRMATION_NANOS = 60_000_000_000L;
    private static Consumer<ResetRequest> resetHandler;

    private final DeathLeaderboard leaderboard = new DeathLeaderboard();
    private MinecraftServer activeServer;
    private ResetCountdown countdown = new ResetCountdown();
    private int lastSecond = -1;
    private Component countdownSubtitle;
    private ManualRestartConfirmation pendingManualRestart;
    private CommandConfirmation pendingLeaderboardClear;

    public static void setResetHandler(Consumer<ResetRequest> handler) {
        resetHandler = handler;
    }

    @Override
    public void onInitialize() {
        DeathRestartConfig.load();
        DeathRestartCommands.register(this);
        PayloadTypeRegistry.clientboundPlay().register(ResetNoticePayload.TYPE, ResetNoticePayload.CODEC);
        // AFTER_DEATH excludes totems and deaths cancelled by other mods.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) onDeath(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerTickEvents.END_SERVER_TICK.register(leaderboard::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            leaderboard.stopped(server);
            if (pendingManualRestart != null && pendingManualRestart.confirmation().server() == server) {
                pendingManualRestart = null;
            }
            if (pendingLeaderboardClear != null && pendingLeaderboardClear.server() == server) {
                pendingLeaderboardClear = null;
            }
            if (server == activeServer) {
                activeServer = null;
                countdown = new ResetCountdown();
                lastSecond = -1;
                countdownSubtitle = null;
            }
        });
    }

    private void onDeath(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || server.isDedicatedServer() || !server.isPublished() || resetHandler == null) return;
        leaderboard.recordDeath(player);
        if (activeServer != server) {
            activeServer = server;
            countdown = new ResetCountdown(DeathRestartConfig.get().resetCountdownSeconds());
            lastSecond = -1;
        }
        pendingManualRestart = null;
        pendingLeaderboardClear = null;
        if (countdown.start(System.nanoTime())) {
            String deadPlayer = player.getPlainTextName();
            countdownSubtitle = Component.translatableWithFallback("deathrestart.countdown_subtitle",
                    "%s died · World reset incoming", deadPlayer).withStyle(ChatFormatting.YELLOW);
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatableWithFallback("deathrestart.death_broadcast",
                            "%s died! The world will reset in %s seconds.", deadPlayer, countdown.durationSeconds())
                            .withStyle(ChatFormatting.RED), false);
            playStartCue(server);
            showCountdown(server, countdown.durationSeconds());
            LOGGER.info("{} died; resetting LAN world in {} seconds", deadPlayer, countdown.durationSeconds());
        }
    }

    private void tick(MinecraftServer server) {
        if (activeServer != server || !countdown.isStarted()) return;
        long now = System.nanoTime();
        int remaining = countdown.secondsRemaining(now);
        if (remaining > 0 && remaining != lastSecond) showCountdown(server, remaining);
        if (!countdown.fireIfReady(now)) return;

        ResetRequest request = new ResetRequest(server, server.getWorldPath(LevelResource.ROOT),
                server.getWorldData().getLevelSettings().copy(),
                server.getGameRules().copy(server.getWorldData().enabledFeatures()),
                server.getWorldGenSettings().options().seed(), server.getPort(),
                server.getForcedGameType(), server.getPlayerList().isAllowCommandsForAllPlayers());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!server.isSingleplayerOwner(player.nameAndId())
                    && ServerPlayNetworking.canSend(player, ResetNoticePayload.TYPE)) {
                ServerPlayNetworking.send(player, ResetNoticePayload.INSTANCE);
            }
        }
        pendingManualRestart = null;
        resetHandler.accept(request);
    }

    private void showCountdown(MinecraftServer server, int seconds) {
        lastSecond = seconds;
        var settings = DeathRestartConfig.get();
        if (settings.countdownFinalSecondsSound() && seconds <= 3 && seconds > 0) {
            float pitch = 1.0f + (3 - seconds) * 0.15f;
            playCue(server, pitch);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 0));
            player.connection.send(new ClientboundSetSubtitleTextPacket(countdownSubtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatableWithFallback("deathrestart.countdown_seconds", "%s seconds", seconds)
                            .withStyle(ChatFormatting.RED)));
            player.sendSystemMessage(Component.translatableWithFallback("deathrestart.countdown_actionbar",
                    "World reset countdown: %s seconds", seconds), true);
        }
    }

    private void playStartCue(MinecraftServer server) {
        if (DeathRestartConfig.get().countdownStartSound()) playCue(server, 0.8f);
    }

    private static void playCue(MinecraftServer server, float pitch) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_PLING, SoundSource.MASTER,
                    player.getX(), player.getY(), player.getZ(), 0.8f, pitch, player.getRandom().nextLong()));
        }
    }

    boolean isHost(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                && source.getServer().isSingleplayerOwner(player.nameAndId());
    }

    int showCommandUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("deathrestart.command.usage"), false);
        return 1;
    }

    int showLeaderboardUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("deathrestart.command.leaderboard.usage"), false);
        return 1;
    }

    int updateLeaderboardDisplay(CommandSourceStack source, boolean enabled) {
        if (!isPublishedLanWorld(source.getServer())) {
            source.sendFailure(Component.translatable("deathrestart.command.not_lan"));
            return 0;
        }
        if (!DeathRestartConfig.apply(DeathRestartConfig.get().withDeathLeaderboard(enabled))) {
            source.sendFailure(Component.translatable("deathrestart.command.save_failed"));
            return 0;
        }
        leaderboard.refresh(source.getServer());
        source.sendSuccess(() -> Component.translatable(enabled
                ? "deathrestart.command.leaderboard_enabled" : "deathrestart.command.leaderboard_disabled"), false);
        return 1;
    }

    int prepareLeaderboardClear(CommandSourceStack source) {
        if (!canClearLeaderboard(source)) return 0;
        pendingLeaderboardClear = CommandConfirmation.create(source);
        source.sendSuccess(() -> Component.translatable("deathrestart.command.leaderboard.clear_prompt"), false);
        return 1;
    }

    int confirmLeaderboardClear(CommandSourceStack source) {
        var confirmed = pendingLeaderboardClear;
        pendingLeaderboardClear = null;
        if (confirmed == null || !confirmed.matches(source)) {
            source.sendFailure(Component.translatable("deathrestart.command.leaderboard.clear_expired"));
            return 0;
        }
        if (!canClearLeaderboard(source)) return 0;
        try {
            leaderboard.clear(source.getServer());
        } catch (IOException failure) {
            LOGGER.warn("Could not clear the death leaderboard", failure);
            source.sendFailure(Component.translatable("deathrestart.command.leaderboard.clear_failed"));
            return 0;
        }
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("deathrestart.command.leaderboard.cleared").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private boolean canClearLeaderboard(CommandSourceStack source) {
        if (!isHost(source) || source.getServer().isStopped()) return false;
        if (activeServer == source.getServer() && countdown.isStarted()) {
            source.sendFailure(countdown.isFired()
                    ? Component.translatable("deathrestart.command.status_reset_started")
                    : Component.translatable("deathrestart.command.already_scheduled",
                            countdown.secondsRemaining(System.nanoTime())));
            return false;
        }
        return true;
    }

    int prepareManualRestart(CommandSourceStack source) {
        return prepareManualRestart(source, DeathRestartConfig.get().resetCountdownSeconds());
    }

    int prepareManualRestart(CommandSourceStack source, int seconds) {
        if (!canScheduleManualRestart(source)) return 0;
        if (!DeathRestartConfig.COUNTDOWN_VALUES.contains(seconds)) {
            source.sendFailure(Component.translatable("deathrestart.command.invalid_countdown"));
            return 0;
        }

        if (!DeathRestartConfig.get().manualRestartConfirmation()) {
            return startManualRestart(source, seconds);
        }

        pendingManualRestart = new ManualRestartConfirmation(CommandConfirmation.create(source), seconds);
        source.sendSuccess(() -> Component.translatable(
                "deathrestart.command.restart_confirmation_prompt", seconds), false);
        return 1;
    }

    int confirmManualRestart(CommandSourceStack source) {
        if (!hasManualRestartConfirmation(source)) {
            pendingManualRestart = null;
            source.sendFailure(Component.translatable("deathrestart.command.restart_confirmation_expired"));
            return 0;
        }
        int seconds = pendingManualRestart.seconds();
        pendingManualRestart = null;
        if (!canScheduleManualRestart(source)) return 0;

        return startManualRestart(source, seconds);
    }

    private int startManualRestart(CommandSourceStack source, int seconds) {
        MinecraftServer server = source.getServer();
        activeServer = server;
        countdown = new ResetCountdown(seconds);
        countdownSubtitle = Component.translatable("deathrestart.command.restart_subtitle")
                .withStyle(ChatFormatting.YELLOW);
        lastSecond = -1;
        countdown.start(System.nanoTime());
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("deathrestart.command.restart_broadcast", countdown.durationSeconds())
                        .withStyle(ChatFormatting.GOLD), false);
        playStartCue(server);
        showCountdown(server, countdown.durationSeconds());
        LOGGER.info("Host started a LAN world restart in {} seconds", countdown.durationSeconds());
        return 1;
    }

    private boolean hasManualRestartConfirmation(CommandSourceStack source) {
        return pendingManualRestart != null && pendingManualRestart.confirmation().matches(source);
    }

    private boolean canScheduleManualRestart(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (!isPublishedLanWorld(server)) {
            source.sendFailure(Component.translatable("deathrestart.command.not_lan"));
            return false;
        }
        if (pendingManualRestart != null) {
            if (hasManualRestartConfirmation(source)) {
                source.sendFailure(Component.translatable("deathrestart.command.restart_confirmation_pending"));
                return false;
            }
            pendingManualRestart = null;
        }
        if (activeServer == server && countdown.isStarted()) {
            source.sendFailure(countdown.isFired()
                    ? Component.translatable("deathrestart.command.reset_started")
                    : Component.translatable("deathrestart.command.already_scheduled",
                            countdown.secondsRemaining(System.nanoTime())));
            return false;
        }
        return true;
    }

    int cancelRestart(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (activeServer != server || !countdown.isStarted()) {
            if (hasManualRestartConfirmation(source)) {
                pendingManualRestart = null;
                source.sendSuccess(() -> Component.translatable(
                        "deathrestart.command.restart_confirmation_cancelled"), false);
                return 1;
            }
            source.sendFailure(Component.translatable("deathrestart.command.no_countdown"));
            return 0;
        }
        if (!countdown.cancel()) {
            source.sendFailure(Component.translatable("deathrestart.command.reset_started"));
            return 0;
        }

        countdown = new ResetCountdown(DeathRestartConfig.get().resetCountdownSeconds());
        countdownSubtitle = null;
        lastSecond = -1;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            player.sendSystemMessage(Component.empty(), true);
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("deathrestart.command.cancelled").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    int showRestartStatus(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Component countdownStatus = countdownStatus(source);
        var settings = DeathRestartConfig.get();
        Component statisticsMode = Component.translatable("deathrestart.config.statistics_mode."
                + settings.statisticsMode().name().toLowerCase(Locale.ROOT));
        Component leaderboardStatus = Component.translatable(settings.deathLeaderboard()
                ? "deathrestart.command.enabled" : "deathrestart.command.disabled");
        Component port = isPublishedLanWorld(server)
                ? Component.literal(Integer.toString(server.getPort()))
                : Component.translatable("deathrestart.command.status_not_open");
        Component authentication = Component.translatable(server.usesAuthentication()
                ? "deathrestart.command.online_mode.enabled" : "deathrestart.command.online_mode.disabled");
        source.sendSuccess(() -> Component.translatable(
                "deathrestart.command.status_countdown_line", countdownStatus), false);
        source.sendSuccess(() -> Component.translatable("deathrestart.command.status_details",
                statisticsMode, leaderboardStatus, port, authentication), false);
        return 1;
    }

    private Component countdownStatus(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (!isPublishedLanWorld(server)) {
            return Component.translatable("deathrestart.command.status_not_lan");
        }
        if (hasManualRestartConfirmation(source)) {
            return Component.translatable("deathrestart.command.status_confirmation", pendingManualRestart.seconds());
        }
        if (activeServer != server || !countdown.isStarted()) {
            return Component.translatable("deathrestart.command.status_idle");
        }
        if (countdown.isFired()) {
            return Component.translatable("deathrestart.command.status_reset_started");
        }
        return Component.translatable("deathrestart.command.status_countdown",
                countdown.secondsRemaining(System.nanoTime()));
    }

    int updateDefaultCountdown(CommandSourceStack source, int seconds) {
        if (!DeathRestartConfig.COUNTDOWN_VALUES.contains(seconds)) {
            source.sendFailure(Component.translatable("deathrestart.command.invalid_countdown"));
            return 0;
        }
        if (!DeathRestartConfig.apply(DeathRestartConfig.get().withResetCountdownSeconds(seconds))) {
            source.sendFailure(Component.translatable("deathrestart.command.save_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("deathrestart.command.countdown_updated", seconds), false);
        return 1;
    }

    private static boolean isPublishedLanWorld(MinecraftServer server) {
        return !server.isDedicatedServer() && server.isPublished() && resetHandler != null;
    }

    private record ManualRestartConfirmation(CommandConfirmation confirmation, int seconds) {
    }

    /** Confirms only the requesting player, world and server session, within one minute. */
    private record CommandConfirmation(MinecraftServer server, UUID player, Path world, long createdAt) {
        static CommandConfirmation create(CommandSourceStack source) {
            return new CommandConfirmation(source.getServer(), source.getEntity().getUUID(),
                    source.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), System.nanoTime());
        }

        boolean matches(CommandSourceStack source) {
            return server == source.getServer() && player.equals(source.getEntity().getUUID())
                    && world.equals(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize())
                    && System.nanoTime() - createdAt < COMMAND_CONFIRMATION_NANOS;
        }
    }
}
