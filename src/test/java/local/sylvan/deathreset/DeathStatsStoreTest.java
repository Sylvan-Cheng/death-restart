package local.sylvan.deathreset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DeathStatsStoreTest {
    @TempDir Path root;

    @Test
    void deathTotalsSurviveReloadAndPlayerRename() throws IOException {
        Path file = root.resolve("campaign.json");
        UUID player = UUID.randomUUID();
        var first = new DeathStatsStore(file);
        assertTrue(first.track(player, "Alice"));
        assertEquals(0, first.entries().get(player).deaths());
        first.recordDeath(player, "Alice");
        first.recordDeath(player, "Alice");
        first.save();
        var nextRound = new DeathStatsStore(file);
        nextRound.track(player, "RenamedAlice");
        assertEquals(2, nextRound.entries().get(player).deaths());
        assertEquals("RenamedAlice", nextRound.entries().get(player).name());
        nextRound.recordDeath(player, "RenamedAlice");
        nextRound.save();
        assertEquals(3, new DeathStatsStore(file).entries().get(player).deaths());
    }

    @Test
    void differentWorldsKeepIndependentTotals() throws IOException {
        UUID player = UUID.randomUUID();
        var first = new DeathStatsStore(root.resolve("one.json"));
        first.recordDeath(player, "Bob");
        first.save();
        assertTrue(new DeathStatsStore(root.resolve("two.json")).entries().isEmpty());
    }

    @Test
    void rejectsCorruptStatsWithoutOverwritingThem() throws IOException {
        Path file = root.resolve("corrupt.json");
        String contents = "{\"" + UUID.randomUUID() + "\":{\"name\":\"Alice\",\"deaths\":-1}}";
        Files.writeString(file, contents);
        assertThrows(IOException.class, () -> new DeathStatsStore(file));
        assertEquals(contents, Files.readString(file));
    }
}
