package com.itinventory.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents an IT asset in the inventory system.
 */
public class Asset {

    public enum Status {
        ACTIVE, INACTIVE, IN_REPAIR, RETIRED, MISSING
    }

    public enum Category {
        LAPTOP, DESKTOP, SERVER, MONITOR, PRINTER, NETWORK_DEVICE, PHONE, TABLET, PERIPHERAL, SOFTWARE_LICENSE, OTHER
    }

    private String assetId;
    private String name;
    private Category category;
    private String manufacturer;
    private String model;
    private String serialNumber;
    private Status status;
    private String assignedTo;       // Employee name or ID
    private String location;         // Office / Room
    private LocalDate purchaseDate;
    private double purchasePrice;
    private LocalDate warrantyExpiry;
    private String notes;
    private String assetTag; // e.g. IT-DEPT-042 OR 1234, nullable

    // ── Constructors ─────────────────────────────────────────────────────────

    public Asset() {}

    public Asset(String assetId, String name, Category category, String manufacturer,
                 String model, String serialNumber, Status status, String assignedTo,
                 String location, LocalDate purchaseDate, double purchasePrice,
                 LocalDate warrantyExpiry, String notes) {
        this.assetId       = assetId;
        this.name          = name;
        this.category      = category;
        this.manufacturer  = manufacturer;
        this.model         = model;
        this.serialNumber  = serialNumber;
        this.status        = status;
        this.assignedTo    = assignedTo;
        this.location      = location;
        this.purchaseDate  = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.warrantyExpiry = warrantyExpiry;
        this.notes         = notes;
    }

    // ── CSV Serialisation ─────────────────────────────────────────────────────

    /**
     * Returns the CSV header line.
     */
    public static String csvHeader() {
        return "AssetID,Name,Category,Manufacturer,Model,SerialNumber,Status," +
               "AssignedTo,Location,PurchaseDate,PurchasePrice,WarrantyExpiry,Notes,AssetTag";
    }

    /**
     * Serialises this asset to a single CSV row.
     * Fields that may contain commas are wrapped in quotes.
     */
    public String toCsvRow() {
        return String.join(",",
                escape(assetId),
                escape(name),
                category  != null ? category.name()  : "",
                escape(manufacturer),
                escape(model),
                escape(serialNumber),
                status    != null ? status.name()    : "",
                escape(assignedTo),
                escape(location),
                purchaseDate   != null ? purchaseDate.toString()   : "",
                String.valueOf(purchasePrice),
                warrantyExpiry != null ? warrantyExpiry.toString() : "",
                escape(notes),
                escape(assetTag)

        );
    }

    /**
     * Parses a CSV row into an Asset.
     * Expects the same column order as {@link #csvHeader()}.
     */
    public static Asset fromCsvRow(String csvLine) {
        String[] fields = parseCsvLine(csvLine);
        if (fields.length < 13) {
            throw new IllegalArgumentException("Invalid CSV row (expected 13 fields): " + csvLine);
        }

        Asset a = new Asset();
        a.assetId       = fields[0].trim();
        a.name          = fields[1].trim();
        a.category      = fields[2].isBlank() ? null : Category.valueOf(fields[2].trim());
        a.manufacturer  = fields[3].trim();
        a.model         = fields[4].trim();
        a.serialNumber  = fields[5].trim();
        a.status        = fields[6].isBlank() ? null : Status.valueOf(fields[6].trim());
        a.assignedTo    = fields[7].trim();
        a.location      = fields[8].trim();
        a.purchaseDate  = fields[9].isBlank()  ? null : LocalDate.parse(fields[9].trim());
        a.purchasePrice = fields[10].isBlank() ? 0.0  : Double.parseDouble(fields[10].trim());
        a.warrantyExpiry = fields[11].isBlank() ? null : LocalDate.parse(fields[11].trim());
        a.notes         = fields[12].trim();
        a.assetTag = fields.length > 13 ? fields[13].trim() : "";
        return a;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Wraps a field in quotes if it contains a comma, quote, or newline. */
    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Splits a CSV line, respecting quoted fields.
     */
    static String[] parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    // ── Warranty helper ───────────────────────────────────────────────────────

    public boolean isWarrantyExpired() {
        return warrantyExpiry != null && warrantyExpiry.isBefore(LocalDate.now());
    }

    public boolean isWarrantyExpiringSoon(int withinDays) {
        if (warrantyExpiry == null) return false;
        LocalDate threshold = LocalDate.now().plusDays(withinDays);
        return !warrantyExpiry.isBefore(LocalDate.now()) && !warrantyExpiry.isAfter(threshold);
    }

    // ── equals / hashCode / toString ─────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Asset)) return false;
        return Objects.equals(assetId, ((Asset) o).assetId);
    }

    @Override
    public int hashCode() { return Objects.hash(assetId); }

    @Override
    public String toString() {
        return String.format("Asset{id='%s', name='%s', category=%s, status=%s, assignedTo='%s'}",
                assetId, name, category, status, assignedTo);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getAssetId()           { return assetId; }
    public void   setAssetId(String v)   { this.assetId = v; }

    public String getName()              { return name; }
    public void   setName(String v)      { this.name = v; }

    public Category getCategory()             { return category; }
    public void     setCategory(Category v)   { this.category = v; }

    public String getManufacturer()           { return manufacturer; }
    public void   setManufacturer(String v)   { this.manufacturer = v; }

    public String getModel()             { return model; }
    public void   setModel(String v)     { this.model = v; }

    public String getSerialNumber()           { return serialNumber; }
    public void   setSerialNumber(String v)   { this.serialNumber = v; }

    public Status getStatus()            { return status; }
    public void   setStatus(Status v)    { this.status = v; }

    public String getAssignedTo()             { return assignedTo; }
    public void   setAssignedTo(String v)     { this.assignedTo = v; }

    public String getLocation()          { return location; }
    public void   setLocation(String v)  { this.location = v; }

    public LocalDate getPurchaseDate()             { return purchaseDate; }
    public void      setPurchaseDate(LocalDate v)  { this.purchaseDate = v; }

    public double getPurchasePrice()            { return purchasePrice; }
    public void   setPurchasePrice(double v)    { this.purchasePrice = v; }

    public LocalDate getWarrantyExpiry()              { return warrantyExpiry; }
    public void      setWarrantyExpiry(LocalDate v)   { this.warrantyExpiry = v; }

    public String getNotes()             { return notes; }
    public void   setNotes(String v)     { this.notes = v; }

    public String getAssetTag() { return assetTag; }
    public void   setAssetTag(String v) { this.assetTag = v; }
}
