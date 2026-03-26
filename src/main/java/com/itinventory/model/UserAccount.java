package com.itinventory.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Represents a Lake Erie Inventory user account.
 *
 * Stored in data/users.csv (in the shared data directory).
 * Access codes are salted and SHA-256 hashed - never stored in plain text.
 *
 * Roles:
 *   ADMIN      - full access including Settings and User Management
 *   READ_WRITE - can add, edit, delete assets; cannot access Settings
 *   VIEW_ONLY  - read-only access; all editing controls are disabled
 */
public class UserAccount {

    public enum Role {
        ADMIN, READ_WRITE, VIEW_ONLY;

        public String displayName() {
            return switch (this) {
                case ADMIN      -> "Admin";
                case READ_WRITE -> "Read & Write";
                case VIEW_ONLY  -> "View Only";
            };
        }
    }

    private String username;
    private String salt;           // base64-encoded random salt
    private String hashedCode;     // base64-encoded SHA-256(salt + accessCode)
    private Role   role;
    private boolean active;        // false = account disabled

    // ── Constructors ──────────────────────────────────────────────────────────

    public UserAccount() {}

    /**
     * Creates a new user account with a plain-text access code.
     * The code is salted and hashed immediately - the plain text is not stored.
     */
    public UserAccount(String username, String plainAccessCode, Role role) {
        this.username = username.trim().toLowerCase();
        this.role     = role;
        this.active   = true;
        setAccessCode(plainAccessCode);
    }

    // ── Access code handling ──────────────────────────────────────────────────

    /**
     * Hashes and stores a new access code for this user.
     */
    public void setAccessCode(String plainCode) {
        this.salt       = generateSalt();
        this.hashedCode = hash(this.salt, plainCode);
    }

    /**
     * Returns true if the provided plain-text code matches the stored hash.
     */
    public boolean verifyAccessCode(String plainCode) {
        if (salt == null || hashedCode == null) return false;
        return hashedCode.equals(hash(salt, plainCode));
    }

    // ── CSV serialisation ─────────────────────────────────────────────────────

    public static String csvHeader() {
        return "Username,Salt,HashedCode,Role,Active";
    }

    public String toCsvRow() {
        return String.join(",",
                escape(username),
                escape(salt),
                escape(hashedCode),
                role != null ? role.name() : "",
                active ? "true" : "false"
        );
    }

    public static UserAccount fromCsvRow(String csvLine) {
        String[] fields = csvLine.split(",", -1);
        if (fields.length < 5) {
            throw new IllegalArgumentException("Invalid user CSV row: " + csvLine);
        }
        UserAccount u = new UserAccount();
        u.username   = fields[0].trim().toLowerCase();
        u.salt       = fields[1].trim();
        u.hashedCode = fields[2].trim();
        u.role       = fields[3].isBlank() ? Role.VIEW_ONLY : Role.valueOf(fields[3].trim());
        u.active     = "true".equalsIgnoreCase(fields[4].trim());
        return u;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private static String hash(String salt, String plainCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = salt + plainCode;
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── Role helpers ──────────────────────────────────────────────────────────

    public boolean isAdmin()     { return role == Role.ADMIN; }
    public boolean canEdit()     { return role == Role.ADMIN || role == Role.READ_WRITE; }
    public boolean isViewOnly()  { return role == Role.VIEW_ONLY; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String  getUsername()              { return username; }
    public void    setUsername(String v)      { this.username = v.trim().toLowerCase(); }
    public String  getSalt()                  { return salt; }
    public void    setSalt(String v)          { this.salt = v; }
    public String  getHashedCode()            { return hashedCode; }
    public void    setHashedCode(String v)    { this.hashedCode = v; }
    public Role    getRole()                  { return role; }
    public void    setRole(Role v)            { this.role = v; }
    public boolean isActive()                 { return active; }
    public void    setActive(boolean v)       { this.active = v; }

    @Override
    public String toString() {
        return "UserAccount{username='" + username + "', role=" + role + ", active=" + active + "}";
    }
}
