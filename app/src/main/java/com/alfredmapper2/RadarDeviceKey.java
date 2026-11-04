package com.alfredmapper2;

final class RadarDeviceKey {
    private static final String WIFI_PREFIX = "wifi:";
    private static final String BLUETOOTH_PREFIX = "bt:";

    private RadarDeviceKey() {}

    static String wifi(String identifier) {
        return WIFI_PREFIX + identifier;
    }

    static String bluetooth(String identifier) {
        return BLUETOOTH_PREFIX + identifier;
    }

    static boolean isWifi(String key) {
        return key.startsWith(WIFI_PREFIX);
    }
}
