package local.sylvan.deathreset.client;

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

    void arm(Minecraft client) {
        if (client.hasSingleplayerServer() || client.getCurrentServer() == null) return;
        var current = client.getCurrentServer();
        target = new ServerData(current.name, current.ip, current.type());
        target.copyFrom(current);
        armedAt = System.nanoTime();
        disconnected = false;
        showedWaitingScreen = false;
        attempts = 0;
    }

    void onDisconnect() {
        if (target == null || disconnected) return;
        long now = System.nanoTime();
        if (now - armedAt > 15_000_000_000L) {
            cancel();
            return;
        }
        disconnected = true;
        deadline = now + 120_000_000_000L;
        nextAttempt = now + 3_000_000_000L;
    }

    void onJoin() {
        cancel();
    }

    void cancel() {
        target = null;
        disconnected = false;
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
            if (client.screen instanceof ReconnectScreen waiting) waiting.timedOut();
            cancel();
            return;
        }
        if (showedWaitingScreen && client.screen instanceof TitleScreen) {
            // Vanilla's connection Cancel button returns to the title and stops the retry loop.
            cancel();
            return;
        }
        if (client.screen instanceof DisconnectedScreen) {
            client.setScreen(new ReconnectScreen(this, target.ip));
            showedWaitingScreen = true;
        }
        if (client.screen instanceof ReconnectScreen waiting && now >= nextAttempt) {
            nextAttempt = now + 5_000_000_000L;
            attempts++;
            waiting.setAttempt(attempts);
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(target.ip), target, false, null);
        }
    }
}
