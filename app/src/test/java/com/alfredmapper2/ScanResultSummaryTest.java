package com.alfredmapper2;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class ScanResultSummaryTest {
    @Test
    public void formatsWifiResultsAlphabetically() {
        String summary = ScanResultSummary.build(Arrays.asList(
                new ScanResultSummary.Entry("Zulu", "AA:BB", "WPA2", -71),
                new ScanResultSummary.Entry("alpha", "11:22", "OPEN", -52)),
                true);

        assertEquals(
                "ALFRED MAPPER 2 - WI-FI RESULTS\n"
                        + "\nalpha\n11:22\n-52 dBm\nOPEN"
                        + "\nZulu\nAA:BB\n-71 dBm\nWPA2",
                summary);
    }

    @Test
    public void formatsEmptyBluetoothResultsWithHeader() {
        assertEquals(
                "ALFRED MAPPER 2 - BLUETOOTH RESULTS\n",
                ScanResultSummary.build(Collections.emptyList(), false));
    }

    @Test
    public void formatsMixedRadarResults() {
        String summary = ScanResultSummary.buildRadar(Arrays.asList(
                new ScanResultSummary.Entry("Watch", "AA:BB", "Bluetooth", -61),
                new ScanResultSummary.Entry("Guest", "11:22", "Wi-Fi", -48)));

        assertEquals(
                "ALFRED MAPPER 2 - RADAR RESULTS"
                        + "\n\nWi-Fi\nGuest\n11:22\n-48 dBm"
                        + "\n\nBluetooth\nWatch\nAA:BB\n-61 dBm",
                summary);
    }
}
