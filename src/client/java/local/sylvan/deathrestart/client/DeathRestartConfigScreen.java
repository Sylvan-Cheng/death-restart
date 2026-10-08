package local.sylvan.deathrestart.client;

import java.util.List;
import local.sylvan.deathrestart.DeathRestartConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Vanilla widgets keep the optional ModMenu integration independent of other config libraries. */
public final class DeathRestartConfigScreen extends Screen {
    private final Screen parent;
    private int resetCountdown;
    private boolean automaticReconnect;
    private int reconnectInterval;
    private int reconnectTimeout;
    private boolean deathLeaderboard;
    private CycleButton<Integer> intervalButton;
    private CycleButton<Integer> timeoutButton;

    public DeathRestartConfigScreen(Screen parent) {
        super(Component.translatable("deathrestart.config.title"));
        this.parent = parent;
        applyDraft(DeathRestartConfig.get());
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(310, width - 40);
        var layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        var content = LinearLayout.vertical().spacing(4);
        content.addChild(new MultiLineTextWidget(Component.translatable("deathrestart.config.scope"), font)
                .setMaxWidth(contentWidth), cell -> cell.paddingBottom(6));

        addSection(content, "deathrestart.config.reset_countdown_group");
        content.addChild(numberOption("deathrestart.config.reset_countdown", resetCountdown,
                DeathRestartConfig.COUNTDOWN_VALUES, contentWidth, (button, value) -> resetCountdown = value));

        addSection(content, "deathrestart.config.reconnect_group");
        content.addChild(CycleButton.onOffBuilder(automaticReconnect)
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("deathrestart.config.automatic_reconnect.tooltip")))
                .create(0, 0, contentWidth, 20,
                        Component.translatable("deathrestart.config.automatic_reconnect"),
                        (button, value) -> {
                            automaticReconnect = value;
                            updateReconnectControls();
                        }));
        intervalButton = content.addChild(numberOption("deathrestart.config.reconnect_interval", reconnectInterval,
                DeathRestartConfig.RECONNECT_INTERVAL_VALUES, contentWidth,
                (button, value) -> reconnectInterval = value));
        timeoutButton = content.addChild(numberOption("deathrestart.config.reconnect_timeout",
                reconnectTimeout, DeathRestartConfig.RECONNECT_TIMEOUT_VALUES, contentWidth,
                (button, value) -> reconnectTimeout = value));
        updateReconnectControls();

        addSection(content, "deathrestart.config.display_group");
        content.addChild(CycleButton.onOffBuilder(deathLeaderboard)
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("deathrestart.config.death_leaderboard.tooltip")))
                .create(0, 0, contentWidth, 20,
                        Component.translatable("deathrestart.config.death_leaderboard"),
                        (button, value) -> deathLeaderboard = value));
        content.addChild(new MultiLineTextWidget(Component.translatable("deathrestart.config.backup_port_note"), font)
                .setMaxWidth(contentWidth), cell -> cell.paddingTop(6));

        layout.addToContents(new ScrollableLayout(minecraft, content, layout.getContentHeight()));
        var footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        int buttonWidth = (contentWidth - 8) / 2;
        footer.addChild(Button.builder(Component.translatable("deathrestart.config.reset"), button -> {
                    applyDraft(DeathRestartConfig.DEFAULTS);
                    rebuildWidgets();
                }).width(buttonWidth).build());
        footer.addChild(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .width(buttonWidth).build());
        layout.arrangeElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void addSection(LinearLayout content, String key) {
        content.addChild(new StringWidget(Component.translatable(key).withStyle(ChatFormatting.GOLD), font),
                cell -> cell.paddingTop(6));
    }

    private CycleButton<Integer> numberOption(String key, int initial, List<Integer> values, int width,
                                              CycleButton.OnValueChange<Integer> onChange) {
        return CycleButton.builder((Integer value) ->
                        Component.translatable("deathrestart.config.seconds", value), initial)
                .withValues(values)
                .withTooltip(value -> Tooltip.create(Component.translatable(key + ".tooltip")))
                .create(0, 0, width, 20, Component.translatable(key), onChange);
    }

    private void updateReconnectControls() {
        intervalButton.active = automaticReconnect;
        timeoutButton.active = automaticReconnect;
    }

    private void saveAndClose() {
        var settings = DeathRestartConfig.get();
        if (DeathRestartConfig.apply(new DeathRestartConfig.Settings(resetCountdown, automaticReconnect,
                reconnectInterval, reconnectTimeout, deathLeaderboard, settings.statisticsMode(),
                settings.countdownStartSound(), settings.countdownFinalSecondsSound(),
                settings.backupRetentionCount(), settings.manualRestartConfirmation()))) {
            ScreenCompatibility.set(minecraft, parent);
        } else {
            ScreenCompatibility.set(minecraft, new AlertScreen(() -> ScreenCompatibility.set(minecraft, this),
                    Component.translatable("deathrestart.config.save_failed"),
                    Component.translatable("deathrestart.config.save_failed_details")));
        }
    }

    private void applyDraft(DeathRestartConfig.Settings settings) {
        resetCountdown = settings.resetCountdownSeconds();
        automaticReconnect = settings.automaticReconnect();
        reconnectInterval = settings.reconnectIntervalSeconds();
        reconnectTimeout = settings.reconnectTimeoutSeconds();
        deathLeaderboard = settings.deathLeaderboard();
    }

    @Override
    public void onClose() {
        saveAndClose();
    }
}
