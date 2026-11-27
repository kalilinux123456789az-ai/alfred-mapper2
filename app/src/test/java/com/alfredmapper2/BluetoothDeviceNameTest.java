package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class BluetoothDeviceNameTest {
    private static final String UNNAMED = "<unnamed device>";

    @Test
    public void replacesPlaceholderWhenANameIsObservedLater() {
        assertEquals(
                "Headphones",
                BluetoothDeviceName.preferKnownName(UNNAMED, "Headphones", UNNAMED));
    }

    @Test
    public void keepsKnownNameWhenLaterObservationHasNoName() {
        assertEquals(
                "Headphones",
                BluetoothDeviceName.preferKnownName("Headphones", UNNAMED, UNNAMED));
    }

    @Test
    public void doesNotChangePlaceholderUntilANameIsAvailable() {
        assertEquals(
                UNNAMED,
                BluetoothDeviceName.preferKnownName(UNNAMED, UNNAMED, UNNAMED));
    }
}
