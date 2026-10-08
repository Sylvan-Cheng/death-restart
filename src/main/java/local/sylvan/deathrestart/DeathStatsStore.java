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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stored outside saves so a new seed keeps the campaign's death totals. */
public final class DeathStatsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String UUID_PREFIX = "uuid:";
    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public DeathStatsStore(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var player : json.entrySet()) {
                JsonObject value = player.getValue().getAsJsonObject();
                String name = value.get("name").getAsString();
                int deaths = value.get("deaths").getAsBigDecimal().intValueExact();
                if (name.isBlank() || name.length() > 64 || deaths < 0) {
                    throw new IllegalArgumentException("Invalid death stats");
                }
                String key = player.getKey();
                if (key.startsWith(UUID_PREFIX)) {
                    UUID id = UUID.fromString(key.substring(UUID_PREFIX.length()));
                    if (!key.equals(uuidKey(id))) throw new IllegalArgumentException("Invalid UUID death stats key");
                } else if (!key.equals(name.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Death stats keys must match lowercase player names");
                }
                entries.put(key, new Entry(name, deaths));
            }
        } catch (RuntimeException failure) {
            throw new InvalidStatsException(file, failure);
        }
    }

    /** Preserve unsupported or damaged data before starting a writable, independent history. */
    public static LoadResult load(Path file) throws IOException {
        try {
            return new LoadResult(new DeathStatsStore(file), null);
        } catch (InvalidStatsException invalid) {
            Path preserved = file.resolveSibling(file.getFileName() + ".invalid-" + UUID.randomUUID() + ".bak");
            // A failed move must leave the original in place; never replace a previous backup.
            Files.move(file, preserved);
            return new LoadResult(new DeathStatsStore(file), preserved);
        }
    }

    public boolean track(String name) {
        return track(null, name, DeathRestartConfig.StatisticsMode.USERNAME);
    }

    public boolean track(UUID id, String name, DeathRestartConfig.StatisticsMode mode) {
        String key = identityKey(id, name, mode);
        Entry previous = entries.get(key);
        if (previous != null && previous.name().equals(name)) return false;
        entries.put(key, new Entry(name, previous == null ? 0 : previous.deaths()));
        return true;
    }

    public void recordDeath(String name) {
        recordDeath(null, name, DeathRestartConfig.StatisticsMode.USERNAME);
    }

    public void recordDeath(UUID id, String name, DeathRestartConfig.StatisticsMode mode) {
        track(id, name, mode);
        String key = identityKey(id, name, mode);
        Entry previous = entries.get(key);
        int deaths = previous.deaths() == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.deaths() + 1;
        entries.put(key, new Entry(name, deaths));
    }

    public Map<String, Entry> entries() {
        return entries(DeathRestartConfig.StatisticsMode.USERNAME);
    }

    public Map<String, Entry> entries(DeathRestartConfig.StatisticsMode mode) {
        boolean uuidMode = mode == DeathRestartConfig.StatisticsMode.UUID;
        var selected = new LinkedHashMap<String, Entry>();
        entries.forEach((key, entry) -> {
            if (key.startsWith(UUID_PREFIX) == uuidMode) selected.put(key, entry);
        });
        return Map.copyOf(selected);
    }

    private static String identityKey(UUID id, String name, DeathRestartConfig.StatisticsMode mode) {
        return mode == DeathRestartConfig.StatisticsMode.UUID ? uuidKey(id) : name.toLowerCase(Locale.ROOT);
    }

    private static String uuidKey(UUID id) {
        return UUID_PREFIX + Objects.requireNonNull(id, "UUID mode requires a player UUID");
    }

    public void clear() throws IOException {
        clearAndTrack(Map.of(), DeathRestartConfig.StatisticsMode.USERNAME);
    }

    /** A failed save leaves both the displayed totals and the stored history intact. */
    public void clearAndTrack(Map<UUID, String> onlinePlayers, DeathRestartConfig.StatisticsMode mode) throws IOException {
        var previous = new LinkedHashMap<>(entries);
        entries.clear();
        try {
            onlinePlayers.forEach((id, name) -> track(id, name, mode));
            save();
        } catch (IOException failure) {
            entries.clear();
            entries.putAll(previous);
            throw failure;
        }
    }

    public void save() throws IOException {
        JsonObject json = new JsonObject();
        entries.forEach((key, entry) -> {
            JsonObject value = new JsonObject();
            value.addProperty("name", entry.name());
            value.addProperty("deaths", entry.deaths());
            json.add(key, value);
        });
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".death-stats-", ".json");
        try {
            Files.writeString(temporary, GSON.toJson(json));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record Entry(String name, int deaths) {
    }

    public record LoadResult(DeathStatsStore stats, Path preservedFile) {
    }

    private static final class InvalidStatsException extends IOException {
        private InvalidStatsException(Path file, RuntimeException cause) {
            super("Invalid death leaderboard file: " + file, cause);
        }
    }
}
