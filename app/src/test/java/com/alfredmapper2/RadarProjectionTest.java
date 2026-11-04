package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RadarProjectionTest {
    @Test
    public void strongerSignalsAppearCloserToRadarCenter() {
        float weakSignalRadius = RadarProjection.radiusFractionForRssi(-100);
        float strongSignalRadius = RadarProjection.radiusFractionForRssi(-30);

        assertTrue(weakSignalRadius > strongSignalRadius);
        assertEquals(0.9f, weakSignalRadius, 0.0001f);
        assertEquals(0.22f, strongSignalRadius, 0.0001f);
    }

    @Test
    public void signalPositionsStayInsideRadarRange() {
        for (int rssi = -150; rssi <= 20; rssi++) {
            float radius = RadarProjection.radiusFractionForRssi(rssi);

            assertTrue("RSSI " + rssi + " was placed outside the radar", radius >= 0.2199f);
            assertTrue("RSSI " + rssi + " was placed outside the radar", radius <= 0.9001f);
        }
    }

    @Test
    public void strongerSignalMovesInwardAtEveryRssiStep() {
        float previousRadius = RadarProjection.radiusFractionForRssi(-100);

        for (int rssi = -99; rssi <= -30; rssi++) {
            float radius = RadarProjection.radiusFractionForRssi(rssi);

            assertTrue("RSSI " + rssi + " should appear closer to the center",
                    radius < previousRadius);
            previousRadius = radius;
        }
    }

    @Test
    public void signalPositionClampsRssiOutsideExpectedRange() {
        assertEquals(RadarProjection.radiusFractionForRssi(-100),
                RadarProjection.radiusFractionForRssi(-150), 0.0001f);
        assertEquals(RadarProjection.radiusFractionForRssi(-30),
                RadarProjection.radiusFractionForRssi(20), 0.0001f);
    }

    @Test
    public void deviceAngleIsStableAndMatchesExpectedPosition() {
        String key = "wifi:00:11:22:33:44:55";

        assertEquals(253, RadarProjection.angleDegreesForKey(key));
        assertEquals(253, RadarProjection.angleDegreesForKey(key));
    }

    @Test
    public void deviceAngleAlwaysFallsWithinOneCircle() {
        for (int i = 0; i < 10_000; i++) {
            int angle = RadarProjection.angleDegreesForKey("device:" + i);

            assertTrue("Angle must be in [0, 360)", angle >= 0 && angle < 360);
        }
    }
}
