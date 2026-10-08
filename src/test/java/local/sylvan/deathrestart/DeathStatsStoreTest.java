package local.sylvan.deathrestart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DeathStatsStoreTest {
    @TempDir Path root;

    @Test
    void deathTotalsSurviveReload() throws IOException {
        Path file = root.resolve("campaign.json");
        var first = new DeathStatsStore(file);
        assertTrue(first.track("Alice"));
        assertEquals(0, first.entries().get("alice").deaths());
        first.recordDeath("Alice");
        first.recordDeath("Alice");
        first.save();

        var nextRound = new DeathStatsStore(file);
        assertFalse(nextRound.track("Alice"));
        assertEquals(2, nextRound.entries().get("alice").deaths());
        nextRound.recordDeath("Alice");
        nextRound.save();
        assertEquals(3, new DeathStatsStore(file).entries().get("alice").deaths());
    }

    @Test
    void caseChangesKeepOnePlayerAndOneTotal() throws IOException {
        Path file = root.resolve("campaign.json");
        var stats = new DeathStatsStore(file);
        stats.recordDeath("Alice");
        stats.recordDeath("ALICE");
        assertEquals(1, stats.entries().size());
        assertEquals(2, stats.entries().get("alice").deaths());
        assertFalse(stats.track("ALICE"));
        stats.save();

        var reloaded = new DeathStatsStore(file);
        assertTrue(reloaded.track("Alice"));
        assertEquals(1, reloaded.entries().size());
        assertEquals(2, reloaded.entries().get("alice").deaths());
        assertEquals("Alice", reloaded.entries().get("alice").name());
        assertFalse(reloaded.track("Alice"));
    }

    @Test
    void keepsUsernameAndUuidTotalsSeparateAcrossModeSwitches() throws IOException {
        Path file = root.resolve("campaign.json");
        UUID account = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAccount = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var stats = new DeathStatsStore(file);
        var usernameMode = DeathRestartConfig.StatisticsMode.USERNAME;
        var uuidMode = DeathRestartConfig.StatisticsMode.UUID;

        stats.recordDeath(account, "Alice", usernameMode);
        stats.recordDeath(secondAccount, "alice", usernameMode);
        stats.recordDeath(account, "Alice", uuidMode);
        stats.track(account, "RenamedAlice", uuidMode);

        assertEquals(1, stats.entries(usernameMode).size());
        assertEquals(2, stats.entries(usernameMode).get("alice").deaths());
        assertEquals(1, stats.entries(uuidMode).size());
        assertEquals("RenamedAlice", stats.entries(uuidMode).get("uuid:" + account).name());
        assertEquals(1, stats.entries(uuidMode).get("uuid:" + account).deaths());

        stats.save();
        var reloaded = new DeathStatsStore(file);
        assertEquals(stats.entries(usernameMode), reloaded.entries(usernameMode));
        assertEquals(stats.entries(uuidMode), reloaded.entries(uuidMode));
    }

    @Test
    void rejectsUuidKeysWithoutMigratingOrOverwritingTheFile() throws IOException {
        Path file = root.resolve("unsupported.json");
        String contents = """
                {
                  "00000000-0000-0000-0000-000000000001": {"name": "Alice", "deaths": 2},
                  "00000000-0000-0000-0000-000000000002": {"name": "alice", "deaths": 3},
                  "00000000-0000-0000-0000-000000000003": {"name": "Bob", "deaths": 4}
                }
                """;
        Files.writeString(file, contents);
        assertThrows(IOException.class, () -> new DeathStatsStore(file));
        assertEquals(contents, Files.readString(file));
    }

    @Test
    void newDeathsSaturateInsteadOfOverflowing() throws IOException {
        Path file = root.resolve("campaign.json");
        Files.writeString(file, "{\"alice\":{\"name\":\"Alice\",\"deaths\":"
                + Integer.MAX_VALUE + "}}");
        var stats = new DeathStatsStore(file);
        assertEquals(1, stats.entries().size());
        assertEquals(Integer.MAX_VALUE, stats.entries().get("alice").deaths());
        stats.recordDeath("Alice");
        assertEquals(Integer.MAX_VALUE, stats.entries().get("alice").deaths());
    }

    @Test
    void rejectsKeysThatDoNotMatchTheStoredName() throws IOException {
        Path file = root.resolve("mismatched.json");
        String contents = "{\"alice\":{\"name\":\"Bob\",\"deaths\":3}}";
        Files.writeString(file, contents);
        assertThrows(IOException.class, () -> new DeathStatsStore(file));
        assertEquals(contents, Files.readString(file));
    }

    @Test
    void differentNamesHaveSeparateTotals() throws IOException {
        var stats = new DeathStatsStore(root.resolve("campaign.json"));
        stats.recordDeath("Alice");
        stats.recordDeath("RenamedAlice");
        assertEquals(2, stats.entries().size());
        assertEquals(1, stats.entries().get("alice").deaths());
        assertEquals(1, stats.entries().get("renamedalice").deaths());
    }

    @Test
    void differentWorldsKeepIndependentTotals() throws IOException {
        var first = new DeathStatsStore(root.resolve("one.json"));
        first.recordDeath("Bob");
        first.save();
        assertTrue(new DeathStatsStore(root.resolve("two.json")).entries().isEmpty());
    }

    @Test
    void clearRemovesBothIdentityModesAndLeavesOtherWorldsAlone() throws IOException {
        Path file = root.resolve("campaign.json");
        var stats = new DeathStatsStore(file);
        var account = UUID.randomUUID();
        stats.recordDeath("Alice");
        stats.recordDeath(account, "Alice", DeathRestartConfig.StatisticsMode.UUID);
        stats.save();
        var other = new DeathStatsStore(root.resolve("other.json"));
        other.recordDeath("Bob");
        other.save();

        stats.clearAndTrack(Map.of(account, "Alice"), DeathRestartConfig.StatisticsMode.USERNAME);
        assertEquals(0, stats.entries().get("alice").deaths());
        assertTrue(stats.entries(DeathRestartConfig.StatisticsMode.UUID).isEmpty());
        var reloaded = new DeathStatsStore(file);
        assertEquals(0, reloaded.entries().get("alice").deaths());
        assertTrue(reloaded.entries(DeathRestartConfig.StatisticsMode.UUID).isEmpty());
        assertEquals(1, new DeathStatsStore(root.resolve("other.json")).entries().get("bob").deaths());
        stats.recordDeath("Alice");
        stats.save();
        assertEquals(1, new DeathStatsStore(file).entries().get("alice").deaths());
    }

    @Test
    void failedClearRestoresSessionTotalsAndRemovesTemporaryFiles() throws IOException {
        Path file = root.resolve("campaign.json");
        var stats = new DeathStatsStore(file);
        var account = UUID.randomUUID();
        stats.recordDeath("Alice");
        stats.recordDeath(account, "Alice", DeathRestartConfig.StatisticsMode.UUID);
        stats.save();
        Path previous = Files.move(file, root.resolve("previous.json"));
        Files.createDirectory(file);
        Files.writeString(file.resolve("occupied.txt"), "occupied");

        assertThrows(IOException.class, stats::clear);
        assertEquals(1, stats.entries().get("alice").deaths());
        assertEquals(1, stats.entries(DeathRestartConfig.StatisticsMode.UUID).get("uuid:" + account).deaths());
        assertEquals(1, new DeathStatsStore(previous).entries().get("alice").deaths());
        assertEquals("occupied", Files.readString(file.resolve("occupied.txt")));
        try (var files = Files.list(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".death-stats-")));
        }
    }

    @Test
    void rejectsCorruptStatsWithoutOverwritingThem() throws IOException {
        Path file = root.resolve("corrupt.json");
        String contents = "{\"alice\":{\"name\":\"Alice\",\"deaths\":-1}}";
        Files.writeString(file, contents);
        assertThrows(IOException.class, () -> new DeathStatsStore(file));
        assertEquals(contents, Files.readString(file));
    }

    @Test
    void unsupportedHistoryIsPreservedAndNewDeathsSurviveWorldResets() throws IOException {
        Path file = root.resolve("campaign.json");
        String original = "{\"00000000-0000-0000-0000-000000000001\":{\"name\":\"Alice\",\"deaths\":4}}";
        Files.writeString(file, original);

        var recovered = DeathStatsStore.load(file);
        assertEquals(original, Files.readString(recovered.preservedFile()));
        assertTrue(recovered.stats().entries().isEmpty());
        recovered.stats().recordDeath("Alice");
        recovered.stats().save();

        var nextRound = DeathStatsStore.load(file);
        assertNull(nextRound.preservedFile());
        assertEquals(1, nextRound.stats().entries().get("alice").deaths());
        nextRound.stats().recordDeath("Alice");
        nextRound.stats().save();
        assertEquals(2, DeathStatsStore.load(file).stats().entries().get("alice").deaths());
        assertEquals(original, Files.readString(recovered.preservedFile()));
    }

    @Test
    void corruptHistoryIsPreservedWithoutImportingPartialEntries() throws IOException {
        Path file = root.resolve("campaign.json");
        String original = "{\"alice\":{\"name\":\"Alice\",\"deaths\":3},\"bob\":{\"name\":\"Bob\",\"deaths\":-1}}";
        Files.writeString(file, original);

        var recovered = DeathStatsStore.load(file);
        assertTrue(recovered.stats().entries().isEmpty());
        assertEquals(original, Files.readString(recovered.preservedFile()));
        recovered.stats().recordDeath("Bob");
        recovered.stats().save();
        assertEquals(Map.of("bob", new DeathStatsStore.Entry("Bob", 1)),
                new DeathStatsStore(file).entries());
    }

    @Test
    void validHistoryIsLoadedWithoutCreatingBackups() throws IOException {
        Path file = root.resolve("campaign.json");
        var stats = new DeathStatsStore(file);
        stats.recordDeath("Alice");
        stats.save();

        var loaded = DeathStatsStore.load(file);
        assertNull(loaded.preservedFile());
        assertEquals(1, loaded.stats().entries().get("alice").deaths());
        try (var files = Files.list(root)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void filesystemReadFailuresAreNotTreatedAsInvalidHistory() throws IOException {
        Path file = Files.createDirectory(root.resolve("campaign.json"));
        Files.writeString(file.resolve("keep.txt"), "preserve");

        assertThrows(IOException.class, () -> DeathStatsStore.load(file));
        assertEquals("preserve", Files.readString(file.resolve("keep.txt")));
        try (var files = Files.list(root)) {
            assertEquals(1, files.count());
        }
    }
}
