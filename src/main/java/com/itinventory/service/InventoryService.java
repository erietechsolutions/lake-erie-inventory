package com.itinventory.service;

import com.itinventory.model.Asset;
import com.itinventory.model.Asset.Category;
import com.itinventory.model.Asset.Status;
import com.itinventory.util.CsvManager;
import com.itinventory.util.IdGenerator;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service layer for IT Inventory Management.
 * All mutating operations are immediately persisted to CSV via CsvManager.
 */
public class InventoryService {

    private final CsvManager csvManager;
    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private IdGenerator idGen;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public InventoryService(String dataDirectory) throws IOException {
        this.csvManager = new CsvManager(dataDirectory);
        reload();
    }

    /** Re-reads the CSV file, replacing the in-memory store. */
    public void reload() throws IOException {
        assets.clear();
        List<Asset> loaded = csvManager.loadAll();
        loaded.forEach(a -> assets.put(a.getAssetId(), a));
        idGen = new IdGenerator(assets.keySet());
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Adds a new asset, assigning a generated ID.
     * @return the generated asset ID
     */
    public String addAsset(Asset asset) throws IOException {
        String id = idGen.next();
        asset.setAssetId(id);
        validateUniqueTag(asset.getAssetTag(), id);
        assets.put(id, asset);
        persist();
        return id;
    }

    /**
     * Updates an existing asset.
     * @throws NoSuchElementException if the asset ID is not found
     */
    public void updateAsset(Asset updated) throws IOException {
        requireExists(updated.getAssetId());
        validateUniqueTag(updated.getAssetTag(), updated.getAssetId());
        assets.put(updated.getAssetId(), updated);
        persist();
    }

    /**
     * Deletes an asset by ID.
     * @throws NoSuchElementException if the asset ID is not found
     */
    public void deleteAsset(String assetId) throws IOException {
        requireExists(assetId);
        assets.remove(assetId);
        persist();
    }

    /**
     * Retrieves a single asset by ID.
     * @throws NoSuchElementException if not found
     */
    public Asset getAsset(String assetId) {
        requireExists(assetId);
        return assets.get(assetId);
    }

    /** Returns an unmodifiable view of all assets. */
    public Collection<Asset> getAllAssets() {
        return Collections.unmodifiableCollection(assets.values());
    }

    // ── Status updates ────────────────────────────────────────────────────────

    public void updateStatus(String assetId, Status newStatus) throws IOException {
        Asset a = getAsset(assetId);
        a.setStatus(newStatus);
        persist();
    }

    public void assignTo(String assetId, String employee, String location) throws IOException {
        Asset a = getAsset(assetId);
        a.setAssignedTo(employee);
        a.setLocation(location);
        a.setStatus(Status.ACTIVE);
        persist();
    }

    public void unassign(String assetId) throws IOException {
        Asset a = getAsset(assetId);
        a.setAssignedTo("");
        a.setStatus(Status.INACTIVE);
        persist();
    }

    // ── Search and filter ─────────────────────────────────────────────────────

    public List<Asset> searchByName(String query) {
        String q = query.toLowerCase();
        return assets.values().stream()
                .filter(a -> a.getName() != null && a.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public List<Asset> filterByCategory(Category category) {
        return assets.values().stream()
                .filter(a -> category != null &&
                             category.name().equalsIgnoreCase(a.getCategoryStr()))
                .collect(Collectors.toList());
    }

    public List<Asset> filterByCategoryStr(String category) {
        if (category == null || category.isBlank()) return new ArrayList<>(assets.values());
        return assets.values().stream()
                .filter(a -> category.equalsIgnoreCase(a.getCategoryStr()))
                .collect(Collectors.toList());
    }

    public List<Asset> filterByStatus(Status status) {
        return assets.values().stream()
                .filter(a -> status != null &&
                             status.name().equalsIgnoreCase(a.getStatusStr()))
                .collect(Collectors.toList());
    }

    public List<Asset> filterByStatusStr(String status) {
        if (status == null || status.isBlank()) return new ArrayList<>(assets.values());
        return assets.values().stream()
                .filter(a -> status.equalsIgnoreCase(a.getStatusStr()))
                .collect(Collectors.toList());
    }

    public List<Asset> filterByAssignee(String assignee) {
        String q = assignee.toLowerCase();
        return assets.values().stream()
                .filter(a -> a.getAssignedTo() != null && a.getAssignedTo().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public List<Asset> filterByLocation(String location) {
        String q = location.toLowerCase();
        return assets.values().stream()
                .filter(a -> a.getLocation() != null && a.getLocation().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    /** Finds an asset by exact tag (case-insensitive). Returns empty if not found. */
    public Optional<Asset> findByTag(String tag) {
        if (tag == null || tag.isBlank()) return Optional.empty();
        return assets.values().stream()
                .filter(a -> tag.equalsIgnoreCase(a.getAssetTag()))
                .findFirst();
    }

    /** Returns all assets that have any tag assigned. */
    public List<Asset> getTaggedAssets() {
        return assets.values().stream()
                .filter(a -> a.getAssetTag() != null && !a.getAssetTag().isBlank())
                .collect(Collectors.toList());
    }

    // ── Warranty reports ──────────────────────────────────────────────────────

    public List<Asset> getExpiredWarranties() {
        return assets.values().stream()
                .filter(Asset::isWarrantyExpired)
                .collect(Collectors.toList());
    }

    public List<Asset> getWarrantiesExpiringSoon(int withinDays) {
        return assets.values().stream()
                .filter(a -> a.isWarrantyExpiringSoon(withinDays))
                .collect(Collectors.toList());
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public int getTotalCount() { return assets.size(); }

    public Map<String, Long> countByCategoryStr() {
        return assets.values().stream()
                .filter(a -> a.getCategoryStr() != null && !a.getCategoryStr().isBlank())
                .collect(Collectors.groupingBy(Asset::getCategoryStr, Collectors.counting()));
    }

    public Map<String, Long> countByStatusStr() {
        return assets.values().stream()
                .filter(a -> a.getStatusStr() != null && !a.getStatusStr().isBlank())
                .collect(Collectors.groupingBy(Asset::getStatusStr, Collectors.counting()));
    }

    // Legacy enum-based methods kept for backwards compat
    public Map<Category, Long> countByCategory() {
        return assets.values().stream()
                .filter(a -> a.getCategory() != null)
                .collect(Collectors.groupingBy(Asset::getCategory, Collectors.counting()));
    }

    public Map<Status, Long> countByStatus() {
        return assets.values().stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(Asset::getStatus, Collectors.counting()));
    }

    public double getTotalValue() {
        return assets.values().stream()
                .mapToDouble(Asset::getPurchasePrice)
                .sum();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    public void exportFiltered(List<Asset> subset, String outputPath) throws IOException {
        csvManager.exportToCsv(subset, outputPath);
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /**
     * Wipes all assets from memory and deletes the inventory CSV.
     * A backup is automatically created before deletion.
     */
    public void resetDatabase() throws IOException {
        assets.clear();
        csvManager.resetDatabase();
        idGen = new IdGenerator(Collections.emptyList());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Throws IllegalArgumentException if the tag is already used by a different asset.
     */
    private void validateUniqueTag(String tag, String excludeId) {
        if (tag == null || tag.isBlank()) return;
        for (Asset a : assets.values()) {
            if (a.getAssetTag() != null
                    && a.getAssetTag().equalsIgnoreCase(tag)
                    && !a.getAssetId().equals(excludeId)) {
                throw new IllegalArgumentException(
                        "Tag '" + tag + "' is already assigned to " + a.getAssetId());
            }
        }
    }

    private void persist() throws IOException {
        csvManager.saveAll(assets.values());
    }

    private void requireExists(String assetId) {
        if (!assets.containsKey(assetId)) {
            throw new NoSuchElementException("Asset not found: " + assetId);
        }
    }
}
