package com.itinventory.util;

import com.itinventory.model.Asset;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles all CSV file I/O for the inventory system.
 *
 * File layout:
 *   data/
 *     inventory.csv        - primary inventory file
 *     backups/             - timestamped backups written before every save
 */
public class CsvManager {

    private static final Logger LOG = Logger.getLogger(CsvManager.class.getName());

    private final Path inventoryFile;
    private final Path backupDir;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CsvManager(String dataDirectory) {
        Path dir = Paths.get(dataDirectory);
        this.inventoryFile = dir.resolve("inventory.csv");
        this.backupDir     = dir.resolve("backups");

        try {
            Files.createDirectories(dir);
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize data directory: " + dataDirectory, e);
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Loads all assets from the inventory CSV.
     * Returns an empty list if the file does not exist yet.
     */
    public List<Asset> loadAll() throws IOException {
        if (!Files.exists(inventoryFile)) {
            LOG.info("Inventory file not found - starting with empty inventory.");
            return new ArrayList<>();
        }

        List<Asset> assets = new ArrayList<>();
        List<String> lines = Files.readAllLines(inventoryFile, StandardCharsets.UTF_8);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank() || line.startsWith("AssetID")) continue;

            try {
                assets.add(Asset.fromCsvRow(line));
            } catch (Exception e) {
                LOG.warning("Skipping malformed row " + (i + 1) + ": " + e.getMessage());
            }
        }

        LOG.info("Loaded " + assets.size() + " asset(s) from " + inventoryFile);
        return assets;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Writes all assets to the inventory CSV, creating a timestamped backup first.
     */
    public void saveAll(Collection<Asset> assets) throws IOException {
        backupIfExists();

        try (BufferedWriter writer = Files.newBufferedWriter(inventoryFile, StandardCharsets.UTF_8)) {
            writer.write(Asset.csvHeader());
            writer.newLine();
            for (Asset a : assets) {
                writer.write(a.toCsvRow());
                writer.newLine();
            }
        }

        LOG.info("Saved " + assets.size() + " asset(s) to " + inventoryFile);
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Exports a subset of assets to a named CSV file (e.g. for reporting).
     */
    public void exportToCsv(Collection<Asset> assets, String outputPath) throws IOException {
        Path out = Paths.get(outputPath);
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writer.write(Asset.csvHeader());
            writer.newLine();
            for (Asset a : assets) {
                writer.write(a.toCsvRow());
                writer.newLine();
            }
        }

        LOG.info("Exported " + assets.size() + " asset(s) to " + out.toAbsolutePath());
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /**
     * Deletes the inventory CSV and all backups, effectively wiping the database.
     * A final backup is written before deletion.
     */
    public void resetDatabase() throws IOException {
        backupIfExists();
        Files.deleteIfExists(inventoryFile);
        LOG.info("Database reset - inventory.csv deleted.");
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private void backupIfExists() throws IOException {
        if (!Files.exists(inventoryFile)) return;

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backup = backupDir.resolve("inventory_" + timestamp + ".csv");
        Files.copy(inventoryFile, backup, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Backup written to " + backup.getFileName());

        pruneOldBackups(10);
    }

    /** Deletes the oldest backups if the backup count exceeds maxKeep. */
    private void pruneOldBackups(int maxKeep) throws IOException {
        try (var stream = Files.list(backupDir)) {
            List<Path> backups = stream
                    .filter(p -> p.getFileName().toString().startsWith("inventory_"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (int i = 0; i < backups.size() - maxKeep; i++) {
                Files.deleteIfExists(backups.get(i));
                LOG.fine("Pruned old backup: " + backups.get(i).getFileName());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Path getInventoryFilePath() { return inventoryFile; }
    public Path getBackupDirectory()   { return backupDir; }
}
