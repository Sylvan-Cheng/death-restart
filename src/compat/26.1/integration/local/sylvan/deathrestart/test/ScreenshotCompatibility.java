package local.sylvan.deathrestart.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class ScreenshotCompatibility {
    private ScreenshotCompatibility() {
    }

    static void grab(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, component -> {});
    }
}
