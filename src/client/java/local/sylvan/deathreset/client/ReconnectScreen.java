package local.sylvan.deathreset.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

final class ReconnectScreen extends Screen {
    private final GuestReconnect reconnect;
    private final String address;
    private boolean timedOut;
    private int attempt;

    ReconnectScreen(GuestReconnect reconnect, String address) {
        super(Component.literal("正在重置世界"));
        this.reconnect = reconnect;
        this.address = address;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("取消自动重连"), button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 45, 200, 20).build());
    }

    void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    void timedOut() {
        timedOut = true;
    }

    @Override
    public void onClose() {
        reconnect.cancel();
        minecraft.setScreen(new TitleScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(timedOut
                ? "重连超时，请从多人游戏列表手动重新加入。"
                : "房主正在生成新世界，稍后自动重连…"), width / 2, height / 2 - 15, 0xFFDDDDDD);
        graphics.centeredText(font, Component.literal("连接地址：" + address + (attempt > 0 ? " · 第 " + attempt + " 次尝试" : "")),
                width / 2, height / 2 + 5, 0xFFAAAAAA);
    }
}
