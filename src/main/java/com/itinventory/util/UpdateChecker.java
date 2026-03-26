package com.itinventory.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for updates by querying the GitHub Releases API for the
 * Lake Erie Inventory repository.
 *
 * How it works:
 *   1. Calls https://api.github.com/repos/erietechsolutions/lake-erie-inventory/releases/latest
 *   2. Parses the "tag_name" field from the JSON response (e.g. "v1.2.0")
 *   3. Compares it against the current app version (CURRENT_VERSION constant below)
 *   4. Returns an UpdateResult indicating whether an update is available
 *
 * To publish a new release:
 *   - Tag your commit as v1.x.x on GitHub and create a Release with that tag.
 *   - The app will automatically detect it on next check.
 */
public class UpdateChecker {

    private static final Logger LOG = Logger.getLogger(UpdateChecker.class.getName());

    // ── Version ───────────────────────────────────────────────────────────────

    /** The version of this build. Update this string with every release. */
    public static final String CURRENT_VERSION = "v1.0.1";

    // ── GitHub config ─────────────────────────────────────────────────────────

    private static final String GITHUB_OWNER   = "erietechsolutions";
    private static final String GITHUB_REPO    = "lake-erie-inventory";
    private static final String RELEASES_URL   =
        "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String RELEASES_PAGE  =
        "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final int TIMEOUT_SECONDS = 8;

    // ── Result type ───────────────────────────────────────────────────────────

    public enum Status {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        ERROR,
        NO_RELEASES
    }

    public record UpdateResult(
        Status  status,
        String  currentVersion,
        String  latestVersion,
        String  releaseUrl,
        String  releaseNotes,
        String  errorMessage
    ) {
        public boolean isUpdateAvailable() { return status == Status.UPDATE_AVAILABLE; }
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    /**
     * Performs the update check synchronously.
     * Always call from a background thread — never on the JavaFX Application Thread.
     */
    public static UpdateResult check() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RELEASES_URL))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "LakeErieInventory/" + CURRENT_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                // Repository exists but no releases have been published yet
                return new UpdateResult(Status.NO_RELEASES, CURRENT_VERSION,
                        CURRENT_VERSION, RELEASES_PAGE, "", "");
            }

            if (response.statusCode() != 200) {
                return error("GitHub API returned status " + response.statusCode());
            }

            String body = response.body();

            // Parse tag_name from JSON (e.g. "tag_name": "v1.2.0")
            String latestTag = extractField(body, "tag_name");
            if (latestTag == null || latestTag.isBlank()) {
                return error("Could not parse release tag from GitHub response.");
            }

            // Parse release notes (body field)
            String notes = extractField(body, "body");
            if (notes == null) notes = "";
            // Trim to a reasonable length for display
            if (notes.length() > 600) notes = notes.substring(0, 597) + "...";

            // Parse html_url for direct link to the release page
            String releaseUrl = extractField(body, "html_url");
            if (releaseUrl == null || releaseUrl.isBlank()) releaseUrl = RELEASES_PAGE;

            // Compare versions
            if (isNewer(latestTag, CURRENT_VERSION)) {
                return new UpdateResult(Status.UPDATE_AVAILABLE, CURRENT_VERSION,
                        latestTag, releaseUrl, notes, "");
            } else {
                return new UpdateResult(Status.UP_TO_DATE, CURRENT_VERSION,
                        latestTag, releaseUrl, notes, "");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Update check was interrupted.");
        } catch (IOException e) {
            LOG.warning("Update check failed: " + e.getMessage());
            return error("Could not reach GitHub. Check your internet connection.");
        } catch (Exception e) {
            LOG.warning("Update check unexpected error: " + e.getMessage());
            return error("Unexpected error during update check: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts a string value from a simple JSON response by field name.
     * Handles both quoted strings and escaped characters.
     * This avoids needing a JSON library dependency.
     */
    private static String extractField(String json, String fieldName) {
        // Match: "fieldName": "value"  or  "fieldName":"value"
        Pattern p = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            // Unescape common JSON escape sequences
            return m.group(1)
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return null;
    }

    /**
     * Returns true if latestTag represents a higher version than currentTag.
     * Compares semantic version numbers (v1.2.3 format).
     * Falls back to string comparison if parsing fails.
     */
    static boolean isNewer(String latestTag, String currentTag) {
        try {
            int[] latest  = parseVersion(latestTag);
            int[] current = parseVersion(currentTag);
            for (int i = 0; i < Math.min(latest.length, current.length); i++) {
                if (latest[i] > current[i]) return true;
                if (latest[i] < current[i]) return false;
            }
            return latest.length > current.length;
        } catch (Exception e) {
            // Fall back to simple string inequality
            return !latestTag.equalsIgnoreCase(currentTag);
        }
    }

    private static int[] parseVersion(String tag) {
        // Strip leading 'v' or 'V'
        String clean = tag.replaceAll("^[vV]", "").split("-")[0];
        String[] parts = clean.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = Integer.parseInt(parts[i].trim());
        }
        return nums;
    }

    private static UpdateResult error(String message) {
        return new UpdateResult(Status.ERROR, CURRENT_VERSION, "", RELEASES_PAGE, "", message);
    }

    public static String getReleasesPage() { return RELEASES_PAGE; }
}
