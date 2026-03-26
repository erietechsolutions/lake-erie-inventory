package com.itinventory.util;

import com.itinventory.model.Asset;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages custom categories and statuses for Lake Erie Inventory.
 *
 * Stored in {dataDir}/custom_fields.properties on the shared drive.
 * Built-in values are always locked and cannot be modified.
 *
 * Guardrails enforced:
 *   1. Built-in values are permanently locked
 *   2. Names: 1-30 chars, letters/numbers/spaces/hyphens/underscores only
 *   3. Max 20 custom entries per type
 *   4. Names must be unique (case-insensitive) within each type
 *   5. Cannot delete entries in use by existing assets
 *   6. Disable rather than delete when assets reference the entry
 *   7. Character validation enforced on input
 *   8. Admin-only (enforced in UI layer)
 *   9. Backup written before every save
 */
public class CustomFieldsManager {

    private static final Logger LOG = Logger.getLogger(CustomFieldsManager.class.getName());

    public static final String FILE_NAME   = "custom_fields.properties";
    public static final int    MAX_CUSTOM  = 20;
    public static final int    MAX_NAME_LEN = 30;
    private static final Pattern VALID_NAME =
            Pattern.compile("^[A-Za-z0-9 \\-_]{1,30}$");

    // ── Built-in values (locked) ──────────────────────────────────────────────

    public static final List<String> BUILTIN_CATEGORIES = List.of(
        "LAPTOP", "DESKTOP", "SERVER", "MONITOR", "PRINTER",
        "NETWORK_DEVICE", "PHONE", "TABLET", "PERIPHERAL",
        "SOFTWARE_LICENSE", "OTHER"
    );

    public static final List<String> BUILTIN_STATUSES = List.of(
        "ACTIVE", "INACTIVE", "IN_REPAIR", "RETIRED", "MISSING"
    );

    // ── Custom entry ──────────────────────────────────────────────────────────

