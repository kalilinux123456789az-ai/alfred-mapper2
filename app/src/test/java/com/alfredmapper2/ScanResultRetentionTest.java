package com.alfredmapper2;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ScanResultRetentionTest {
    @Test
    public void removesExpiredResultsAndKeepsRecentlySeenResults() {
        Map<String, Long> results = new LinkedHashMap<>();
        results.put("expired", 1_000L);
        results.put("recent", 3_000L);

        ScanResultRetention.prune(results, 62_000L, 10, 60_000L, timestamp -> timestamp);

        assertFalse(results.containsKey("expired"));
        assertTrue(results.containsKey("recent"));
    }

    @Test
    public void removesLeastRecentlySeenResultsUntilWithinLimit() {
        Map<String, Long> results = new LinkedHashMap<>();
        results.put("oldest", 10L);
        results.put("middle", 20L);
        results.put("newest", 30L);

        ScanResultRetention.prune(results, 30L, 2, 100L, timestamp -> timestamp);

        assertEquals(2, results.size());
        assertFalse(results.containsKey("oldest"));
        assertTrue(results.containsKey("middle"));
        assertTrue(results.containsKey("newest"));
    }
}
