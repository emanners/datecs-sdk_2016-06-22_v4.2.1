package com.datecs.android.hardware.usb;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.datecs.hub.Hub;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Created by lkm on 16.5.2016 г..
 */
public class HubManager {

    private class HubReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (HubService.ACTION_ACCESSORY_DEBUG.equals(action)) {
                debugHub(intent.getStringExtra(Intent.EXTRA_TEXT));
            } else if (HubService.ACTION_ACCESSORY_ATTACHED.equals(action)) {
                connectHub();
            } else if (HubService.ACTION_ACCESSORY_DETACHED.equals(action)) {
                disconnectHub(true);
            }
        }
    }

    private final HubReceiver mReceiver = new HubReceiver();

    private HubConnection mConnection;

    private Socket mClientSocket;

    private Hub mHub;

    public HubManager() {
        // Hub.setDebug(true);
    }

    private void debugHub(String message) {
        mConnection.onHubDebug(message);
    }

    private void connectHub() {
        mHub = null;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                SocketAddress address = new InetSocketAddress("127.0.0.1", HubService.SERVER_PORT);

                try {
                    mClientSocket = new Socket();
                    mClientSocket.setTcpNoDelay(true);
                    mClientSocket.connect(address, 1000);
                    if (mClientSocket.isConnected()) {
                        mHub = new Hub(mClientSocket.getInputStream(),
                                mClientSocket.getOutputStream());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    synchronized (HubManager.this) {
                        HubManager.this.notify();
                    }
                }
            }
        });
        thread.start();

        synchronized (HubManager.this) {
            try {
                HubManager.this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (mHub != null) {
            mConnection.onHubConnected(mHub);
        } else {
            // Nothing to do here
        }
    }

    private void disconnectHub(boolean notify) {
        if (mClientSocket != null) {
            try {
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mHub != null) {
            try {
                mHub.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (notify) {
                mConnection.onHubDisconnected(mHub);
            }

            mHub = null;
        }
    }

    public void bindService(Activity activity, HubConnection connection) {
        IntentFilter intentFilter = new IntentFilter(HubService.ACTION_ACCESSORY_ATTACHED);
        intentFilter.addAction(HubService.ACTION_ACCESSORY_DETACHED);
        intentFilter.addAction(HubService.ACTION_ACCESSORY_DEBUG);
        activity.registerReceiver(mReceiver, intentFilter);
        mConnection = connection;
        connectHub();
    }

    public void unbindService(Activity activity) {
        activity.unregisterReceiver(mReceiver);
        disconnectHub(true);
    }
}
