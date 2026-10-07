package local.sylvan.deathreset;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.gamerules.GameRules;

/** Immutable handoff from the server thread to the host's client thread. */
public record ResetRequest(MinecraftServer server, Path worldPath, LevelSettings settings,
                           GameRules gameRules, long previousSeed, int port,
                           GameType lanGameType, boolean allowCommands) {
}
