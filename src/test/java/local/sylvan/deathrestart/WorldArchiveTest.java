package local.sylvan.deathrestart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        var second = WorldArchive.prepare(saves, world, root.resolve("backups"));
        assertNotEquals(first.backupPath(), second.backupPath());
        assertEquals("original-world", Files.readString(first.backupPath().resolve("level.dat")));
        assertEquals("round-two", Files.readString(second.backupPath().resolve("level.dat")));
    }
}
