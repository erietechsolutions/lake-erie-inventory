package com.itinventory.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Records all significant system actions to change_log.csv in the
 * shared data directory.
 *
 * Each entry contains:
 *   timestamp, username, hostname, actionType, target, detail, oldValue, newValue
 *
 * Log is pruned to keep the last N days (default 90) on startup.
 * A backup is written before pruning.
 */
public class ChangeLogger {

    private static final Logger LOG = Logger.getLogger(ChangeLogger.class.getName());

    public static final String LOG_FILE     = "change_log.csv";
    public static final int    DEFAULT_DAYS = 90;

    // ── Action types ──────────────────────────────────────────────────────────

    public enum Action {
        // Asset actions
        ASSET_CREATED, ASSET_UPDATED, ASSET_DELETED,
        // User actions
        USER_LOGIN, USER_LOGIN_FAILED, USER_CREATED, USER_UPDATED,
        USER_ROLE_CHANGED, USER_DISABLED, USER_ENABLED, USER_DELETED,
        USER_CODE_RESET,
        // Settings actions
        SETTINGS_CHANGED, SHARED_PATH_CHANGED,
        // Custom fields
        CUSTOM_CATEGORY_ADDED, CUSTOM_CATEGORY_RENAMED,
        CUSTOM_CATEGORY_DISABLED, CUSTOM_CATEGORY_ENABLED,
        CUSTOM_CATEGORY_DELETED,
        CUSTOM_STATUS_ADDED, CUSTOM_STATUS_RENAMED,
        CUSTOM_STATUS_DISABLED, CUSTOM_STATUS_ENABLED,
        CUSTOM_STATUS_DELETED,
        // Database events
        DATABASE_RESET, SETTINGS_RESET, BACKUP_CREATED,
        // Lock events
        LOCK_ACQUIRED, LOCK_RELEASED, LOCK_STALE_CLEARED
    }

    // ── Log entry ─────────────────────────────────────────────────────────────

