package com.itinventory.util;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles the full auto-update process for Lake Erie Inventory.
 *
 * Process:
 *   1. Finds the download URL for the latest release zip from GitHub
 *   2. Downloads it to a temp folder with progress reporting
 *   3. Extracts the zip
 *   4. Migrates the user's data/ folder into the new version
 *   5. Replaces all source files (src/, styles.css, run.bat) with the new ones
 *   6. Cleans up temp files
 *   7. Signals completion so the UI can prompt the user to relaunch
 *
 * The user's data is ALWAYS preserved. The old installation files are
 * backed up to update_backup/ before replacement so recovery is possible.
 */
public class AutoUpdater {

    private static final Logger LOG = Logger.getLogger(AutoUpdater.class.getName());

    private static final String GITHUB_OWNER  = "erietechsolutions";
    private static final String GITHUB_REPO   = "lake-erie-inventory";
    private static final String API_URL        =
        "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    // Temp folder used during update
    private static final String TEMP_DIR      = "update_temp";
    // Backup folder - old files go here before replacement
    private static final String BACKUP_DIR    = "update_backup";

    // ── Public API ────────────────────────────────────────────────────────────

    public enum Step {
        FINDING_DOWNLOAD,
        DOWNLOADING,
        EXTRACTING,
        MIGRATING_DATA,
        REPLACING_FILES,
        CLEANING_UP,
        COMPLETE,
        FAILED
    }

    public record Progress(
        Step   step,
        String message,
        int    percent,   // 0-100, -1 for indeterminate
        boolean failed,
        String errorMessage
    ) {}

