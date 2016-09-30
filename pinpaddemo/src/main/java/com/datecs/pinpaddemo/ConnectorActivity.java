package com.datecs.pinpaddemo;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.datecs.pinpaddemo.connectivity.AbstractConnector;
import com.datecs.pinpaddemo.connectivity.BluetoothHelper;
import com.datecs.pinpaddemo.connectivity.NetworkConnector;
import com.datecs.pinpaddemo.widget.ConnectorAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectorActivity extends AppCompatActivity implements SwipeRefreshLayout
        .OnRefreshListener, ConnectorAdapter.OnItemClickListener  {

    private static final int REQUEST_PINPAD = 0;
    private static final int REQUEST_ENABLE_BT = 1;

    private static final String PREF_HOST_LIST = "hosts";

    private SharedPreferences mPreferences;
    private RecyclerView mConnectorView;
    private SwipeRefreshLayout mSwipeLayout;
    private List<AbstractConnector> mConnectorList;
    private ConnectorAdapter mConnectorAdapter;

    private BluetoothHelper bluetoothHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connector);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mConnectorList = new ArrayList<>();
        mConnectorAdapter = new ConnectorAdapter(mConnectorList, this);

        mConnectorView = (RecyclerView) findViewById(R.id.list);
        mConnectorView.setAdapter(mConnectorAdapter);

        ItemTouchHelper.Callback callback = new ConnectorSwipeHelper(mConnectorAdapter);
        ItemTouchHelper helper = new ItemTouchHelper(callback);
        helper.attachToRecyclerView(mConnectorView);

        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        assert mSwipeLayout != null;
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        // When activity is started from application launcher probably we want Bluetooth to
        // be enabled.
        bluetoothHelper = new BluetoothHelper(this);
        bluetoothHelper.enableBluetooth();
        bluetoothHelper.receiverSetup();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister receivers.
        bluetoothHelper.receiverTearDown();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == BluetoothHelper.REQUEST_ENABLE_BT) {
            bluetoothHelper.init();
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

        /*if (id == R.id.action_add_network_connection) {
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
                                    mConnectorView.smoothScrollToPosition(0);
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
        }*/

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

    }



    public void fail(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

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
