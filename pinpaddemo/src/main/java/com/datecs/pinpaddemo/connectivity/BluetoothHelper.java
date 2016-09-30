package com.datecs.pinpaddemo.connectivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import com.datecs.pinpaddemo.PinpadManager;
import com.datecs.pinpaddemo.widget.ConnectorAdapter;

import java.io.IOException;
import java.util.Set;

/**
 * Created by pinpad demo on 28/09/2016.
 */

public class BluetoothHelper {

    private ConnectorAdapter mConnectorAdapter;
    public static final int REQUEST_ENABLE_BT = 1;


    private final BluetoothReceiver mBluetoothReceiver = new BluetoothReceiver();

    Activity connectorActivity;

    public BluetoothHelper(Activity connectorActivity) {
        this.connectorActivity = connectorActivity;
    }

    public void enableBluetooth() {

        if (Intent.ACTION_MAIN.equals(connectorActivity.getIntent().getAction())) {
            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            if (adapter != null && !adapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                connectorActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    public void receiverSetup() {

        // Register receiver to notify when Bluetooth state is changed.
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        connectorActivity.registerReceiver(mBluetoothReceiver, bluetoothFilter);

        init();
    }

    public void receiverTearDown() {
        connectorActivity.unregisterReceiver(mBluetoothReceiver);
    }

    public interface ConnectionListener {
        void connected();
    }


    private class BluetoothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        init();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        init();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // Nothing to do here
                System.out.println("Bluetooth discovery is started");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                System.out.println("Bluetooth discovery is finished");
                //if (mSwipeLayout.isRefreshing()) {
                //    mSwipeLayout.setRefreshing(false);
                //}
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                System.out.println("Bluetooth device is found");
                AbstractConnector connector;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                        connector = new BluetoothLeConnector(context, adapter, device);
                    } else {
                        connector = new BluetoothSppConnector(context, adapter, device);
                    }
                } else {
                    connector = new BluetoothSppConnector(context, adapter, device);
                }
                // Do not duplicate devices.
                //if (!mConnectorList.contains(connector)) {
                //    mConnectorAdapter.add(0, connector);
                //    mConnectorView.smoothScrollToPosition(0);
                //}
            }
        }
    }

    public void init() {
        // Enumerate Bluetooth devices
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            Set<BluetoothDevice> boundedDevices = adapter.getBondedDevices();

            AbstractConnector connector = null;

            if (boundedDevices.size() == 1) {

                BluetoothDevice device = boundedDevices.iterator().next();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                        connector = new BluetoothLeConnector(connectorActivity, adapter, device);
                    } else {
                        connector = new BluetoothSppConnector(connectorActivity, adapter, device);
                    }
                } else {
                    connector = new BluetoothSppConnector(connectorActivity, adapter, device);
                }

                connect(connector);

                Log.d("BluetoothHelper", "connected");
            } else if (boundedDevices.size() > 1 ){
                Log.d("BluetoothHelper", "too many connected");
            } else  {
                Log.d("BluetoothHelper", "no devices detected!");
            }
        }
    }

    private void connect(final AbstractConnector item) {
        final ProgressDialog dialog = new ProgressDialog(connectorActivity);
        dialog.setMessage("Connecting to device...");
        dialog.setCancelable(false);
        dialog.show();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        item.connect();
                    } catch (Exception e) {
                        //connectorActivity.fail("Connection error: " + e.getMessage());
                        return;
                    }

                    try {
                        PinpadManager.init(item);
                        connectorActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ConnectionListener connectionListener = (ConnectionListener)connectorActivity;
                            connectionListener.connected();
                        }
                    });

                    } catch (Exception e) {
                        try {
                            item.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        //connectorActivity.fail("Pinpad error: " + e.getMessage());
                        return;
                    }

                    /*connectorActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Context context = connectorActivity.getApplicationContext();
                            Intent intent = new Intent(context, PinpadActivity.class);
                            startActivityForResult(intent, REQUEST_PINPAD);
                        }
                    });*/

                } finally {
                    dialog.dismiss();
                }
            }
        });
        thread.start();
    }

}
