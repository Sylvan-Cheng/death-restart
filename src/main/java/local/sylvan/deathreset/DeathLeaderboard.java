package local.sylvan.deathreset;

import java.io.IOException;
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
    static final String OBJECTIVE = "deathreset_deaths";
    private MinecraftServer server;
    private DeathStatsStore stats;
    private boolean writable;

    void tick(MinecraftServer current) {
        if (current.isDedicatedServer() || !current.isPublished()) return;
        boolean changed = initialize(current);
        if (current.getTickCount() % 20 != 0 && !changed) return;
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            changed |= stats.track(player.getUUID(), player.getPlainTextName());
        }
        if (changed) save();
        refresh();
    }

    void recordDeath(ServerPlayer player) {
        MinecraftServer current = player.level().getServer();
        initialize(current);
        stats.recordDeath(player.getUUID(), player.getPlainTextName());
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
        var file = FabricLoader.getInstance().getConfigDir().resolve("deathreset/deaths").resolve(worldId + ".json");
        try {
            stats = new DeathStatsStore(file);
            writable = true;
        } catch (IOException failure) {
            // Preserve a malformed existing file instead of replacing its historical counts.
            DeathResetMod.LOGGER.error("Cannot read death leaderboard; preserving {} and showing session totals", file, failure);
            try {
                stats = new DeathStatsStore(file.resolveSibling(worldId + ".session-" + System.nanoTime() + ".json"));
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
            DeathResetMod.LOGGER.error("Could not persist the death leaderboard", failure);
        }
    }

    private void refresh() {
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                    Component.literal("死亡榜（累计）").withStyle(ChatFormatting.GOLD),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        for (var entry : stats.entries().entrySet()) {
            var score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.getKey().toString()), objective);
            score.set(entry.getValue().deaths());
            score.display(Component.literal(entry.getValue().name()));
        }
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
    }
}
