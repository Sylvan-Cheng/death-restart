package local.sylvan.deathrestart.client;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;

public final class LanCompatibility {
    private LanCompatibility() {
    }

    public static LanSettings capture(IntegratedServer server) {
        return new LanSettings(server.getForcedGameType(),
                server.getPlayerList().isAllowCommandsForAllPlayers(), server.getForcedGameType() != null,
                server.usesAuthentication());
    }

    public static boolean publishLan(IntegratedServer server, LanSettings settings, int port) {
        server.setUsesAuthentication(settings.usesAuthentication());
        return server.publishServer(MinecraftServer.MultiplayerScope.LAN,
                settings.gameType(), settings.allowCommands(), port);
    }
}
