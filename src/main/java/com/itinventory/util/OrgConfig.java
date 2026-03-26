package com.itinventory.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Manages two separate configuration files:
 *
 * 1. LOCAL config  (data/org.local.properties) — stays on this machine only.
 *    Stores: licenseAccepted, sharedDataPath
 *
 * 2. SHARED config ({dataDir}/org.properties) — lives in the shared data folder.
 *    Stores: orgName, orgType, adminName, accentColor, configured, allowReset
 *
 * When no shared path is set, both files live in the local data/ folder and
 * the app behaves exactly as before. When a shared path is configured, the
 * shared config is read from and written to the network folder so all machines
 * see the same organization settings.
 */
public class OrgConfig {

    // ── File paths ────────────────────────────────────────────────────────────

    /** Always local — never on the share. */
    public static final String LOCAL_CONFIG_FILE  = "data/org.local.properties";

    /** Filename used inside whatever dataDir is active (local or shared). */
    public static final String SHARED_CONFIG_NAME = "org.properties";

    // ── Local-only keys ───────────────────────────────────────────────────────
    public static final String KEY_LICENSE_ACCEPTED = "org.licenseAccepted";
    public static final String KEY_SHARED_DATA_PATH = "org.sharedDataPath";

    // ── Shared keys ───────────────────────────────────────────────────────────
    public static final String KEY_NAME        = "org.name";
    public static final String KEY_TYPE        = "org.type";
    public static final String KEY_ADMIN       = "org.adminName";
    public static final String KEY_COLOR       = "org.accentColor";
    public static final String KEY_CONFIGURED  = "org.configured";
    public static final String KEY_ALLOW_RESET = "org.allowReset";

    public static final String DEFAULT_COLOR   = "#4f7cff";

    // ── Properties objects ────────────────────────────────────────────────────

    private final Properties localProps  = new Properties();
    private final Properties sharedProps = new Properties();

    private final Path localConfigPath;
    private Path sharedConfigPath;

    // ── Constructor ───────────────────────────────────────────────────────────

    public OrgConfig() {
        this.localConfigPath = Paths.get(LOCAL_CONFIG_FILE);
        loadLocal();
        resolveSharedPath();
        loadShared();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadLocal() {
        if (!Files.exists(localConfigPath)) return;
        try (InputStream in = Files.newInputStream(localConfigPath)) {
            localProps.load(in);
        } catch (IOException e) {
            System.err.println("Could not load local config: " + e.getMessage());
        }
    }

    private void resolveSharedPath() {
        String shared = localProps.getProperty(KEY_SHARED_DATA_PATH, "").trim();
        String dir    = shared.isBlank() ? "data" : shared;
        this.sharedConfigPath = Paths.get(dir, SHARED_CONFIG_NAME);
    }

    private void loadShared() {
        if (!Files.exists(sharedConfigPath)) return;
        try (InputStream in = Files.newInputStream(sharedConfigPath)) {
            sharedProps.load(in);
        } catch (IOException e) {
            System.err.println("Could not load shared config: " + e.getMessage());
        }
    }

    /**
     * Reloads both config files from disk.
     * Call after saving to ensure the in-memory state is current.
     */
    public void reload() {
        localProps.clear();
        sharedProps.clear();
        loadLocal();
        resolveSharedPath();
        loadShared();
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /** Saves the local config (license, shared path). */
    public void saveLocal() throws IOException {
        Files.createDirectories(localConfigPath.getParent());
        try (OutputStream out = Files.newOutputStream(localConfigPath)) {
            localProps.store(out,
                "Lake Erie Inventory - Local Configuration (do not copy to other machines)");
        }
    }

    /** Saves the shared config (org info) to the active data directory. */
    public void saveShared() throws IOException {
        Files.createDirectories(sharedConfigPath.getParent());
        try (OutputStream out = Files.newOutputStream(sharedConfigPath)) {
            sharedProps.store(out,
                "Lake Erie Inventory - Shared Organization Configuration");
        }
    }

    /** Saves both configs. */
    public void save() throws IOException {
        saveLocal();
        saveShared();
    }

    // ── Local accessors ───────────────────────────────────────────────────────

    public boolean isLicenseAccepted() {
        return "true".equalsIgnoreCase(
                localProps.getProperty(KEY_LICENSE_ACCEPTED, "false"));
    }

    public void setLicenseAccepted(boolean v) {
        localProps.setProperty(KEY_LICENSE_ACCEPTED, v ? "true" : "false");
    }

    public String getDataPath() {
        String shared = localProps.getProperty(KEY_SHARED_DATA_PATH, "").trim();
        return shared.isBlank() ? "data" : shared;
    }

    public void setDataPath(String v) throws IOException {
        String newPath = (v == null ? "" : v.trim());
        localProps.setProperty(KEY_SHARED_DATA_PATH, newPath);
        // Re-resolve shared config path so subsequent saves go to the right place
        resolveSharedPath();

        // If switching to a new shared path, migrate existing shared config there
        if (!newPath.isBlank()) {
            Path newSharedConfig = Paths.get(newPath, SHARED_CONFIG_NAME);
            if (!Files.exists(newSharedConfig) && !sharedProps.isEmpty()) {
                Files.createDirectories(Paths.get(newPath));
                try (OutputStream out = Files.newOutputStream(newSharedConfig)) {
                    sharedProps.store(out,
                        "Lake Erie Inventory - Shared Organization Configuration");
                }
            }
            // Reload from the new location
            sharedConfigPath = newSharedConfig;
            loadShared();
        }
    }

    public boolean isUsingSharedPath() {
        String shared = localProps.getProperty(KEY_SHARED_DATA_PATH, "").trim();
        return !shared.isBlank();
    }

    // ── Shared accessors ──────────────────────────────────────────────────────

    public boolean isConfigured() {
        return "true".equalsIgnoreCase(
                sharedProps.getProperty(KEY_CONFIGURED, "false"));
    }

    public void markConfigured() {
        sharedProps.setProperty(KEY_CONFIGURED, "true");
    }

    public String getOrgName()     { return sharedProps.getProperty(KEY_NAME,  ""); }
    public String getOrgType()     { return sharedProps.getProperty(KEY_TYPE,  ""); }
    public String getAdminName()   { return sharedProps.getProperty(KEY_ADMIN, ""); }
    public String getAccentColor() { return sharedProps.getProperty(KEY_COLOR, DEFAULT_COLOR); }

    public void setOrgName(String v)     { sharedProps.setProperty(KEY_NAME,  v); }
    public void setOrgType(String v)     { sharedProps.setProperty(KEY_TYPE,  v); }
    public void setAdminName(String v)   { sharedProps.setProperty(KEY_ADMIN, v); }
    public void setAccentColor(String v) { sharedProps.setProperty(KEY_COLOR, v); }

    public boolean isResetAllowed() {
        return "true".equalsIgnoreCase(
                sharedProps.getProperty(KEY_ALLOW_RESET, "false"));
    }

    public void setResetAllowed(boolean v) {
        sharedProps.setProperty(KEY_ALLOW_RESET, v ? "true" : "false");
    }

    // ── Path helpers ──────────────────────────────────────────────────────────

    public Path getSharedConfigPath() { return sharedConfigPath; }
    public Path getLocalConfigPath()  { return localConfigPath; }
}
