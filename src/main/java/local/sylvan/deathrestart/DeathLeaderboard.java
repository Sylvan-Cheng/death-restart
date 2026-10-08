package local.sylvan.deathrestart;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Vanilla sidebar packets also display the leaderboard for guests without the mod. */
final class DeathLeaderboard {
    static final String OBJECTIVE = "deathrestart_deaths";
    private MinecraftServer server;
    private DeathStatsStore stats;
    private boolean writable;

    void tick(MinecraftServer current) {
        if (current.isDedicatedServer() || !current.isPublished()) return;
        boolean changed = initialize(current);
        if (current.getTickCount() % 20 != 0 && !changed) return;
        var mode = DeathRestartConfig.get().statisticsMode();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            changed |= stats.track(player.getUUID(), player.getPlainTextName(), mode);
        }
        if (changed) save();
        refresh();
    }

    void recordDeath(ServerPlayer player) {
        MinecraftServer current = player.level().getServer();
        initialize(current);
        stats.recordDeath(player.getUUID(), player.getPlainTextName(), DeathRestartConfig.get().statisticsMode());
        save();
        refresh();
    }

    void stopped(MinecraftServer current) {
        if (server == current) {
            server = null;
            stats = null;
        }
    }

    private boolean initialize(MinecraftServer current) {
        if (server == current) return false;
        server = current;
        String worldId = current.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        var file = FabricLoader.getInstance().getConfigDir()
                .resolve("deathrestart/deaths").resolve(worldId + ".json");
        try {
            stats = new DeathStatsStore(file);
            writable = true;
        } catch (IOException failure) {
            // Preserve a malformed existing file instead of replacing its historical counts.
            DeathRestartMod.LOGGER.error(
                    "Cannot read death leaderboard; preserving {} and showing session totals", file, failure);
            try {
                stats = new DeathStatsStore(
                        file.resolveSibling(worldId + ".session-" + System.nanoTime() + ".json"));
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
            writable = false;
        }
        return true;
    }

    private void save() {
        if (!writable) return;
        try {
            stats.save();
        } catch (IOException failure) {
            DeathRestartMod.LOGGER.error("Could not persist the death leaderboard", failure);
        }
    }

    void refresh() {
        if (server == null || stats == null) return;
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                    Component.translatableWithFallback("deathrestart.leaderboard", "Deaths (Total)")
                            .withStyle(ChatFormatting.GOLD),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        var settings = DeathRestartConfig.get();
        var mode = settings.statisticsMode();
        var entries = stats.entries(mode);
        var owners = entries.keySet().stream()
                .map(key -> scoreOwner(key, mode))
                .collect(Collectors.toSet());
        for (var score : scoreboard.listPlayerScores(objective)) {
            if (!owners.contains(score.owner())) {
                scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(score.owner()), objective);
            }
        }
        for (var entry : entries.entrySet()) {
            var score = scoreboard.getOrCreatePlayerScore(
                    ScoreHolder.forNameOnly(scoreOwner(entry.getKey(), mode)), objective);
            score.set(entry.getValue().deaths());
            score.display(Component.literal(entry.getValue().name()));
        }
        var displayed = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (settings.deathLeaderboard()) {
            if (displayed != objective) scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        } else if (displayed == objective) {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, null);
        }
    }

    void refresh(MinecraftServer current) {
        initialize(current);
        refresh();
    }

    void clear(MinecraftServer current) throws IOException {
        initialize(current);
        if (!writable) throw new IOException("Cannot clear an unreadable death statistics file");
        var mode = DeathRestartConfig.get().statisticsMode();
        var onlinePlayers = new LinkedHashMap<UUID, String>();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            onlinePlayers.put(player.getUUID(), player.getPlainTextName());
        }
        stats.clearAndTrack(onlinePlayers, mode);
        refresh();
    }

    private static String scoreOwner(String key, DeathRestartConfig.StatisticsMode mode) {
        return mode == DeathRestartConfig.StatisticsMode.UUID ? key.substring("uuid:".length()) : key;
    }
}
