package com.datecs.lineademo;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.datecs.android.hardware.usb.LineaConnection;
import com.datecs.android.hardware.usb.LineaManager;
import com.datecs.barcode.Barcode;
import com.datecs.barcode.Intermec;
import com.datecs.barcode.Newland;
import com.datecs.linea.LineaPro;
import com.datecs.linea.LineaProException;
import com.datecs.linea.LineaProInformation;
import com.datecs.lineademo.util.MediaUtil;
import com.datecs.lineademo.view.StatusView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements LineaPro.BarcodeListener, LineaPro
        .ButtonListener, LineaAction {

    private static final boolean DEBUG = false;

    private static final int UPDATE_BATTERY_TIME = 5000;

    public interface LineaRunnable {
        void run(LineaPro linea) throws IOException;
    }

    private final LineaConnection mConnection = new LineaConnection() {
        @Override
        public void onLineaConnected(LineaPro linea) {
            MediaUtil.playSound(MainActivity.this, R.raw.connect);
            mStatusView.hide();
            mLineaPro = linea;
            mLineaPro.setBarcodeListener(MainActivity.this);
            mLineaPro.setButtonListener(MainActivity.this);
            mUpdateHandler.removeCallbacksAndMessages(null);
            mUpdateHandler.post(mUpdateRunnable);
            initLinea();
        }

        @Override
        public void onLineaDisconnected(LineaPro linea) {
            MediaUtil.playSound(MainActivity.this, R.raw.disconnect);
            mStatusView.show(R.drawable.usb_unplugged);
            mMainFragment.resetBattery();
            mUpdateHandler.removeCallbacksAndMessages(null);
        }

        @Override
        public void onLineaDebug(String message) {
            if (DEBUG) {
                mMainFragment.addLog(message);
            }
        }
    };

    private class UpdateRunnable implements Runnable {
        @Override
        public void run() {
            final boolean isCharging = isCharging();

            // Update battery information
            runAsync(new LineaRunnable() {
                @Override
                public void run(LineaPro linea) throws IOException {
                    final LineaPro.BatteryInfo batteryInfo = linea.getBatteryInfo();
                    final boolean isBatteryCharge = linea.isBatteryChargeEnabled();
                    final boolean wantBatteryCharge = mPrefs.getBoolean("battery_charge", false);

                    // Enable battery charging
                    if (!isCharging && !isBatteryCharge && wantBatteryCharge) {
                        linea.enableBatteryCharge(true);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mMainFragment.updateBattery(batteryInfo);
                        }
                    });
                }
            }, false);

            mUpdateHandler.postDelayed(this, UPDATE_BATTERY_TIME);
        }
    }

    private final Handler mUpdateHandler = new Handler();
    private final UpdateRunnable mUpdateRunnable = new UpdateRunnable();

    private SharedPreferences mPrefs;
    private LineaPro mLineaPro;
    private LineaManager mLineaManager;
    private MainFragment mMainFragment;
    private StatusView mStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.content, mMainFragment = new MainFragment());
        fragmentTransaction.commit();

        mStatusView = (StatusView) findViewById(R.id.status_pane);
        mStatusView.show(R.drawable.usb_unplugged);

        mLineaManager = new LineaManager();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mLineaManager.bindService(this, mConnection);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLineaManager.unbindService(this);
        mUpdateHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.clear_log:
                mMainFragment.clearLog();
                break;
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        final FragmentManager fragmentManager = getFragmentManager();

        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onReadBarcode(final Barcode barcode) {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                if (mPrefs.getBoolean("beep_upon_scan", false)) {
                    linea.beep(100, new int[]{2730, 150, 65000, 20, 2730, 150});
                }

                if (mPrefs.getBoolean("vibrate_upon_scan", false)) {
                    linea.startVibrator(500);
                }
            }
        }, false);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMainFragment.addLog("<I>Barcode: (" + barcode.getTypeString() + ") " + barcode
                        .getDataString());
            }
        });
    }

    @Override
    public void onButtonStateChanged(int index, final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (state) {
                    mStatusView.show(R.drawable.barcode);
                } else {
                    mStatusView.hide();
                }
            }
        });
    }

    @Override
    public void actionResetBarcodeEngine() {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                linea.bcRestoreDefaultMode();
            }
        }, true);
    }

    @Override
    public void actionUpdateSetting(final String key, final String value) {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                updateSetting(linea, key, value);
            }
        }, true);
    }

    @Override
    public void actionSetLed(final boolean red, final boolean green, final boolean blue) {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                final LineaProInformation info = linea.getInformation();

                if (info.hasLED()) {
                    linea.setLED(red, green, blue);
                }
            }
        }, true);
    }

    @Override
    public void actionUpdateFirmware(final String path, final int mode) {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                File file = new File(path);
                byte[] data = new byte[(int) file.length()];
                FileInputStream fis = new FileInputStream(file);
                if (fis.read(data) != data.length) {
                    throw new IOException("Can't read firmware file");
                }
                fis.close();

                LineaProInformation info = linea.getInformation();
                switch (mode) {
                    case 0:
                        linea.fwUpdate(data);
                        break;
                    case 1:
                        if (info.hasIntermecEngine()) {
                            Intermec engine = (Intermec) mLineaPro.bcGetEngine();
                            engine.updateFirmware(data, null);
                        } else {
                            warn("Barcode update is not supported");
                        }
                        break;
                    default: {
                        warn("Unsupported firmware update " + mode);
                    }
                }
            }
        }, true);
    }

    @Override
    public void actionTurnOff() {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                linea.turnOff();
            }
        }, false);
    }

    @Override
    public void actionStartScan() {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                linea.bcStartScan();
            }
        }, false);
    }

    @Override
    public void actionStopScan() {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                linea.bcStopScan();
            }
        }, false);
    }

    @Override
    public void actionReadInformation() {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                readInformation(linea);
            }
        }, false);
    }

    private void warn(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMainFragment.addLog("<W>" + text);
            }
        });
    }

    private void fail(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMainFragment.addLog("<E>" + text);
            }
        });
    }

    private void runAsync(final LineaRunnable r, final boolean showProgress) {
        if (showProgress) {
            mStatusView.show(R.drawable.process);
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (mLineaPro != null) {
                    try {
                        try {
                            r.run(mLineaPro);
                        } catch (LineaProException e) {
                            e.printStackTrace();
                            warn("Linea error: " + e.getMessage());
                        } catch (IOException e) {
                            e.printStackTrace();
                            fail("I/O error: " + e.getMessage());
                        } catch (Exception e) {
                            e.printStackTrace();
                            fail("Critical error: " + e.getMessage());
                        }
                    } finally {
                        if (showProgress) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mStatusView.hide();
                                }
                            });
                        }
                    }
                }
            }
        });
        thread.start();
    }

    private void initLinea() {
        runAsync(new LineaRunnable() {
            @Override
            public void run(LineaPro linea) throws IOException {
                readInformation(linea);
                linea.bcStopScan();
                linea.bcStopBeep();

                updateSetting(linea, "scan_button");
                updateSetting(linea, "battery_charge");
                updateSetting(linea, "power_max_current");
                updateSetting(linea, "external_speaker");
                updateSetting(linea, "external_speaker_button");
                updateSetting(linea, "device_timeout_period");
                updateSetting(linea, "code128_symbology");
                updateSetting(linea, "barcode_scan_mode");
                updateSetting(linea, "barcode_scope_scale_mode");
            }
        }, true);
    }

    private void updateSetting(LineaPro linea, String key, String value) throws IOException {
        final LineaProInformation info = linea.getInformation();

        if ("scan_button".equals(key)) {
            boolean enabled = Boolean.parseBoolean(value);
            linea.enableScanButton(enabled);
        } else if ("battery_charge".equals(key)) {
            // Do not enable battery charge until device is in charging state.
            boolean enabled = Boolean.parseBoolean(value);
            if (!enabled) {
                linea.enableBatteryCharge(false);
            }
        } else if ("power_max_current".equals(key)) {
            boolean enabled = Boolean.parseBoolean(value);
            linea.enableMaxCurrent(enabled);
        } else if ("external_speaker".equals(key)) {
            if (info.hasExternalSpeaker()) {
                boolean enabled = Boolean.parseBoolean(value);
                linea.enableExternalSpeaker(enabled);
            }
        } else if ("external_speaker_button".equals(key)) {
            if (info.hasExternalSpeaker()) {
                boolean enabled = Boolean.parseBoolean(value);
                linea.enableExternalSpeakerButton(enabled);
            }
        } else if ("device_timeout_period".equals(key)) {
            int autoOffTimeIndex = Integer.parseInt(value);
            if (autoOffTimeIndex == 0) {
                linea.setAutoOffTime(true, 30000);
            } else {
                linea.setAutoOffTime(true, 60000);
            }

        } else if ("code128_symbology".equals(key)) {
            if (info.hasIntermecEngine()) {
                Intermec engine = (Intermec) linea.bcGetEngine();
                if (engine != null) {
                    boolean enabled = Boolean.parseBoolean(value);
                    engine.enableCode128(enabled);
                }
            }
        } else if ("barcode_scan_mode".equals(key)) {
            int scanMode = Integer.parseInt(value);
            linea.bcSetMode(scanMode);
        } else if ("barcode_scope_scale_mode".equals(key)) {
            if (info.hasNewlandEngine()) {
                int scopeScaleMode = Integer.parseInt(value);
                Newland engine = (Newland) linea.bcGetEngine();
                if (scopeScaleMode > 0) {
                    engine.turnOnScopeScaling(scopeScaleMode);
                } else {
                    engine.turnOffScopeScaling();
                }
            }
        }
    }

    private void updateSetting(LineaPro linea, String key) throws IOException {
        Object item = mPrefs.getAll().get(key);
        String value = item != null ? item.toString() : null;
        if (value != null) {
            updateSetting(linea, key, value);
        }
    }

    private void readInformation(LineaPro linea) throws IOException {
        // Read linea pro information
        final LineaProInformation info = linea.getInformation();
        final Object barcodeEngine = linea.bcGetEngine();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMainFragment.addLog("<I>" + info.getName() + " " +
                        info.getModel() + " " + info.getVersion() + " " +
                        info.getSerialNumber());

                String barcodeIdent = "";
                if (info.hasIntermecEngine()) {
                    barcodeIdent = "Intermec";
                    try {
                        Intermec engine = (Intermec) barcodeEngine;
                        barcodeIdent += " " + engine.getIdent();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (info.hasNewlandEngine()) {
                    barcodeIdent = "Newland Barcode Engine";
                }
                mMainFragment.addLog("<I>" + barcodeIdent);
            }
        });

    }

    private boolean isCharging() {
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, batteryFilter);
        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        return isCharging;
    }
}
