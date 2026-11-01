package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DistanceEstimatorTest {
    @Test
    public void returnsCalibrationDistanceAtReferenceSignal() {
        assertEquals(
                2.5,
                DistanceEstimator.estimateMeters(-65, -65, 2.5, 2.0),
                0.0001);
    }

    @Test
    public void weakerSignalIncreasesEstimatedDistance() {
        double estimate = DistanceEstimator.estimateMeters(-66, -60, 1.0, 2.0);

        assertEquals(Math.pow(10.0, 6.0 / 20.0), estimate, 0.0001);
    }
}
