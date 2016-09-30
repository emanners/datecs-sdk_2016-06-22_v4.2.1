package com.datecs.hubdemo;

import android.app.ProgressDialog;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.datecs.android.hardware.usb.HubConnection;
import com.datecs.android.hardware.usb.HubManager;
import com.datecs.android.view.LogView;
import com.datecs.hub.Hub;
import com.datecs.hub.HubException;
import com.datecs.hub.SlaveConnection;
import com.datecs.printer.Printer;
import com.datecs.printer.ProtocolAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements SlaveConnection,
        SlaveAdapter.OnItemClickListener {

    private static final boolean DEBUG = true;

    public interface HubRunnable {
        void run(Hub hub, ProgressDialog dialog) throws IOException;
    }

    private HubConnection mConnection = new HubConnection() {
        @Override
        public void onHubConnected(Hub hub) {
            mStateView.setVisibility(View.INVISIBLE);
            mHub = hub;
            mHub.setSlaveConnectionListener(MainActivity.this);
            updateDevices();
        }

        @Override
        public void onHubDisconnected(Hub hub) {
            mStateView.setVisibility(View.VISIBLE);
            mSlaveAdapter.clear();
        }

        @Override
        public void onHubDebug(String message) {
            if (DEBUG) {
                mLogView.add(message);
            }
        }
    };

    private View mStateView;
    private LogView mLogView;
    private RecyclerView mSlaveView;
    private SlaveAdapter mSlaveAdapter;

    private HubManager mHubManager;
    private Hub mHub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStateView = findViewById(R.id.connection_state_panel);
        mLogView = (LogView) findViewById(R.id.log_view);
        mSlaveView = (RecyclerView) findViewById(R.id.slave_list);
        mSlaveAdapter = new SlaveAdapter(this);
        mSlaveView.setAdapter(mSlaveAdapter);

        mHubManager = new HubManager();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mHubManager.bindService(this, mConnection);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHubManager.unbindService(this);
    }

    @Override
    public void onSlaveConnected(final int slaveID) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDevices();
            }
        });
    }

    @Override
    public void onSlaveDisconnected(final int slaveID, boolean overCurrent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDevices();
            }
        });
    }

    @Override
    public void onItemClick(SlaveItem item) {
        handleSlave(item);
    }

    public void actionToggleLog(View view) {
        if (mLogView.getVisibility() == View.GONE) {
            mLogView.setVisibility(View.VISIBLE);
            ((Button)view).setText(R.string.action_hide_log);
        } else {
            mLogView.setVisibility(View.GONE);
            ((Button)view).setText(R.string.action_show_log);
        }
    }


    private void warn(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fail(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void runAsync(final HubRunnable r, final String message) {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        if (message != null) {
            progressDialog.setMessage(message);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        r.run(mHub, progressDialog);
                    } catch (HubException e) {
                        e.printStackTrace();
                        warn("Hub error: " + e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                        fail("I/O error: " + e.getMessage());
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("Critical error: " + e.getMessage());
                    }
                } finally {
                    if (message != null) {
                        progressDialog.dismiss();
                    }
                }
            }
        });
        thread.start();
    }

    private void updateDevices() {
        runAsync(new HubRunnable() {
            @Override
            public void run(Hub hub, ProgressDialog dialog) throws IOException {
                // Iterate through all slaves
                for (int id = 0; id < 255; id++) {
                    // Read slave type.
                    int type;
                    try {
                        type = mHub.getSlaveType(id);
                    } catch (HubException e) {
                        break;
                    }

                    // Read slave state
                    int state = mHub.getSlaveState(id);

                    String descriptor = null;
                    if (type == Hub.SLAVE_TYPE_USB && state == Hub.SLAVE_STATE_READY) {
                        descriptor = mHub.getUsbStringDescriptor(id).replace('\0', '\n');

                        final String ss = descriptor;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mLogView.addI(ss);
                            }
                        });
                    }

                    final SlaveItem item = new SlaveItem(id, type, state, descriptor);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mSlaveAdapter.put(item);
                        }
                    });
                }
            }
        }, "Please, wait...");
    }

    private void handleSlave(final SlaveItem item) {
        if (item.isPrinterConnected()) {
            printTestReceipt(item.id);
        }
    }

    private void printTestReceipt(final int slaveID) {
        runAsync(new HubRunnable() {
            @Override
            public void run(Hub hub, ProgressDialog dialog) throws IOException {
                OutputStream outputStream = hub.getDataOutputStream(slaveID);
                InputStream inputStream = hub.getDataInputStream(slaveID);
                ProtocolAdapter adapter = new ProtocolAdapter(inputStream, outputStream);
                Printer printer;
                try {
                    if (adapter.isProtocolEnabled()) {
                        ProtocolAdapter.Channel channel = adapter.getChannel(ProtocolAdapter.CHANNEL_PRINTER);
                        printer = new Printer(channel.getInputStream(), channel.getOutputStream());
                    } else {
                        printer = new Printer(adapter.getRawInputStream(), adapter.getRawOutputStream());
                    }

                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false;

                    final AssetManager assetManager = getApplicationContext().getAssets();
                    final Bitmap bitmap = BitmapFactory.decodeStream(assetManager.open("sample.png"),
                            null, options);
                    final int width = bitmap.getWidth();
                    final int height = bitmap.getHeight();
                    final int[] argb = new int[width * height];
                    bitmap.getPixels(argb, 0, width, 0, 0, width, height);
                    bitmap.recycle();

                    printer.reset();
                    printer.printCompressedImage(argb, width, height, Printer.ALIGN_CENTER, true);
                    printer.feedPaper(110);
                    printer.flush();
                } finally {
                    adapter.close();
                }
            }
        }, "Printing receipt...");
    }
}
