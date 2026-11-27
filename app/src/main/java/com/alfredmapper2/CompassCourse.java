package com.alfredmapper2;

import android.location.Location;

final class CompassCourse {
    private CompassCourse() {}

    static Float fromLocation(Location current, Location previous) {
        if (current == null) return null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && current.hasBearing()
                && (current.hasSpeed() && current.getSpeed() >= 0.5f)) {
            return normalize(current.getBearing());
        }
        if (previous == null) return null;
        float distance = current.distanceTo(previous);
        if (distance < 8f) return null;
        float bearing = previous.bearingTo(current);
        return normalize(bearing);
    }

    static float normalize(float bearing) {
        float normalized = bearing % 360f;
        return normalized < 0f ? normalized + 360f : normalized;
    }
}
