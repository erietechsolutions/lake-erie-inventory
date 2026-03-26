package com.itinventory;

import com.itinventory.model.Asset;
import com.itinventory.model.Asset.Category;
import com.itinventory.model.Asset.Status;
import com.itinventory.service.InventoryService;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Lightweight self-contained test harness (no external frameworks required).
 * Run via: java -cp out com.itinventory.InventoryTests
 */
public class InventoryTests {

    private static int passed = 0;
    private static int failed = 0;

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("Running IT Inventory tests...\n");

        testAssetCsvRoundtrip();
        testAssetCsvWithCommaInField();
        testWarrantyExpiry();
        testServiceCrud();
        testServiceSearch();
        testServiceStatistics();

        System.out.printf("%n── Results: %d passed, %d failed ──%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    static void testAssetCsvRoundtrip() {
        Asset original = buildSampleAsset();
        String csv = original.toCsvRow();
        Asset parsed = Asset.fromCsvRow(csv);

        assertEqual("name",          original.getName(),           parsed.getName());
        assertEqual("category",      original.getCategory(),       parsed.getCategory());
        assertEqual("status",        original.getStatus(),         parsed.getStatus());
        assertEqual("purchaseDate",  original.getPurchaseDate(),   parsed.getPurchaseDate());
        assertEqual("price",         original.getPurchasePrice(),  parsed.getPurchasePrice());
        assertEqual("warrantyExpiry",original.getWarrantyExpiry(), parsed.getWarrantyExpiry());
        pass("testAssetCsvRoundtrip");
    }

    static void testAssetCsvWithCommaInField() {
        Asset a = new Asset();
        a.setAssetId("ASSET-0001");
        a.setName("Server, Rack Unit");           // comma in name
        a.setNotes("Located in \"Room A\", floor 2"); // quotes and comma in notes
        a.setCategory(Category.SERVER);
        a.setStatus(Status.ACTIVE);

        String csv = a.toCsvRow();
        Asset parsed = Asset.fromCsvRow(csv);

        assertEqual("name with comma",   a.getName(),  parsed.getName());
        assertEqual("notes with quotes", a.getNotes(), parsed.getNotes());
        pass("testAssetCsvWithCommaInField");
    }

    static void testWarrantyExpiry() {
        Asset expired = new Asset();
        expired.setWarrantyExpiry(LocalDate.now().minusDays(1));
        assertTrue("expired warranty detected", expired.isWarrantyExpired());

        Asset expiringSoon = new Asset();
        expiringSoon.setWarrantyExpiry(LocalDate.now().plusDays(15));
        assertTrue("expiring in 15 days detected", expiringSoon.isWarrantyExpiringSoon(30));
        assertFalse("not expiring within 10 days", expiringSoon.isWarrantyExpiringSoon(10));

        Asset valid = new Asset();
        valid.setWarrantyExpiry(LocalDate.now().plusDays(180));
        assertFalse("valid warranty not expired", valid.isWarrantyExpired());

        pass("testWarrantyExpiry");
    }

    static void testServiceCrud() throws IOException {
        Path tmpDir = Files.createTempDirectory("inv-test-");
        InventoryService svc = new InventoryService(tmpDir.toString());

        assertEqual("empty at start", 0, svc.getTotalCount());

        // Add
        Asset a = buildSampleAsset();
        String id = svc.addAsset(a);
        assertEqual("count after add", 1, svc.getTotalCount());
        assertEqual("id assigned", id, svc.getAsset(id).getAssetId());

        // Update
        Asset fetched = svc.getAsset(id);
        fetched.setName("Updated Laptop");
        svc.updateAsset(fetched);
        assertEqual("name updated", "Updated Laptop", svc.getAsset(id).getName());

        // Reload from disk
        svc.reload();
        assertEqual("persisted after reload", "Updated Laptop", svc.getAsset(id).getName());

        // Delete
        svc.deleteAsset(id);
        assertEqual("count after delete", 0, svc.getTotalCount());

        cleanup(tmpDir);
        pass("testServiceCrud");
    }

    static void testServiceSearch() throws IOException {
        Path tmpDir = Files.createTempDirectory("inv-search-");
        InventoryService svc = new InventoryService(tmpDir.toString());

        Asset a1 = buildSampleAsset();  a1.setName("Dell Latitude"); a1.setAssignedTo("Alice");
        Asset a2 = buildSampleAsset();  a2.setName("HP EliteBook");  a2.setAssignedTo("Bob");
        a2.setCategory(Category.LAPTOP); a2.setStatus(Status.INACTIVE);

        svc.addAsset(a1);
        svc.addAsset(a2);

        assertEqual("search by name 'Dell'", 1, svc.searchByName("Dell").size());
        assertEqual("search by name 'ok'",   1, svc.searchByName("ok").size()); // EliteBook
        assertEqual("filter by assignee",     1, svc.filterByAssignee("Alice").size());
        assertEqual("filter INACTIVE status", 1, svc.filterByStatus(Status.INACTIVE).size());

        cleanup(tmpDir);
        pass("testServiceSearch");
    }

    static void testServiceStatistics() throws IOException {
        Path tmpDir = Files.createTempDirectory("inv-stats-");
        InventoryService svc = new InventoryService(tmpDir.toString());

        for (int i = 0; i < 3; i++) {
            Asset a = buildSampleAsset();
            a.setPurchasePrice(1000.0);
            svc.addAsset(a);
        }

        assertEqual("total count", 3, svc.getTotalCount());
        assertEqual("total value", 3000.0, svc.getTotalValue());
        assertTrue("laptop category count > 0",
                svc.countByCategory().getOrDefault(Category.LAPTOP, 0L) > 0);

        cleanup(tmpDir);
        pass("testServiceStatistics");
    }

    // ── Sample data ───────────────────────────────────────────────────────────

    private static Asset buildSampleAsset() {
        return new Asset(
                null,
                "Dell Latitude 5540",
                Category.LAPTOP,
                "Dell",
                "Latitude 5540",
                "SN-12345",
                Status.ACTIVE,
                "John Doe",
                "Office 101",
                LocalDate.of(2023, 6, 15),
                1299.99,
                LocalDate.of(2026, 6, 14),
                "Standard corporate laptop"
        );
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    static void assertEqual(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            System.err.printf("  FAIL [%s]: expected <%s> but got <%s>%n", label, expected, actual);
            failed++;
        }
    }

    static void assertTrue(String label, boolean condition) {
        if (!condition) {
            System.err.printf("  FAIL [%s]: expected true%n", label);
            failed++;
        }
    }

    static void assertFalse(String label, boolean condition) {
        if (condition) {
            System.err.printf("  FAIL [%s]: expected false%n", label);
            failed++;
        }
    }

    static void pass(String testName) {
        System.out.printf("  PASS  %s%n", testName);
        passed++;
    }

    static void cleanup(Path dir) {
        try {
            Files.walk(dir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
