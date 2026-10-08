package local.sylvan.deathrestart.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import local.sylvan.deathrestart.DeathRestartConfig;
import local.sylvan.deathrestart.DeathRestartMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/** Vanilla widgets keep the optional ModMenu integration independent of other config libraries. */
public final class DeathRestartConfigScreen extends Screen {
    private final Screen parent;
    private int resetCountdown;
    private boolean automaticReconnect;
    private int reconnectInterval;
    private int reconnectTimeout;
    private boolean deathLeaderboard;
    private DeathRestartConfig.StatisticsMode statisticsMode;
    private boolean countdownStartSound;
    private boolean countdownFinalSecondsSound;
    private int backupRetentionCount;
    private boolean manualRestartConfirmation;
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

        addResetOptions(content, contentWidth);
        addStatisticsOptions(content, contentWidth);

        addSection(content, "deathrestart.config.backup_group");
        content.addChild(hostOption(backupRetentionOption(contentWidth)));

        addReconnectOptions(content, contentWidth);

        content.addChild(new MultiLineTextWidget(Component.translatable("deathrestart.config.backup_port_note"), font)
                .setMaxWidth(contentWidth), cell -> cell.paddingTop(6));
        addDataActions(content, contentWidth);

        layout.addToContents(new ScrollableLayout(minecraft, content, layout.getContentHeight()));
        var footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        int buttonWidth = (contentWidth - 8) / 2;
        footer.addChild(Button.builder(Component.translatable("deathrestart.config.reset"),
                button -> restoreDefaults()).width(buttonWidth).build());
        footer.addChild(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .width(buttonWidth).build());
        layout.arrangeElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void addResetOptions(LinearLayout content, int contentWidth) {
        addSection(content, "deathrestart.config.reset_countdown_group");
        content.addChild(hostOption(numberOption("deathrestart.config.reset_countdown", resetCountdown,
                DeathRestartConfig.COUNTDOWN_VALUES, contentWidth, (button, value) -> resetCountdown = value)));
        content.addChild(hostOption(toggleOption("deathrestart.config.manual_restart_confirmation",
                manualRestartConfirmation, contentWidth, (button, value) -> manualRestartConfirmation = value)));
        content.addChild(hostOption(toggleOption("deathrestart.config.countdown_start_sound", countdownStartSound,
                contentWidth, (button, value) -> countdownStartSound = value)));
        content.addChild(hostOption(toggleOption("deathrestart.config.countdown_final_seconds_sound",
                countdownFinalSecondsSound, contentWidth, (button, value) -> countdownFinalSecondsSound = value)));
    }

    private void addReconnectOptions(LinearLayout content, int contentWidth) {
        addSection(content, "deathrestart.config.reconnect_group");
        content.addChild(toggleOption("deathrestart.config.automatic_reconnect", automaticReconnect,
                contentWidth, (button, value) -> {
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
    }

    private void addStatisticsOptions(LinearLayout content, int contentWidth) {
        addSection(content, "deathrestart.config.display_group");
        content.addChild(hostOption(toggleOption("deathrestart.config.death_leaderboard", deathLeaderboard,
                contentWidth, (button, value) -> deathLeaderboard = value)));
        content.addChild(hostOption(CycleButton.builder((DeathRestartConfig.StatisticsMode value) ->
                        Component.translatable("deathrestart.config.statistics_mode."
                                + value.name().toLowerCase(Locale.ROOT)), statisticsMode)
                .withValues(DeathRestartConfig.StatisticsMode.values())
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("deathrestart.config.statistics_mode.tooltip")))
                .create(0, 0, contentWidth, 20,
                        Component.translatable("deathrestart.config.statistics_mode"),
                        (button, value) -> statisticsMode = value)));
    }

    private void addDataActions(LinearLayout content, int contentWidth) {
        addSection(content, "deathrestart.config.data_actions_group");
        boolean canClear = isLocalHost();
        var clearButton = content.addChild(Button.builder(
                        Component.translatable("deathrestart.config.clear_leaderboard")
                                .withStyle(canClear ? ChatFormatting.RED : ChatFormatting.GRAY),
                        button -> confirmLeaderboardClear())
                .width(contentWidth)
                .tooltip(Tooltip.create(Component.translatable("deathrestart.config.clear_leaderboard.tooltip")))
                .build());
        clearButton.active = canClear;
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

    private Checkbox toggleOption(String key, boolean initial, int width, Checkbox.OnValueChange onChange) {
        var checkbox = Checkbox.builder(Component.translatable(key), font)
                .selected(initial)
                .maxWidth(width)
                .onValueChange(onChange)
                .build();
        // The vanilla builder only attaches tooltips to overflowing labels.
        checkbox.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        return checkbox;
    }

    private CycleButton<Integer> backupRetentionOption(int width) {
        return CycleButton.builder((Integer value) -> Component.translatable(value == 0
                        ? "deathrestart.config.backup_retention.unlimited"
                        : "deathrestart.config.backup_retention.count", value), backupRetentionCount)
                .withValues(DeathRestartConfig.BACKUP_RETENTION_VALUES)
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("deathrestart.config.backup_retention.tooltip")))
                .create(0, 0, width, 20,
                        Component.translatable("deathrestart.config.backup_retention"),
                        (button, value) -> backupRetentionCount = value);
    }

