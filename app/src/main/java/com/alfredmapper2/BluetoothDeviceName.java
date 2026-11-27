package com.alfredmapper2;

final class BluetoothDeviceName {
    private BluetoothDeviceName() {}

    static String preferKnownName(String currentName, String observedName, String unnamedName) {
        if (currentName.equals(unnamedName) && !observedName.equals(unnamedName)) {
            return observedName;
        }
        return currentName;
    }
}
