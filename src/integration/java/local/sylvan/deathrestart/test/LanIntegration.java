package local.sylvan.deathrestart.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import local.sylvan.deathrestart.DeathRestartConfig;
import local.sylvan.deathrestart.DeathRestartMod;
import local.sylvan.deathrestart.DeathStatsStore;
import local.sylvan.deathrestart.WorldArchive;
import local.sylvan.deathrestart.client.LanCompatibility;
import local.sylvan.deathrestart.client.LanSettings;
import local.sylvan.deathrestart.client.ScreenCompatibility;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;

/** Test-only orchestration in two ordinary clients, without altering vanilla thread scheduling. */
public final class LanIntegration implements ClientModInitializer {
    private static final BlockPos MARKER = new BlockPos(10, 200, 10);
    private final boolean host = System.getProperty("deathrestart.test.role", "host").equals("host");
    private final Path sync = Path.of(System.getProperty("deathrestart.test.sync"));
    private final String statsFixture = System.getProperty("deathrestart.test.stats", "valid");
    private final int expectedGuestDeaths = statsFixture.equals("valid") ? 6 : 1;
    private int stage;
    private int round;
    private int port;
    private final long started = System.nanoTime();
    private long deathTime;
    private long seed;
    private Path savePath;
    private IntegratedServer previousServer;
    private LanSettings previousLanSettings;
    private DeathRestartConfig.Settings originalSettings;
    private String pnpConfig;
    private ClientLevel previousLevel;
    private CompletableFuture<?> work;
    private boolean screenshotTaken;
    private boolean leaderboardHostCommandTested;
    private Path recoveryWorld;
    private WorldArchive.Transaction recoveryFixture;
    private boolean startupRecoveryChecked;
    private int leaderboardCommandStage;
    private long leaderboardCommandTime;
    private long manualPreparationTime;

