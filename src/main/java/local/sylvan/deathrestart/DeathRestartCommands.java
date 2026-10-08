package local.sylvan.deathrestart;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

final class DeathRestartCommands {
    private DeathRestartCommands() {
    }

    static void register(DeathRestartMod mod) {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            if (!selection.includeIntegrated) return;
            dispatcher.register(Commands.literal("deathrestart")
                    .executes(context -> mod.showCommandUsage(context.getSource()))
                    .then(restart(mod))
                    .then(Commands.literal("cancel")
                            .requires(mod::isHost)
                            .executes(context -> mod.cancelRestart(context.getSource())))
                    .then(Commands.literal("status")
                            .requires(mod::isHost)
                            .executes(context -> mod.showRestartStatus(context.getSource())))
                    .then(leaderboard(mod))
                    .then(Commands.literal("countdown")
                            .requires(mod::isHost)
                            .then(secondsArgument()
                                    .executes(context -> mod.updateDefaultCountdown(context.getSource(),
                                            IntegerArgumentType.getInteger(context, "seconds"))))));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> restart(DeathRestartMod mod) {
        return Commands.literal("restart")
                .requires(mod::isHost)
                .executes(context -> mod.prepareManualRestart(context.getSource()))
                .then(secondsArgument()
                        .executes(context -> mod.prepareManualRestart(context.getSource(),
                                IntegerArgumentType.getInteger(context, "seconds"))))
                .then(Commands.literal("confirm")
                        .executes(context -> mod.confirmManualRestart(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> leaderboard(DeathRestartMod mod) {
        return Commands.literal("leaderboard")
                .requires(mod::isHost)
                .executes(context -> mod.showLeaderboardUsage(context.getSource()))
                .then(Commands.literal("on")
                        .executes(context -> mod.updateLeaderboardDisplay(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> mod.updateLeaderboardDisplay(context.getSource(), false)))
                .then(Commands.literal("clear")
                        .executes(context -> mod.prepareLeaderboardClear(context.getSource()))
                        .then(Commands.literal("confirm")
                                .executes(context -> mod.confirmLeaderboardClear(context.getSource()))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> secondsArgument() {
        return Commands.argument("seconds", IntegerArgumentType.integer())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        DeathRestartConfig.COUNTDOWN_VALUES.stream().map(String::valueOf), builder));
    }
}
