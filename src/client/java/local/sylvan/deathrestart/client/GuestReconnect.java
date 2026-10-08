package local.sylvan.deathrestart.client;

import local.sylvan.deathrestart.DeathRestartConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Reconnect only after this server explicitly announces a world reset. */
final class GuestReconnect {
    private ServerData target;
    private long armedAt;
    private long deadline;
    private long nextAttempt;
    private boolean disconnected;
    private boolean showedWaitingScreen;
    private int attempts;
    private long intervalNanos;
    private long timeoutNanos;

    void arm(Minecraft client) {
        var settings = DeathRestartConfig.get();
        if (!settings.automaticReconnect()) {
            cancel();
            return;
        }
        if (client.hasSingleplayerServer() || client.getCurrentServer() == null) return;
        var current = client.getCurrentServer();
        target = new ServerData(current.name, current.ip, current.type());
        target.copyFrom(current);
        armedAt = System.nanoTime();
        disconnected = false;
        showedWaitingScreen = false;
        attempts = 0;
        intervalNanos = settings.reconnectIntervalSeconds() * 1_000_000_000L;
        timeoutNanos = settings.reconnectTimeoutSeconds() * 1_000_000_000L;
    }

    void onDisconnect() {
        if (target == null || disconnected) return;
        long now = System.nanoTime();
        if (now - armedAt > 15_000_000_000L) {
            cancel();
            return;
        }
        disconnected = true;
        deadline = now + timeoutNanos;
        nextAttempt = now + intervalNanos;
    }

    void onJoin() {
        cancel();
    }

    void cancel() {
        target = null;
        disconnected = false;
    }

    private void abortConnectingScreen(Minecraft client) {
        if (ScreenCompatibility.current(client) instanceof ConnectScreen screen
                && screen instanceof ConnectScreenAccess access) {
            access.deathrestart$abortConnection();
        }
    }

    void tick(Minecraft client) {
        if (target == null) return;
        long now = System.nanoTime();
        if (!disconnected) {
            if (now - armedAt > 15_000_000_000L) cancel();
            return;
        }
        if (client.level != null || client.hasSingleplayerServer()) {
            cancel();
            return;
        }
        if (now >= deadline) {
            String address = target.ip;
            abortConnectingScreen(client);
            ReconnectScreen timeout = new ReconnectScreen(this, address, attempts);
            timeout.timedOut();
            ScreenCompatibility.set(client, timeout);
            cancel();
            return;
        }
        if (showedWaitingScreen && ScreenCompatibility.current(client) instanceof TitleScreen) {
            // Vanilla's connection Cancel button returns to the title and stops the retry loop.
            cancel();
            return;
        }
        if (ScreenCompatibility.current(client) instanceof DisconnectedScreen) {
            ScreenCompatibility.set(client, new ReconnectScreen(this, target.ip, attempts));
            showedWaitingScreen = true;
        }
        if (ScreenCompatibility.current(client) instanceof ReconnectScreen && now >= nextAttempt) {
            nextAttempt = now + intervalNanos;
            attempts++;
            ConnectScreen.startConnecting(new TitleScreen(), client,
                    ServerAddress.parseString(target.ip), target, false, null);
        }
    }
}