    private void updateReconnectControls() {
        intervalButton.active = automaticReconnect;
        timeoutButton.active = automaticReconnect;
    }

    private boolean isLocalHost() {
        var server = minecraft.getSingleplayerServer();
        return server != null && minecraft.player != null
                && server.isSingleplayerOwner(minecraft.player.nameAndId());
    }

    private boolean canEditHostOptions() {
        return minecraft.getConnection() == null || isLocalHost();
    }

    private <T extends AbstractWidget> T hostOption(T widget) {
        widget.active = canEditHostOptions();
        return widget;
    }

    private void restoreDefaults() {
        var defaults = DeathRestartConfig.DEFAULTS;
        if (canEditHostOptions()) {
            applyDraft(defaults);
        } else {
            automaticReconnect = defaults.automaticReconnect();
            reconnectInterval = defaults.reconnectIntervalSeconds();
            reconnectTimeout = defaults.reconnectTimeoutSeconds();
        }
        rebuildWidgets();
    }

    private void confirmLeaderboardClear() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        var hostId = minecraft.player.getUUID();
        ScreenCompatibility.set(minecraft, new ConfirmScreen(confirmed -> {
            ScreenCompatibility.set(minecraft, this);
            if (!confirmed || minecraft.getSingleplayerServer() != server || minecraft.player == null
                    || !minecraft.player.getUUID().equals(hostId)) return;
            // Reuse command validation on the server thread; update widgets only on the client thread.
            server.submit(() -> executeLeaderboardClear(server, hostId))
                    .whenComplete((success, failure) -> minecraft.execute(() ->
                            showLeaderboardClearResult(server, Boolean.TRUE.equals(success), failure)));
        }, Component.translatable("deathrestart.config.clear_leaderboard"),
                Component.translatable("deathrestart.config.clear_leaderboard.confirm"),
                Component.translatable("deathrestart.config.clear_leaderboard"), Component.translatable("gui.cancel")));
    }

    private static boolean executeLeaderboardClear(IntegratedServer server, UUID hostId) {
        if (server.isStopped()) return false;
        var host = server.getPlayerList().getPlayer(hostId);
        if (host == null || !server.isSingleplayerOwner(host.nameAndId())) return false;
        var source = host.createCommandSourceStack();
        try {
            var dispatcher = server.getCommands().getDispatcher();
            return dispatcher.execute("deathrestart leaderboard clear", source) == 1
                    && dispatcher.execute("deathrestart leaderboard clear confirm", source) == 1;
        } catch (CommandSyntaxException rejected) {
            DeathRestartMod.LOGGER.warn("Could not execute leaderboard clear from settings", rejected);
            return false;
        }
    }

    private void showLeaderboardClearResult(IntegratedServer server, boolean success, Throwable failure) {
        if (minecraft.getSingleplayerServer() != server || ScreenCompatibility.current(minecraft) != this) return;
        if (failure != null) DeathRestartMod.LOGGER.warn("Could not clear leaderboard from settings", failure);
        ScreenCompatibility.set(minecraft, new AlertScreen(() -> ScreenCompatibility.set(minecraft, this),
                Component.translatable("deathrestart.config.clear_leaderboard"),
                Component.translatable(failure == null && success
                        ? "deathrestart.command.leaderboard.cleared"
                        : "deathrestart.command.leaderboard.clear_failed")));
    }

    private void saveAndClose() {
        if (DeathRestartConfig.apply(new DeathRestartConfig.Settings(resetCountdown, automaticReconnect,
                reconnectInterval, reconnectTimeout, deathLeaderboard, statisticsMode,
                countdownStartSound, countdownFinalSecondsSound, backupRetentionCount,
                manualRestartConfirmation))) {
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
        statisticsMode = settings.statisticsMode();
        countdownStartSound = settings.countdownStartSound();
        countdownFinalSecondsSound = settings.countdownFinalSecondsSound();
        backupRetentionCount = settings.backupRetentionCount();
        manualRestartConfirmation = settings.manualRestartConfirmation();
    }

    @Override
    public void onClose() {
        saveAndClose();
    }
}
