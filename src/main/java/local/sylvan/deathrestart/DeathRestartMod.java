package local.sylvan.deathrestart;

import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeathRestartMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("deathrestart");
    private static Consumer<ResetRequest> resetHandler;
    private MinecraftServer activeServer;
    private ResetCountdown countdown = new ResetCountdown();
    private int lastSecond = -1;
    private String deadPlayer;
    private final DeathLeaderboard leaderboard = new DeathLeaderboard();

    public static void setResetHandler(Consumer<ResetRequest> handler) {
        resetHandler = handler;
    }

    @Override
    public void onInitialize() {
        DeathRestartConfig.load();
        // Official Fabric API: AFTER_DEATH runs after an actual death, so totems do not trigger it.
        // Docs: https://maven.fabricmc.net/docs/fabric-api-0.155.3+26.1.2/
        // net/fabricmc/fabric/api/entity/event/v1/ServerLivingEntityEvents.html
        PayloadTypeRegistry.clientboundPlay().register(ResetNoticePayload.TYPE, ResetNoticePayload.CODEC);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) onDeath(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerTickEvents.END_SERVER_TICK.register(leaderboard::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            leaderboard.stopped(server);
            if (server == activeServer) {
                activeServer = null;
                countdown = new ResetCountdown();
                lastSecond = -1;
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
        if (countdown.start(System.nanoTime())) {
            deadPlayer = player.getPlainTextName();
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatableWithFallback("deathrestart.death_broadcast",
                            "%s died! The world will reset in %s seconds.", deadPlayer, countdown.durationSeconds())
                            .withStyle(ChatFormatting.RED), false);
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
        resetHandler.accept(request);
    }

    private void showCountdown(MinecraftServer server, int seconds) {
        lastSecond = seconds;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 0));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatableWithFallback("deathrestart.countdown_subtitle",
                            "%s died · World reset incoming", deadPlayer).withStyle(ChatFormatting.YELLOW)));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatableWithFallback("deathrestart.countdown_seconds", "%s seconds", seconds)
                            .withStyle(ChatFormatting.RED)));
            player.sendSystemMessage(Component.translatableWithFallback("deathrestart.countdown_actionbar",
                    "World reset countdown: %s seconds", seconds), true);
        }
    }
}