    @Override
    public void onInitializeClient() {
        try {
            Path game = FabricLoader.getInstance().getGameDir();
            Path saves = Files.createDirectories(game.resolve("saves"));
            recoveryWorld = Files.createDirectories(saves.resolve("RecoveryIntegration"));
            Files.writeString(recoveryWorld.resolve("level.dat"), "original-recovery-world");
            recoveryFixture = WorldArchive.prepare(saves, recoveryWorld, game.resolve("death-restart-backups"));
            Files.writeString(recoveryWorld.resolve("level.dat"), "partial-recovery-world");
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare startup recovery test", failure);
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (stage < 0) return;
                if (!startupRecoveryChecked && client.isGameLoadFinished()) {
                    check(Files.readString(recoveryWorld.resolve("level.dat")).equals("original-recovery-world"),
                            "Startup restores the original world before it can be opened");
                    check(Files.readString(recoveryFixture.failedWorldPath().resolve("level.dat")).equals("partial-recovery-world"),
                            "Startup preserves partial generation files");
                    check(!Files.exists(recoveryFixture.journalPath()), "Startup finishes the recovery journal");
                    if (!(ScreenCompatibility.current(client) instanceof AlertScreen)) return;
                    ScreenCompatibility.set(client, new TitleScreen());
                    startupRecoveryChecked = true;
                }
                if (!startupRecoveryChecked) return;
                if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Integration test timed out at stage " + stage);
                if (host) tickHost(client); else tickGuest(client);
            } catch (Throwable failure) {
                stage = -1;
                DeathRestartMod.LOGGER.error("LAN integration failed", failure);
                try {
                    Files.createDirectories(sync);
                    Files.writeString(sync.resolve((host ? "host" : "guest") + "-failed.txt"), failure.toString());
                } catch (Exception ignored) {
                }
                client.stop();
            }
        });
    }

    private void tickHost(Minecraft client) throws Exception {
        if (stage == 0 && client.isGameLoadFinished() && ScreenCompatibility.current(client) != null) {
            client.options.onboardAccessibility = false;
            client.options.pauseOnLostFocus = false;
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            stage = 1;
            client.createWorldOpenFlows().createFreshLevel("DeathRestartIntegration",
                    new LevelSettings("Death Restart LAN Test", GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.HARD, false, false), true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(123456789L, true, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
        } else if (stage == 1 && ready(client)) {
            previousServer = client.getSingleplayerServer();
            originalSettings = DeathRestartConfig.get();
            check(DeathRestartConfig.apply(originalSettings.withDeathLeaderboard(true)
                            .withManualRestartConfirmation(true).withBackupRetentionCount(0)),
                    "Enable the leaderboard and unlimited backup retention for the host integration client");
            seed = 123456789L;
            savePath = previousServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path statsFile = FabricLoader.getInstance().getConfigDir().resolve("deathrestart/deaths")
                    .resolve(savePath.getFileName() + ".json");
            Files.createDirectories(statsFile.getParent());
            if (statsFixture.equals("valid")) Files.writeString(statsFile, """
                    {
                      "restartguest": {"name": "RestartGuest", "deaths": 5},
                      "uuid:%s": {"name": "RestartGuest", "deaths": 5}
                    }
                    """.formatted(offlineUuid("RestartGuest")));
            if (statsFixture.equals("invalid")) Files.writeString(statsFile, """
                    {"%s": {"name": "RestartGuest", "deaths": 5}}
                    """.formatted(offlineUuid("RestartGuest")));
            if (statsFixture.equals("missing")) check(!Files.exists(statsFile), "Start without a statistics file");
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            check(LanCompatibility.publishLan(previousServer,
                    new LanSettings(GameType.SURVIVAL, false, true, false), port), "Publish LAN");
            previousLanSettings = LanCompatibility.capture(previousServer);
            Files.createDirectories(sync);
            Files.writeString(sync.resolve("port.txt"), Integer.toString(port));
            stage = 2;
        } else if (stage == 2) {
            if (round == 0 && !Files.exists(sync.resolve("guest-ready.txt"))) return;
            if (work == null) {
                work = previousServer.submit(() -> previousServer.getPlayerCount() == 2
                        && previousServer.getPlayerList().getPlayers().stream().allMatch(p -> p.connection.hasClientLoaded()));
            } else if (work.isDone()) {
                boolean loaded = (Boolean) work.join();
                work = null;
                if (!loaded) return;
                IntegratedServer server = previousServer;
                work = server.submit(() -> {
                    if (round == 0 && !leaderboardHostCommandTested) {
                        var host = server.getPlayerList().getPlayerByName("RestartHost");
                        check(DeathRestartConfig.get().deathLeaderboard(), "Leaderboard is enabled by default");
                        check(executeCommand(server, host, "deathrestart leaderboard off") == 1,
                                "Host can hide the synchronized leaderboard");
                        check(server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) == null,
                                "Host can hide the leaderboard for every client");
                        check(executeCommand(server, host, "deathrestart leaderboard on") == 1,
                                "Host can restore the synchronized leaderboard");
                        var sidebar = server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
                        check(sidebar != null && sidebar.getName().equals("deathrestart_deaths"),
                                "Host can restore the leaderboard for every client");
                        leaderboardHostCommandTested = true;
                    }
                    if (round == 0 && FabricLoader.getInstance().isModLoaded("mcwifipnp")) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "onlinemode false");
                        try {
                            pnpConfig = Files.readString(savePath.resolve("mcwifipnp.json"));
                        } catch (Exception failure) {
                            throw new AssertionError("Read LAN World PNP configuration", failure);
                        }
                    }
                    server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                    server.getGameRules().set(GameRules.IMMEDIATE_RESPAWN, true, server);
                    server.overworld().setBlockAndUpdate(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState());
                    server.getPlayerList().getPlayers().forEach(p -> p.giveExperienceLevels(23));
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "give @a minecraft:diamond 7");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "advancement grant @a only minecraft:story/root");
                    var scoreboard = server.getScoreboard();
                    var objective = scoreboard.getObjective("deathrestart_deaths");
                    scoreboard.getOrCreatePlayerScore(
                            ScoreHolder.forNameOnly("absentplayer"), objective).set(2);
                    if (round == 0) {
                        var host = server.getPlayerList().getPlayerByName("RestartHost");
                        var guest = server.getPlayerList().getPlayerByName("RestartGuest");
                        int originalCountdown = originalSettings.resetCountdownSeconds();
                        verifyConfirmedRestartCommands(server, host, guest, originalCountdown);
                        verifyImmediateRestartCommands(server, host, originalCountdown);
                    } else {
                        check(DeathRestartConfig.apply(DeathRestartConfig.get().withManualRestartConfirmation(false)),
                                "Disable confirmation before testing an automatic death reset");
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill RestartHost");
                        check(scoreboard.listPlayerScores(objective).size() == 2,
                                "Keep leaderboard rows in sync with name totals");
                    }
                });
                stage = round == 0 ? 9 : 3;
            }
        } else if (stage == 9) {
            if (work != null) {
                if (!work.isDone()) return;
                work.join();
                work = null;
                manualPreparationTime = System.nanoTime();
            }
            if (System.nanoTime() - manualPreparationTime < 6_000_000_000L) return;
            check(ready(client) && client.getSingleplayerServer() == previousServer,
                    "Preparing a 5-second restart does not reset the world after 6 seconds");
            IntegratedServer server = previousServer;
            work = server.submit(() -> {
                var player = server.getPlayerList().getPlayerByName("RestartHost");
                check(server.getWorldGenSettings().options().seed() == seed,
                        "World seed does not change while awaiting confirmation");
                check(DeathRestartConfig.apply(DeathRestartConfig.get().withManualRestartConfirmation(false)),
                        "Disable confirmation with a request already pending");
                check(executeCommand(server, player, "deathrestart restart 5") == 0,
                        "A settings change does not bypass an existing pending request");
                check(executeCommand(server, player, "deathrestart restart confirm") == 1,
                        "A prepared restart remains confirmable after its countdown duration");
                check(executeCommand(server, player, "deathrestart cancel") == 1,
                        "Cancel the confirmed one-time restart before resetting the world");
                check(DeathRestartConfig.apply(DeathRestartConfig.get().withManualRestartConfirmation(true)),
                        "Re-enable confirmation before testing death invalidation");
                check(executeCommand(server, player, "deathrestart restart 5") == 1,
                        "Prepare a restart before testing death invalidation");
                check(executeCommand(server, player, "deathrestart leaderboard clear") == 1,
                        "Prepare leaderboard clear before testing world-change invalidation");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill RestartGuest");
                check(executeCommand(server, player, "deathrestart restart confirm") == 0,
                        "A player death invalidates the pending manual restart confirmation");
                var scoreboard = server.getScoreboard();
                check(scoreboard.listPlayerScores(scoreboard.getObjective("deathrestart_deaths")).size() == 2,
                        "Keep leaderboard rows in sync with name totals");
            });
            stage = 3;
        } else if (stage == 3 && work.isDone()) {
            work.join();
            work = null;
            deathTime = System.nanoTime();
            screenshotTaken = false;
            stage = 4;
        } else if (stage == 4) {
            if (!screenshotTaken && System.nanoTime() - deathTime > 1_000_000_000L && client.level != null) {
                ScreenshotCompatibility.grab(client, "countdown-" + round + ".png");
                screenshotTaken = true;
            }
            if (!ready(client) || client.getSingleplayerServer() == previousServer || !client.getSingleplayerServer().isPublished()) return;
            check(System.nanoTime() - deathTime >= 9_850_000_000L, "Countdown must last 10 seconds");
            check(previousServer.isShutdown(), "Old server shut down");
            IntegratedServer next = client.getSingleplayerServer();
            check(next.getPort() == port, "Reuse LAN port");
            check(!next.usesAuthentication(), "Keep authentication disabled after reset");
            check(LanCompatibility.capture(next).equals(previousLanSettings), "Preserve LAN settings");
            if (pnpConfig != null) {
                check(Files.readString(savePath.resolve("mcwifipnp.json")).equals(pnpConfig),
                        "Preserve LAN World PNP configuration");
            }
            check(next.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(savePath), "Keep world folder");
            work = next.submit(() -> {
                var host = next.getPlayerList().getPlayers().stream()
                        .filter(player -> next.isSingleplayerOwner(player.nameAndId())).findFirst().orElseThrow();
                check(executeCommand(next, host, "deathrestart leaderboard clear confirm") == 0,
                        "A previous world's clear request cannot be confirmed after resetting");
                long newSeed = next.getWorldGenSettings().options().seed();
                check(newSeed != seed, "Change seed");
                check(next.getWorldData().getDifficulty() == Difficulty.HARD, "Preserve difficulty");
                check(next.getGameRules().get(GameRules.KEEP_INVENTORY), "Preserve game rules");
                check(!next.overworld().getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "Clear old terrain and buildings");
                var objective = next.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
                check(objective != null && objective.getName().equals("deathrestart_deaths"), "Show sidebar death leaderboard");
                var previousGuest = next.getScoreboard().listPlayerScores(objective).stream()
                        .filter(entry -> entry.display() != null && entry.display().getString().equals("RestartGuest")).findFirst().orElseThrow();
                check(previousGuest.value() == expectedGuestDeaths, "Retain guest death count after world reset");
                var hostEntry = next.getScoreboard().listPlayerScores(objective).stream()
                        .filter(entry -> entry.display() != null && entry.display().getString().equals("RestartHost"))
                        .findFirst().orElseThrow();
                check(hostEntry.value() == round, "Retain host death count after world reset");
                verifyStoredDeathTotals();
                var scores = next.getScoreboard().listPlayerScores(objective);
                check(scores.size() == 2, "One leaderboard row per player");
                var mode = DeathRestartConfig.get().statisticsMode();
                String guestOwner = scoreOwner("RestartGuest", mode);
                String hostOwner = scoreOwner("RestartHost", mode);
                check(scores.stream().allMatch(entry -> entry.owner().equals(hostOwner)
                        || entry.owner().equals(guestOwner)), "Leaderboard owners match the configured identity mode");
                return newSeed;
            });
            previousServer = next;
            stage = 5;
        } else if (stage == 5 && work.isDone()) {
            seed = (Long) work.join();
            work = null;
            stage = 6;
        } else if (stage == 6) {
            Path guestResult = sync.resolve(round == 0 ? "guest-round-one.txt" : "guest-passed.txt");
            if (!Files.exists(guestResult)) return;
            if (work == null) {
                work = previousServer.submit(() -> {
                    if (previousServer.getPlayerCount() != 2) return false;
                    previousServer.getPlayerList().getPlayers().forEach(p -> {
                        check(p.getInventory().isEmpty(), "Clear inventories despite keep_inventory");
                        check(p.experienceLevel == 0 && p.totalExperience == 0, "Clear XP");
                    });
                    return true;
                });
            } else if (work.isDone()) {
                boolean rejoined = (Boolean) work.join();
                work = null;
                if (!rejoined) return;
                round++;
                if (round == 1) {
                    stage = 2;
                } else {
                    leaderboardCommandTime = System.nanoTime();
                    stage = 8;
                }
            }
        } else if (stage == 8) {
            if (work != null && !work.isDone()) return;
            if (work != null) {
                work.join();
                work = null;
            }
            if (System.nanoTime() - leaderboardCommandTime < 1_000_000_000L) return;
            if (leaderboardCommandStage < 3) {
                String command = switch (leaderboardCommandStage++) {
                    case 0 -> "deathrestart leaderboard off";
                    case 1 -> "deathrestart leaderboard clear";
                    default -> "deathrestart leaderboard clear confirm";
                };
                work = previousServer.submit(() -> {
                    verifyLeaderboardCommand(command);
                });
                leaderboardCommandTime = System.nanoTime();
            } else {
                if (statsFixture.equals("invalid")) {
                    Path statsDir = FabricLoader.getInstance().getConfigDir().resolve("deathrestart/deaths");
                    try (var files = Files.list(statsDir)) {
                        var preserved = files.filter(p -> p.getFileName().toString().startsWith(
                                savePath.getFileName() + ".json.invalid-")).toList();
                        check(preserved.size() == 1, "Preserve invalid statistics only once across two resets");
                        check(Files.readString(preserved.getFirst()).equals("""
                                {"%s": {"name": "RestartGuest", "deaths": 5}}
                                """.formatted(offlineUuid("RestartGuest"))), "Preserve the exact unsupported history");
                    }
                }
                var backups = WorldArchive.listBackups(client.gameDirectory.toPath().resolve("death-restart-backups"),
                        savePath.getFileName().toString());
                check(backups.stream().filter(b -> b.kind() == WorldArchive.BackupKind.SUCCESSFUL).count() == 2,
                        "Unlimited retention preserves both successful world backups");
                check(Files.exists(recoveryFixture.failedWorldPath()), "Successful resets preserve failed-world recovery data");
                check(DeathRestartConfig.get().backupRetentionCount() == 0,
                        "Integration run keeps backup retention unlimited");
                ScreenshotCompatibility.grab(client, "new-round.png");
                check(DeathRestartConfig.apply(originalSettings), "Restore the integration client's original settings");
                Files.writeString(sync.resolve("host-passed.txt"), "PASS: guest death, host death, two 10-second countdowns, two new seeds, same LAN port, inventory/XP/blocks cleared, rules and authentication preserved, death totals stored by identity, leaderboard rows synchronized, guest reconnected twice"
                        + ", startup interrupted-reset recovery and unlimited backup retention"
                        + ", manual restart confirmation, cancellation and death invalidation"
                        + ", confirmation toggle, immediate manual countdowns and pending-request preservation"
                        + ", confirmed leaderboard clear across both identity modes while hidden"
                        + ", statistics mode " + DeathRestartConfig.get().statisticsMode()
                        + (pnpConfig == null ? "" : ", LAN World PNP configuration preserved"));
                Files.writeString(sync.resolve("finish.txt"), "done");
                deathTime = System.nanoTime();
                stage = 7;
            }
        } else if (stage == 7 && System.nanoTime() - deathTime > 2_000_000_000L) {
            stage = -1;
            client.stop();
        }
    }

    private static void verifyConfirmedRestartCommands(IntegratedServer server, ServerPlayer host,
                                                        ServerPlayer guest, int originalCountdown) {
        check(executeCommand(server, host, "deathrestart countdown 15") == 1,
                "Host can save the default countdown without ModMenu");
        check(DeathRestartConfig.get().resetCountdownSeconds() == 15,
                "Saved countdown becomes the default");
        check(executeCommand(server, host, "deathrestart countdown 7") == 0,
                "Reject unsupported default countdowns");
        check(DeathRestartConfig.apply(
                DeathRestartConfig.get().withResetCountdownSeconds(originalCountdown)),
                "Restore test settings after countdown command");
        check(DeathRestartConfig.get().resetCountdownSeconds() == originalCountdown,
                "Restore the configured countdown");
        check(executeCommand(server, host, "deathrestart restart 5") == 1,
                "Host can prepare a one-time countdown override");
        check(DeathRestartConfig.get().resetCountdownSeconds() == originalCountdown,
                "One-time countdown does not change the default");
        check(executeCommand(server, host, "deathrestart restart 6") == 0,
                "Reject unsupported one-time countdowns");
        check(executeCommand(server, host, "deathrestart cancel") == 1,
                "Host can cancel a pending manual restart confirmation");
        check(executeCommand(server, host, "deathrestart restart confirm") == 0,
                "Reject confirmation after cancellation");
        check(executeCommand(server, host, "deathrestart restart") == 1,
                "Host can prepare a manual restart without enabling LAN commands");
        check(executeCommand(server, host, "deathrestart restart") == 0,
                "Do not replace a pending confirmation");
        check(executeCommand(server, host, "deathrestart restart confirm") == 1,
                "Host can confirm a prepared manual restart");
        check(executeCommand(server, host, "deathrestart restart confirm") == 0,
                "Reject confirmation after the countdown starts");
        check(executeCommand(server, guest, "deathrestart restart") == 0,
                "Guest cannot control world resets");
        check(executeCommand(server, host, "deathrestart leaderboard clear confirm") == 0,
                "Leaderboard clear requires a prepared request");
        check(executeCommand(server, guest, "deathrestart leaderboard clear") == 0,
                "Guest cannot prepare leaderboard clear");
        check(executeCommand(server, guest, "deathrestart leaderboard clear confirm") == 0,
                "Guest cannot confirm leaderboard clear");
        check(executeCommand(server, host, "deathrestart status") == 1,
                "Host can inspect reset status");
        check(executeCommand(server, host, "deathrestart cancel") == 1,
                "Host can cancel the confirmed reset countdown");
        check(executeCommand(server, host, "deathrestart status") == 1,
                "Host can inspect status after cancellation");
    }

    private static void verifyImmediateRestartCommands(IntegratedServer server, ServerPlayer host,
                                                        int originalCountdown) {
        check(DeathRestartConfig.apply(DeathRestartConfig.get().withManualRestartConfirmation(false)),
                "Disable manual restart confirmation");
        check(executeCommand(server, host, "deathrestart restart 6") == 0,
                "Unsupported countdowns remain rejected when confirmation is disabled");
        check(executeCommand(server, host, "deathrestart restart") == 1,
                "Default manual restart starts immediately with confirmation disabled");
        check(executeCommand(server, host, "deathrestart restart confirm") == 0,
                "Immediate default restart leaves no pending confirmation");
        check(executeCommand(server, host, "deathrestart restart 5") == 0,
                "Immediate default restart is already counting down");
        check(executeCommand(server, host, "deathrestart cancel") == 1,
                "Cancel an immediate default restart");
        check(executeCommand(server, host, "deathrestart restart 5") == 1,
                "One-time manual restart starts immediately with confirmation disabled");
        check(executeCommand(server, host, "deathrestart restart confirm") == 0,
                "Immediate one-time restart leaves no pending confirmation");
        check(executeCommand(server, host, "deathrestart restart") == 0,
                "Immediate one-time restart is already counting down");
        check(DeathRestartConfig.get().resetCountdownSeconds() == originalCountdown,
                "Immediate one-time restart preserves the configured countdown");
        check(executeCommand(server, host, "deathrestart cancel") == 1,
                "Cancel an immediate one-time restart");
        check(DeathRestartConfig.apply(DeathRestartConfig.get().withManualRestartConfirmation(true)),
                "Restore manual restart confirmation");
        check(executeCommand(server, host, "deathrestart restart 5") == 1,
                "Prepare a short restart to verify it waits for confirmation");
    }

    private void verifyLeaderboardCommand(String command) {
        var player = previousServer.getPlayerList().getPlayers().stream()
                .filter(p -> previousServer.isSingleplayerOwner(p.nameAndId())).findFirst().orElseThrow();
        check(executeCommand(previousServer, player, command) == 1, "Host leaderboard command succeeds: " + command);
        if (command.equals("deathrestart leaderboard clear")) {
            check(!DeathRestartConfig.get().manualRestartConfirmation(),
                    "Clear confirmation is required even with restart confirmation disabled");
            var objective = previousServer.getScoreboard().getObjective("deathrestart_deaths");
            check(previousServer.getScoreboard().listPlayerScores(objective).stream()
                            .anyMatch(score -> score.value() == expectedGuestDeaths),
                    "Preparing leaderboard clear does not change death totals");
        } else if (command.equals("deathrestart leaderboard clear confirm")) {
            var scoreboard = previousServer.getScoreboard();
            var objective = scoreboard.getObjective("deathrestart_deaths");
            check(scoreboard.listPlayerScores(objective).size() == 2
                            && scoreboard.listPlayerScores(objective).stream().allMatch(score -> score.value() == 0),
                    "Confirmed clear resets online players to zero and removes old leaderboard rows");
            check(scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) == null
                            && !DeathRestartConfig.get().deathLeaderboard(),
                    "Clear preserves the hidden leaderboard setting");
            check(executeCommand(previousServer, player, "deathrestart leaderboard clear confirm") == 0,
                    "A clear confirmation is consumed after use");
            try {
                Path file = FabricLoader.getInstance().getConfigDir().resolve("deathrestart/deaths")
                        .resolve(savePath.getFileName() + ".json");
                var cleared = new DeathStatsStore(file);
                var mode = DeathRestartConfig.get().statisticsMode();
                var retained = cleared.entries(mode);
                var removed = cleared.entries(mode == DeathRestartConfig.StatisticsMode.UUID
                        ? DeathRestartConfig.StatisticsMode.USERNAME
                        : DeathRestartConfig.StatisticsMode.UUID);
                check(retained.size() == 2 && retained.values().stream().allMatch(entry -> entry.deaths() == 0)
                                && removed.isEmpty(),
                        "Clear removes both histories and persists online players at zero in the active identity mode");
            } catch (Exception failure) {
                throw new AssertionError("Verify persisted leaderboard clear", failure);
            }
            check(executeCommand(previousServer, player, "deathrestart leaderboard on") == 1,
                    "Restore leaderboard display after testing clear");
        }
    }

    private void tickGuest(Minecraft client) throws Exception {
        if (stage == 0 && client.isGameLoadFinished() && ScreenCompatibility.current(client) != null
                && Files.exists(sync.resolve("port.txt"))) {
            client.options.onboardAccessibility = false;
            client.options.pauseOnLostFocus = false;
            client.options.renderDistance().set(6);
            String address = "127.0.0.1:" + Files.readString(sync.resolve("port.txt")).trim();
            stage = 1;
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Death Restart test", address, ServerData.Type.LAN), false, null);
        } else if (stage == 1 && ready(client)) {
            previousLevel = client.level;
            originalSettings = DeathRestartConfig.get();
            check(DeathRestartConfig.apply(originalSettings.withDeathLeaderboard(false)),
                    "Guest cannot change the host-controlled display setting");
            var sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
            check(sidebar != null && sidebar.getName().equals("deathrestart_deaths"),
                    "Guest configuration cannot override the host leaderboard display");
            Files.writeString(sync.resolve("guest-ready.txt"), "ready");
            stage = 2;
        } else if (stage == 2 && client.level == null) {
            stage = 3;
        } else if (stage == 3 && ready(client) && client.level != previousLevel) {
            check(client.player.getInventory().isEmpty(), "Guest inventory cleared");
            check(client.player.experienceLevel == 0, "Guest XP cleared");
            var localSidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
            check(localSidebar != null && localSidebar.getName().equals("deathrestart_deaths"),
                    "The host leaderboard remains visible after a guest-side config change");
            var guestEntry = client.level.getScoreboard().listPlayerScores(localSidebar).stream()
                    .filter(entry -> entry.display() != null && entry.display().getString().equals("RestartGuest"))
                    .findFirst().orElseThrow();
            check(guestEntry.value() == expectedGuestDeaths,
                    "Deaths continue accumulating while display settings are ignored on guests");
            round++;
            Files.writeString(sync.resolve(round == 1 ? "guest-round-one.txt" : "guest-passed.txt"), "Automatically rejoined round " + round);
            previousLevel = client.level;
            stage = round == 2 ? 4 : 2;
        } else if (stage == 4 && Files.exists(sync.resolve("finish.txt"))) {
            check(DeathRestartConfig.apply(originalSettings), "Restore the guest integration client's original settings");
            stage = -1;
            client.stop();
        }
    }

    private static boolean ready(Minecraft client) {
        return client.level != null && client.player != null && ScreenCompatibility.current(client) == null;
    }

    private void verifyStoredDeathTotals() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("deathrestart/deaths")
                    .resolve(savePath.getFileName() + ".json");
            var mode = DeathRestartConfig.get().statisticsMode();
            var entries = new DeathStatsStore(file).entries(mode);
            String guestKey = mode == DeathRestartConfig.StatisticsMode.UUID
                    ? "uuid:" + offlineUuid("RestartGuest") : "restartguest";
            String hostKey = mode == DeathRestartConfig.StatisticsMode.UUID
                    ? "uuid:" + offlineUuid("RestartHost") : "restarthost";
            check(entries.get(guestKey).deaths() == expectedGuestDeaths, "Persist guest deaths after each reset");
            check(entries.get(hostKey).deaths() == round, "Persist host deaths after each reset");
        } catch (Exception failure) {
            throw new AssertionError("Verify death statistics independently of the scoreboard", failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    private static String scoreOwner(String playerName, DeathRestartConfig.StatisticsMode mode) {
        return mode == DeathRestartConfig.StatisticsMode.UUID
                ? offlineUuid(playerName).toString()
                : playerName.toLowerCase(java.util.Locale.ROOT);
    }

    private static int executeCommand(IntegratedServer server, ServerPlayer player,
                                      String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        } catch (CommandSyntaxException rejected) {
            return 0;
        }
    }
}
