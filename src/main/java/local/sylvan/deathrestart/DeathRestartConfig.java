package local.sylvan.deathrestart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Each installation stores its own host rules and guest preferences. */
public final class DeathRestartConfig {
    public static final List<Integer> COUNTDOWN_VALUES = List.of(5, 10, 15, 20, 30, 45, 60);
    public static final List<Integer> RECONNECT_INTERVAL_VALUES = List.of(1, 3, 5, 10, 15);
    public static final List<Integer> RECONNECT_TIMEOUT_VALUES = List.of(30, 60, 120, 300);
    public static final Settings DEFAULTS = new Settings(ResetCountdown.DEFAULT_SECONDS, true, 5, 120, true);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // The integrated server reads settings on a different thread from the settings screen.
    private static volatile Settings settings = DEFAULTS;

    private DeathRestartConfig() {
    }

    public static void load() {
        Path file = configFile();
        try {
            settings = load(file);
        } catch (IOException failure) {
            settings = DEFAULTS;
            DeathRestartMod.LOGGER.warn("Could not load {}; preserving the file and using defaults", file, failure);
        }
    }

    public static Settings get() {
        return settings;
    }

    public static boolean apply(Settings next) {
        Settings normalized = next.normalized();
        try {
            write(configFile(), normalized);
            settings = normalized;
            return true;
        } catch (IOException failure) {
            DeathRestartMod.LOGGER.warn("Could not save Death Restart settings", failure);
            return false;
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("deathrestart.json");
    }

    static Settings load(Path file) throws IOException {
        Settings loaded = read(file);
        if (!Files.exists(file)) write(file, loaded);
        return loaded;
    }

    static Settings read(Path file) throws IOException {
        if (!Files.exists(file)) return DEFAULTS;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return new Settings(
                    intValue(json, "resetCountdownSeconds", DEFAULTS.resetCountdownSeconds()),
                    booleanValue(json, "automaticReconnect", DEFAULTS.automaticReconnect()),
                    intValue(json, "reconnectIntervalSeconds", DEFAULTS.reconnectIntervalSeconds()),
                    intValue(json, "reconnectTimeoutSeconds", DEFAULTS.reconnectTimeoutSeconds()),
                    booleanValue(json, "deathLeaderboard", DEFAULTS.deathLeaderboard())).normalized();
        } catch (RuntimeException failure) {
            throw new IOException("Invalid Death Restart settings: " + file, failure);
        }
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject json, String key, boolean fallback) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) return fallback;
        return value.getAsBoolean();
    }

    private static int supportedOrDefault(int value, List<Integer> allowed, int fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    static void write(Path file, Settings value) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".deathrestart-config-", ".json");
        try {
            Files.writeString(temporary, GSON.toJson(value));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record Settings(int resetCountdownSeconds, boolean automaticReconnect,
                           int reconnectIntervalSeconds, int reconnectTimeoutSeconds,
                           boolean deathLeaderboard) {
        public Settings normalized() {
            return new Settings(
                    supportedOrDefault(resetCountdownSeconds, COUNTDOWN_VALUES, DEFAULTS.resetCountdownSeconds()),
                    automaticReconnect,
                    supportedOrDefault(reconnectIntervalSeconds, RECONNECT_INTERVAL_VALUES,
                            DEFAULTS.reconnectIntervalSeconds()),
                    supportedOrDefault(reconnectTimeoutSeconds, RECONNECT_TIMEOUT_VALUES,
                            DEFAULTS.reconnectTimeoutSeconds()),
                    deathLeaderboard);
        }
    }
}
