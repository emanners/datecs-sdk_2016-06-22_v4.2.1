package com.datecs.android.hardware.usb;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.datecs.linea.LineaPro;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class LineaManager {

    private class LineaReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (LineaService.ACTION_ACCESSORY_DEBUG.equals(action)) {
                debugLinea(intent.getStringExtra(Intent.EXTRA_TEXT));
            } else if (LineaService.ACTION_ACCESSORY_ATTACHED.equals(action)) {
                connectLinea();
            } else if (LineaService.ACTION_ACCESSORY_DETACHED.equals(action)) {
                disconnectLinea(true);
            }
        }
    }

    private final LineaReceiver mReceiver = new LineaReceiver();

    private LineaConnection mConnection;

    private Socket mClientSocket;

    private LineaPro mLineaPro;

    public LineaManager() {
        // LineaPro.setDebug(true);
    }

    private void debugLinea(String message) {
        mConnection.onLineaDebug(message);
    }

    private void connectLinea() {
        mLineaPro = null;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                SocketAddress address = new InetSocketAddress("127.0.0.1", LineaService.SERVER_PORT);

                try {
                    mClientSocket = new Socket();
                    mClientSocket.setTcpNoDelay(true);
                    mClientSocket.connect(address, 1000);
                    if (mClientSocket.isConnected()) {
                        mLineaPro = new LineaPro(mClientSocket.getInputStream(),
                                mClientSocket.getOutputStream());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    synchronized (LineaManager.this) {
                        LineaManager.this.notify();
                    }
                }
            }
        });
        thread.start();

        synchronized (LineaManager.this) {
            try {
                LineaManager.this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (mLineaPro != null) {
            mConnection.onLineaConnected(mLineaPro);
        } else {
            // Nothing to do here
        }
    }

    private void disconnectLinea(boolean notify) {
        if (mClientSocket != null) {
            try {
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mLineaPro != null) {
            try {
                mLineaPro.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (notify) {
                mConnection.onLineaDisconnected(mLineaPro);
            }

            mLineaPro = null;
        }
    }

    public void bindService(Activity activity, LineaConnection connection) {
        IntentFilter intentFilter = new IntentFilter(LineaService.ACTION_ACCESSORY_ATTACHED);
        intentFilter.addAction(LineaService.ACTION_ACCESSORY_DETACHED);
        intentFilter.addAction(LineaService.ACTION_ACCESSORY_DEBUG);
        activity.registerReceiver(mReceiver, intentFilter);
        mConnection = connection;
        connectLinea();
    }

    public void unbindService(Activity activity) {
        activity.unregisterReceiver(mReceiver);
        disconnectLinea(true);
    }
}