    public record CustomEntry(String name, boolean active) {
        public String displayName() {
            return name.replace("_", " ");
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Path fieldsFile;
    private final Path backupDir;
    private final List<CustomEntry> customCategories = new ArrayList<>();
    private final List<CustomEntry> customStatuses   = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public CustomFieldsManager(String dataDirectory) {
        this.fieldsFile = Paths.get(dataDirectory, FILE_NAME);
        this.backupDir  = Paths.get(dataDirectory, "backups");
        load();
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    private void load() {
        customCategories.clear();
        customStatuses.clear();
        if (!Files.exists(fieldsFile)) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(fieldsFile)) {
            props.load(in);
        } catch (IOException e) {
            LOG.warning("Could not load custom fields: " + e.getMessage());
            return;
        }

        // Categories: custom.category.0=NAME:true, custom.category.1=NAME:false ...
        for (int i = 0; i < MAX_CUSTOM; i++) {
            String val = props.getProperty("custom.category." + i);
            if (val == null) break;
            String[] parts = val.split(":", 2);
            if (parts.length == 2) {
                customCategories.add(new CustomEntry(
                    parts[0].trim(), "true".equalsIgnoreCase(parts[1].trim())));
            }
        }

        for (int i = 0; i < MAX_CUSTOM; i++) {
            String val = props.getProperty("custom.status." + i);
            if (val == null) break;
            String[] parts = val.split(":", 2);
            if (parts.length == 2) {
                customStatuses.add(new CustomEntry(
                    parts[0].trim(), "true".equalsIgnoreCase(parts[1].trim())));
            }
        }

        LOG.info("Loaded " + customCategories.size() + " custom categories, " +
                 customStatuses.size() + " custom statuses.");
    }

    public void save() throws IOException {
        backupIfExists();
        Files.createDirectories(fieldsFile.getParent());

        Properties props = new Properties();
        for (int i = 0; i < customCategories.size(); i++) {
            CustomEntry e = customCategories.get(i);
            props.setProperty("custom.category." + i, e.name() + ":" + e.active());
        }
        for (int i = 0; i < customStatuses.size(); i++) {
            CustomEntry e = customStatuses.get(i);
            props.setProperty("custom.status." + i, e.name() + ":" + e.active());
        }

        try (OutputStream out = Files.newOutputStream(fieldsFile)) {
            props.store(out, "Lake Erie Inventory - Custom Fields");
        }
        LOG.info("Saved custom fields.");
    }

    private void backupIfExists() throws IOException {
        if (!Files.exists(fieldsFile)) return;
        Files.createDirectories(backupDir);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Files.copy(fieldsFile, backupDir.resolve("custom_fields_" + ts + ".properties"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** All category names available for selection (built-ins + active custom). */
    public List<String> getActiveCategories() {
        List<String> result = new ArrayList<>(BUILTIN_CATEGORIES);
        customCategories.stream()
            .filter(CustomEntry::active)
            .map(CustomEntry::name)
            .forEach(result::add);
        return Collections.unmodifiableList(result);
    }

    /** All status names available for selection (built-ins + active custom). */
    public List<String> getActiveStatuses() {
        List<String> result = new ArrayList<>(BUILTIN_STATUSES);
        customStatuses.stream()
            .filter(CustomEntry::active)
            .map(CustomEntry::name)
            .forEach(result::add);
        return Collections.unmodifiableList(result);
    }

    public List<CustomEntry> getCustomCategories() {
        return Collections.unmodifiableList(customCategories);
    }

    public List<CustomEntry> getCustomStatuses() {
        return Collections.unmodifiableList(customStatuses);
    }

    public boolean isBuiltinCategory(String name) {
        return BUILTIN_CATEGORIES.stream()
                .anyMatch(b -> b.equalsIgnoreCase(name));
    }

    public boolean isBuiltinStatus(String name) {
        return BUILTIN_STATUSES.stream()
                .anyMatch(b -> b.equalsIgnoreCase(name));
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    public void addCategory(String name) throws IOException {
        String normalized = normalize(name);
        validateName(normalized);
        validateNotBuiltinCategory(normalized);
        validateUniqueCategoryName(normalized, null);
        if (customCategories.size() >= MAX_CUSTOM) {
            throw new IllegalStateException(
                "Maximum of " + MAX_CUSTOM + " custom categories reached.");
        }
        customCategories.add(new CustomEntry(normalized, true));
        save();
    }

    public void addStatus(String name) throws IOException {
        String normalized = normalize(name);
        validateName(normalized);
        validateNotBuiltinStatus(normalized);
        validateUniqueStatusName(normalized, null);
        if (customStatuses.size() >= MAX_CUSTOM) {
            throw new IllegalStateException(
                "Maximum of " + MAX_CUSTOM + " custom statuses reached.");
        }
        customStatuses.add(new CustomEntry(normalized, true));
        save();
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    public void renameCategory(String oldName, String newName) throws IOException {
        validateNotBuiltinCategory(oldName);
        String normalized = normalize(newName);
        validateName(normalized);
        validateNotBuiltinCategory(normalized);
        validateUniqueCategoryName(normalized, oldName);
        replaceInList(customCategories, oldName, normalized);
        save();
    }

    public void renameStatus(String oldName, String newName) throws IOException {
        validateNotBuiltinStatus(oldName);
        String normalized = normalize(newName);
        validateName(normalized);
        validateNotBuiltinStatus(normalized);
        validateUniqueStatusName(normalized, oldName);
        replaceInList(customStatuses, oldName, normalized);
        save();
    }

    // ── Disable / Enable ──────────────────────────────────────────────────────

    public void setCategoryActive(String name, boolean active) throws IOException {
        validateNotBuiltinCategory(name);
        setActive(customCategories, name, active);
        save();
    }

    public void setStatusActive(String name, boolean active) throws IOException {
        validateNotBuiltinStatus(name);
        setActive(customStatuses, name, active);
        save();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a custom category only if no assets reference it.
     * @param assetCategories set of all category strings currently used in inventory
     */
    public void deleteCategory(String name,
                               Collection<String> assetCategories) throws IOException {
        validateNotBuiltinCategory(name);
        long inUse = assetCategories.stream()
                .filter(c -> c != null && c.equalsIgnoreCase(name))
                .count();
        if (inUse > 0) {
            throw new IllegalStateException(
                "Cannot delete '" + name + "' - it is used by " + inUse +
                " asset(s). Disable it instead.");
        }
        customCategories.removeIf(e -> e.name().equalsIgnoreCase(name));
        save();
    }

    /**
     * Deletes a custom status only if no assets reference it.
     * @param assetStatuses set of all status strings currently used in inventory
     */
    public void deleteStatus(String name,
                             Collection<String> assetStatuses) throws IOException {
        validateNotBuiltinStatus(name);
        long inUse = assetStatuses.stream()
                .filter(s -> s != null && s.equalsIgnoreCase(name))
                .count();
        if (inUse > 0) {
            throw new IllegalStateException(
                "Cannot delete '" + name + "' - it is used by " + inUse +
                " asset(s). Disable it instead.");
        }
        customStatuses.removeIf(e -> e.name().equalsIgnoreCase(name));
        save();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public static String validateNameInput(String name) {
        if (name == null || name.isBlank())
            return "Name cannot be empty.";
        String normalized = normalize(name);
        if (normalized.length() > MAX_NAME_LEN)
            return "Name cannot exceed " + MAX_NAME_LEN + " characters.";
        if (!VALID_NAME.matcher(normalized).matches())
            return "Name can only contain letters, numbers, spaces, hyphens, and underscores.";
        return null; // valid
    }

    private void validateName(String name) {
        String err = validateNameInput(name);
        if (err != null) throw new IllegalArgumentException(err);
    }

    private void validateNotBuiltinCategory(String name) {
        if (isBuiltinCategory(name))
            throw new IllegalArgumentException(
                "'" + name + "' is a built-in category and cannot be modified.");
    }

    private void validateNotBuiltinStatus(String name) {
        if (isBuiltinStatus(name))
            throw new IllegalArgumentException(
                "'" + name + "' is a built-in status and cannot be modified.");
    }

    private void validateUniqueCategoryName(String name, String excludeName) {
        boolean exists = customCategories.stream()
            .filter(e -> excludeName == null || !e.name().equalsIgnoreCase(excludeName))
            .anyMatch(e -> e.name().equalsIgnoreCase(name));
        if (exists || isBuiltinCategory(name))
            throw new IllegalArgumentException(
                "Category '" + name + "' already exists.");
    }

    private void validateUniqueStatusName(String name, String excludeName) {
        boolean exists = customStatuses.stream()
            .filter(e -> excludeName == null || !e.name().equalsIgnoreCase(excludeName))
            .anyMatch(e -> e.name().equalsIgnoreCase(name));
        if (exists || isBuiltinStatus(name))
            throw new IllegalArgumentException(
                "Status '" + name + "' already exists.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Converts to uppercase with underscores for spaces. */
    public static String normalize(String name) {
        if (name == null) return "";
        return name.trim().toUpperCase().replace(' ', '_');
    }

    private void replaceInList(List<CustomEntry> list,
                               String oldName, String newName) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(oldName)) {
                list.set(i, new CustomEntry(newName, list.get(i).active()));
                return;
            }
        }
        throw new NoSuchElementException("Entry not found: " + oldName);
    }

    private void setActive(List<CustomEntry> list, String name, boolean active) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(name)) {
                list.set(i, new CustomEntry(list.get(i).name(), active));
                return;
            }
        }
        throw new NoSuchElementException("Entry not found: " + name);
    }
}