    /**
     * Runs the full update process synchronously.
     * Call from a background thread only.
     *
     * @param onProgress callback invoked on each progress update (safe to use with Platform.runLater)
     * @param latestVersion the version tag we are updating to (e.g. "v1.1.0")
     */
    public static void runUpdate(String latestVersion, Consumer<Progress> onProgress) {
        Path tempDir   = Paths.get(TEMP_DIR);
        Path backupDir = Paths.get(BACKUP_DIR);
        Path installDir = Paths.get("").toAbsolutePath(); // current working directory

        try {
            // ── Step 1: Find download URL ─────────────────────────────────────
            report(onProgress, Step.FINDING_DOWNLOAD, "Contacting GitHub...", -1);

            String downloadUrl = findZipDownloadUrl(latestVersion);
            if (downloadUrl == null) {
                // Fall back to constructing the URL from the tag
                downloadUrl = "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO +
                              "/archive/refs/tags/" + latestVersion + ".zip";
            }

            LOG.info("Download URL: " + downloadUrl);

            // ── Step 2: Download ──────────────────────────────────────────────
            report(onProgress, Step.DOWNLOADING, "Downloading " + latestVersion + "...", 0);

            Files.createDirectories(tempDir);
            Path zipFile = tempDir.resolve("update.zip");
            downloadFile(downloadUrl, zipFile, onProgress);

            // ── Step 3: Extract ───────────────────────────────────────────────
            report(onProgress, Step.EXTRACTING, "Extracting update...", -1);

            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            extractZip(zipFile, extractDir);

            // The zip extracts into a subfolder like "lake-erie-inventory-1.1.0/"
            // Find the actual root of the extracted content
            Path newVersionRoot = findExtractedRoot(extractDir);
            LOG.info("Extracted root: " + newVersionRoot);

            // ── Step 4: Migrate data ──────────────────────────────────────────
            report(onProgress, Step.MIGRATING_DATA, "Preserving your inventory data...", -1);

            Path newDataDir = newVersionRoot.resolve("data");
            Path existingDataDir = installDir.resolve("data");

            if (Files.exists(existingDataDir)) {
                // Copy existing data into the new version's data folder
                copyDirectory(existingDataDir, newDataDir);
                LOG.info("Data migrated from " + existingDataDir + " to " + newDataDir);
            }

            // ── Step 5: Back up old files then replace ────────────────────────
            report(onProgress, Step.REPLACING_FILES, "Installing new version...", -1);

            // Back up current src/ and run.bat before overwriting
            Files.createDirectories(backupDir);
            Path oldSrc = installDir.resolve("src");
            Path oldBat = installDir.resolve("run.bat");
            Path oldRes = installDir.resolve("src").resolve("main").resolve("resources");

            if (Files.exists(oldSrc)) {
                copyDirectory(oldSrc, backupDir.resolve("src"));
            }
            if (Files.exists(oldBat)) {
                Files.copy(oldBat, backupDir.resolve("run.bat"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            // Replace src/ with new version
            Path newSrc = newVersionRoot.resolve("src");
            if (Files.exists(newSrc)) {
                deleteDirectory(oldSrc);
                copyDirectory(newSrc, oldSrc);
            }

            // Replace run.bat
            Path newBat = newVersionRoot.resolve("run.bat");
            if (Files.exists(newBat)) {
                Files.copy(newBat, installDir.resolve("run.bat"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            // Delete old compiled output so run.bat recompiles cleanly
            Path outDir = installDir.resolve("out");
            if (Files.exists(outDir)) {
                deleteDirectory(outDir);
                LOG.info("Deleted old out/ directory - will recompile on next launch");
            }

            // ── Step 6: Clean up temp files ───────────────────────────────────
            report(onProgress, Step.CLEANING_UP, "Cleaning up...", -1);
            deleteDirectory(tempDir);

            // ── Step 7: Done ──────────────────────────────────────────────────
            report(onProgress, Step.COMPLETE,
                "Update to " + latestVersion + " complete! Please relaunch the app.", 100);

        } catch (Exception e) {
            LOG.severe("Auto-update failed: " + e.getMessage());
            // Clean up temp on failure
            try { deleteDirectory(tempDir); } catch (Exception ignored) {}
            onProgress.accept(new Progress(Step.FAILED, "Update failed.", 0, true, e.getMessage()));
        }
    }

    // ── Download helpers ──────────────────────────────────────────────────────

    /**
     * Queries the GitHub API to find the direct zip asset download URL.
     * Falls back to null if no zip asset is attached - caller uses tag-based URL.
     */
    private static String findZipDownloadUrl(String tag) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LakeErieInventory/" + UpdateChecker.CURRENT_VERSION)
                    .GET().build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            // Look for zipball_url in the response
            String body = resp.body();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"zipball_url\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(body);
            return m.find() ? m.group(1) : null;

        } catch (Exception e) {
            LOG.warning("Could not find zip URL from API: " + e.getMessage());
            return null;
        }
    }

    /**
     * Downloads a file from a URL to a local path, reporting progress.
     */
    private static void downloadFile(String url, Path dest,
                                     Consumer<Progress> onProgress) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "LakeErieInventory/" + UpdateChecker.CURRENT_VERSION)
                .GET().build();

        // Stream the download so we can report progress
        HttpResponse<InputStream> resp = client.send(req,
                HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() != 200) {
            throw new IOException("Download failed with HTTP " + resp.statusCode());
        }

        long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);

        try (InputStream in = resp.body();
             OutputStream out = Files.newOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;

                if (contentLength > 0) {
                    int percent = (int) (downloaded * 100 / contentLength);
                    String msg = String.format("Downloading... %d KB / %d KB",
                            downloaded / 1024, contentLength / 1024);
                    report(onProgress, Step.DOWNLOADING, msg, percent);
                }
            }
        }

        LOG.info("Downloaded to " + dest);
    }

    // ── Zip helpers ───────────────────────────────────────────────────────────

    private static void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                // Security check - prevent zip slip attacks
                if (!target.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Finds the root folder inside the extracted zip.
     * GitHub zips extract into a subfolder like "repo-name-v1.1.0/"
     * so we need to find that folder rather than using the extract root directly.
     */
    private static Path findExtractedRoot(Path extractDir) throws IOException {
        try (var stream = Files.list(extractDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(extractDir);
        }
    }

    // ── File system helpers ───────────────────────────────────────────────────

    private static void copyDirectory(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ── Reporting helper ──────────────────────────────────────────────────────

    private static void report(Consumer<Progress> onProgress, Step step,
                                String message, int percent) {
        onProgress.accept(new Progress(step, message, percent, false, ""));
        LOG.info("[" + step + "] " + message);
    }
}
