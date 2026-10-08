package local.sylvan.deathrestart;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class WorldArchiveTest {
    @TempDir Path root;

    private Path createWorld(Path parent) throws IOException {
        Path world = Files.createDirectories(parent.resolve("多人 极限生存"));
        Files.writeString(world.resolve("level.dat"), "original-world");
        for (String folder : new String[]{"region", "DIM-1/region", "DIM1/region", "playerdata", "advancements", "stats", "data"}) {
            Files.createDirectories(world.resolve(folder));
            Files.writeString(world.resolve(folder).resolve("old.dat"), "old-" + folder);
        }
        Files.createDirectories(world.resolve("datapacks/custom/data"));
        Files.writeString(world.resolve("datapacks/custom/data/pack.json"), "world-input");
        Files.writeString(world.resolve("resources.zip"), "resource-pack");
        return world;
    }

    @Test
    void archivesAllDimensionsAndProgressButOnlyCarriesCreationInputsToTheNewWorld() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        var transaction = WorldArchive.prepare(saves, world, root.resolve("backups"));
        assertEquals("original-world", Files.readString(transaction.backupPath().resolve("level.dat")));
        for (String folder : new String[]{"region", "DIM-1/region", "DIM1/region", "playerdata", "advancements", "stats", "data"}) {
            assertEquals("old-" + folder, Files.readString(transaction.backupPath().resolve(folder).resolve("old.dat")));
            assertFalse(Files.exists(world.resolve(folder)));
        }
        assertFalse(Files.exists(world.resolve("level.dat")));
        assertEquals("world-input", Files.readString(world.resolve("datapacks/custom/data/pack.json")));
        assertEquals("resource-pack", Files.readString(world.resolve("resources.zip")));
    }

    @Test
    void preservesLanWorldPnpConfigAcrossConsecutiveResets() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        String config = "{\"onlineMode\":false,\"enableUUIDFixer\":false,\"maxPlayers\":12}";
        Files.writeString(world.resolve("mcwifipnp.json"), config);

        var first = WorldArchive.prepare(saves, world, root.resolve("backups"));
        assertEquals(config, Files.readString(world.resolve("mcwifipnp.json")));
        assertEquals(config, Files.readString(first.backupPath().resolve("mcwifipnp.json")));

        Files.writeString(world.resolve("level.dat"), "round-two");
        first.complete();
        var second = WorldArchive.prepare(saves, world, root.resolve("backups"));
        assertEquals(config, Files.readString(world.resolve("mcwifipnp.json")));
        assertEquals(config, Files.readString(second.backupPath().resolve("mcwifipnp.json")));
    }

    @Test
    void recoveryRestoresTheOldWorldAndKeepsTheFailedAttempt() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        var transaction = WorldArchive.prepare(saves, world, root.resolve("backups"));
        Files.writeString(world.resolve("level.dat"), "failed-new-world");
        transaction.restore();
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
        assertEquals("old-playerdata", Files.readString(world.resolve("playerdata/old.dat")));
        assertEquals("failed-new-world", Files.readString(transaction.failedWorldPath().resolve("level.dat")));
    }

    @Test
    void rejectsAWorldOutsideSavesWithoutMovingIt() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(root);
        assertThrows(IOException.class, () -> WorldArchive.prepare(saves, world, root.resolve("backups")));
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void rejectsBackupInsideTheWorldWithoutMovingIt() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        assertThrows(IOException.class, () -> WorldArchive.prepare(saves, world, world.resolve("backups")));
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void consecutiveRoundsHaveDifferentBackups() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        var first = WorldArchive.prepare(saves, world, root.resolve("backups"));
        Files.writeString(world.resolve("level.dat"), "round-two");
        first.complete();
        var second = WorldArchive.prepare(saves, world, root.resolve("backups"));
        assertNotEquals(first.backupPath(), second.backupPath());
        assertEquals("original-world", Files.readString(first.backupPath().resolve("level.dat")));
        assertEquals("round-two", Files.readString(second.backupPath().resolve("level.dat")));
    }

    @Test
    void cleanupKeepsNewestSuccessfulBackupsAndNeverTouchesFailedWorlds() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backupRoot = root.resolve("backups");
        var first = WorldArchive.prepare(saves, world, backupRoot);
        Files.setLastModifiedTime(first.backupPath(), FileTime.fromMillis(1_000));
        Files.writeString(world.resolve("level.dat"), "round-two");
        first.complete();
        var second = WorldArchive.prepare(saves, world, backupRoot);
        Files.setLastModifiedTime(second.backupPath(), FileTime.fromMillis(2_000));
        Files.writeString(world.resolve("level.dat"), "round-three");
        second.complete();
        var third = WorldArchive.prepare(saves, world, backupRoot);
        Files.writeString(world.resolve("level.dat"), "round-four");
        third.complete();
        Files.setLastModifiedTime(third.backupPath(), FileTime.fromMillis(3_000));
        Files.createDirectories(third.failedWorldPath());
        Files.writeString(third.failedWorldPath().resolve("level.dat"), "failed-new-world");

        assertEquals(1, WorldArchive.cleanupBackups(backupRoot, world.getFileName().toString(), 2));
        assertFalse(Files.exists(first.backupPath()));
        assertTrue(Files.exists(second.backupPath()));
        assertTrue(Files.exists(third.backupPath()));
        assertTrue(Files.exists(third.failedWorldPath()));
        assertEquals(0, WorldArchive.cleanupBackups(backupRoot, world.getFileName().toString(), 0));
    }

    @Test
    void startupRestoresIncompleteResetAndKeepsPartiallyGeneratedWorld() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("level.dat"), "partially-generated");

        var recovery = WorldArchive.recoverIncomplete(saves, backups);
        assertEquals(List.of(world.getFileName().toString()), recovery.restoredWorlds());
        assertTrue(recovery.failures().isEmpty());
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
        assertEquals("partially-generated", Files.readString(interrupted.failedWorldPath().resolve("level.dat")));
        assertFalse(Files.exists(interrupted.journalPath()));
        assertTrue(WorldArchive.recoverIncomplete(saves, backups).restoredWorlds().isEmpty());
    }

    @Test
    void startupRecoversCrashBetweenArchiveAndStagingMoves() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        Files.move(world, interrupted.stagingPath());
        assertFalse(Files.exists(world));

        assertTrue(WorldArchive.recoverIncomplete(saves, backups).failures().isEmpty());
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(interrupted.stagingPath()));
    }

    @Test
    void journalWrittenBeforeFirstMoveLeavesOriginalWorldUntouched() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        Files.move(world, interrupted.stagingPath());
        Files.move(interrupted.backupPath(), world);

        assertTrue(WorldArchive.recoverIncomplete(saves, backups).failures().isEmpty());
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(interrupted.failedWorldPath()));
        assertFalse(Files.exists(interrupted.stagingPath()));
    }

    @Test
    void startupResumesRollbackInterruptedAtEitherMove() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("level.dat"), "partial");
        Files.writeString(interrupted.journalPath(), Files.readString(interrupted.journalPath())
                .replace("RESETTING", "RESTORING"));
        Files.move(world, interrupted.failedWorldPath());
        assertTrue(WorldArchive.recoverIncomplete(saves, backups).failures().isEmpty());
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));

        var second = WorldArchive.prepare(saves, world, backups);
        Files.writeString(second.journalPath(), Files.readString(second.journalPath()).replace("RESETTING", "RESTORING"));
        Files.move(world, second.failedWorldPath());
        Files.move(second.backupPath(), world);
        assertTrue(WorldArchive.recoverIncomplete(saves, backups).failures().isEmpty());
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void completedResetNeverRollsBackEvenWhenCommitJournalRemains() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var completed = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("level.dat"), "new-successful-world");
        String journal = Files.readString(completed.journalPath()).replace("RESETTING", "COMMITTED");
        completed.complete();
        Files.writeString(completed.journalPath(), journal);

        var recovery = WorldArchive.recoverIncomplete(saves, backups);
        assertTrue(recovery.restoredWorlds().isEmpty());
        assertTrue(recovery.failures().isEmpty());
        assertEquals("new-successful-world", Files.readString(world.resolve("level.dat")));
        assertTrue(Files.exists(completed.backupPath()));
        assertFalse(Files.exists(completed.journalPath()));
    }

    @Test
    void lockedWorldIsNotMovedOrOverwrittenAndRecoveryCanRetry() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("session.lock"), "lock");
        try (var channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertEquals(1, WorldArchive.recoverIncomplete(saves, backups).failures().size());
            assertTrue(Files.exists(interrupted.backupPath()));
            assertTrue(Files.exists(interrupted.journalPath()));
        }
        assertTrue(WorldArchive.recoverIncomplete(saves, backups).failures().isEmpty());
        assertEquals("original-world", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void invalidJournalNeverMovesWorldAndBlocksCleanup() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        String malicious = Files.readString(interrupted.journalPath()).replace(world.getFileName().toString(), "../outside");
        Files.writeString(interrupted.journalPath(), malicious);

        assertEquals(1, WorldArchive.recoverIncomplete(saves, backups).failures().size());
        assertEquals("original-world", Files.readString(interrupted.backupPath().resolve("level.dat")));
        assertEquals(malicious, Files.readString(interrupted.journalPath()));
        assertThrows(IOException.class, () -> WorldArchive.cleanupBackups(backups, world.getFileName().toString(), 1));
    }

    @Test
    void cleanupProtectsPendingBackupsAndKeepsNewestCompletedBackup() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var first = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("level.dat"), "round-two");
        first.complete();
        Files.setLastModifiedTime(first.backupPath(), FileTime.fromMillis(1_000));
        var second = WorldArchive.prepare(saves, world, backups);
        assertEquals(WorldArchive.BackupKind.PENDING, WorldArchive.listBackups(backups, world.getFileName().toString())
                .stream().filter(backup -> backup.path().equals(second.backupPath())).findFirst().orElseThrow().kind());
        assertEquals(0, WorldArchive.cleanupBackups(backups, world.getFileName().toString(), 1));
        Files.writeString(world.resolve("level.dat"), "round-three");
        second.complete();
        Files.setLastModifiedTime(second.backupPath(), FileTime.fromMillis(2_000));
        assertEquals(1, WorldArchive.cleanupBackups(backups, world.getFileName().toString(), 1));
        assertFalse(Files.exists(first.backupPath()));
        assertTrue(Files.exists(second.backupPath()));
    }

    @Test
    void cleanupIgnoresAnotherWorldWithTheSamePrefixAndUnrelatedDirectories() throws IOException {
        Path backups = Files.createDirectory(root.resolve("backups"));
        Path own = Files.createDirectory(backups.resolve("world-20261008-100000-12345678"));
        Files.writeString(own.resolve("level.dat"), "own");
        Path other = Files.createDirectory(backups.resolve("world-extra-20261008-100000-12345678"));
        Files.writeString(other.resolve("level.dat"), "other");
        Files.createDirectory(backups.resolve("world-unrelated"));
        assertEquals(List.of(own.toRealPath()), WorldArchive.listBackups(backups, "world").stream()
                .map(WorldArchive.Backup::path).toList());
        assertTrue(Files.exists(other));
    }

    @Test
    void cleanupNeverTreatsFailedWorldDirectoriesAsAnotherWorldsSuccessfulBackups() throws IOException {
        Path backups = Files.createDirectory(root.resolve("backups"));
        for (String suffix : List.of("20261008-100000-12345678", "20261008-110000-12345678")) {
            Path failed = Files.createDirectory(backups.resolve("failed-world-" + suffix));
            Files.writeString(failed.resolve("level.dat"), "failed-attempt");
        }
        var entries = WorldArchive.listBackups(backups, "failed-world");
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(backup -> backup.kind() == WorldArchive.BackupKind.FAILED));
        assertEquals(0, WorldArchive.cleanupBackups(backups, "failed-world", 1));
        assertTrue(entries.stream().allMatch(backup -> Files.exists(backup.path())));
    }

    @Test
    void committedJournalDoesNotPreventAnotherResetInTheSameSession() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var completed = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("level.dat"), "new-successful-world");
        String journal = Files.readString(completed.journalPath()).replace("RESETTING", "COMMITTED");
        completed.complete();
        Files.writeString(completed.journalPath(), journal);
        var next = WorldArchive.prepare(saves, world, backups);
        assertEquals("new-successful-world", Files.readString(next.backupPath().resolve("level.dat")));
        assertEquals(1, WorldArchive.recoverIncomplete(saves, backups).restoredWorlds().size());
        assertEquals("new-successful-world", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void missingArchiveCannotBeMistakenForSuccessfulRollback() throws IOException {
        Path saves = Files.createDirectory(root.resolve("saves"));
        Path world = createWorld(saves);
        Path backups = root.resolve("backups");
        var interrupted = WorldArchive.prepare(saves, world, backups);
        Files.writeString(world.resolve("level.dat"), "partial-world");
        Files.move(interrupted.backupPath(), backups.resolve("manually-moved-archive"));

        assertEquals(1, WorldArchive.recoverIncomplete(saves, backups).failures().size());
        assertEquals("partial-world", Files.readString(world.resolve("level.dat")));
        assertTrue(Files.exists(interrupted.journalPath()));
        assertFalse(Files.exists(interrupted.failedWorldPath()));
    }
}
