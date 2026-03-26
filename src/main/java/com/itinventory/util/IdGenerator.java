package com.itinventory.util;

import java.util.Collection;

/**
 * Generates sequential, prefixed asset IDs (e.g. ASSET-0042).
 */
public class IdGenerator {

    private static final String PREFIX = "ASSET-";
    private int counter;

    public IdGenerator(Collection<String> existingIds) {
        int max = 0;
        for (String id : existingIds) {
            if (id != null && id.startsWith(PREFIX)) {
                try {
                    int n = Integer.parseInt(id.substring(PREFIX.length()));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {}
            }
        }
        this.counter = max;
    }

    /** Returns the next unique asset ID and advances the counter. */
    public String next() {
        counter++;
        return String.format("%s%04d", PREFIX, counter);
    }
}
