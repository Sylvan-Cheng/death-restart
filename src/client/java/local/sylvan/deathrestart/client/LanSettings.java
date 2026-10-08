package local.sylvan.deathrestart.client;

import net.minecraft.world.level.GameType;

/** LAN-only settings that are not necessarily stored in level.dat. */
public record LanSettings(GameType gameType, boolean allowCommands, boolean forceGameMode,
                           boolean usesAuthentication) {
}
