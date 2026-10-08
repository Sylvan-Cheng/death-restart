package local.sylvan.deathrestart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenCompatibility {
    private ScreenCompatibility() {
    }

    public static Screen current(Minecraft client) {
        return client.gui.screen();
    }

    public static void set(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }
}
