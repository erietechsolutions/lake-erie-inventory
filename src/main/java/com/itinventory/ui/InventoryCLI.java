package com.itinventory.ui;

import com.itinventory.model.Asset;
import com.itinventory.model.Asset.Category;
import com.itinventory.model.Asset.Status;
import com.itinventory.service.InventoryService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Interactive command-line interface for IT Inventory Management.
 *
 * <p>Run {@link #start()} to enter the main menu loop.
 */
public class InventoryCLI {

    private static final String DATA_DIR   = "data";
    private static final String SEPARATOR  = "─".repeat(62);
    private static final String THIN_SEP   = "·".repeat(62);

    private final Scanner     input;
    private final InventoryService service;

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    public InventoryCLI() throws IOException {
        this.input   = new Scanner(System.in);
        this.service = new InventoryService(DATA_DIR);
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    public void start() {
        banner();
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Choose an option").trim();
            switch (choice) {
                case "1"  -> listAllAssets();
                case "2"  -> addAssetWizard();
                case "3"  -> editAssetWizard();
                case "4"  -> deleteAssetWizard();
                case "5"  -> searchMenu();
                case "6"  -> reportsMenu();
                case "7"  -> exportMenu();
                case "8"  -> { reload(); }
                case "0"  -> running = false;
                default   -> warn("Unknown option – please try again.");
            }
        }
        println("\nGoodbye! Inventory data saved to ./" + DATA_DIR + "/inventory.csv");
    }

    // ── Menus ─────────────────────────────────────────────────────────────────

    private void printMainMenu() {
        println("\n" + SEPARATOR);
        println("  IT INVENTORY MANAGEMENT  –  " + service.getTotalCount() + " asset(s) on record");
        println(SEPARATOR);
        println("  1. List all assets            5. Search / filter");
        println("  2. Add new asset              6. Reports");
        println("  3. Edit asset                 7. Export to CSV");
        println("  4. Delete asset               8. Reload from disk");
        println("  0. Quit");
        println(SEPARATOR);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private void listAllAssets() {
        Collection<Asset> all = service.getAllAssets();
        if (all.isEmpty()) {
            info("No assets in inventory yet.");
            return;
        }
        printAssetTable(all);
    }

    // ── Add wizard ────────────────────────────────────────────────────────────

    private void addAssetWizard() {
        println("\n── Add New Asset " + THIN_SEP.substring(16));
        Asset a = new Asset();

        a.setName(prompt("  Name (e.g. 'Dell Latitude 5540')"));
        a.setCategory(pickEnum("  Category", Category.class));
        a.setManufacturer(prompt("  Manufacturer"));
        a.setModel(prompt("  Model"));
        a.setSerialNumber(prompt("  Serial Number"));
        a.setStatus(pickEnum("  Status", Status.class));
        a.setAssignedTo(promptOptional("  Assigned To (leave blank if unassigned)"));
        a.setLocation(promptOptional("  Location / Room"));
        a.setPurchaseDate(promptDate("  Purchase Date (YYYY-MM-DD, blank to skip)"));
        a.setPurchasePrice(promptDouble("  Purchase Price (USD, 0 if unknown)"));
        a.setWarrantyExpiry(promptDate("  Warranty Expiry (YYYY-MM-DD, blank to skip)"));
        a.setNotes(promptOptional("  Notes"));

        try {
            String id = service.addAsset(a);
            ok("Asset added successfully with ID: " + id);
        } catch (IOException e) {
            error("Failed to save asset: " + e.getMessage());
        }
    }

    // ── Edit wizard ───────────────────────────────────────────────────────────

    private void editAssetWizard() {
        String id = prompt("  Enter Asset ID to edit");
        Asset existing;
        try {
            existing = service.getAsset(id);
        } catch (NoSuchElementException e) {
            warn("Asset '" + id + "' not found.");
            return;
        }

        println("\n── Edit Asset: " + id + " – current values shown in [brackets] " + THIN_SEP.substring(40));

        existing.setName(         promptWithDefault("  Name",          existing.getName()));
        existing.setManufacturer( promptWithDefault("  Manufacturer",  existing.getManufacturer()));
        existing.setModel(        promptWithDefault("  Model",         existing.getModel()));
        existing.setSerialNumber( promptWithDefault("  Serial Number", existing.getSerialNumber()));
        existing.setStatus(       pickEnumWithDefault("  Status", Status.class, existing.getStatus()));
        existing.setAssignedTo(   promptWithDefault("  Assigned To",   existing.getAssignedTo()));
        existing.setLocation(     promptWithDefault("  Location",      existing.getLocation()));
        existing.setPurchasePrice(promptDoubleWithDefault("  Purchase Price", existing.getPurchasePrice()));
        existing.setWarrantyExpiry(promptDateWithDefault("  Warranty Expiry (YYYY-MM-DD)", existing.getWarrantyExpiry()));
        existing.setNotes(        promptWithDefault("  Notes",         existing.getNotes()));

        try {
            service.updateAsset(existing);
            ok("Asset " + id + " updated.");
        } catch (IOException e) {
            error("Failed to update asset: " + e.getMessage());
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void deleteAssetWizard() {
        String id = prompt("  Enter Asset ID to delete");
        try {
            Asset a = service.getAsset(id);
            println("  You are about to delete: " + a);
            String confirm = prompt("  Type 'yes' to confirm");
            if ("yes".equalsIgnoreCase(confirm)) {
                service.deleteAsset(id);
                ok("Asset " + id + " deleted.");
            } else {
                info("Delete cancelled.");
            }
        } catch (NoSuchElementException e) {
            warn("Asset '" + id + "' not found.");
        } catch (IOException e) {
            error("Failed to delete asset: " + e.getMessage());
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void searchMenu() {
        println("\n── Search / Filter ──────────────────────────────────────");
        println("  1. Search by name        4. Filter by status");
        println("  2. Filter by category    5. Filter by assignee");
        println("  3. Filter by location    0. Back");
        String c = prompt("  Choose");
        switch (c) {
            case "1" -> printAssetTable(service.searchByName(prompt("  Name contains")));
            case "2" -> printAssetTable(service.filterByCategory(pickEnum("  Category", Category.class)));
            case "3" -> printAssetTable(service.filterByLocation(prompt("  Location contains")));
            case "4" -> printAssetTable(service.filterByStatus(pickEnum("  Status", Status.class)));
            case "5" -> printAssetTable(service.filterByAssignee(prompt("  Assignee contains")));
            case "0" -> { }
            default  -> warn("Unknown option.");
        }
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    private void reportsMenu() {
        println("\n── Reports ──────────────────────────────────────────────");
        println("  1. Summary statistics");
        println("  2. Assets by category");
        println("  3. Assets by status");
        println("  4. Expired warranties");
        println("  5. Warranties expiring soon (30 days)");
        println("  0. Back");
        String c = prompt("  Choose");
        switch (c) {
            case "1" -> reportSummary();
            case "2" -> reportByCategory();
            case "3" -> reportByStatus();
            case "4" -> printAssetTable(service.getExpiredWarranties());
            case "5" -> printAssetTable(service.getWarrantiesExpiringSoon(30));
            case "0" -> { }
            default  -> warn("Unknown option.");
        }
    }

    private void reportSummary() {
        println("\n  ── Inventory Summary ──────────────────────────────");
        printf("  %-30s %s%n", "Total assets:",  service.getTotalCount());
        printf("  %-30s $%.2f%n", "Total value:", service.getTotalValue());
        printf("  %-30s %d%n", "Expired warranties:", service.getExpiredWarranties().size());
        printf("  %-30s %d%n", "Expiring in 30 days:", service.getWarrantiesExpiringSoon(30).size());
    }

    private void reportByCategory() {
        println("\n  ── Assets by Category ─────────────────────────────");
        service.countByCategory().entrySet().stream()
                .sorted(Map.Entry.<Category, Long>comparingByValue().reversed())
                .forEach(e -> printf("  %-20s %d%n", e.getKey(), e.getValue()));
    }

    private void reportByStatus() {
        println("\n  ── Assets by Status ───────────────────────────────");
        service.countByStatus().entrySet().stream()
                .sorted(Map.Entry.<Status, Long>comparingByValue().reversed())
                .forEach(e -> printf("  %-20s %d%n", e.getKey(), e.getValue()));
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private void exportMenu() {
        println("\n── Export ───────────────────────────────────────────────");
        println("  1. Export all assets");
        println("  2. Export expired warranties");
        println("  3. Export by status");
        println("  0. Back");
        String c = prompt("  Choose");
        String path;
        try {
            switch (c) {
                case "1" -> {
                    path = promptWithDefault("  Output file", "data/export_all.csv");
                    service.exportFiltered(new ArrayList<>(service.getAllAssets()), path);
                    ok("Exported to " + path);
                }
                case "2" -> {
                    path = promptWithDefault("  Output file", "data/export_expired_warranty.csv");
                    service.exportFiltered(service.getExpiredWarranties(), path);
                    ok("Exported to " + path);
                }
                case "3" -> {
                    Status s = pickEnum("  Status to export", Status.class);
                    path = promptWithDefault("  Output file", "data/export_" + s.name().toLowerCase() + ".csv");
                    service.exportFiltered(service.filterByStatus(s), path);
                    ok("Exported to " + path);
                }
                case "0" -> { }
                default  -> warn("Unknown option.");
            }
        } catch (IOException e) {
            error("Export failed: " + e.getMessage());
        }
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    private void reload() {
        try {
            service.reload();
            ok("Inventory reloaded from disk. " + service.getTotalCount() + " asset(s).");
        } catch (IOException e) {
            error("Reload failed: " + e.getMessage());
        }
    }

    // ── Table rendering ───────────────────────────────────────────────────────

    private void printAssetTable(Collection<Asset> list) {
        if (list == null || list.isEmpty()) {
            info("No assets match the query.");
            return;
        }
        println("");
        printf("  %-12s %-24s %-14s %-12s %-16s %-14s%n",
                "ID", "Name", "Category", "Status", "Assigned To", "Location");
        println("  " + THIN_SEP);
        for (Asset a : list) {
            printf("  %-12s %-24s %-14s %-12s %-16s %-14s%n",
                    trunc(a.getAssetId(), 12),
                    trunc(a.getName(), 24),
                    a.getCategory() != null ? a.getCategory().name() : "-",
                    a.getStatus()   != null ? a.getStatus().name()   : "-",
                    trunc(a.getAssignedTo(), 16),
                    trunc(a.getLocation(), 14));
        }
        println("  " + THIN_SEP);
        printf("  %d asset(s)%n", list.size());
    }

    // ── Input helpers ─────────────────────────────────────────────────────────

    private String prompt(String label) {
        System.out.print(label + ": ");
        return input.nextLine();
    }

    private String promptOptional(String label) {
        System.out.print(label + ": ");
        return input.nextLine().trim();
    }

    private String promptWithDefault(String label, String defaultValue) {
        String d = defaultValue == null ? "" : defaultValue;
        System.out.print(label + " [" + d + "]: ");
        String v = input.nextLine().trim();
        return v.isBlank() ? d : v;
    }

    private double promptDouble(String label) {
        while (true) {
            System.out.print(label + ": ");
            String v = input.nextLine().trim();
            if (v.isBlank()) return 0.0;
            try { return Double.parseDouble(v); }
            catch (NumberFormatException e) { warn("  Please enter a valid number."); }
        }
    }

    private double promptDoubleWithDefault(String label, double current) {
        System.out.print(label + " [" + current + "]: ");
        String v = input.nextLine().trim();
        if (v.isBlank()) return current;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { warn("  Invalid number – keeping existing value."); return current; }
    }

    private LocalDate promptDate(String label) {
        while (true) {
            System.out.print(label + ": ");
            String v = input.nextLine().trim();
            if (v.isBlank()) return null;
            try { return LocalDate.parse(v); }
            catch (DateTimeParseException e) { warn("  Invalid date – use YYYY-MM-DD or leave blank."); }
        }
    }

    private LocalDate promptDateWithDefault(String label, LocalDate current) {
        String d = current != null ? current.toString() : "";
        System.out.print(label + " [" + d + "]: ");
        String v = input.nextLine().trim();
        if (v.isBlank()) return current;
        try { return LocalDate.parse(v); }
        catch (DateTimeParseException e) { warn("  Invalid date – keeping existing value."); return current; }
    }

    private <E extends Enum<E>> E pickEnum(String label, Class<E> enumClass) {
        E[] vals = enumClass.getEnumConstants();
        println(label + ":");
        for (int i = 0; i < vals.length; i++) {
            printf("    %2d. %s%n", i + 1, vals[i].name());
        }
        while (true) {
            System.out.print("  Enter number: ");
            String v = input.nextLine().trim();
            try {
                int idx = Integer.parseInt(v) - 1;
                if (idx >= 0 && idx < vals.length) return vals[idx];
            } catch (NumberFormatException ignored) {}
            warn("  Please enter a number between 1 and " + vals.length);
        }
    }

    private <E extends Enum<E>> E pickEnumWithDefault(String label, Class<E> enumClass, E current) {
        E[] vals = enumClass.getEnumConstants();
        println(label + " [current: " + (current != null ? current.name() : "none") + "]:");
        for (int i = 0; i < vals.length; i++) {
            printf("    %2d. %s%n", i + 1, vals[i].name());
        }
        System.out.print("  Enter number (blank to keep): ");
        String v = input.nextLine().trim();
        if (v.isBlank()) return current;
        try {
            int idx = Integer.parseInt(v) - 1;
            if (idx >= 0 && idx < vals.length) return vals[idx];
        } catch (NumberFormatException ignored) {}
        warn("  Invalid choice – keeping existing value.");
        return current;
    }

    // ── Print helpers ─────────────────────────────────────────────────────────

    private void banner() {
        println("\n╔══════════════════════════════════════════════════════════════╗");
        println("║          IT ASSET INVENTORY MANAGEMENT SYSTEM               ║");
        println("║                    CSV-Backed Edition                       ║");
        println("╚══════════════════════════════════════════════════════════════╝");
        printf("  Data directory : %s/inventory.csv%n", DATA_DIR);
        printf("  Assets loaded  : %d%n", service.getTotalCount());
    }

    private void println(String s)                   { System.out.println(s); }
    private void printf(String fmt, Object... args)  { System.out.printf(fmt, args); }
    private void ok(String msg)    { System.out.println("  ✔  " + msg); }
    private void info(String msg)  { System.out.println("  ℹ  " + msg); }
    private void warn(String msg)  { System.out.println("  ⚠  " + msg); }
    private void error(String msg) { System.out.println("  ✖  " + msg); }

    private String trunc(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
