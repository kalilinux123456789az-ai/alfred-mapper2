package com.alfredmapper2;

final class ScanInputSanitizer {
    private static final int MAX_NAME_LENGTH = 96;

    private ScanInputSanitizer() {}

    static boolean isValidRssi(int rssi) {
        return rssi >= -127 && rssi <= 20;
    }

    static boolean isValidBluetoothAddress(String address) {
        if (address == null || address.length() != 17) return false;
        for (int i = 0; i < address.length(); i++) {
            char character = address.charAt(i);
            if (i % 3 == 2) {
                if (character != ':') return false;
            } else if (!((character >= '0' && character <= '9')
                    || (character >= 'A' && character <= 'F')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    static String sanitizeName(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return fallback;
        StringBuilder safe = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length() && safe.length() < MAX_NAME_LENGTH; i++) {
            char character = trimmed.charAt(i);
            if (character >= 32 && character != 127) safe.append(character);
        }
        String result = safe.toString().trim();
        return result.isEmpty() ? fallback : result;
    }
}
