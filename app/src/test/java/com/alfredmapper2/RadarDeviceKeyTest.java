package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RadarDeviceKeyTest {
    @Test
    public void wifiAndBluetoothIdentifiersCannotCollide() {
        String identifier = "00:11:22:33:44:55";

        assertEquals("wifi:" + identifier, RadarDeviceKey.wifi(identifier));
        assertEquals("bt:" + identifier, RadarDeviceKey.bluetooth(identifier));
        assertFalse(RadarDeviceKey.wifi(identifier)
                .equals(RadarDeviceKey.bluetooth(identifier)));
    }

    @Test
    public void identifiesOnlyWifiRadarEntriesAsWifi() {
        assertTrue(RadarDeviceKey.isWifi(RadarDeviceKey.wifi("network")));
        assertFalse(RadarDeviceKey.isWifi(RadarDeviceKey.bluetooth("device")));
    }

    @Test
    public void preservesIdentifierWhenAddingRadioTypePrefix() {
        String wifiIdentifier = "Cafe Net";
        String bluetoothIdentifier = "Headphones";

        assertEquals("wifi:" + wifiIdentifier, RadarDeviceKey.wifi(wifiIdentifier));
        assertEquals("bt:" + bluetoothIdentifier,
                RadarDeviceKey.bluetooth(bluetoothIdentifier));
    }
}