    public record LogEntry(
        LocalDateTime timestamp,
        String        username,
        String        hostname,
        Action        action,
        String        target,   // asset ID, username, field name, etc.
        String        detail,   // human-readable description
        String        oldValue,
        String        newValue
    ) {
        public String toDisplayString() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Path   logFile;
    private final Path   backupDir;
    private final String username;
    private final String hostname;
    private final int    retentionDays;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChangeLogger(String dataDirectory, String username,
                        String hostname, int retentionDays) {
        this.logFile       = Paths.get(dataDirectory, LOG_FILE);
        this.backupDir     = Paths.get(dataDirectory, "backups");
        this.username      = username != null ? username : "system";
        this.hostname      = hostname != null ? hostname : "unknown";
        this.retentionDays = retentionDays > 0 ? retentionDays : DEFAULT_DAYS;
    }

    public ChangeLogger(String dataDirectory, String username, String hostname) {
        this(dataDirectory, username, hostname, DEFAULT_DAYS);
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    /**
     * Logs an action with full before/after values.
     */
    public void log(Action action, String target,
                    String detail, String oldValue, String newValue) {
        LogEntry entry = new LogEntry(
            LocalDateTime.now(), username, hostname,
            action, target,
            detail != null ? detail : "",
            oldValue != null ? oldValue : "",
            newValue != null ? newValue : ""
        );
        append(entry);
    }

    /**
     * Logs a simple action with just a detail string.
     */
    public void log(Action action, String target, String detail) {
        log(action, target, detail, "", "");
    }

    /**
     * Logs a simple action with no extra detail.
     */
    public void log(Action action, String target) {
        log(action, target, "", "", "");
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Loads all log entries, newest first.
     */
    public List<LogEntry> loadAll() throws IOException {
        if (!Files.exists(logFile)) return new ArrayList<>();

        List<LogEntry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank() || line.startsWith("Timestamp")) continue;
            try {
                entries.add(parseRow(line));
            } catch (Exception e) {
                LOG.warning("Skipping malformed log row " + (i + 1) + ": " + e.getMessage());
            }
        }

        // Return newest first
        entries.sort(Comparator.comparing(LogEntry::timestamp).reversed());
        return entries;
    }

    /**
     * Loads entries filtered by date range and/or username and/or action type.
     */
    public List<LogEntry> loadFiltered(LocalDate from, LocalDate to,
                                        String filterUser, Action filterAction)
            throws IOException {
        return loadAll().stream()
            .filter(e -> from == null || !e.timestamp().toLocalDate().isBefore(from))
            .filter(e -> to   == null || !e.timestamp().toLocalDate().isAfter(to))
            .filter(e -> filterUser   == null || filterUser.isBlank()
                         || e.username().equalsIgnoreCase(filterUser))
            .filter(e -> filterAction == null || e.action() == filterAction)
            .collect(Collectors.toList());
    }

    // ── Pruning ───────────────────────────────────────────────────────────────

    /**
     * Removes entries older than retentionDays.
     * Backs up the full log before pruning.
     */
    public void prune() throws IOException {
        if (!Files.exists(logFile)) return;

        List<LogEntry> all = loadAll();
        LocalDate cutoff   = LocalDate.now().minusDays(retentionDays);
        List<LogEntry> keep = all.stream()
            .filter(e -> !e.timestamp().toLocalDate().isBefore(cutoff))
            .collect(Collectors.toList());

        if (keep.size() < all.size()) {
            // Back up before pruning
            Files.createDirectories(backupDir);
            String ts = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Files.copy(logFile, backupDir.resolve("change_log_" + ts + ".csv"),
                    StandardCopyOption.REPLACE_EXISTING);

            // Rewrite with kept entries (sort oldest first for file storage)
            keep.sort(Comparator.comparing(LogEntry::timestamp));
            rewriteAll(keep);

            LOG.info("Pruned " + (all.size() - keep.size()) +
                    " log entries older than " + retentionDays + " days.");
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    public void exportToCsv(List<LogEntry> entries, String outputPath) throws IOException {
        Path out = Paths.get(outputPath);
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write(csvHeader());
            w.newLine();
            for (LogEntry e : entries) {
                w.write(toRow(e));
                w.newLine();
            }
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void append(LogEntry entry) {
        try {
            Files.createDirectories(logFile.getParent());
            boolean needsHeader = !Files.exists(logFile);
            try (BufferedWriter w = Files.newBufferedWriter(logFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (needsHeader) {
                    w.write(csvHeader());
                    w.newLine();
                }
                w.write(toRow(entry));
                w.newLine();
            }
        } catch (IOException e) {
            LOG.warning("Could not write log entry: " + e.getMessage());
        }
    }

    private void rewriteAll(List<LogEntry> entries) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            w.write(csvHeader());
            w.newLine();
            for (LogEntry e : entries) {
                w.write(toRow(e));
                w.newLine();
            }
        }
    }

    private static String csvHeader() {
        return "Timestamp,Username,Hostname,Action,Target,Detail,OldValue,NewValue";
    }

    private static String toRow(LogEntry e) {
        return String.join(",",
            escape(e.timestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
            escape(e.username()),
            escape(e.hostname()),
            e.action().name(),
            escape(e.target()),
            escape(e.detail()),
            escape(e.oldValue()),
            escape(e.newValue())
        );
    }

    private static LogEntry parseRow(String line) {
        String[] f = splitCsv(line);
        if (f.length < 8) throw new IllegalArgumentException("Not enough fields");
        return new LogEntry(
            LocalDateTime.parse(f[0].trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            f[1].trim(), f[2].trim(),
            Action.valueOf(f[3].trim()),
            f[4].trim(), f[5].trim(), f[6].trim(), f[7].trim()
        );
    }

    private static String escape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    private static String[] splitCsv(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i+1) == '"') {
                    sb.append('"'); i++;
                } else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString()); sb.setLength(0);
            } else sb.append(c);
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }
}
