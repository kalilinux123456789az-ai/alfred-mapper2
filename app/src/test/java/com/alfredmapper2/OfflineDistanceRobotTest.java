package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class OfflineDistanceRobotTest {
    @Test
    public void filtersShortRssiSpike() {
        OfflineDistanceRobot estimator = new OfflineDistanceRobot();
        OfflineDistanceRobot.Estimate normal = estimator.estimateMeters(
                "device-1", false, -60, -59, 1.0, 2.2);
        estimator.estimateMeters(
                "device-1", false, -20, -59, 1.0, 2.2);
        OfflineDistanceRobot.Estimate stable = estimator.estimateMeters(
                "device-1", false, -60, -59, 1.0, 2.2);

        assertTrue(normal.meters > 0.0);
        assertTrue(stable.meters < normal.meters * 2.0);
        assertTrue(normal.confidencePercent >= 25);
        assertTrue(normal.confidencePercent <= 95);
    }

    @Test
    public void keepsEstimateWithinSafeMaximum() {
        OfflineDistanceRobot estimator = new OfflineDistanceRobot();
        OfflineDistanceRobot.Estimate estimate = estimator.estimateMeters(
                "wifi-1", true, -120, -59, 1.0, 2.2);
        assertTrue(estimate.meters <= 1000.0);
    }

    @Test
    public void providesReadableRangeBands() {
        assertTrue("very close".equals(OfflineDistanceRobot.rangeLabel(0.8)));
        assertTrue("near".equals(OfflineDistanceRobot.rangeLabel(2.0)));
        assertTrue("medium".equals(OfflineDistanceRobot.rangeLabel(6.0)));
        assertTrue("far".equals(OfflineDistanceRobot.rangeLabel(20.0)));
    }

    @Test
    public void canClearOneRadioHistoryWithoutAffectingTheOther() {
        OfflineDistanceRobot estimator = new OfflineDistanceRobot();
        estimator.estimateMeters("wifi-1", true, -60, -59, 1.0, 2.2);
        estimator.estimateMeters("bt-1", false, -60, -59, 1.0, 2.2);

        estimator.clear(true);
        OfflineDistanceRobot.Estimate wifi =
                estimator.estimateMeters("wifi-1", true, -60, -59, 1.0, 2.2);
        OfflineDistanceRobot.Estimate bluetooth =
                estimator.estimateMeters("bt-1", false, -60, -59, 1.0, 2.2);

        assertTrue(wifi.sampleCount == 1);
        assertTrue(bluetooth.sampleCount == 2);
    }
}
