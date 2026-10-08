package local.sylvan.deathrestart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Journals world moves; recovery and retention share the same lock. */
public final class WorldArchive {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String SUFFIX = "[0-9]{8}-[0-9]{6}-[0-9a-f]{8}";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WorldArchive() {
    }

    public static synchronized Transaction prepare(Path savesRoot, Path worldPath, Path backupRoot) throws IOException {
        Path saves = savesRoot.toRealPath();
        Path source = worldPath.toAbsolutePath().normalize();
        requireName(source.getFileName().toString());
        requireChild(source, saves);
        requireWorld(source);
        requireUnlocked(source);
        Files.createDirectories(backupRoot);
        Path backups = backupRoot.toRealPath();
        if (backups.startsWith(source) || source.startsWith(backups)) {
            throw new IOException("Backup directory must be outside the world: " + backups);
        }
        for (Path journal : journals(backups)) {
            Journal previous = readJournal(journal);
            if (previous.phase() != Phase.COMMITTED && previous.worldName().equals(source.getFileName().toString())) {
                throw new IOException("An unfinished reset already exists: " + journal);
            }
        }
        String suffix = TIMESTAMP.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path staging = Files.createTempDirectory(backups, ".pending-");
        var journal = new Journal(1, source.getFileName().toString(), suffix,
                staging.getFileName().toString(), Phase.RESETTING);
        Path journalPath = backups.resolve(".reset-" + UUID.randomUUID() + ".json");
        Transaction transaction = transaction(saves, backups, journalPath, journal);
        try {
            copyIfPresent(source.resolve("datapacks"), staging.resolve("datapacks"));
            copyIfPresent(source.resolve("resources.zip"), staging.resolve("resources.zip"));
            copyIfPresent(source.resolve("mcwifipnp.json"), staging.resolve("mcwifipnp.json"));
            // Force the journal before the first move of the only complete world.
            writeJournal(journalPath, journal);
            Files.move(source, transaction.backupPath());
            Files.move(staging, source);
            return transaction;
        } catch (IOException failure) {
            try {
                if (Files.exists(journalPath, LinkOption.NOFOLLOW_LINKS)) transaction.restore();
                else deleteTree(staging);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public static synchronized Recovery recoverIncomplete(Path savesRoot, Path backupRoot) throws IOException {
        if (!Files.exists(backupRoot, LinkOption.NOFOLLOW_LINKS)) return new Recovery(List.of(), List.of());
        Path backups = backupRoot.toRealPath();
        List<String> restored = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Path file : journals(backups)) {
            try {
                Journal journal = readJournal(file);
                if (journal.phase() == Phase.COMMITTED) {
                    Files.delete(file);
                    continue;
                }
                var transaction = transaction(savesRoot.toRealPath(), backups, file, journal);
                transaction.restore();
                restored.add(journal.worldName());
            } catch (IOException failure) {
                failures.add(file.getFileName() + ": " + failure.getMessage());
            }
        }
        return new Recovery(List.copyOf(restored), List.copyOf(failures));
    }

    public static synchronized List<Backup> listBackups(Path backupRoot, String worldName) throws IOException {
        requireName(worldName);
        if (!Files.exists(backupRoot, LinkOption.NOFOLLOW_LINKS)) return List.of();
        Path backups = backupRoot.toRealPath();
        var pending = new HashSet<String>();
        for (Path file : journals(backups)) {
            Journal journal = readJournal(file);
            if (journal.phase() != Phase.COMMITTED) pending.add(journal.worldName() + "-" + journal.suffix());
        }
        Pattern successful = Pattern.compile(Pattern.quote(worldName) + "-" + SUFFIX);
        Pattern failed = Pattern.compile("failed-" + Pattern.quote(worldName) + "-" + SUFFIX);
        List<Backup> result = new ArrayList<>();
        try (var entries = Files.list(backups)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName().toString();
                if (!successful.matcher(name).matches() && !failed.matcher(name).matches()) continue;
                requireChild(path, backups);
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                // Reserve failed-* even when the save itself has that prefix.
                BackupKind kind = BackupKind.SUCCESSFUL;
                if (name.startsWith("failed-")) kind = BackupKind.FAILED;
                else if (pending.contains(name)) kind = BackupKind.PENDING;
                if (kind == BackupKind.SUCCESSFUL
                        && !Files.isRegularFile(path.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) continue;
                result.add(new Backup(path, kind, Files.getLastModifiedTime(path)));
            }
        }
        result.sort(Comparator.comparing(Backup::modified).reversed()
                .thenComparing(backup -> backup.path().getFileName().toString(), Comparator.reverseOrder()));
        return List.copyOf(result);
    }

    /** Zero disables automatic cleanup. Failed and journal-referenced backups are always excluded. */
    public static synchronized int cleanupBackups(Path backupRoot, String worldName, int retentionCount)
            throws IOException {
        if (retentionCount < 0) throw new IllegalArgumentException("Backup retention cannot be negative");
        if (retentionCount == 0) return 0;
        List<Path> candidates = listBackups(backupRoot, worldName).stream()
                .filter(backup -> backup.kind() == BackupKind.SUCCESSFUL)
                .skip(retentionCount)
                .map(Backup::path)
                .toList();
        // Check every lock before deleting anything; one busy world aborts the cleanup.
        for (Path candidate : candidates) requireUnlocked(candidate);
        for (Path candidate : candidates) deleteTree(candidate);
        return candidates.size();
    }

    private static List<Path> journals(Path backups) throws IOException {
        try (var entries = Files.list(backups)) {
            return entries.filter(path -> path.getFileName().toString().startsWith(".reset-")
                    && path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    private static Journal readJournal(Path path) throws IOException {
        requireChild(path, path.getParent().toRealPath());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid journal: " + path);
        }
        try {
            Journal journal = GSON.fromJson(Files.readString(path), Journal.class);
            if (journal == null || journal.version() != 1 || journal.phase() == null
                    || journal.suffix() == null || !journal.suffix().matches(SUFFIX)
                    || journal.stagingName() == null || !journal.stagingName().matches("\\.pending-[0-9]+")) {
                throw new IOException("Invalid reset journal: " + path);
            }
            requireName(journal.worldName());
            return journal;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid reset journal: " + path, failure);
        }
    }

    private static void writeJournal(Path path, Journal journal) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".journal-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(GSON.toJson(journal));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Transaction transaction(Path saves, Path backups, Path file, Journal journal) throws IOException {
        Path world = saves.resolve(journal.worldName());
        Path backup = backups.resolve(journal.worldName() + "-" + journal.suffix());
        Path failed = backups.resolve("failed-" + journal.worldName() + "-" + journal.suffix());
        Path staging = backups.resolve(journal.stagingName());
        requireChild(world, saves);
        requireChild(backup, backups);
        requireChild(failed, backups);
        requireChild(staging, backups);
        if (world.startsWith(backups) || backups.startsWith(world)) {
            throw new IOException("Overlapping world and backup paths");
        }
        return new Transaction(world, backup, failed, staging, file);
    }

    private static void requireName(String name) throws IOException {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.contains(":")) {
            throw new IOException("Invalid world directory name: " + name);
        }
    }

    private static void requireChild(Path path, Path parent) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path actualParent = normalized.getParent();
        Path realParent = parent.toRealPath();
        if (actualParent == null || !actualParent.toRealPath().equals(realParent) || Files.isSymbolicLink(path)
                || (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !path.toRealPath().getParent().equals(realParent))) {
            throw new IOException("Path must be a real direct child of " + parent + ": " + path);
        }
    }

    private static void requireWorld(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(path.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World has no regular level.dat: " + path);
        }
    }

    private static void requireUnlocked(Path world) throws IOException {
        Path lockFile = world.resolve("session.lock");
        if (!Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid world lock: " + lockFile);
        }
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("World is in use: " + world);
        } catch (OverlappingFileLockException failure) {
            throw new IOException("World is in use: " + world, failure);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Path realRoot = root.toRealPath();
        List<Path> paths;
        try (var entries = Files.walk(root)) {
            paths = entries.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            if (!Files.isSymbolicLink(path) && !path.toRealPath().startsWith(realRoot)) {
                throw new IOException("Cannot delete a path outside the backup: " + path);
            }
        }
        for (Path path : paths) Files.deleteIfExists(path);
    }

    private static void copyIfPresent(Path source, Path destination) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        Path realSource = source.toRealPath();
        if (!realSource.getParent().equals(source.getParent().toRealPath())) {
            throw new IOException("Creation input points outside the world: " + source);
        }
        try (var entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry) || !entry.toRealPath().startsWith(realSource)) {
                    throw new IOException("Cannot copy a link outside the world: " + entry);
                }
                Path target = destination.resolve(source.relativize(entry));
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(entry, target);
                }
            }
        }
    }

    private enum Phase {
        RESETTING,
        RESTORING,
        COMMITTED
    }

    private record Journal(int version, String worldName, String suffix, String stagingName, Phase phase) {
        Journal withPhase(Phase next) {
            return new Journal(version, worldName, suffix, stagingName, next);
        }
    }

    public enum BackupKind {
        SUCCESSFUL,
        FAILED,
        PENDING
    }

    public record Backup(Path path, BackupKind kind, FileTime modified) {
    }

    public record Recovery(List<String> restoredWorlds, List<String> failures) {
    }

    public record Transaction(Path worldPath, Path backupPath, Path failedWorldPath, Path stagingPath, Path journalPath) {
        /** Commit before retention cleanup so a crash cannot restore a deleted backup. */
        public void complete() throws IOException {
            synchronized (WorldArchive.class) {
                requireWorld(worldPath);
                Journal journal = readJournal(journalPath);
                if (journal.phase() != Phase.COMMITTED) writeJournal(journalPath, journal.withPhase(Phase.COMMITTED));
                try {
                    Files.delete(journalPath);
                } catch (IOException failure) {
                    // The durable commit is sufficient; startup retries this housekeeping operation.
                    DeathRestartMod.LOGGER.warn("Could not remove committed reset journal {}", journalPath, failure);
                }
            }
        }

        /** Rollback is idempotent even if interrupted between either of its directory moves. */
        public void restore() throws IOException {
            synchronized (WorldArchive.class) {
                Journal journal = readJournal(journalPath);
                if (journal.phase() == Phase.COMMITTED) throw new IOException("Cannot restore a committed reset");
                transaction(worldPath.getParent(), backupPath.getParent(), journalPath, journal);
                requireUnlocked(worldPath);
                if (Files.exists(backupPath, LinkOption.NOFOLLOW_LINKS)) {
                    requireWorld(backupPath);
                    requireUnlocked(backupPath);
                    if (Files.exists(worldPath, LinkOption.NOFOLLOW_LINKS)
                            && Files.exists(failedWorldPath, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Failed-world directory already exists: " + failedWorldPath);
                    }
                    writeJournal(journalPath, journal.withPhase(Phase.RESTORING));
                    if (Files.exists(worldPath, LinkOption.NOFOLLOW_LINKS)) Files.move(worldPath, failedWorldPath);
                    Files.move(backupPath, worldPath);
                } else {
                    // The first move never ran, or rollback already moved the old world back.
                    if (journal.phase() != Phase.RESTORING
                            && !Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Old-world archive is missing; preserving the recovery journal: " + backupPath);
                    }
                    requireWorld(worldPath);
                }
                deleteTree(stagingPath);
                Files.delete(journalPath);
            }
        }
    }
}
