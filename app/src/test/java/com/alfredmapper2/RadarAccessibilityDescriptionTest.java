package com.alfredmapper2;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class RadarAccessibilityDescriptionTest {
    @Test
    public void includesCountsAndStrongestSignals() {
        String description = RadarAccessibilityDescription.build(
                "Radar chart.",
                "2 Wi-Fi and 1 Bluetooth signals detected.",
                Arrays.asList("Wi-Fi network Guest at -42 dBm.", "Bluetooth device Watch at -55 dBm."),
                "No signals are currently listed.",
                "");

        assertEquals(
                "Radar chart. 2 Wi-Fi and 1 Bluetooth signals detected. "
                        + "Wi-Fi network Guest at -42 dBm. "
                        + "Bluetooth device Watch at -55 dBm.",
                description);
    }

    @Test
    public void explainsWhenNoSignalsAreAvailable() {
        String description = RadarAccessibilityDescription.build(
                "Radar chart.",
                "0 Wi-Fi and 0 Bluetooth signals detected.",
                Collections.emptyList(),
                "No signals are currently listed.",
                "");

        assertEquals(
                "Radar chart. 0 Wi-Fi and 0 Bluetooth signals detected. "
                        + "No signals are currently listed.",
                description);
    }

    @Test
    public void reportsSignalsOmittedFromSpokenSummary() {
        String description = RadarAccessibilityDescription.build(
                "Radar chart.",
                "4 Wi-Fi and 0 Bluetooth signals detected.",
                Collections.singletonList("Wi-Fi network Guest at -42 dBm."),
                "No signals are currently listed.",
                "3 additional signals omitted.");

        assertEquals(
                "Radar chart. 4 Wi-Fi and 0 Bluetooth signals detected. "
                        + "Wi-Fi network Guest at -42 dBm. 3 additional signals omitted.",
                description);
    }
}
