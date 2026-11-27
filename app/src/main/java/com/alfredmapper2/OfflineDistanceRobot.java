package com.alfredmapper2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OfflineDistanceRobot {
    private static final int MAX_HISTORY_PER_DEVICE = 9;
    private static final int MAX_TRACKED_DEVICES = 200;

    private final Map<String, Map<String, ArrayDeque<Integer>>> histories =
            new HashMap<>();

    OfflineDistanceRobot() {
        histories.put("wifi", new LinkedHashMap<>());
        histories.put("bluetooth", new LinkedHashMap<>());
    }

    static final class Estimate {
        final double meters;
        final int confidencePercent;
        final int sampleCount;

        Estimate(double meters, int confidencePercent, int sampleCount) {
            this.meters = meters;
            this.confidencePercent = confidencePercent;
            this.sampleCount = sampleCount;
        }
    }

    Estimate estimateMeters(
            String key,
            boolean wifi,
            int rssi,
            int referenceRssi,
            double referenceDistanceMeters,
            double pathLossExponent) {
        Map<String, ArrayDeque<Integer>> history =
                histories.get(wifi ? "wifi" : "bluetooth");
        if (history == null) throw new IllegalArgumentException("Invalid radio type.");

        ArrayDeque<Integer> samples = history.get(key);
        if (samples == null) {
            samples = new ArrayDeque<>(MAX_HISTORY_PER_DEVICE);
            history.put(key, samples);
        }
        samples.addLast(rssi);
        while (samples.size() > MAX_HISTORY_PER_DEVICE) {
            samples.removeFirst();
        }

        if (history.size() > MAX_TRACKED_DEVICES) {
            String oldest = history.keySet().iterator().next();
            history.remove(oldest);
        }

        int smoothedRssi = median(samples);
        double estimate = DistanceEstimator.estimateMeters(
                smoothedRssi,
                referenceRssi,
                referenceDistanceMeters,
                pathLossExponent);
        if (Double.isInfinite(estimate) || Double.isNaN(estimate)) {
            throw new IllegalArgumentException("Estimate could not be calculated.");
        }
        int medianAbsoluteDeviation = mad(samples, smoothedRssi);
        int confidence = confidencePercent(samples.size(), medianAbsoluteDeviation);
        return new Estimate(
                Math.min(1000.0, estimate),
                confidence,
                samples.size());
    }

    private int median(ArrayDeque<Integer> source) {
        List<Integer> samples = new ArrayList<>(source);
        Collections.sort(samples);
        return samples.get(samples.size() / 2);
    }

    private int mad(ArrayDeque<Integer> source, int medianRssi) {
        List<Integer> deviations = new ArrayList<>(source.size());
        for (Integer sample : source) {
            deviations.add(Math.abs(sample - medianRssi));
        }
        List<Integer> copy = new ArrayList<>(deviations);
        Collections.sort(copy);
        return copy.get(copy.size() / 2);
    }

    private int confidencePercent(int sampleCount, int medianAbsoluteDeviation) {
        int confidence;
        if (sampleCount >= 5) confidence = 78;
        else if (sampleCount >= 3) confidence = 68;
        else if (sampleCount >= 2) confidence = 55;
        else confidence = 35;

        if (medianAbsoluteDeviation <= 3) confidence += 8;
        else if (medianAbsoluteDeviation > 12) confidence -= 25;
        else if (medianAbsoluteDeviation > 8) confidence -= 15;

        return Math.max(25, Math.min(95, confidence));
    }

    static String rangeLabel(double meters) {
        if (Double.isNaN(meters) || Double.isInfinite(meters)) return "unknown";
        if (meters < 1.0) return "very close";
        if (meters < 3.0) return "near";
        if (meters < 10.0) return "medium";
        return "far";
    }

    void clear() {
        for (Map<String, ArrayDeque<Integer>> history : histories.values()) {
            history.clear();
        }
    }

    void clear(boolean wifi) {
        Map<String, ArrayDeque<Integer>> history =
                histories.get(wifi ? "wifi" : "bluetooth");
        if (history != null) history.clear();
    }
}
