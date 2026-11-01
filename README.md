# Alfred Mapper 2

Alfred Mapper 2 is a standalone Android app by Tambet Sarapu for scanning
nearby Wi-Fi access points and discoverable Bluetooth LE and classic Bluetooth
devices. It displays advertised names, signal strength, and an RSSI-based
distance estimate when signal data is available. The app scans only; it does
not connect to networks or pair with devices.

The interface uses a red terminal-inspired style and has separate Wi-Fi and
Bluetooth modes. Results refresh while scanning. Use **CLEAR** to stop that
mode and clear its results. Bluetooth results unseen for 60 seconds are
removed, and the list is capped at 200 devices to keep long scans responsive.
Bluetooth result changes are grouped into updates at most 30 ms apart. Wi-Fi
results appear when Android reports new scan results; Android controls and
rate-limits how often the Wi-Fi radio can perform a scan.

## Install the APK

Download [`alfred-mapper2.apk`](./alfred-mapper2.apk), copy it to your Android
phone, and open it to install. Android may ask you to allow installation from
your file manager or browser. The APK is a debug build.

On the first scan, grant the requested nearby-device, Wi-Fi, and notification
permissions. Enable Wi-Fi, Bluetooth, and Location in the phone settings as
needed. A visible Android foreground-service notification keeps a scan running
when the app screen is closed and provides a **Stop scanning** action. The
in-app Stop button does the same.

Android or the phone manufacturer may still stop background work after a
force-stop, permission revocation, device restart, or aggressive battery
optimization. To prevent scanning from continuing, tap **Stop scanning** in
the app or its notification.

## Build from source

Requirements:

- JDK 17 or newer
- Android SDK Platform 35
- Android SDK Build Tools (the Gradle build selects the required version)

Build the debug APK:

```bash
./gradlew assembleDebug
```

The output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Tests and lint

Run the unit tests and Android lint checks:

```bash
./gradlew testDebugUnitTest lintDebug
```

## Distance calibration and limitations

The displayed distance is an approximate RSSI estimate, not an exact
measurement. Indoor reflections, walls, interference, antenna orientation, and
differences between devices can make it inaccurate. Some Bluetooth devices do
not report signal strength, so their distance cannot be estimated.

For a more useful estimate, scan a device at a measured distance and use
**CALIBRATE DISTANCE** to save that distance and its displayed RSSI. Choose an
environment factor of about 2 for open areas or 3 for typical indoor spaces.
Wi-Fi and Bluetooth calibrations are stored separately on the phone. For best
results, calibrate each with the same device and radio type you plan to measure.

## Related project

The separate Linux/Python terminal program is published at
[Alfred Mapper](https://github.com/kalilinux123456789az-ai/alfred-mapper).
