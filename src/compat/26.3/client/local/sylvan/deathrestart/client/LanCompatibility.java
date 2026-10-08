package local.sylvan.deathrestart.client;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;

public final class LanCompatibility {
    private LanCompatibility() {
    }

    public static LanSettings capture(IntegratedServer server) {
        return new LanSettings(server.getDefaultGameType(),
                server.getGuestCommandAccess(), server.forceGameMode(), server.usesAuthentication());
    }

    public static boolean publishLan(IntegratedServer server, LanSettings settings, int port) {
        server.setUsesAuthentication(settings.usesAuthentication());
        server.setWorldGameType(settings.gameType());
        server.setForceGameMode(settings.forceGameMode());
        return server.publishServer(MinecraftServer.MultiplayerScope.LAN, settings.allowCommands(), port);
    }
}
