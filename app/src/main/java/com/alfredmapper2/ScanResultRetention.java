package com.alfredmapper2;

import java.util.Iterator;
import java.util.Map;

final class ScanResultRetention {
    static final int MAX_BLUETOOTH_RESULTS = 200;
    static final long BLUETOOTH_RESULT_MAX_AGE_MS = 60_000;

    private ScanResultRetention() {}

    interface LastSeenAt<T> {
        long get(T value);
    }

    static <T> void prune(
            Map<String, T> results,
            long now,
            int maxSize,
            long maxAgeMs,
            LastSeenAt<T> lastSeenAt) {
        Iterator<Map.Entry<String, T>> iterator = results.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - lastSeenAt.get(iterator.next().getValue()) >= maxAgeMs) {
                iterator.remove();
            }
        }

        while (results.size() > maxSize) {
            String oldestKey = null;
            long oldestTimestamp = Long.MAX_VALUE;
            for (Map.Entry<String, T> entry : results.entrySet()) {
                long timestamp = lastSeenAt.get(entry.getValue());
                if (timestamp < oldestTimestamp) {
                    oldestTimestamp = timestamp;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) return;
            results.remove(oldestKey);
        }
    }
}
