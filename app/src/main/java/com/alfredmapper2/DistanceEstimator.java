package com.alfredmapper2;

final class DistanceEstimator {
    static final double DEFAULT_REFERENCE_DISTANCE_METERS = 1.0;
    static final int DEFAULT_REFERENCE_RSSI_DBM = -59;
    static final double DEFAULT_PATH_LOSS_EXPONENT = 2.2;

    private DistanceEstimator() {}

    static double estimateMeters(
            int rssi,
            int referenceRssi,
            double referenceDistanceMeters,
            double pathLossExponent) {
        if (Double.isNaN(referenceDistanceMeters)
                || Double.isInfinite(referenceDistanceMeters)
                || referenceDistanceMeters <= 0
                || Double.isNaN(pathLossExponent)
                || Double.isInfinite(pathLossExponent)
                || pathLossExponent <= 0) {
            throw new IllegalArgumentException("Calibration values must be positive.");
        }
        return referenceDistanceMeters
                * Math.pow(10.0, (referenceRssi - rssi) / (10.0 * pathLossExponent));
    }
}
