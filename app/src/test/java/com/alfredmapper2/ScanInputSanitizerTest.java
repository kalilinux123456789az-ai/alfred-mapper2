package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ScanInputSanitizerTest {
    @Test
    public void acceptsValidRssiAndRejectsOutliers() {
        assertTrue(ScanInputSanitizer.isValidRssi(-127));
        assertTrue(ScanInputSanitizer.isValidRssi(-60));
        assertTrue(ScanInputSanitizer.isValidRssi(20));
        assertFalse(ScanInputSanitizer.isValidRssi(-128));
        assertFalse(ScanInputSanitizer.isValidRssi(21));
    }

    @Test
    public void validatesBluetoothAddressShape() {
        assertTrue(ScanInputSanitizer.isValidBluetoothAddress("AA:BB:CC:DD:EE:FF"));
        assertTrue(ScanInputSanitizer.isValidBluetoothAddress("00:11:22:33:44:55"));
        assertFalse(ScanInputSanitizer.isValidBluetoothAddress("not-a-device"));
        assertFalse(ScanInputSanitizer.isValidBluetoothAddress("AA-BB-CC-DD-EE-FF"));
        assertFalse(ScanInputSanitizer.isValidBluetoothAddress(null));
    }

    @Test
    public void stripsControlCharactersAndBoundsNames() {
        assertEquals(
                "Safe name",
                ScanInputSanitizer.sanitizeName("  Safe name  ", "fallback"));
        assertEquals(
                "fallback",
                ScanInputSanitizer.sanitizeName("\n\r\t", "fallback"));

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 1000; i++) longName.append('x');
        assertEquals(
                96,
                ScanInputSanitizer.sanitizeName(longName.toString(), "fallback").length());
    }
}
