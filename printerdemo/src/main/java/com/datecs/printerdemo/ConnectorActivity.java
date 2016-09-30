package com.datecs.printerdemo;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.datecs.printerdemo.connectivity.AbstractConnector;
import com.datecs.printerdemo.connectivity.BluetoothLeConnector;
import com.datecs.printerdemo.connectivity.BluetoothSppConnector;
import com.datecs.printerdemo.connectivity.NetworkConnector;
import com.datecs.printerdemo.connectivity.UsbDeviceConnector;
import com.datecs.printerdemo.widget.ConnectorAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectorActivity extends AppCompatActivity implements SwipeRefreshLayout
        .OnRefreshListener, ConnectorAdapter.OnItemClickListener  {

    private static final int REQUEST_PINPAD = 0;
    private static final int REQUEST_ENABLE_BT = 1;

    private static final String PREF_HOST_LIST = "hosts";

    private SharedPreferences mPreferences;
    private SwipeRefreshLayout mSwipeLayout;
    private List<AbstractConnector> mConnectorList;
    private ConnectorAdapter mConnectorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connector);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mConnectorList = new ArrayList<>();
        mConnectorAdapter = new ConnectorAdapter(mConnectorList, this);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.list);
        assert recyclerView != null;
        recyclerView.setAdapter(mConnectorAdapter);

        ItemTouchHelper.Callback callback = new ConnectorSwipeHelper(mConnectorAdapter);
        ItemTouchHelper helper = new ItemTouchHelper(callback);
        helper.attachToRecyclerView(recyclerView);

        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        assert mSwipeLayout != null;
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        // When activity is started from application launcher probably we want Bluetooth to
        // be enabled.
        if (Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            enableBluetooth();
        }

        // Register receiver to notify when USB device is detached.
        registerReceiver(mUsbDeviceDetachedReceiver, new IntentFilter(UsbManager
                .ACTION_USB_DEVICE_DETACHED));
        // Register receiver to notify when Bluetooth state is changed.
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBluetoothReceiver, bluetoothFilter);

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister receivers.
        unregisterReceiver(mUsbDeviceDetachedReceiver);
        unregisterReceiver(mBluetoothReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            AbstractConnector connector = new UsbDeviceConnector(this, manager, device);
            mConnectorAdapter.add(0, connector);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            init();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connectivity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_add_network_connection) {
            LayoutInflater inflater = getLayoutInflater();
            final ViewGroup root = null;
            final View dialogView = inflater.inflate(R.layout.fragment_add_network, root);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.action_add_network_connection)
                    .setView(dialogView)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            Context context = getApplicationContext();
                            EditText hostView = (EditText) dialogView.findViewById(R.id.host);
                            EditText portView = (EditText) dialogView.findViewById(R.id.port);
                            NetworkConnector connector = null;
                            try {
                                String host = hostView.getText().toString();
                                int port = Integer.parseInt(portView.getText().toString());
                                connector = new NetworkConnector(context, host, port);
                            } catch (Exception e) {
                                Toast.makeText(getApplicationContext(), "Invalid host or port",
                                        Toast.LENGTH_SHORT).show();
                            }

                            if (connector != null) {
                                Set<String> hosts = mPreferences.getStringSet(PREF_HOST_LIST, new
                                        HashSet<String>());
                                String url = connector.getHost() + ":" + connector.getPort();
                                if (!hosts.contains(url)) {
                                    hosts.add(url);
                                    mPreferences.edit().putStringSet(PREF_HOST_LIST, hosts).apply();
                                    mConnectorAdapter.add(0, connector);
                                }
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });

            AlertDialog alert = builder.create();
            alert.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            adapter.startDiscovery();
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSwipeLayout.setRefreshing(false);
                }
            });
        }
        mConnectorAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(View view, final AbstractConnector item) {
        final ProgressDialog dialog = new ProgressDialog(this);
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
                        fail("Connection error: " + e.getMessage());
                        return;
                    }

                    try {
                        PrinterManager.instance.init(item);
                    } catch (Exception e) {
                        try {
                            item.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        fail("Pinpad error: " + e.getMessage());
                        return;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Context context = getApplicationContext();
                            Intent intent = new Intent(context, PrinterActivity.class);
                            startActivityForResult(intent, REQUEST_PINPAD);
                        }
                    });

                } finally {
                    dialog.dismiss();
                }
            }
        });
        thread.start();
    }

    private void fail(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void init() {
        mConnectorAdapter.clear();

        // Enumerate all network devices.
        Set<String> hostList = mPreferences.getStringSet(PREF_HOST_LIST, new HashSet<String>());
        for (String url : hostList) {
            int delimiter = url.indexOf(":");
            String host = url.substring(0, delimiter > 0 ? delimiter : url.length());
            int port = Integer.parseInt(url.substring(delimiter > 0 ? delimiter + 1 : 0));
            AbstractConnector connector = new NetworkConnector(this, host, port);
            mConnectorAdapter.add(connector);
        }

        // Enumerate USB devices
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager != null) {
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

            for (UsbDevice device : deviceList.values()) {
                if (manager.hasPermission(device)) {
                    AbstractConnector connector = new UsbDeviceConnector(this, manager, device);
                    mConnectorAdapter.add(connector);
                }
            }
        }

        // Enumerate Bluetooth devices
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            Set<BluetoothDevice> boundedDevices = adapter.getBondedDevices();

            for (BluetoothDevice device: boundedDevices) {
                AbstractConnector connector;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                        connector = new BluetoothLeConnector(this, adapter, device);
                    } else {
                        connector = new BluetoothSppConnector(this, adapter, device);
                    }
                } else {
                    connector = new BluetoothSppConnector(this, adapter, device);
                }
                mConnectorAdapter.add(connector);
            }
        }

        mConnectorAdapter.notifyDataSetChanged();
    }

    private void enableBluetooth() {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter != null && !adapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private class UsbDeviceDetachedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device  = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                int position = 0;

                for (AbstractConnector connector: mConnectorList) {
                    if (connector instanceof UsbDeviceConnector) {
                        UsbDeviceConnector usbDeviceConnector = (UsbDeviceConnector)connector;

                        if (usbDeviceConnector.getDevice().equals(device)) {
                            mConnectorAdapter.remove(position);
                            break;
                        }
                    }
                    position++;
                }
            }
        }
    }
    private final UsbDeviceDetachedReceiver mUsbDeviceDetachedReceiver = new UsbDeviceDetachedReceiver();

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
                if (mSwipeLayout.isRefreshing()) {
                    mSwipeLayout.setRefreshing(false);
                }
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
                if (!mConnectorList.contains(connector)) {
                    mConnectorAdapter.add(0, connector);
                }
            }
        }
    }
    private final BluetoothReceiver mBluetoothReceiver = new BluetoothReceiver();

    private class ConnectorSwipeHelper extends ItemTouchHelper.SimpleCallback {
        private ConnectorAdapter mConnectorAdapter;

        public ConnectorSwipeHelper(ConnectorAdapter movieAdapter){
            super(0, ItemTouchHelper.RIGHT);
            this.mConnectorAdapter = movieAdapter;
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            //TODO: Not implemented here
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();
            AbstractConnector connector = mConnectorList.get(position);
            // Removed network device from list
            if (connector instanceof NetworkConnector) {
                NetworkConnector networkConnector = (NetworkConnector)connector;
                Set<String> hostList = mPreferences.getStringSet(PREF_HOST_LIST, new HashSet<String>());
                String url = networkConnector.getHost() + ":" + networkConnector.getPort();
                if (hostList.remove(url)) {
                    mPreferences.edit().putStringSet(PREF_HOST_LIST, hostList).apply();
                }
            }
            mConnectorAdapter.remove(position);
        }
    }
}
