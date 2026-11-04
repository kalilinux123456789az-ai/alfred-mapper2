package com.alfredmapper2;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final String DISTANCE_PREFERENCES = "distance_calibration";
    private static final String REFERENCE_RSSI_KEY = "reference_rssi";
    private static final String REFERENCE_DISTANCE_KEY = "reference_distance";
    private static final String PATH_LOSS_KEY = "path_loss_exponent";
    private static final int MODE_COMPASS = 2;
    private static final int COLOR_BACKGROUND = Color.rgb(8, 8, 8);
    private static final int COLOR_CARD = Color.rgb(24, 12, 12);
    private static final int COLOR_TEXT = Color.rgb(255, 75, 75);
    private static final int COLOR_MUTED = Color.rgb(181, 87, 87);
    private static final int COLOR_ACCENT = Color.rgb(255, 28, 28);

    private final Map<String, RadioItem> bluetoothItems = new HashMap<>();
    private final Map<String, RadioItem> wifiItems = new HashMap<>();
    private final Map<String, RadioItem> radarItems = new HashMap<>();
    private LinearLayout wifiPanel;
    private LinearLayout bluetoothPanel;
    private LinearLayout compassPanel;
    private LinearLayout wifiList;
    private LinearLayout bluetoothList;
    private TextView statusView;
    private TextView compassHeadingView;
    private TextView locationView;
    private TextView gpsCourseView;
    private TextView radarCountView;
    private TextView radarResultsView;
    private TextView wifiCountView;
    private TextView bluetoothCountView;
    private Button scanButton;
    private Button wifiScanButton;
    private Button wifiModeButton;
    private Button bluetoothModeButton;
    private Button compassModeButton;
    private Button radarScanButton;
    private CompassView compassView;
    private RadarView radarView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private int currentMode = RadioScanService.MODE_WIFI;
    private int activeMode = RadioScanService.MODE_NONE;
    private int permissionRequestMode = -1;
    private boolean scanning;
    private boolean wifiScanning;
    private boolean updateReceiverRegistered;
    private boolean serviceBound;
    private boolean notificationPermissionMissing;
    private boolean compassSensorRegistered;
    private boolean locationUpdatesRegistered;
    private boolean preciseLocation;
    private float compassHeading = Float.NaN;
    private RadioScanService scanService;

    private final class CompassView extends View {
        private final Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint northPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint southPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float heading;
        private boolean gpsCourse;
        private boolean headingAvailable;

        CompassView(Context context) {
            super(context);
            dialPaint.setColor(COLOR_ACCENT);
            dialPaint.setStyle(Paint.Style.STROKE);
            dialPaint.setStrokeWidth(dp(2));
            tickPaint.setColor(COLOR_MUTED);
            tickPaint.setStrokeWidth(dp(1));
            textPaint.setColor(COLOR_TEXT);
            textPaint.setTextSize(dp(16));
            textPaint.setTypeface(Typeface.MONOSPACE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            northPaint.setColor(COLOR_ACCENT);
            southPaint.setColor(COLOR_MUTED);
            centerPaint.setColor(COLOR_TEXT);
        }

        void setHeading(float value) {
            heading = value;
            gpsCourse = false;
            headingAvailable = true;
            invalidate();
        }

        void clearHeading() {
            gpsCourse = false;
            headingAvailable = false;
            invalidate();
        }

        void setGpsCourse(float value) {
            heading = value;
            gpsCourse = true;
            headingAvailable = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(centerX, centerY) - dp(14);
            canvas.drawCircle(centerX, centerY, radius, dialPaint);

            canvas.save();
            if (headingAvailable && !gpsCourse) canvas.rotate(-heading, centerX, centerY);
            for (int i = 0; i < 36; i++) {
                canvas.save();
                canvas.rotate(i * 10f, centerX, centerY);
                float tickLength = i % 9 == 0 ? dp(15) : dp(7);
                canvas.drawLine(
                        centerX, centerY - radius + dp(3),
                        centerX, centerY - radius + dp(3) + tickLength,
                        tickPaint);
                canvas.restore();
            }
            float labelRadius = radius - dp(28);
            canvas.drawText("N", centerX, centerY - labelRadius + dp(6), textPaint);
            canvas.drawText("E", centerX + labelRadius, centerY + dp(6), textPaint);
            canvas.drawText("S", centerX, centerY + labelRadius + dp(6), textPaint);
            canvas.drawText("W", centerX - labelRadius, centerY + dp(6), textPaint);
            if (gpsCourse) {
                canvas.save();
                canvas.rotate(heading, centerX, centerY);
                Path course = new Path();
                course.moveTo(centerX, centerY - radius + dp(24));
                course.lineTo(centerX - dp(10), centerY + dp(18));
                course.lineTo(centerX, centerY + dp(10));
                course.lineTo(centerX + dp(10), centerY + dp(18));
                course.close();
                canvas.drawPath(course, northPaint);
                canvas.restore();
            } else if (headingAvailable) {
                Path north = new Path();
                north.moveTo(centerX, centerY - radius + dp(24));
                north.lineTo(centerX - dp(10), centerY);
                north.lineTo(centerX, centerY - dp(5));
                north.lineTo(centerX + dp(10), centerY);
                north.close();
                canvas.drawPath(north, northPaint);
                Path south = new Path();
                south.moveTo(centerX, centerY + radius - dp(24));
                south.lineTo(centerX - dp(10), centerY);
                south.lineTo(centerX, centerY + dp(5));
                south.lineTo(centerX + dp(10), centerY);
                south.close();
                canvas.drawPath(south, southPaint);
            }
            canvas.restore();
            canvas.drawCircle(centerX, centerY, dp(5), centerPaint);
        }
    }

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

    private final class RadarView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wifiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bluetoothPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<RadioItem> items = new ArrayList<>();

        RadarView(Context context) {
            super(context);
            ringPaint.setColor(COLOR_MUTED);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1));
            axisPaint.setColor(Color.rgb(75, 35, 35));
            axisPaint.setStrokeWidth(dp(1));
            wifiPaint.setColor(COLOR_ACCENT);
            bluetoothPaint.setColor(Color.rgb(255, 170, 80));
            centerPaint.setColor(COLOR_TEXT);
        }

        void setItems(List<RadioItem> results) {
            items.clear();
            items.addAll(results);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(centerX, centerY) - dp(14);
            for (int ring = 1; ring <= 3; ring++) {
                canvas.drawCircle(centerX, centerY, radius * ring / 3f, ringPaint);
            }
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint);
            canvas.drawCircle(centerX, centerY, dp(3), centerPaint);

            List<RadioItem> sorted = new ArrayList<>(items);
            Collections.sort(sorted, (first, second) -> {
                int signalOrder = Integer.compare(second.rssi, first.rssi);
                return signalOrder != 0 ? signalOrder : first.key.compareTo(second.key);
            });
            float labelSize = Math.max(dp(9), Math.min(dp(11), radius / 14f));
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setTextSize(labelSize);
            for (int i = 0; i < Math.min(sorted.size(), 18); i++) {
                RadioItem item = sorted.get(i);
                float distance = radius * RadarProjection.radiusFractionForRssi(item.rssi);
                float angle = RadarProjection.angleDegreesForKey(item.key)
                        * (float) Math.PI / 180f;
                float x = centerX + (float) Math.sin(angle) * distance;
                float y = centerY - (float) Math.cos(angle) * distance;
                boolean wifi = RadarDeviceKey.isWifi(item.key);
                labelPaint.setColor(wifi ? wifiPaint.getColor() : bluetoothPaint.getColor());
                canvas.drawCircle(x, y, dp(4), labelPaint);
                String label = item.name.length() > 12
                        ? item.name.substring(0, 11) + "…" : item.name;
                float labelX = x + dp(7);
                if (labelX > getWidth() - dp(48)) labelX = x - dp(7);
                labelPaint.setTextAlign(labelX < x ? Paint.Align.RIGHT : Paint.Align.LEFT);
                canvas.drawText(label, labelX, y - dp(5), labelPaint);
            }
        }
    }

    private final BroadcastReceiver scanUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RadioScanService.ACTION_UPDATE.equals(intent.getAction())) return;
            applyScanUpdate(intent);
        }
    };

    private final SensorEventListener compassSensorListener = new SensorEventListener() {
        private final float[] rotationMatrix = new float[9];
        private final float[] orientation = new float[3];

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            compassHeading = (float) Math.toDegrees(orientation[0]);
            if (compassHeading < 0) compassHeading += 360;
            updateCompassHeading();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
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
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentMode == MODE_COMPASS) {
            startCompassSensors();
            if (hasLocationPermission()) {
                startLocationUpdates();
            } else {
                locationView.setText(R.string.gps_permission_required);
                gpsCourseView.setText(R.string.gps_course_waiting);
            }
        }
    }

    @Override
    protected void onPause() {
        stopCompassSensors();
        stopLocationUpdates();
        super.onPause();
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

        compassModeButton = button("[ COMPASS / RADAR ]", COLOR_CARD, COLOR_TEXT);
        compassModeButton.setOnClickListener(v -> showMode(MODE_COMPASS));
        LinearLayout.LayoutParams compassModeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        compassModeParams.topMargin = dp(8);
        root.addView(compassModeButton, compassModeParams);

        statusView = text("> READY. SELECT A SCAN MODE.", 13, COLOR_ACCENT, false);
        statusView.setPadding(0, dp(14), 0, dp(16));
        root.addView(statusView);

        compassPanel = new LinearLayout(this);
        compassPanel.setOrientation(LinearLayout.VERTICAL);
        radarCountView = sectionTitle("> RADAR: 0 WI-FI · 0 BLUETOOTH");
        radarCountView.setGravity(Gravity.CENTER);
        compassPanel.addView(radarCountView);
        radarView = new RadarView(this);
        radarView.setContentDescription(getString(R.string.radar_accessibility));
        LinearLayout.LayoutParams radarParams = new LinearLayout.LayoutParams(
                dp(220), dp(220));
        radarParams.gravity = Gravity.CENTER_HORIZONTAL;
        compassPanel.addView(radarView, radarParams);
        TextView radarLegend = text(
                getString(R.string.radar_legend) + "\n"
                        + getString(R.string.radar_direction_note),
                11,
                COLOR_MUTED,
                false);
        radarLegend.setGravity(Gravity.CENTER);
        compassPanel.addView(radarLegend);
        radarResultsView = text(getString(R.string.radar_empty), 12, COLOR_TEXT, false);
        radarResultsView.setPadding(0, dp(10), 0, dp(14));
        compassPanel.addView(radarResultsView);
        radarScanButton = button(
                getString(R.string.stop_radar), COLOR_CARD, COLOR_ACCENT);
        radarScanButton.setOnClickListener(v -> {
            if (scanning && wifiScanning) stopScan();
            else requestCompassLocation();
        });
        compassPanel.addView(radarScanButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        compassView = new CompassView(this);
        compassView.setContentDescription(getString(R.string.compass_accessibility));
        LinearLayout.LayoutParams compassViewParams = new LinearLayout.LayoutParams(
                dp(180), dp(180));
        compassViewParams.gravity = Gravity.CENTER_HORIZONTAL;
        compassPanel.addView(compassView, compassViewParams);
        compassHeadingView = text("> --°", 24, COLOR_ACCENT, true);
        compassHeadingView.setGravity(Gravity.CENTER);
        compassHeadingView.setPadding(0, dp(8), 0, dp(12));
        compassPanel.addView(compassHeadingView);
        locationView = text(getString(R.string.gps_waiting), 13, COLOR_TEXT, false);
        locationView.setPadding(0, 0, 0, dp(12));
        compassPanel.addView(locationView);
        gpsCourseView = text(getString(R.string.gps_course_waiting), 13, COLOR_TEXT, false);
        gpsCourseView.setPadding(0, 0, 0, dp(12));
        compassPanel.addView(gpsCourseView);
        TextView compassNote = text(getString(R.string.compass_gps_note), 12, COLOR_MUTED, false);
        compassPanel.addView(compassNote);
        root.addView(compassPanel);

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
                "> RSSI DISTANCE IS AN ESTIMATE. CALIBRATE AT A KNOWN DISTANCE.",
                11,
                COLOR_MUTED,
                false);
        note.setPadding(0, dp(18), 0, dp(6));
        root.addView(note);
        Button calibrateDistanceButton = button(
                "[ CALIBRATE DISTANCE ]", COLOR_CARD, COLOR_ACCENT);
        calibrateDistanceButton.setOnClickListener(v -> showDistanceCalibration());
        root.addView(calibrateDistanceButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        updateModeViews();
    }

    private void showMode(int mode) {
        if (currentMode == mode) return;
        if (scanning || wifiScanning) stopScan();
        permissionRequestMode = -1;
        currentMode = mode;
        updateModeViews();
        if (mode == MODE_COMPASS) {
            startCompassSensors();
            requestCompassLocation();
        } else {
            stopCompassSensors();
            stopLocationUpdates();
        }
    }

    private void updateModeViews() {
        compassPanel.setVisibility(currentMode == MODE_COMPASS ? View.VISIBLE : View.GONE);
        wifiPanel.setVisibility(currentMode == RadioScanService.MODE_WIFI
                ? View.VISIBLE : View.GONE);
        bluetoothPanel.setVisibility(currentMode == RadioScanService.MODE_BLUETOOTH
                ? View.VISIBLE : View.GONE);
        compassModeButton.setBackgroundTintList(ColorStateList.valueOf(
                currentMode == MODE_COMPASS ? COLOR_ACCENT : COLOR_CARD));
        compassModeButton.setTextColor(
                currentMode == MODE_COMPASS ? Color.BLACK : COLOR_TEXT);
        wifiModeButton.setBackgroundTintList(ColorStateList.valueOf(
                currentMode == RadioScanService.MODE_WIFI ? COLOR_ACCENT : COLOR_CARD));
        wifiModeButton.setTextColor(
                currentMode == RadioScanService.MODE_WIFI ? Color.BLACK : COLOR_TEXT);
        bluetoothModeButton.setBackgroundTintList(ColorStateList.valueOf(
                currentMode == RadioScanService.MODE_BLUETOOTH ? COLOR_ACCENT : COLOR_CARD));
        bluetoothModeButton.setTextColor(
                currentMode == RadioScanService.MODE_BLUETOOTH ? Color.BLACK : COLOR_TEXT);
        if (!scanning && !wifiScanning) {
            if (currentMode == MODE_COMPASS) {
                statusView.setText(R.string.compass_mode_ready);
            } else {
                statusView.setText(currentMode == RadioScanService.MODE_WIFI
                        ? "> WI-FI MODE READY." : "> BLUETOOTH MODE READY.");
            }
        }
    }

    private void requestCompassLocation() {
        List<String> needed = radarPermissionsMissing();
        if (needed.isEmpty()) {
            startScan(RadioScanService.MODE_RADAR);
            startLocationUpdates();
            return;
        }
        if (permissionRequestMode != -1) {
            statusView.setText(R.string.permission_request_pending);
            return;
        }
        permissionRequestMode = MODE_COMPASS;
        requestPermissions(needed.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    private List<String> radarPermissionsMissing() {
        List<String> needed = new ArrayList<>();
        addNotificationPermissionIfNeeded(needed);
        if (Build.VERSION.SDK_INT >= 31) {
            addIfMissing(needed, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(needed, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(needed, Manifest.permission.ACCESS_COARSE_LOCATION);
            addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(needed, Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        addIfMissing(needed, Manifest.permission.ACCESS_COARSE_LOCATION);
        addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION);
        return needed;
    }

    private boolean hasRadarPermissions() {
        boolean bluetoothGranted;
        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothGranted = checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            bluetoothGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        boolean wifiGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED);
        return bluetoothGranted && wifiGranted;
    }

    private void startCompassSensors() {
        if (sensorManager == null || rotationSensor == null) {
            compassHeadingView.setText(R.string.compass_sensor_gps_fallback);
            compassView.setContentDescription(getString(R.string.compass_sensor_gps_fallback));
            statusView.setText(R.string.compass_sensor_gps_fallback);
            return;
        }
        if (!compassSensorRegistered) {
            compassSensorRegistered = sensorManager.registerListener(
                    compassSensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        if (!compassSensorRegistered) {
            compassHeadingView.setText(R.string.compass_sensor_gps_fallback);
            compassView.setContentDescription(getString(R.string.compass_sensor_gps_fallback));
            statusView.setText(R.string.compass_sensor_gps_fallback);
        }
    }

    private void stopCompassSensors() {
        if (sensorManager != null && compassSensorRegistered) {
            sensorManager.unregisterListener(compassSensorListener);
        }
        compassSensorRegistered = false;
    }

    private void startLocationUpdates() {
        if (currentMode != MODE_COMPASS || locationUpdatesRegistered) return;
        if (locationManager == null) {
            locationView.setText(R.string.gps_unavailable);
            return;
        }
        preciseLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!preciseLocation
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationView.setText(R.string.gps_permission_required);
            gpsCourseView.setText(R.string.gps_course_waiting);
            return;
        }
        gpsCourseView.setText(R.string.gps_course_waiting);
        try {
            String provider = preciseLocation
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            Location lastKnown = locationManager.getLastKnownLocation(provider);
            if (lastKnown != null
                    && System.currentTimeMillis() - lastKnown.getTime() <= 120_000L) {
                updateLocation(lastKnown);
            } else {
                locationView.setText(R.string.gps_waiting);
            }
            if (!locationManager.isProviderEnabled(provider)) {
                locationView.setText(R.string.gps_enable_location);
                return;
            }
            if (locationListener == null) {
                locationListener = this::updateLocation;
            }
            locationManager.requestLocationUpdates(
                    provider, 2000, 5, locationListener);
            locationUpdatesRegistered = true;
            if (lastKnown == null) locationView.setText(R.string.gps_waiting);
        } catch (SecurityException exception) {
            locationView.setText(R.string.gps_permission_required);
        } catch (IllegalArgumentException exception) {
            locationView.setText(R.string.gps_unavailable);
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null && locationUpdatesRegistered) {
            locationManager.removeUpdates(locationListener);
        }
        locationUpdatesRegistered = false;
    }

    private void updateLocation(Location location) {
        if (currentMode != MODE_COMPASS || locationView == null) return;
        long age = System.currentTimeMillis() - location.getTime();
        if (age < 0 || age > 120_000L) return;
        locationView.setText(getString(
                preciseLocation ? R.string.gps_coordinates : R.string.approximate_coordinates,
                location.getLatitude(), location.getLongitude()));
        if (preciseLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && location.hasSpeed() && location.getSpeed() >= 0.5f
                && location.hasBearing()) {
            float bearing = location.getBearing();
            String[] directions = getResources().getStringArray(R.array.compass_directions);
            int index = Math.round(bearing / 22.5f) % directions.length;
            int roundedBearing = Math.round(bearing) % 360;
            if (rotationSensor == null || !compassSensorRegistered) {
                compassView.setGpsCourse(bearing);
                compassHeadingView.setText(getString(
                        R.string.gps_course_heading, directions[index], roundedBearing));
                compassView.setContentDescription(getString(
                        R.string.gps_course_accessibility, directions[index], roundedBearing));
            }
            gpsCourseView.setText(getString(
                    R.string.gps_course, directions[index], roundedBearing));
        } else {
            if (rotationSensor == null || !compassSensorRegistered) {
                compassView.clearHeading();
                compassHeadingView.setText(R.string.compass_sensor_gps_fallback);
                compassView.setContentDescription(
                        getString(R.string.compass_sensor_gps_fallback));
            }
            gpsCourseView.setText(R.string.gps_course_waiting);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateCompassHeading() {
        if (compassView == null || Float.isNaN(compassHeading)) return;
        compassView.setHeading(compassHeading);
        String[] directions = getResources().getStringArray(R.array.compass_directions);
        int index = Math.round(compassHeading / 22.5f) % directions.length;
        int roundedHeading = Math.round(compassHeading) % 360;
        compassHeadingView.setText(
                getString(R.string.compass_heading, directions[index], roundedHeading));
        compassView.setContentDescription(getString(
                R.string.compass_accessibility_heading, directions[index], roundedHeading));
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
        if (requestedMode == MODE_COMPASS) {
            if (currentMode != MODE_COMPASS) {
                statusView.setText(R.string.permission_mode_changed);
            } else if (hasRadarPermissions()) {
                notificationPermissionMissing = Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED;
                startScan(RadioScanService.MODE_RADAR);
                startLocationUpdates();
            } else {
                statusView.setText(R.string.radar_permission_denied);
            }
            return;
        }
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
                        : mode == RadioScanService.MODE_RADAR
                        ? RadioScanService.ACTION_START_RADAR
                        : RadioScanService.ACTION_START_BLUETOOTH);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            statusView.setText(mode == RadioScanService.MODE_WIFI
                    ? R.string.wifi_monitor_active
                    : mode == RadioScanService.MODE_RADAR
                    ? R.string.radar_scanning : R.string.scanning);
        } catch (SecurityException | IllegalStateException exception) {
            statusView.setText(mode == RadioScanService.MODE_WIFI
                    ? R.string.wifi_scan_unavailable
                    : mode == RadioScanService.MODE_RADAR
                    ? R.string.radar_scan_unavailable : R.string.bluetooth_scan_unavailable);
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
                || updateMode == RadioScanService.MODE_BLUETOOTH
                || updateMode == RadioScanService.MODE_RADAR) {
            Map<String, RadioItem> items = updateMode == RadioScanService.MODE_WIFI
                    ? wifiItems
                    : updateMode == RadioScanService.MODE_BLUETOOTH
                    ? bluetoothItems : radarItems;
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
            if (updateMode == RadioScanService.MODE_RADAR) {
                renderRadarItems();
            } else if (updateMode == RadioScanService.MODE_WIFI) {
                renderItems(wifiList, wifiItems, true);
                wifiCountView.setText(getString(R.string.wifi_network_count, wifiItems.size()));
            } else {
                renderItems(bluetoothList, bluetoothItems, false);
                bluetoothCountView.setText(
                        getString(R.string.bluetooth_device_count, bluetoothItems.size()));
            }
        }
        if (active && (updateMode == RadioScanService.MODE_WIFI
                || updateMode == RadioScanService.MODE_BLUETOOTH
                || updateMode == RadioScanService.MODE_RADAR)) {
            activeMode = updateMode;
            scanning = updateMode == RadioScanService.MODE_BLUETOOTH
                    || updateMode == RadioScanService.MODE_RADAR;
            wifiScanning = updateMode == RadioScanService.MODE_WIFI
                    || updateMode == RadioScanService.MODE_RADAR;
            if (currentMode != updateMode && currentMode != MODE_COMPASS) {
                currentMode = updateMode;
                updateModeViews();
            }
        } else {
            if (activeMode == RadioScanService.MODE_BLUETOOTH) scanning = false;
            if (activeMode == RadioScanService.MODE_WIFI) wifiScanning = false;
            if (activeMode == RadioScanService.MODE_RADAR) {
                scanning = false;
                wifiScanning = false;
            }
            activeMode = RadioScanService.MODE_NONE;
        }
        scanButton.setText(scanning
                ? R.string.stop_scanning : R.string.start_scanning);
        wifiScanButton.setText(wifiScanning
                ? R.string.stop_wifi_monitor : R.string.start_wifi_monitor);
        radarScanButton.setText(scanning && wifiScanning
                ? R.string.stop_radar : R.string.start_radar);
        if (currentMode == MODE_COMPASS) {
            if (active && notificationPermissionMissing) {
                statusView.setText(R.string.notification_permission_missing);
            } else if (active) {
                statusView.setText(statusCount >= 0
                        ? getString(status, statusCount) : getString(status));
            } else {
                statusView.setText(R.string.radar_stopped);
            }
        } else {
            if (active && notificationPermissionMissing) {
                statusView.setText(R.string.notification_permission_missing);
            } else {
                statusView.setText(statusCount >= 0
                        ? getString(status, statusCount) : getString(status));
            }
        }
    }

    private void renderRadarItems() {
        List<RadioItem> items = new ArrayList<>(radarItems.values());
        int wifiCount = 0;
        int bluetoothCount = 0;
        Collections.sort(items,
                (first, second) -> Integer.compare(second.rssi, first.rssi));
        StringBuilder summary = new StringBuilder();
        int visibleCount = Math.min(items.size(), 40);
        for (int i = 0; i < items.size(); i++) {
            RadioItem item = items.get(i);
            boolean wifi = RadarDeviceKey.isWifi(item.key);
            if (wifi) wifiCount++;
            else bluetoothCount++;
            if (i < visibleCount) {
                summary.append(wifi ? "WIFI  " : "BT    ")
                        .append(item.name)
                        .append(" · ").append(item.rssi).append(" dBm\n");
            }
        }
        radarView.setItems(items);
        radarCountView.setText(getString(
                R.string.radar_count, wifiCount, bluetoothCount));
        if (summary.length() == 0) {
            radarResultsView.setText(R.string.radar_empty);
        } else {
            if (items.size() > visibleCount) {
                summary.append(getString(
                        R.string.radar_more_results, items.size() - visibleCount));
            }
            radarResultsView.setText(summary.toString().trim());
        }
    }

    private void renderItems(
            LinearLayout container, Map<String, RadioItem> items, boolean wifi) {
        if (container == null) return;
        container.removeAllViews();
        container.addView(text(getString(R.string.copy_result_hint), 11, COLOR_MUTED, false));
        List<RadioItem> sorted = new ArrayList<>(items.values());
        Collections.sort(sorted,
                (first, second) -> first.name.compareToIgnoreCase(second.name));
        for (RadioItem item : sorted) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(COLOR_CARD);
            card.setClickable(true);
            card.setOnClickListener(v -> copyItemDetails(item, wifi));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            container.addView(card, cardParams);

            card.addView(text(item.name, 15, COLOR_TEXT, true));
            String signal = wifi
                    ? item.signalPercent + "% signal · " + item.rssi + " dBm"
                    : item.rssi + " dBm RSSI";
            card.addView(text(signal + " · " + estimateDistance(item.rssi, wifi),
                    12, COLOR_ACCENT, false));
            card.addView(text(wifi ? item.detail + " · " + item.key : item.key,
                    11, COLOR_MUTED, false));
        }
        if (items.isEmpty()) {
            addEmptyState(container, getString(
                    wifi ? R.string.no_wifi_networks : R.string.no_bluetooth_devices));
        }
    }

    private void copyItemDetails(RadioItem item, boolean wifi) {
        String details = item.name + "\n"
                + (wifi ? item.detail + "\n" : "")
                + item.key + "\n"
                + item.rssi + " dBm RSSI";
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.clipboard_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(item.name, details));
        Toast.makeText(
                this, getString(R.string.copied_device_details, item.name), Toast.LENGTH_SHORT)
                .show();
    }

    private String estimateDistance(int rssi, boolean wifi) {
        android.content.SharedPreferences preferences = distancePreferences();
        int referenceRssi = preferences.getInt(
                distancePreferenceKey(REFERENCE_RSSI_KEY, wifi),
                DistanceEstimator.DEFAULT_REFERENCE_RSSI_DBM);
        double referenceDistance = Double.longBitsToDouble(preferences.getLong(
                distancePreferenceKey(REFERENCE_DISTANCE_KEY, wifi),
                Double.doubleToRawLongBits(DistanceEstimator.DEFAULT_REFERENCE_DISTANCE_METERS)));
        double pathLossExponent = Double.longBitsToDouble(preferences.getLong(
                distancePreferenceKey(PATH_LOSS_KEY, wifi),
                Double.doubleToRawLongBits(DistanceEstimator.DEFAULT_PATH_LOSS_EXPONENT)));
        double distance = DistanceEstimator.estimateMeters(
                rssi, referenceRssi, referenceDistance, pathLossExponent);
        return String.format(
                Locale.getDefault(), "≈ %.2f m (estimate)", distance);
    }

    private void showDistanceCalibration() {
        boolean wifi = currentMode == RadioScanService.MODE_WIFI;
        android.content.SharedPreferences preferences = distancePreferences();
        String referenceRssiKey = distancePreferenceKey(REFERENCE_RSSI_KEY, wifi);
        String referenceDistanceKey = distancePreferenceKey(REFERENCE_DISTANCE_KEY, wifi);
        String pathLossKey = distancePreferenceKey(PATH_LOSS_KEY, wifi);
        int referenceRssi = preferences.getInt(
                referenceRssiKey, DistanceEstimator.DEFAULT_REFERENCE_RSSI_DBM);
        double referenceDistance = Double.longBitsToDouble(preferences.getLong(
                referenceDistanceKey,
                Double.doubleToRawLongBits(DistanceEstimator.DEFAULT_REFERENCE_DISTANCE_METERS)));
        double pathLossExponent = Double.longBitsToDouble(preferences.getLong(
                pathLossKey,
                Double.doubleToRawLongBits(DistanceEstimator.DEFAULT_PATH_LOSS_EXPONENT)));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(12), dp(24), 0);
        EditText distanceInput = calibrationInput(
                String.format(Locale.getDefault(), "%.2f", referenceDistance),
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText rssiInput = calibrationInput(
                Integer.toString(referenceRssi),
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText exponentInput = calibrationInput(
                String.format(Locale.getDefault(), "%.1f", pathLossExponent),
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        addCalibrationField(form, getString(R.string.calibration_distance_label), distanceInput);
        addCalibrationField(form, getString(R.string.calibration_rssi_label), rssiInput);
        addCalibrationField(form, getString(R.string.calibration_exponent_label), exponentInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(wifi
                        ? R.string.calibration_title_wifi
                        : R.string.calibration_title_bluetooth)
                .setMessage(R.string.calibration_instructions)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.calibration_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        double distance = Double.parseDouble(
                                normalizeDecimal(distanceInput.getText().toString()));
                        int rssi = Integer.parseInt(rssiInput.getText().toString().trim());
                        double exponent = Double.parseDouble(
                                normalizeDecimal(exponentInput.getText().toString()));
                        if (Double.isNaN(distance) || Double.isInfinite(distance)
                                || distance <= 0 || distance > 1000
                                || rssi < -120 || rssi > 0
                                || Double.isNaN(exponent) || Double.isInfinite(exponent)
                                || exponent < 1 || exponent > 6) {
                            throw new IllegalArgumentException();
                        }
                        preferences.edit()
                                .putInt(referenceRssiKey, rssi)
                                .putLong(referenceDistanceKey,
                                        Double.doubleToRawLongBits(distance))
                                .putLong(pathLossKey,
                                        Double.doubleToRawLongBits(exponent))
                                .apply();
                        renderItems(wifiList, wifiItems, true);
                        renderItems(bluetoothList, bluetoothItems, false);
                        dialog.dismiss();
                    } catch (IllegalArgumentException exception) {
                        Toast.makeText(
                                this, R.string.calibration_invalid, Toast.LENGTH_SHORT).show();
                    }
                }));
        dialog.show();
    }

    private android.content.SharedPreferences distancePreferences() {
        return getSharedPreferences(DISTANCE_PREFERENCES, MODE_PRIVATE);
    }

    private String distancePreferenceKey(String key, boolean wifi) {
        return (wifi ? "wifi_" : "bluetooth_") + key;
    }

    private EditText calibrationInput(String value, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setText(value);
        return input;
    }

    private String normalizeDecimal(String value) {
        return value.trim().replace(',', '.');
    }

    private void addCalibrationField(LinearLayout form, String label, EditText input) {
        form.addView(text(label, 12, COLOR_TEXT, false));
        form.addView(input);
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
