package com.itinventory.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Loads and saves organization configuration to data/org.properties.
 * This file stays LOCAL to each machine (not on the shared drive).
 *
 * Fields stored:
 *   org.name             - organization name
 *   org.type             - School | Company | Non-Profit | Library | Government | Other
 *   org.adminName        - IT contact / admin name
 *   org.accentColor      - hex color string, e.g. #4f7cff
 *   org.configured       - "true" once setup wizard has been completed
 *   org.licenseAccepted  - "true" once the user has accepted the license agreement
 *   org.allowReset       - "true" if database/settings reset is enabled (default: false)
 *   org.sharedDataPath   - path to shared data folder (blank = use local data/)
 */
public class OrgConfig {

    // Config file stays local - one per machine
    public static final String CONFIG_FILE = "data/org.properties";

    public static final String KEY_NAME             = "org.name";
    public static final String KEY_TYPE             = "org.type";
    public static final String KEY_ADMIN            = "org.adminName";
    public static final String KEY_COLOR            = "org.accentColor";
    public static final String KEY_CONFIGURED       = "org.configured";
    public static final String KEY_LICENSE_ACCEPTED = "org.licenseAccepted";
    public static final String KEY_ALLOW_RESET      = "org.allowReset";
    public static final String KEY_SHARED_DATA_PATH = "org.sharedDataPath";

    public static final String DEFAULT_COLOR = "#4f7cff";

    private final Properties props = new Properties();
    private final Path configPath;

    public OrgConfig() {
        this.configPath = Paths.get(CONFIG_FILE);
        load();
    }

    private void load() {
        if (!Files.exists(configPath)) return;
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Could not load org config: " + e.getMessage());
        }
    }

    public void save() throws IOException {
        Files.createDirectories(configPath.getParent());
        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out, "Lake Erie Inventory - Organization Configuration (local)");
        }
    }

    // ── License ───────────────────────────────────────────────────────────────

    public boolean isLicenseAccepted() {
        return "true".equalsIgnoreCase(props.getProperty(KEY_LICENSE_ACCEPTED, "false"));
    }

    public void setLicenseAccepted(boolean v) {
        props.setProperty(KEY_LICENSE_ACCEPTED, v ? "true" : "false");
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    public boolean isConfigured() {
        return "true".equalsIgnoreCase(props.getProperty(KEY_CONFIGURED, "false"));
    }

    public void markConfigured() {
        props.setProperty(KEY_CONFIGURED, "true");
    }

    // ── Org details ───────────────────────────────────────────────────────────

    public String getOrgName()     { return props.getProperty(KEY_NAME,  ""); }
    public String getOrgType()     { return props.getProperty(KEY_TYPE,  ""); }
    public String getAdminName()   { return props.getProperty(KEY_ADMIN, ""); }
    public String getAccentColor() { return props.getProperty(KEY_COLOR, DEFAULT_COLOR); }

    public void setOrgName(String v)     { props.setProperty(KEY_NAME,  v); }
    public void setOrgType(String v)     { props.setProperty(KEY_TYPE,  v); }
    public void setAdminName(String v)   { props.setProperty(KEY_ADMIN, v); }
    public void setAccentColor(String v) { props.setProperty(KEY_COLOR, v); }

    // ── Shared data path ──────────────────────────────────────────────────────

    /**
     * Returns the path to the shared data directory.
     * Returns "data" (local) if no shared path has been configured.
     */
    public String getDataPath() {
        String shared = props.getProperty(KEY_SHARED_DATA_PATH, "").trim();
        return shared.isBlank() ? "data" : shared;
    }

    public void setDataPath(String v) {
        props.setProperty(KEY_SHARED_DATA_PATH, v == null ? "" : v.trim());
    }

    public boolean isUsingSharedPath() {
        String shared = props.getProperty(KEY_SHARED_DATA_PATH, "").trim();
        return !shared.isBlank();
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    public boolean isResetAllowed() {
        return "true".equalsIgnoreCase(props.getProperty(KEY_ALLOW_RESET, "false"));
    }

    public void setResetAllowed(boolean v) {
        props.setProperty(KEY_ALLOW_RESET, v ? "true" : "false");
    }
}
