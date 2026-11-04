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

    @Test
    public void expiresResultsAtAgeLimitButKeepsResultsOneMillisecondYounger() {
        Map<String, Long> results = new LinkedHashMap<>();
        results.put("at-limit", 40_000L);
        results.put("just-younger", 40_001L);

        ScanResultRetention.prune(results, 100_000L, 10, 60_000L, timestamp -> timestamp);

        assertFalse(results.containsKey("at-limit"));
        assertTrue(results.containsKey("just-younger"));
    }

    @Test
    public void zeroResultLimitRemovesEveryEntry() {
        Map<String, Long> results = new LinkedHashMap<>();
        results.put("first", 10L);
        results.put("second", 20L);

        ScanResultRetention.prune(results, 20L, 0, 100L, timestamp -> timestamp);

        assertTrue(results.isEmpty());
    }
}
