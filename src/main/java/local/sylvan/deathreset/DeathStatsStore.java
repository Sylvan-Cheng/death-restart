package local.sylvan.deathreset;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stored outside saves so a new seed keeps the campaign's death totals. */
public final class DeathStatsStore {
    private final Path file;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public DeathStatsStore(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var player : json.entrySet()) {
                UUID id = UUID.fromString(player.getKey());
                JsonObject value = player.getValue().getAsJsonObject();
                String name = value.get("name").getAsString();
                int deaths = value.get("deaths").getAsBigDecimal().intValueExact();
                if (name.isBlank() || name.length() > 64 || deaths < 0) throw new IllegalArgumentException("Invalid death stats");
                entries.put(id, new Entry(name, deaths));
            }
        } catch (RuntimeException failure) {
            throw new IOException("Invalid death leaderboard file: " + file, failure);
        }
    }

    public boolean track(UUID id, String name) {
        Entry previous = entries.get(id);
        if (previous != null && previous.name().equals(name)) return false;
        entries.put(id, new Entry(name, previous == null ? 0 : previous.deaths()));
        return true;
    }

    public void recordDeath(UUID id, String name) {
        track(id, name);
        Entry previous = entries.get(id);
        entries.put(id, new Entry(name, previous.deaths() == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.deaths() + 1));
    }

    public Map<UUID, Entry> entries() {
        return Map.copyOf(entries);
    }

    public void save() throws IOException {
        JsonObject json = new JsonObject();
        entries.forEach((id, entry) -> {
            JsonObject value = new JsonObject();
            value.addProperty("name", entry.name());
            value.addProperty("deaths", entry.deaths());
            json.add(id.toString(), value);
        });
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".death-stats-", ".json");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException failure) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Entry(String name, int deaths) {
    }
}
