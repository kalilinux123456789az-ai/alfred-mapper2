package com.alfredmapper2;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int COLOR_BACKGROUND = Color.rgb(8, 8, 8);
    private static final int COLOR_CARD = Color.rgb(24, 12, 12);
    private static final int COLOR_TEXT = Color.rgb(255, 75, 75);
    private static final int COLOR_MUTED = Color.rgb(181, 87, 87);
    private static final int COLOR_ACCENT = Color.rgb(255, 28, 28);

    private final Map<String, RadioItem> bluetoothItems = new HashMap<>();
    private final Map<String, RadioItem> wifiItems = new HashMap<>();
    private LinearLayout wifiPanel;
    private LinearLayout bluetoothPanel;
    private LinearLayout wifiList;
    private LinearLayout bluetoothList;
    private TextView statusView;
    private TextView wifiCountView;
    private TextView bluetoothCountView;
    private Button scanButton;
    private Button wifiScanButton;
    private Button wifiModeButton;
    private Button bluetoothModeButton;
    private int currentMode = RadioScanService.MODE_WIFI;
    private int activeMode = RadioScanService.MODE_NONE;
    private int permissionRequestMode = -1;
    private boolean scanning;
    private boolean wifiScanning;
    private boolean updateReceiverRegistered;
    private boolean serviceBound;
    private boolean notificationPermissionMissing;
    private RadioScanService scanService;

    private static final class RadioItem {
        final String key;
        final String name;
        final String detail;
        final int rssi;
        final int signalPercent;

        RadioItem(String key, String name, int rssi, String detail, int signalPercent) {
            this.key = key;
            this.name = name;
            this.rssi = rssi;
            this.detail = detail;
            this.signalPercent = signalPercent;
        }
    }

    private final BroadcastReceiver scanUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RadioScanService.ACTION_UPDATE.equals(intent.getAction())) return;
            applyScanUpdate(intent);
        }
    };

    private final ServiceConnection scanServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            scanService = ((RadioScanService.LocalBinder) binder).getService();
            scanService.publishSnapshot();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            scanService = null;
            serviceBound = false;
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        int systemUiVisibility = getWindow().getDecorView().getSystemUiVisibility()
                & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            systemUiVisibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUiVisibility);
        buildScreen();
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(RadioScanService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanUpdateReceiver, filter);
        }
        updateReceiverRegistered = true;
        serviceBound = bindService(
                new Intent(this, RadioScanService.class),
                scanServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        if (serviceBound) {
            unbindService(scanServiceConnection);
            serviceBound = false;
            scanService = null;
        }
        if (updateReceiverRegistered) {
            unregisterReceiver(scanUpdateReceiver);
            updateReceiverRegistered = false;
        }
        super.onStop();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(
                    dp(20),
                    dp(28) + insets.getSystemWindowInsetTop(),
                    dp(20),
                    dp(28) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("ALFRED_MAPPER_2", 25, COLOR_ACCENT, true));
        TextView subtitle = text("> RADIO SCANNER // ANDROID", 13, COLOR_MUTED, false);
        subtitle.setPadding(0, dp(5), 0, dp(18));
        root.addView(subtitle);
        TextView developer = text("> DEVELOPER: TAMBET SARAPU", 11, COLOR_MUTED, false);
        developer.setPadding(0, 0, 0, dp(18));
        root.addView(developer);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        wifiModeButton = button("[ WI-FI ]", COLOR_ACCENT, Color.BLACK);
        bluetoothModeButton = button("[ BLUETOOTH ]", COLOR_CARD, COLOR_TEXT);
        wifiModeButton.setOnClickListener(v -> showMode(RadioScanService.MODE_WIFI));
        bluetoothModeButton.setOnClickListener(v -> showMode(RadioScanService.MODE_BLUETOOTH));
        modes.addView(wifiModeButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams bluetoothModeParams =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        bluetoothModeParams.setMargins(dp(8), 0, 0, 0);
        modes.addView(bluetoothModeButton, bluetoothModeParams);
        root.addView(modes);

        statusView = text("> READY. SELECT A SCAN MODE.", 13, COLOR_ACCENT, false);
        statusView.setPadding(0, dp(14), 0, dp(16));
        root.addView(statusView);

        wifiPanel = new LinearLayout(this);
        wifiPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(wifiPanel);
        LinearLayout wifiControls = new LinearLayout(this);
        wifiControls.setOrientation(LinearLayout.HORIZONTAL);
        wifiScanButton = button(getString(R.string.start_wifi_monitor), COLOR_CARD, COLOR_ACCENT);
        wifiScanButton.setOnClickListener(v -> {
            if (wifiScanning) stopScan();
            else requestPermissionsAndScanWifi();
        });
        wifiControls.addView(wifiScanButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button clearWifiButton = button("[ CLEAR ]", COLOR_CARD, COLOR_TEXT);
        clearWifiButton.setOnClickListener(v -> clearResults(RadioScanService.MODE_WIFI));
        LinearLayout.LayoutParams clearWifiParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearWifiParams.setMargins(dp(8), 0, 0, 0);
        wifiControls.addView(clearWifiButton, clearWifiParams);
        wifiPanel.addView(wifiControls);
        wifiCountView = sectionTitle("> WI-FI NETWORKS");
        wifiCountView.setPadding(0, dp(16), 0, dp(8));
        wifiPanel.addView(wifiCountView);
        wifiList = new LinearLayout(this);
        wifiList.setOrientation(LinearLayout.VERTICAL);
        wifiPanel.addView(wifiList);
        renderItems(wifiList, wifiItems, true);

        bluetoothPanel = new LinearLayout(this);
        bluetoothPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(bluetoothPanel);
        LinearLayout bluetoothControls = new LinearLayout(this);
        bluetoothControls.setOrientation(LinearLayout.HORIZONTAL);
        scanButton = button(getString(R.string.start_scanning), COLOR_CARD, COLOR_ACCENT);
        scanButton.setOnClickListener(v -> {
            if (scanning) stopScan();
            else requestPermissionsAndStart();
        });
        bluetoothControls.addView(scanButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button clearBluetoothButton = button("[ CLEAR ]", COLOR_CARD, COLOR_TEXT);
        clearBluetoothButton.setOnClickListener(v -> clearResults(RadioScanService.MODE_BLUETOOTH));
        LinearLayout.LayoutParams clearBluetoothParams =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        clearBluetoothParams.setMargins(dp(8), 0, 0, 0);
        bluetoothControls.addView(clearBluetoothButton, clearBluetoothParams);
        bluetoothPanel.addView(bluetoothControls);
        bluetoothCountView = sectionTitle("> BLUETOOTH DEVICES");
        bluetoothCountView.setPadding(0, dp(16), 0, dp(8));
        bluetoothPanel.addView(bluetoothCountView);
        bluetoothList = new LinearLayout(this);
        bluetoothList.setOrientation(LinearLayout.VERTICAL);
        bluetoothPanel.addView(bluetoothList);
        renderItems(bluetoothList, bluetoothItems, false);

        TextView note = text(
                "> DISTANCE IS AN APPROXIMATE RSSI ESTIMATE, NOT EXACT",
                11,
                COLOR_MUTED,
                false);
        note.setPadding(0, dp(18), 0, dp(6));
        root.addView(note);
        updateModeViews();
    }

    private void showMode(int mode) {
        if (currentMode == mode) return;
        if (scanning || wifiScanning) stopScan();
        permissionRequestMode = -1;
        currentMode = mode;
        updateModeViews();
    }

    private void updateModeViews() {
        wifiPanel.setVisibility(currentMode == RadioScanService.MODE_WIFI
                ? View.VISIBLE : View.GONE);
        bluetoothPanel.setVisibility(currentMode == RadioScanService.MODE_BLUETOOTH
                ? View.VISIBLE : View.GONE);
        wifiModeButton.setBackgroundTintList(ColorStateList.valueOf(
                currentMode == RadioScanService.MODE_WIFI ? COLOR_ACCENT : COLOR_CARD));
        wifiModeButton.setTextColor(
                currentMode == RadioScanService.MODE_WIFI ? Color.BLACK : COLOR_TEXT);
        bluetoothModeButton.setBackgroundTintList(ColorStateList.valueOf(
                currentMode == RadioScanService.MODE_BLUETOOTH ? COLOR_ACCENT : COLOR_CARD));
        bluetoothModeButton.setTextColor(
                currentMode == RadioScanService.MODE_BLUETOOTH ? Color.BLACK : COLOR_TEXT);
        if (!scanning && !wifiScanning) {
            statusView.setText(currentMode == RadioScanService.MODE_WIFI
                    ? "> WI-FI MODE READY." : "> BLUETOOTH MODE READY.");
        }
    }

    private void requestPermissionsAndStart() {
        requestScanPermissions(RadioScanService.MODE_BLUETOOTH);
    }

    private void requestPermissionsAndScanWifi() {
        requestScanPermissions(RadioScanService.MODE_WIFI);
    }

    private void requestScanPermissions(int mode) {
        if (permissionRequestMode != -1) {
            statusView.setText(R.string.permission_request_pending);
            return;
        }
        List<String> needed = mode == RadioScanService.MODE_WIFI
                ? wifiPermissionsMissing() : bluetoothPermissionsMissing();
        if (!needed.isEmpty()) {
            permissionRequestMode = mode;
            requestPermissions(needed.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        startScan(mode);
    }

    private List<String> bluetoothPermissionsMissing() {
        List<String> needed = new ArrayList<>();
        addNotificationPermissionIfNeeded(needed);
        if (Build.VERSION.SDK_INT >= 31) {
            addIfMissing(needed, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(needed, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(needed, Manifest.permission.ACCESS_COARSE_LOCATION);
            addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return needed;
    }

    private List<String> wifiPermissionsMissing() {
        List<String> needed = new ArrayList<>();
        addNotificationPermissionIfNeeded(needed);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(needed, Manifest.permission.NEARBY_WIFI_DEVICES);
            addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            addIfMissing(needed, Manifest.permission.ACCESS_COARSE_LOCATION);
            addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return needed;
    }

    private void addNotificationPermissionIfNeeded(List<String> needed) {
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void addIfMissing(List<String> needed, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            needed.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_PERMISSIONS) return;
        int requestedMode = permissionRequestMode;
        permissionRequestMode = -1;
        boolean granted = results != null && results.length > 0;
        if (results != null) {
            for (int i = 0; i < results.length; i++) {
                boolean notificationPermission = Build.VERSION.SDK_INT >= 33
                        && Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i]);
                if (!notificationPermission) {
                    granted &= results[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
        }
        if (requestedMode == -1) {
            statusView.setText(R.string.permission_scan_cancelled);
        } else if (!granted) {
            statusView.setText(R.string.permission_denied);
        } else if (requestedMode != currentMode) {
            statusView.setText(R.string.permission_mode_changed);
        } else {
            notificationPermissionMissing = Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED;
            startScan(requestedMode);
        }
    }

    private void startScan(int mode) {
        Intent intent = new Intent(this, RadioScanService.class).setAction(
                mode == RadioScanService.MODE_WIFI
                        ? RadioScanService.ACTION_START_WIFI
                        : RadioScanService.ACTION_START_BLUETOOTH);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            statusView.setText(mode == RadioScanService.MODE_WIFI
                    ? R.string.wifi_monitor_active : R.string.scanning);
        } catch (SecurityException | IllegalStateException exception) {
            statusView.setText(mode == RadioScanService.MODE_WIFI
                    ? R.string.wifi_scan_unavailable : R.string.bluetooth_scan_unavailable);
        }
    }

    private void stopScan() {
        permissionRequestMode = -1;
        Intent intent = new Intent(this, RadioScanService.class)
                .setAction(RadioScanService.ACTION_STOP);
        startService(intent);
    }

    private void clearResults(int mode) {
        permissionRequestMode = -1;
        Intent intent = new Intent(this, RadioScanService.class)
                .setAction(RadioScanService.ACTION_CLEAR)
                .putExtra(RadioScanService.EXTRA_MODE, mode);
        startService(intent);
        Map<String, RadioItem> items = mode == RadioScanService.MODE_WIFI
                ? wifiItems : bluetoothItems;
        items.clear();
        if (mode == RadioScanService.MODE_WIFI) {
            renderItems(wifiList, wifiItems, true);
            wifiCountView.setText(getString(R.string.wifi_network_count, 0));
        } else {
            renderItems(bluetoothList, bluetoothItems, false);
            bluetoothCountView.setText(getString(R.string.bluetooth_device_count, 0));
        }
        statusView.setText(mode == RadioScanService.MODE_WIFI
                ? R.string.wifi_results_cleared : R.string.bluetooth_results_cleared);
    }

    private void applyScanUpdate(Intent update) {
        boolean active = update.getBooleanExtra(RadioScanService.EXTRA_ACTIVE, false);
        notificationPermissionMissing = Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
        int updateMode = update.getIntExtra(
                RadioScanService.EXTRA_MODE, RadioScanService.MODE_NONE);
        int status = update.getIntExtra(RadioScanService.EXTRA_STATUS, R.string.ready);
        int statusCount = update.getIntExtra(RadioScanService.EXTRA_STATUS_COUNT, -1);
        if (updateMode == RadioScanService.MODE_WIFI
                || updateMode == RadioScanService.MODE_BLUETOOTH) {
            Map<String, RadioItem> items = updateMode == RadioScanService.MODE_WIFI
                    ? wifiItems : bluetoothItems;
            String[] keys = update.getStringArrayExtra(RadioScanService.EXTRA_KEYS);
            String[] names = update.getStringArrayExtra(RadioScanService.EXTRA_NAMES);
            String[] details = update.getStringArrayExtra(RadioScanService.EXTRA_DETAILS);
            int[] rssis = update.getIntArrayExtra(RadioScanService.EXTRA_RSSI);
            int[] signals = update.getIntArrayExtra(RadioScanService.EXTRA_SIGNAL);
            if (keys != null && names != null && details != null
                    && rssis != null && signals != null
                    && keys.length == names.length
                    && keys.length == details.length
                    && keys.length == rssis.length
                    && keys.length == signals.length) {
                items.clear();
                for (int i = 0; i < keys.length; i++) {
                    items.put(keys[i], new RadioItem(
                            keys[i], names[i], rssis[i], details[i], signals[i]));
                }
            }
            if (updateMode == RadioScanService.MODE_WIFI) {
                renderItems(wifiList, wifiItems, true);
                wifiCountView.setText(getString(R.string.wifi_network_count, wifiItems.size()));
            } else {
                renderItems(bluetoothList, bluetoothItems, false);
                bluetoothCountView.setText(
                        getString(R.string.bluetooth_device_count, bluetoothItems.size()));
            }
        }
        if (active && (updateMode == RadioScanService.MODE_WIFI
                || updateMode == RadioScanService.MODE_BLUETOOTH)) {
            activeMode = updateMode;
            scanning = updateMode == RadioScanService.MODE_BLUETOOTH;
            wifiScanning = updateMode == RadioScanService.MODE_WIFI;
            if (currentMode != updateMode) {
                currentMode = updateMode;
                updateModeViews();
            }
        } else {
            if (activeMode == RadioScanService.MODE_BLUETOOTH) scanning = false;
            if (activeMode == RadioScanService.MODE_WIFI) wifiScanning = false;
            activeMode = RadioScanService.MODE_NONE;
        }
        scanButton.setText(scanning
                ? R.string.stop_scanning : R.string.start_scanning);
        wifiScanButton.setText(wifiScanning
                ? R.string.stop_wifi_monitor : R.string.start_wifi_monitor);
        if (active && notificationPermissionMissing) {
            statusView.setText(R.string.notification_permission_missing);
        } else {
            statusView.setText(statusCount >= 0
                    ? getString(status, statusCount) : getString(status));
        }
    }

    private void renderItems(
            LinearLayout container, Map<String, RadioItem> items, boolean wifi) {
        if (container == null) return;
        container.removeAllViews();
        List<RadioItem> sorted = new ArrayList<>(items.values());
        Collections.sort(sorted,
                (first, second) -> first.name.compareToIgnoreCase(second.name));
        for (RadioItem item : sorted) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(COLOR_CARD);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            container.addView(card, cardParams);

            card.addView(text(item.name, 15, COLOR_TEXT, true));
            String signal = wifi
                    ? item.signalPercent + "% signal · " + item.rssi + " dBm"
                    : item.rssi + " dBm RSSI";
            card.addView(text(signal + " · " + estimateDistance(item.rssi),
                    12, COLOR_ACCENT, false));
            card.addView(text(wifi ? item.detail + " · " + item.key : item.key,
                    11, COLOR_MUTED, false));
        }
        if (items.isEmpty()) {
            addEmptyState(container, getString(
                    wifi ? R.string.no_wifi_networks : R.string.no_bluetooth_devices));
        }
    }

    private String estimateDistance(int rssi) {
        return String.format(
                Locale.getDefault(), "≈ %.2f m (estimate)", Math.pow(10.0, (-59.0 - rssi) / 22.0));
    }

    private void addEmptyState(LinearLayout container, String message) {
        TextView empty = text(message, 13, COLOR_MUTED, false);
        empty.setPadding(dp(4), dp(10), dp(4), dp(10));
        container.addView(empty);
    }

    private TextView sectionTitle(String value) {
        return text(value, 18, COLOR_TEXT, true);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return text;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
