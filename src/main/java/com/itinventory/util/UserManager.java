package com.itinventory.util;

import com.itinventory.model.UserAccount;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages user accounts and file locking for Lake Erie Inventory.
 *
 * User accounts are stored in {dataDir}/users.csv
 * The lock file is stored in {dataDir}/inventory.lock
 *
 * Lock file format (plain text):
 *   username|displayname|hostname|timestamp
 *
 * File locking rules:
 *   - When a user logs in with edit access, a lock file is created
 *   - If a lock file exists and was updated within the last 60 seconds,
 *     the session is considered active
 *   - Lock is refreshed every 30 seconds while the app is running
 *   - Lock is released when the app closes cleanly
 *   - Stale locks (older than 60 seconds) are automatically removed
 */
public class UserManager {

    private static final Logger LOG = Logger.getLogger(UserManager.class.getName());

    private static final String USERS_FILE  = "users.csv";
    private static final String LOCK_FILE   = "inventory.lock";
    private static final long   LOCK_TIMEOUT_MS = 60_000; // 60 seconds
    private static final long   LOCK_REFRESH_MS = 30_000; // refresh every 30 seconds

    private final Path usersFile;
    private final Path lockFile;
    private final String dataDir;

    private UserAccount currentUser;
    private java.util.Timer lockRefreshTimer;

    // ── Constructor ───────────────────────────────────────────────────────────

    public UserManager(String dataDirectory) {
        this.dataDir   = dataDirectory;
        this.usersFile = Paths.get(dataDirectory, USERS_FILE);
        this.lockFile  = Paths.get(dataDirectory, LOCK_FILE);
    }

    // ── User CRUD ─────────────────────────────────────────────────────────────

