package local.sylvan.deathreset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Prepares a fresh save directory, then archives the closed world without deleting it. */
public final class WorldArchive {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private WorldArchive() {
    }

    public static Transaction prepare(Path savesRoot, Path worldPath, Path backupRoot) throws IOException {
        Path saves = savesRoot.toRealPath();
        Path source = worldPath.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source) || !source.toRealPath().getParent().equals(saves)) {
            throw new IOException("Reset world must be a real directory directly inside the saves directory: " + source);
        }
        if (!Files.isRegularFile(source.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World has no regular level.dat: " + source);
        }
        Files.createDirectories(backupRoot);
        Path backups = backupRoot.toRealPath();
        if (backups.startsWith(source) || source.startsWith(backups)) {
            throw new IOException("Backup directory must be outside the world: " + backups);
        }
        String suffix = TIMESTAMP.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path archive = backups.resolve(source.getFileName() + "-" + suffix);
        Path staging = Files.createTempDirectory(backups, ".pending-");

        // Copy only inputs for world creation; regions, player data and progress belong to the old round.
        copyIfPresent(source.resolve("datapacks"), staging.resolve("datapacks"));
        copyIfPresent(source.resolve("resources.zip"), staging.resolve("resources.zip"));
        Files.move(source, archive);
        try {
            Files.move(staging, source);
        } catch (IOException failure) {
            try {
                Files.move(archive, source);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return new Transaction(source, archive, backups.resolve("failed-" + source.getFileName() + "-" + suffix));
    }

    private static void copyIfPresent(Path source, Path destination) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        try (var entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry)) throw new IOException("Cannot copy a symbolic link: " + entry);
                Path target = destination.resolve(source.relativize(entry));
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(entry, target);
                }
            }
        }
    }

    public record Transaction(Path worldPath, Path backupPath, Path failedWorldPath) {
        /** Keep any failed new-world files as well, and restore the complete previous round. */
        public void restore() throws IOException {
            if (Files.exists(worldPath, LinkOption.NOFOLLOW_LINKS)) Files.move(worldPath, failedWorldPath);
            Files.move(backupPath, worldPath);
        }
    }
}
