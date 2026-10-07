package local.sylvan.deathrestart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DeathRestartConfigTest {
    @TempDir Path root;

    @Test
    void settingsSurviveSaveAndReload() throws IOException {
        Path file = root.resolve("deathrestart.json");
        var settings = new DeathRestartConfig.Settings(45, false, 15, 300, false);
        DeathRestartConfig.write(file, settings);
        assertEquals(settings, DeathRestartConfig.read(file));
        assertEquals(1, fileCount());
    }

    @Test
    void migratesLegacySettingsToTheNewIdPath() throws IOException {
        Path legacy = root.resolve("deathreset.json");
        Path current = root.resolve("deathrestart.json");
        var settings = new DeathRestartConfig.Settings(20, true, 3, 60, false);
        DeathRestartConfig.write(legacy, settings);

        assertEquals(settings, DeathRestartConfig.load(current, legacy));
        assertEquals(settings, DeathRestartConfig.read(current));
        assertEquals(settings, DeathRestartConfig.read(legacy));
    }

    @Test
    void missingFieldsKeepDefaultsWithoutChangingValidFields() throws IOException {
        assertEquals(DeathRestartConfig.DEFAULTS, DeathRestartConfig.read(root.resolve("missing.json")));
        Path file = root.resolve("partial.json");
        Files.writeString(file, "{\"resetCountdownSeconds\":20,\"automaticReconnect\":false}");
        assertEquals(new DeathRestartConfig.Settings(20, false, 5, 120, true), DeathRestartConfig.read(file));
    }

    @Test
    void rejectsFractionalOverflowAndWrongJsonTypesPerField() throws IOException {
        Path file = root.resolve("invalid-values.json");
        Files.writeString(file, """
                {"resetCountdownSeconds":5.9,"automaticReconnect":"false",
                 "reconnectIntervalSeconds":4294967297,"reconnectTimeoutSeconds":{},
                 "deathLeaderboard":false}
                """);
        assertEquals(new DeathRestartConfig.Settings(10, true, 5, 120, false), DeathRestartConfig.read(file));
        Files.writeString(file, "{\"resetCountdownSeconds\":0,\"reconnectIntervalSeconds\":-1,\"reconnectTimeoutSeconds\":999}");
        assertEquals(DeathRestartConfig.DEFAULTS, DeathRestartConfig.read(file));
    }

    @Test
    void damagedJsonIsReportedAndPreservedForRecovery() throws IOException {
        Path file = root.resolve("damaged.json");
        String contents = "{broken";
        Files.writeString(file, contents);
        assertThrows(IOException.class, () -> DeathRestartConfig.read(file));
        assertEquals(contents, Files.readString(file));
    }

    @Test
    void failedSaveCleansUpTemporaryFiles() throws IOException {
        Path directoryAtFilePath = Files.createDirectory(root.resolve("deathrestart.json"));
        assertThrows(IOException.class, () -> DeathRestartConfig.write(directoryAtFilePath, DeathRestartConfig.DEFAULTS));
        assertTrue(Files.isDirectory(directoryAtFilePath));
        assertEquals(1, fileCount());
    }

    private long fileCount() throws IOException {
        try (var files = Files.list(root)) {
            return files.count();
        }
    }
}
