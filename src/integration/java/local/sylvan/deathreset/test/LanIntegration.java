package local.sylvan.deathreset.test;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import local.sylvan.deathreset.DeathResetMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
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

/** Test-only orchestration in two ordinary clients, without altering vanilla thread scheduling. */
public final class LanIntegration implements ClientModInitializer {
    private static final BlockPos MARKER = new BlockPos(10, 200, 10);
    private final boolean host = System.getProperty("deathreset.test.role", "host").equals("host");
    private final Path sync = Path.of(System.getProperty("deathreset.test.sync"));
    private int stage;
    private int round;
    private int port;
    private long started = System.nanoTime();
    private long deathTime;
    private long seed;
    private Path savePath;
    private IntegratedServer previousServer;
    private ClientLevel previousLevel;
    private CompletableFuture<?> work;
    private boolean screenshotTaken;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (stage < 0) return;
                if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Integration test timed out at stage " + stage);
                if (host) tickHost(client); else tickGuest(client);
            } catch (Throwable failure) {
                stage = -1;
                DeathResetMod.LOGGER.error("LAN integration failed", failure);
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
        if (stage == 0 && client.isGameLoadFinished() && client.screen != null) {
            client.options.onboardAccessibility = false;
            client.options.pauseOnLostFocus = false;
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            stage = 1;
            client.createWorldOpenFlows().createFreshLevel("DeathResetIntegration",
                    new LevelSettings("Death Reset LAN Test", GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.HARD, false, false), true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(123456789L, true, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
        } else if (stage == 1 && ready(client)) {
            previousServer = client.getSingleplayerServer();
            seed = 123456789L;
            savePath = previousServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            check(previousServer.publishServer(GameType.SURVIVAL, true, port), "Publish LAN");
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
                    server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                    server.getGameRules().set(GameRules.IMMEDIATE_RESPAWN, true, server);
                    server.overworld().setBlockAndUpdate(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState());
                    server.getPlayerList().getPlayers().forEach(p -> p.giveExperienceLevels(23));
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "give @a minecraft:diamond 7");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "advancement grant @a only minecraft:story/root");
                    String victim = round == 0 ? "DeathResetGuest" : "DeathResetHost";
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill " + victim);
                });
                stage = 3;
            }
        } else if (stage == 3 && work.isDone()) {
            work.join();
            work = null;
            deathTime = System.nanoTime();
            screenshotTaken = false;
            stage = 4;
        } else if (stage == 4) {
            if (!screenshotTaken && System.nanoTime() - deathTime > 1_000_000_000L && client.level != null) {
                Screenshot.grab(client.gameDirectory, "countdown-" + round + ".png", client.getMainRenderTarget(), 1, component -> {});
                screenshotTaken = true;
            }
            if (!ready(client) || client.getSingleplayerServer() == previousServer || !client.getSingleplayerServer().isPublished()) return;
            check(System.nanoTime() - deathTime >= 9_850_000_000L, "Countdown must last 10 seconds");
            check(previousServer.isShutdown(), "Old server shut down");
            IntegratedServer next = client.getSingleplayerServer();
            check(next.getPort() == port, "Reuse LAN port");
            check(next.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(savePath), "Keep world folder");
            work = next.submit(() -> {
                long newSeed = next.getWorldGenSettings().options().seed();
                check(newSeed != seed, "Change seed");
                check(next.getWorldData().getDifficulty() == Difficulty.HARD, "Preserve difficulty");
                check(next.getGameRules().get(GameRules.KEEP_INVENTORY), "Preserve game rules");
                check(!next.overworld().getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "Clear old terrain and buildings");
                var objective = next.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
                check(objective != null && objective.getName().equals("deathreset_deaths"), "Show sidebar death leaderboard");
                var previousGuest = next.getScoreboard().listPlayerScores(objective).stream()
                        .filter(entry -> entry.display() != null && entry.display().getString().equals("DeathResetGuest")).findFirst().orElseThrow();
                check(previousGuest.value() == 1, "Retain guest's death count after world reset");
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
                    Screenshot.grab(client.gameDirectory, "new-round.png", client.getMainRenderTarget(), 1, component -> {});
                    Files.writeString(sync.resolve("host-passed.txt"), "PASS: guest death, host death, two 10-second countdowns, two new seeds, same LAN port, inventory/XP/blocks cleared, rules preserved, guest reconnected twice");
                    Files.writeString(sync.resolve("finish.txt"), "done");
                    deathTime = System.nanoTime();
                    stage = 7;
                }
            }
        } else if (stage == 7 && System.nanoTime() - deathTime > 2_000_000_000L) {
            stage = -1;
            client.stop();
        }
    }

    private void tickGuest(Minecraft client) throws Exception {
        if (stage == 0 && client.isGameLoadFinished() && client.screen != null && Files.exists(sync.resolve("port.txt"))) {
            client.options.onboardAccessibility = false;
            client.options.pauseOnLostFocus = false;
            client.options.renderDistance().set(6);
            String address = "127.0.0.1:" + Files.readString(sync.resolve("port.txt")).trim();
            stage = 1;
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Death Reset test", address, ServerData.Type.LAN), false, null);
        } else if (stage == 1 && ready(client)) {
            previousLevel = client.level;
            Files.writeString(sync.resolve("guest-ready.txt"), "ready");
            stage = 2;
        } else if (stage == 2 && client.level == null) {
            stage = 3;
        } else if (stage == 3 && ready(client) && client.level != previousLevel) {
            check(client.player.getInventory().isEmpty(), "Guest inventory cleared");
            check(client.player.experienceLevel == 0, "Guest XP cleared");
            round++;
            Files.writeString(sync.resolve(round == 1 ? "guest-round-one.txt" : "guest-passed.txt"), "Automatically rejoined round " + round);
            previousLevel = client.level;
            stage = round == 2 ? 4 : 2;
        } else if (stage == 4 && Files.exists(sync.resolve("finish.txt"))) {
            stage = -1;
            client.stop();
        }
    }

    private static boolean ready(Minecraft client) {
        return client.level != null && client.player != null && client.screen == null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
