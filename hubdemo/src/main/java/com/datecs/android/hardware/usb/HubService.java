package com.datecs.android.hardware.usb;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class HubService extends Service {

    // Output verbose debug information that can harm productivity
    private static final boolean VERBOSE_DEBUG = true;

    public static final String ACTION_ACCESSORY_DEBUG =
            "com.datecs.android.hardware.ubs.action.ACCESSORY_DEBUG";
    public static final String ACTION_ACCESSORY_ATTACHED =
            "com.datecs.android.hardware.ubs.action.ACCESSORY_ATTACHED";
    public static final String ACTION_ACCESSORY_DETACHED =
            "com.datecs.android.hardware.ubs.action.ACCESSORY_DETACHED";

    public static final int SERVER_PORT = 38006;

    private class AccessoryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // This intent is send when Accessory is detached.
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                debug("<D>Receive ACCESSORY_DETACHED");
                closeAccessory();
            }
        }
    }

    private final AccessoryReceiver mAccessoryReceiver = new AccessoryReceiver();

    private final Handler mHandler = new Handler();

    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private InputStream mAccessoryInputStream;
    private OutputStream mAccessoryOutputStream;
    private Socket mClientSocket;
    private InputStream mClientInputStream;
    private OutputStream mClientOutputStream;

    @Override
    public void onCreate() {
        super.onCreate();

        debug("<D>Service is started");

        IntentFilter accessoryFilter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mAccessoryReceiver, accessoryFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mAccessoryReceiver);

        closeAccessory();

        debug("<D>Service is stopped");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private String byteArrayToHexString(byte[] buffer, int offset, int length) {
        char[] tmp = new char[length * 3];

        for (int i = 0, j = 0; i < length; i++) {
            int a = (buffer[offset + i] & 0xff) >> 4;
            int b = (buffer[offset + i] & 0x0f);
            tmp[j++] = (char)(a < 10 ? '0' + a : 'A' + a - 10);
            tmp[j++] = (char)(b < 10 ? '0' + b : 'A' + b - 10);
            tmp[j++] = ' ';
        }

        return new String(tmp);
    }

    private void debug(String message) {
        Intent intent = new Intent(ACTION_ACCESSORY_DEBUG);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        sendBroadcast(intent);
    }

    private void debug(String message, byte[] buffer, int offset, int length) {
        if (VERBOSE_DEBUG) {
            String dataString = byteArrayToHexString(buffer, offset, length) + "(" + length + ")";
            debug(message + dataString);
        }
    }

    private void sendAction(String action) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            debug("<D>Receive ACCESSORY_ATTACHED");

            final UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory != null) {
                openAccessory(accessory);
            } else {
                debug("<E>Invalid USB accessory");
            }
        }
    }

    private void openAccessory(UsbAccessory accessory) {
        if (mFileDescriptor != null) {
            if (accessory == mAccessory) {
                debug("<W>This accessory is already connected");
            } else {
                debug("<W>Already connected?");
            }
            return;
        }

        try {
            debug("<D>Open accessory");
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            mFileDescriptor = usbManager.openAccessory(accessory);

            if (mFileDescriptor != null) {
                FileDescriptor fd = mFileDescriptor.getFileDescriptor();
                mAccessoryInputStream = new FileInputStream(fd);
                mAccessoryOutputStream = new FileOutputStream(fd);
                mAccessory = accessory;

                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        debug("<D>Send initialization data");
                        try {
                            byte[] command = new byte[]{'>', 0x02, 0x00, 0x00, 0x00, 0x01};
                            mAccessoryOutputStream.write(command);
                            mAccessoryOutputStream.flush();
                            debug("<D>Send: ", command, 0, command.length);
                        } catch (IOException e) {
                            debug("<E>Send failed: " + e.getMessage());
                            stopSelf();
                            return;
                        }

                        startReader(mAccessoryInputStream);
                    }
                }, 1500);

                debug("<D>Accessory is opened");
            } else {
                debug("<E>Open accessory failed: null");
            }
        } catch (Exception e) {
            e.printStackTrace();
            debug("<E>Open accessory failed: " + e.getMessage());
        }
    }

    private void closeAccessory() {
        debug("<D>Close accessory");

        mHandler.removeCallbacksAndMessages(null);

        if (mAccessoryInputStream != null) {
            try {
                mAccessoryInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mAccessoryOutputStream != null) {
            try {
                mAccessoryOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mFileDescriptor != null) {
            try {
                mFileDescriptor.close();
            } catch (IOException e) {
                debug("<E>Close accessory failed: " + e.getMessage());
            }
            mFileDescriptor = null;

            debug("<D>Accessory is closed");
        }
    }

    private void startReader(final InputStream inputStream) {
        if (inputStream == null) {
            return;
        }

        debug("<D>Start reader");

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[2048];
                ServerSocket serverSocket = null;
                boolean active = true;

                try {
                    while (active) {
                        // debug("<D>Read bytes...");
                        int bytes = inputStream.read(buffer);
                        // debug("<D>Read completed");
                        if (bytes > 0) {
                            debug("<D>Recv: ", buffer, 0, bytes);
                        }

                        if (serverSocket != null) {
                            if (bytes < 0) {
                                active = false;
                            } else if (bytes > 0) {
                                if (mClientSocket != null && mClientSocket.isConnected()) {
                                    try {
                                        mClientOutputStream.write(buffer, 0, bytes);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    debug("<W>Missing client connection");
                                }
                            } else {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            debug("<D>Create server socket");
                            serverSocket = new ServerSocket(SERVER_PORT);
                            acceptClient(serverSocket);
                            sendAction(ACTION_ACCESSORY_ATTACHED);
                        }
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    debug("<W>Stop reader: " + e.getMessage());
                } finally {
                    if (serverSocket != null) {
                        debug("<D>Close server socket");
                        try {
                            serverSocket.close();
                        }catch(IOException e){
                            e.printStackTrace();
                        }
                        closeClientSocket();
                    }
                }

                sendAction(ACTION_ACCESSORY_DETACHED);
                stopSelf();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void acceptClient(final ServerSocket server) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!server.isClosed()) {
                        debug("<D>Accept client connection");
                        mClientSocket = server.accept();
                        mClientSocket.setTcpNoDelay(true);
                        mClientInputStream = mClientSocket.getInputStream();
                        mClientOutputStream = mClientSocket.getOutputStream();
                        try {
                            debug("<D>Redirect client to accessory");
                            byte[] buffer = new byte[2048];
                            int bytes;

                            while ((bytes = mClientInputStream.read(buffer)) > 0) {
                                mAccessoryOutputStream.write(buffer, 0, bytes);
                                mAccessoryOutputStream.flush();
                                debug("<D>Send: ", buffer, 0, bytes);
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void closeClientSocket() {
        debug("<D>Close client socket");

        if (mClientInputStream != null) {
            try {
                mClientInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mClientOutputStream != null) {
            try {
                mClientOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mClientSocket != null) {
            try {
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