    public List<UserAccount> loadAll() throws IOException {
        if (!Files.exists(usersFile)) return new ArrayList<>();

        List<UserAccount> users = new ArrayList<>();
        List<String> lines = Files.readAllLines(usersFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank() || line.startsWith("Username")) continue;
            try {
                users.add(UserAccount.fromCsvRow(line));
            } catch (Exception e) {
                LOG.warning("Skipping malformed user row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return users;
    }

    public void saveAll(List<UserAccount> users) throws IOException {
        Files.createDirectories(usersFile.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(usersFile, StandardCharsets.UTF_8)) {
            w.write(UserAccount.csvHeader());
            w.newLine();
            for (UserAccount u : users) {
                w.write(u.toCsvRow());
                w.newLine();
            }
        }
    }

    /**
     * Returns true if no users exist yet (first-time setup).
     */
    public boolean hasNoUsers() throws IOException {
        return loadAll().isEmpty();
    }

    /**
     * Creates the initial admin account. Only call when hasNoUsers() is true.
     */
    public void createFirstAdmin(String username, String accessCode) throws IOException {
        UserAccount admin = new UserAccount(username, accessCode, UserAccount.Role.ADMIN);
        saveAll(List.of(admin));
        LOG.info("Created first admin account: " + username);
    }

    public void addUser(UserAccount user) throws IOException {
        List<UserAccount> users = loadAll();
        // Check for duplicate username
        boolean exists = users.stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(user.getUsername()));
        if (exists) {
            throw new IllegalArgumentException(
                "Username '" + user.getUsername() + "' already exists.");
        }
        users.add(user);
        saveAll(users);
    }

    public void updateUser(UserAccount updated) throws IOException {
        List<UserAccount> users = loadAll();
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equalsIgnoreCase(updated.getUsername())) {
                users.set(i, updated);
                saveAll(users);
                return;
            }
        }
        throw new NoSuchElementException("User not found: " + updated.getUsername());
    }

    public void deleteUser(String username) throws IOException {
        List<UserAccount> users = loadAll();
        users.removeIf(u -> u.getUsername().equalsIgnoreCase(username));
        saveAll(users);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Attempts to authenticate a user.
     * Returns the UserAccount on success, or empty if credentials are wrong.
     */
    public Optional<UserAccount> authenticate(String username, String accessCode)
            throws IOException {
        return loadAll().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .filter(UserAccount::isActive)
                .filter(u -> u.verifyAccessCode(accessCode))
                .findFirst();
    }

    public UserAccount getCurrentUser() { return currentUser; }
    public void setCurrentUser(UserAccount u) { this.currentUser = u; }

    // ── File locking ──────────────────────────────────────────────────────────

    /**
     * Checks if the inventory is currently locked by another active user.
     * Returns the lock info string if locked by someone else, or null if free/stale.
     *
     * IMPORTANT: We do NOT use Files.exists() here because Windows SMB network
     * shares cache directory listings and Files.exists() can return false even
     * when the lock file is present on the share. Instead we attempt a direct
     * read — if it throws FileNotFoundException the file genuinely doesn't exist,
     * any other IOException is treated as locked to be safe.
     */
    public String checkLock() {
        String content;
        try {
            // Direct read bypasses Windows SMB directory cache
            content = Files.readString(lockFile, StandardCharsets.UTF_8).trim();
        } catch (NoSuchFileException e) {
            // File genuinely does not exist
            return null;
        } catch (IOException e) {
            // Cannot read — treat as locked to prevent accidental concurrent edits
            LOG.warning("Could not read lock file - treating as locked: " + e.getMessage());
            return "unknown|unknown|unknown|" +
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        if (content.isBlank()) return null;

        // Check if the lock belongs to the current user on this machine
        // If so it is a leftover from a previous session — clear it and proceed
        if (currentUser != null) {
            String[] parts = content.split("\\|");
            String lockUser = parts.length > 0 ? parts[0] : "";
            String lockHost = parts.length > 2 ? parts[2] : "";
            if (lockUser.equalsIgnoreCase(currentUser.getUsername())
                    && lockHost.equalsIgnoreCase(getHostname())) {
                LOG.info("Found own stale lock - clearing it.");
                try { Files.deleteIfExists(lockFile); } catch (IOException ignored) {}
                return null;
            }
        }

        // Check if lock is stale (older than timeout)
        String[] parts = content.split("\\|");
        if (parts.length >= 4) {
            try {
                LocalDateTime lockTime = LocalDateTime.parse(parts[3],
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                long ageMs = java.time.Duration.between(lockTime,
                        LocalDateTime.now()).toMillis();
                if (ageMs > LOCK_TIMEOUT_MS) {
                    try {
                        Files.deleteIfExists(lockFile);
                        LOG.info("Removed stale lock (age: " + ageMs + "ms)");
                    } catch (IOException e) {
                        LOG.warning("Could not remove stale lock: " + e.getMessage());
                    }
                    return null;
                }
            } catch (Exception e) {
                // Cannot parse timestamp — treat as stale
                try { Files.deleteIfExists(lockFile); }
                catch (IOException ignored) {}
                return null;
            }
        }

        return content; // active lock held by someone else
    }

    /**
     * Acquires the lock for the current user.
     * Verifies the lock was actually written before returning.
     * Throws IOException if the lock cannot be written — caller should
     * treat this as a failure and not grant edit access.
     */
    public void acquireLock() throws IOException {
        if (currentUser == null) return;
        String hostname = getHostname();
        String content  = currentUser.getUsername() + "|" +
                          currentUser.getUsername() + "|" +
                          hostname + "|" +
                          LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Files.createDirectories(lockFile.getParent());
        Files.writeString(lockFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Verify the lock was actually written correctly
        String verify = Files.readString(lockFile, StandardCharsets.UTF_8).trim();
        if (!verify.startsWith(currentUser.getUsername())) {
            throw new IOException(
                "Lock file verification failed - another user may have acquired it simultaneously.");
        }

        LOG.info("Lock acquired by " + currentUser.getUsername() +
                 " on " + hostname + " at " + lockFile.toAbsolutePath());
        startLockRefresh();
    }

    /**
     * Releases the lock. Should be called on app close.
     */
    public void releaseLock() {
        stopLockRefresh();
        try {
            Files.deleteIfExists(lockFile);
            LOG.info("Lock released by " +
                (currentUser != null ? currentUser.getUsername() : "unknown"));
        } catch (IOException e) {
            LOG.warning("Could not release lock: " + e.getMessage());
        }
    }

    /**
     * Parses a lock info string into a human-readable message.
     * e.g. "Locked by alice on DESKTOP-ABC since 2:34 PM"
     */
    public static String describeLock(String lockContent) {
        if (lockContent == null) return "";
        String[] parts = lockContent.split("\\|");
        String user = parts.length > 0 ? parts[0] : "unknown";
        String host = parts.length > 2 ? parts[2] : "unknown machine";
        String time = "";
        if (parts.length > 3) {
            try {
                LocalDateTime dt = LocalDateTime.parse(parts[3],
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                time = " since " + dt.format(DateTimeFormatter.ofPattern("h:mm a"));
            } catch (Exception ignored) {}
        }
        return "Currently locked by " + user + " on " + host + time + ".\n" +
               "You are in read-only mode.";
    }

    // ── Lock refresh ──────────────────────────────────────────────────────────

    private void startLockRefresh() {
        stopLockRefresh();
        lockRefreshTimer = new java.util.Timer("lock-refresh", true);
        lockRefreshTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override public void run() {
                try {
                    if (currentUser != null && Files.exists(lockFile)) {
                        acquireLock(); // re-write with updated timestamp
                    }
                } catch (IOException e) {
                    LOG.warning("Failed to refresh lock: " + e.getMessage());
                }
            }
        }, LOCK_REFRESH_MS, LOCK_REFRESH_MS);
    }

    private void stopLockRefresh() {
        if (lockRefreshTimer != null) {
            lockRefreshTimer.cancel();
            lockRefreshTimer = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getDataDir() { return dataDir; }
}
