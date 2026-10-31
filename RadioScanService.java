package com.alfredmapper2;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.location.LocationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RadioScanService extends Service {
    private static final String TAG = "AlfredRadioScan";
    public static final String ACTION_START_WIFI = "com.alfredmapper2.START_WIFI_SCAN";
    public static final String ACTION_START_BLUETOOTH = "com.alfredmapper2.START_BLUETOOTH_SCAN";
    public static final String ACTION_STOP = "com.alfredmapper2.STOP_SCAN";
    public static final String ACTION_CLEAR = "com.alfredmapper2.CLEAR_SCAN";
    public static final String ACTION_UPDATE = "com.alfredmapper2.SCAN_UPDATE";
    public static final String EXTRA_ACTIVE = "active";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_KEYS = "keys";
    public static final String EXTRA_NAMES = "names";
    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_RSSI = "rssi";
    public static final String EXTRA_SIGNAL = "signal";
    public static final String EXTRA_STATUS_COUNT = "status_count";
    public static final int MODE_NONE = -1;
    public static final int MODE_WIFI = 0;
    public static final int MODE_BLUETOOTH = 1;

    private static final String CHANNEL_ID = "radio_scan";
    private static final int NOTIFICATION_ID = 24;
    private static final long REFRESH_INTERVAL_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Device> devices = new HashMap<>();
    private final ScanSession scanSession = new ScanSession();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleCallback;
    private WifiManager wifiManager;
    private BroadcastReceiver bluetoothReceiver;
    private BroadcastReceiver wifiScanReceiver;
    private boolean bluetoothReceiverRegistered;
    private boolean wifiReceiverRegistered;
    private boolean wifiScanPending;
    private boolean active;
    private boolean bluetoothWarning;
    private boolean classicDiscoveryStarted;
    private int mode = MODE_NONE;
    private int status = R.string.ready;
    private int statusCount = -1;
    private int sessionMode = MODE_NONE;
    private long sessionToken;

    private static final class Device {
        final String key;
        String name;
        String detail;
        int rssi;
        int signal;

        Device(String key, String name, int rssi, String detail, int signal) {
            this.key = key;
            this.name = name;
            this.rssi = rssi;
            this.detail = detail;
            this.signal = signal;
        }
    }

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            if (mode == MODE_BLUETOOTH) {
                if (!hasBluetoothPermissions()) {
                    stopWithStatus(R.string.bluetooth_permission_denied);
                    return;
                }
                startClassicDiscovery();
                if (!bluetoothWarning) {
                    status = R.string.bluetooth_scan_active_count;
                    statusCount = devices.size();
                }
                publishUpdate();
            } else if (mode == MODE_WIFI) {
                if (!hasWifiPermissions()) {
                    stopWithStatus(R.string.wifi_permission_denied);
                    return;
                }
                refreshWifi();
                publishUpdate();
            }
            if (active) handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public final class LocalBinder extends android.os.Binder {
        public RadioScanService getService() {
            return RadioScanService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopWithStatus(R.string.scanning_stopped);
        } else if (ACTION_CLEAR.equals(action)) {
            int clearMode = intent.getIntExtra(EXTRA_MODE, MODE_NONE);
            if (active && mode != clearMode) return START_NOT_STICKY;
            sessionMode = clearMode;
            devices.clear();
            stopWithStatus(clearMode == MODE_WIFI
                    ? R.string.wifi_results_cleared : R.string.bluetooth_results_cleared);
        } else if (ACTION_START_WIFI.equals(action)) {
            startScan(MODE_WIFI);
        } else if (ACTION_START_BLUETOOTH.equals(action)) {
            startScan(MODE_BLUETOOTH);
        } else if (!active) {
            stopSelf(startId);
        } else {
            publishUpdate();
        }
        return START_NOT_STICKY;
    }

    void publishSnapshot() {
        publishUpdate();
    }

    private void startScan(int requestedMode) {
        if (active && mode == requestedMode) {
            publishUpdate();
            return;
        }
        if (!active) sessionMode = requestedMode;
        if (requestedMode == MODE_WIFI && !hasWifiPermissions()) {
            stopWithStatus(R.string.wifi_permission_denied);
            return;
        }
        if (requestedMode == MODE_BLUETOOTH && !hasBluetoothPermissions()) {
            stopWithStatus(R.string.bluetooth_permission_denied);
            return;
        }
        if (requestedMode == MODE_BLUETOOTH
                && (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())) {
            stopWithStatus(bluetoothAdapter == null
                    ? R.string.no_bluetooth : R.string.turn_on_bluetooth);
            return;
        }
        if (requestedMode == MODE_WIFI
                && (wifiManager == null || !wifiManager.isWifiEnabled())) {
            stopWithStatus(wifiManager == null ? R.string.no_wifi : R.string.wifi_disabled);
            return;
        }
        if (requestedMode == MODE_WIFI && !isLocationEnabled()) {
            stopWithStatus(R.string.wifi_location_disabled);
            return;
        }

        if (active) stopHardwareScan();
        mode = requestedMode;
        sessionMode = requestedMode;
        sessionToken = scanSession.start();
        active = true;
        status = requestedMode == MODE_WIFI
                ? R.string.wifi_monitor_active : R.string.scanning;
        statusCount = requestedMode == MODE_BLUETOOTH ? 0 : -1;
        bluetoothWarning = false;
        devices.clear();
        try {
            startForegroundForMode(requestedMode);
            if (requestedMode == MODE_WIFI) startWifiScan(sessionToken);
            else startBluetoothScan(sessionToken);
            publishUpdate();
            handler.removeCallbacks(refreshTask);
            handler.postDelayed(refreshTask, REFRESH_INTERVAL_MS);
        } catch (SecurityException exception) {
            stopWithStatus(requestedMode == MODE_WIFI
                    ? R.string.wifi_permission_denied : R.string.bluetooth_permission_denied);
        } catch (IllegalStateException exception) {
            stopWithStatus(requestedMode == MODE_WIFI
                    ? R.string.wifi_scan_unavailable : R.string.bluetooth_scan_unavailable);
        }
    }

    private void startForegroundForMode(int requestedMode) {
        Notification notification = buildNotification(requestedMode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = requestedMode == MODE_WIFI
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            startForeground(NOTIFICATION_ID, notification, type);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startWifiScan(long token) {
        registerWifiReceiver(token);
        refreshWifi();
    }

    private void refreshWifi() {
        if (!active || mode != MODE_WIFI || wifiManager == null) return;
        try {
            if (!isLocationEnabled()) {
                status = R.string.wifi_location_disabled;
                return;
            }
            if (!wifiManager.isWifiEnabled()) {
                status = R.string.wifi_disabled;
                return;
            }
            if (status == R.string.wifi_disabled
                    || status == R.string.wifi_location_disabled
                    || status == R.string.wifi_scan_unavailable) {
                status = R.string.wifi_monitor_active;
            }
            readWifiResults();
            if (!active || wifiScanPending) return;
            wifiScanPending = true;
            if (!wifiManager.startScan()) {
                wifiScanPending = false;
                status = R.string.wifi_scan_rate_limited;
            } else {
                status = R.string.wifi_monitor_active;
            }
        } catch (SecurityException exception) {
            stopWithStatus(R.string.wifi_permission_denied);
        } catch (IllegalStateException exception) {
            status = R.string.wifi_scan_unavailable;
        }
    }

    private void readWifiResults() {
        try {
            List<android.net.wifi.ScanResult> results = wifiManager.getScanResults();
            Map<String, Device> latest = new HashMap<>();
            for (android.net.wifi.ScanResult result : results) {
                String name = result.SSID == null || result.SSID.isEmpty()
                        ? getString(R.string.hidden_network) : result.SSID;
                String key = result.BSSID == null ? name : result.BSSID;
                latest.put(key, new Device(
                        key,
                        name,
                        result.level,
                        result.capabilities == null ? "Wi-Fi" : result.capabilities,
                        WifiManager.calculateSignalLevel(result.level, 100)));
            }
            devices.clear();
            devices.putAll(latest);
        } catch (SecurityException exception) {
            stopWithStatus(R.string.wifi_results_permission_denied);
        }
    }

    private void startBluetoothScan(long token) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                stopWithStatus(R.string.bluetooth_permission_denied);
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stopWithStatus(R.string.bluetooth_permission_denied);
            return;
        }
        registerBluetoothReceiver(token);
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner != null) {
            bleCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (scanSession.accepts(token)) addBleResult(result, token);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    if (!scanSession.accepts(token)) return;
                    for (ScanResult result : results) addBleResult(result, token);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    handler.post(() -> {
                        if (scanSession.accepts(token) && active && mode == MODE_BLUETOOTH) {
                            bluetoothWarning = true;
                            status = R.string.bluetooth_scan_failed;
                            statusCount = errorCode;
                            publishUpdate();
                        }
                    });
                }
            };
            bleScanner.startScan(bleCallback);
        } else {
            bluetoothWarning = true;
            status = R.string.ble_not_available;
        }
        startClassicDiscovery();
    }

    private void addBleResult(ScanResult result, long token) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> addBleResult(result, token));
            return;
        }
        if (!scanSession.accepts(token) || !active || mode != MODE_BLUETOOTH) return;
        try {
            BluetoothDevice device = result.getDevice();
            String name = result.getScanRecord() == null
                    ? null : result.getScanRecord().getDeviceName();
            addBluetooth(device.getAddress(), deviceName(device, name), result.getRssi(), token);
        } catch (SecurityException exception) {
            stopWithStatus(R.string.bluetooth_permission_denied);
        }
    }

    private void addBluetooth(String address, String name, int rssi, long token) {
        if (!scanSession.accepts(token) || !active || mode != MODE_BLUETOOTH) return;
        Device existing = devices.get(address);
        String safeName = name == null || name.trim().isEmpty()
                ? getString(R.string.unnamed_device) : name.trim();
        if (existing == null) {
            devices.put(address, new Device(address, safeName, rssi, "Bluetooth", -1));
        } else {
            if (existing.name.startsWith("<") && !safeName.startsWith("<")) {
                existing.name = safeName;
            }
            existing.rssi = rssi;
        }
    }

    private void startClassicDiscovery() {
        if (!active || mode != MODE_BLUETOOTH || bluetoothAdapter == null) return;
        try {
            if (!bluetoothAdapter.isDiscovering()) {
                classicDiscoveryStarted = bluetoothAdapter.startDiscovery();
                if (!classicDiscoveryStarted) {
                    bluetoothWarning = true;
                    status = R.string.classic_scan_unavailable;
                }
            }
        } catch (SecurityException exception) {
            bluetoothWarning = true;
            stopWithStatus(R.string.bluetooth_discovery_permission_denied);
        } catch (IllegalStateException exception) {
            bluetoothWarning = true;
            status = R.string.bluetooth_scan_unavailable;
        }
    }

    private void registerBluetoothReceiver(long token) {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!scanSession.accepts(token) || !active || mode != MODE_BLUETOOTH) return;
                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                    classicDiscoveryStarted = false;
                    return;
                }
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    int state = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_TURNING_OFF
                            || state == BluetoothAdapter.STATE_OFF) {
                        stopWithStatus(R.string.turn_on_bluetooth);
                    }
                    return;
                }
                if (!BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) return;
                try {
                    BluetoothDevice device = getBluetoothDevice(intent);
                    if (device == null) return;
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    if (rssi != Short.MIN_VALUE) {
                        addBluetooth(device.getAddress(), deviceName(device, null), rssi, token);
                    }
                } catch (SecurityException exception) {
                    stopWithStatus(R.string.bluetooth_permission_denied);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothReceiver, filter);
        }
        bluetoothReceiverRegistered = true;
    }

    private void registerWifiReceiver(long token) {
        if (wifiReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!scanSession.accepts(token) || !active || mode != MODE_WIFI
                        || !WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) return;
                wifiScanPending = false;
                if (!intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    status = R.string.wifi_scan_limited;
                } else {
                    status = R.string.wifi_monitor_active;
                }
                readWifiResults();
                if (active) publishUpdate();
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(wifiScanReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(wifiScanReceiver, filter);
        }
        wifiReceiverRegistered = true;
    }

    private void stopWithStatus(int message) {
        status = message;
        statusCount = -1;
        bluetoothWarning = false;
        active = false;
        stopHardwareScan();
        mode = MODE_NONE;
        publishUpdate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void stopHardwareScan() {
        active = false;
        scanSession.stop();
        handler.removeCallbacks(refreshTask);
        wifiScanPending = false;
        try {
            if (bleScanner != null && bleCallback != null && hasBluetoothPermissions()) {
                bleScanner.stopScan(bleCallback);
            }
        } catch (SecurityException | IllegalStateException exception) {
            Log.w(TAG, "Could not stop Bluetooth LE scan cleanly", exception);
        }
        bleScanner = null;
        bleCallback = null;
        try {
            if (mode == MODE_BLUETOOTH && classicDiscoveryStarted
                    && bluetoothAdapter != null && hasBluetoothPermissions()
                    && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException | IllegalStateException exception) {
            Log.w(TAG, "Could not stop Bluetooth discovery cleanly", exception);
        } finally {
            classicDiscoveryStarted = false;
        }
        if (bluetoothReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver);
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Bluetooth receiver was already unregistered", exception);
            } finally {
                bluetoothReceiverRegistered = false;
                bluetoothReceiver = null;
            }
        }
        if (wifiReceiverRegistered) {
            try {
                unregisterReceiver(wifiScanReceiver);
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Wi-Fi receiver was already unregistered", exception);
            } finally {
                wifiReceiverRegistered = false;
                wifiScanReceiver = null;
            }
        }
    }

    private String deviceName(BluetoothDevice device, String advertisedName) {
        if (advertisedName != null && !advertisedName.trim().isEmpty()) return advertisedName;
        try {
            String name = device.getName();
            return name == null ? getString(R.string.unnamed_device) : name;
        } catch (SecurityException exception) {
            return getString(R.string.unnamed_device);
        }
    }

    private BluetoothDevice getBluetoothDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasWifiPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED);
    }

    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true;
        LocationManager locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager != null && locationManager.isLocationEnabled();
    }

    private void publishUpdate() {
        Intent update = new Intent(ACTION_UPDATE).setPackage(getPackageName());
        update.putExtra(EXTRA_ACTIVE, active);
        update.putExtra(EXTRA_MODE, active ? mode : sessionMode);
        update.putExtra(EXTRA_STATUS, status);
        update.putExtra(EXTRA_STATUS_COUNT, statusCount);
        List<Device> sorted = new ArrayList<>(devices.values());
        String[] keys = new String[sorted.size()];
        String[] names = new String[sorted.size()];
        String[] details = new String[sorted.size()];
        int[] rssi = new int[sorted.size()];
        int[] signal = new int[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            Device device = sorted.get(i);
            keys[i] = device.key;
            names[i] = device.name;
            details[i] = device.detail;
            rssi[i] = device.rssi;
            signal[i] = device.signal;
        }
        update.putExtra(EXTRA_KEYS, keys);
        update.putExtra(EXTRA_NAMES, names);
        update.putExtra(EXTRA_DETAILS, details);
        update.putExtra(EXTRA_RSSI, rssi);
        update.putExtra(EXTRA_SIGNAL, signal);
        sendBroadcast(update);
    }

    private Notification buildNotification(int scanMode) {
        Intent stopIntent = new Intent(this, RadioScanService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String description = getString(scanMode == MODE_WIFI
                ? R.string.background_wifi_running : R.string.background_bluetooth_running);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(description)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.app_icon, getString(R.string.stop_scanning), stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.scan_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.scan_notification_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopHardwareScan();
        super.onDestroy();
    }
}
