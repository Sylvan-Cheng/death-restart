package local.sylvan.deathrestart.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

final class ReconnectScreen extends Screen {
    private final GuestReconnect reconnect;
    private final String address;
    private boolean timedOut;
    private final int attempt;

    ReconnectScreen(GuestReconnect reconnect, String address, int attempt) {
        super(Component.translatable("deathrestart.reconnect.title"));
        this.reconnect = reconnect;
        this.address = address;
        this.attempt = attempt;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable(timedOut ? "gui.back" : "deathrestart.reconnect.cancel"),
                        button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 45, 200, 20).build());
    }

    void timedOut() {
        timedOut = true;
    }

    @Override
    public void onClose() {
        reconnect.cancel();
        ScreenCompatibility.set(minecraft, new TitleScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable(timedOut
                ? "deathrestart.reconnect.timeout"
                : "deathrestart.reconnect.waiting"), width / 2, height / 2 - 15, 0xFFDDDDDD);
        graphics.centeredText(font, attempt > 0
                        ? Component.translatable("deathrestart.reconnect.attempt", address, attempt)
                        : Component.translatable("deathrestart.reconnect.address", address),
                width / 2, height / 2 + 5, 0xFFAAAAAA);
    }
}
