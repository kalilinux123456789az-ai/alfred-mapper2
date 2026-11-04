package com.alfredmapper2;

final class RadarProjection {
    private RadarProjection() {}

    static float radiusFractionForRssi(int rssi) {
        float strength = Math.max(0, Math.min(1, (rssi + 100f) / 70f));
        return 0.9f - 0.68f * strength;
    }

    static int angleDegreesForKey(String key) {
        return (key.hashCode() & 0x7fffffff) % 360;
    }
}
