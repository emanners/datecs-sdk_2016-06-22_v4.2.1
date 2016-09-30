package com.datecs.printerdemo.connectivity;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeConnector extends BluetoothConnector {
    private static final int MAX_CHARACTERISTIC_SIZE = 19;
    private static final int CONNECTION_TIME = 5000;

    public final static UUID UUID_SERVICE = UUID.fromString("d839fc3c-84dd-4c36-9126-187b07255126");
    public final static UUID UUID_RX_CHAR = UUID.fromString("1f6b14c9-97fa-4f1e-aaa6-7e152fdd04f4");
    public final static UUID UUID_TX_CHAR = UUID.fromString("b378db85-4ec3-4daa-828e-1b99607bd6a0");

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final boolean DEBUG = false;

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mTxChar;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private int mConnectionState = STATE_DISCONNECTED;
    private boolean mPendingWrite = false;

    private final Queue<Byte> mInputBuffer = new LinkedList<>();
    private final Object mCxSyncRoot = new Object();
    private final Object mRxSyncRoot = new Object();

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            debug("onCharacteristicChanged");

            byte[] data = characteristic.getValue();

            if (data != null && data.length > 0) {
                synchronized (mInputBuffer) {
                    for (byte b: data) {
                        mInputBuffer.add(b);
                    }
                }

                synchronized (mRxSyncRoot) {
                    mRxSyncRoot.notify();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            debug("onCharacteristicWrite: "  + status);
            mPendingWrite = false;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            debug("onConnectionStateChange status=" + status + ", newState=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTING;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                synchronized (mCxSyncRoot) {
                    mCxSyncRoot.notify();
                }

                synchronized (mRxSyncRoot) {
                    mRxSyncRoot.notify();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            debug("onServicesDiscovered " + status);

            // List GATT services
            debug("GATT services:");
            for (BluetoothGattService service: mBluetoothGatt.getServices()) {
                debug("  -> " + service.getUuid().toString());
            }

            BluetoothGattService gattService = mBluetoothGatt.getService(UUID_SERVICE);
            if (gattService == null) {
                debug("GATT service not found");
                gatt.disconnect();
                gatt.close();
                return;
            } else {
                debug("GATT service found: " + gattService.getUuid().toString());
            }

            // List GATT characteristics
            debug("GATT service characteristics:");
            for (BluetoothGattService service: mBluetoothGatt.getServices()) {
                debug("  -> " + service.getUuid().toString());
            }

            BluetoothGattCharacteristic txChar = gattService.getCharacteristic(UUID_TX_CHAR);
            if (txChar == null) {
                debug("TX characteristic not found");
                gatt.disconnect();
                gatt.close();
                return;
            } else {
                debug("TX characteristic found: " + txChar.getUuid().toString());
                mTxChar = txChar;
            }

            BluetoothGattCharacteristic rxChar = gattService.getCharacteristic(UUID_RX_CHAR);
            if (rxChar == null) {
                debug("RX characteristic not found");
                gatt.disconnect();
                gatt.close();
                return;
            } else {
                debug("RX characteristic found: " + rxChar.getUuid().toString());
            }

            debug("Set RX characteristic notification");
            mBluetoothGatt.setCharacteristicNotification(rxChar, true);

            debug("Enable notification for RX characteristic descriptors");
            for (BluetoothGattDescriptor descriptor: rxChar.getDescriptors()) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
            }

            debug("Bluetooth device is connected");
            mConnectionState = STATE_CONNECTED;
        }
    };

    public BluetoothLeConnector(Context context, BluetoothAdapter btAdapter, BluetoothDevice btDevice) {
        super(context, btAdapter, btDevice);
    }

    private void debug(String message) {
        if (DEBUG) {
            System.out.println("BluetoothLeConnector: " + message);
        }
    }

    private void writeCharacteristic(byte[] data) throws IOException {
        boolean status;
        int offset = 0;
        int count = 0;

        debug("Write " + data.length + " bytes");
        while (offset < data.length) {
            int chunkSize = Math.min(data.length - offset, MAX_CHARACTERISTIC_SIZE);
            byte[] buffer = new byte[chunkSize];
            System.arraycopy(data, offset, buffer, 0, buffer.length);

            debug("setValue " + mTxChar.setValue(buffer));
            if ((count % 20) == 0) {
                debug("setWriteType WRITE_TYPE_DEFAULT");
                mTxChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                debug("setWriteType WRITE_TYPE_NO_RESPONSE");
                mTxChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }

            mPendingWrite = true;
            status = mBluetoothGatt.writeCharacteristic(mTxChar);
            debug("writeCharacteristic " + status);
            if (status) {
                offset += chunkSize;
                count++;
            }

            debug("Writing...");
            while (mPendingWrite) {
                if (mConnectionState != STATE_CONNECTED) {
                    throw new IOException("Write failed");
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        debug("Write completed");
    }
    
    @Override
    public synchronized void connect() throws IOException {
        mConnectionState = STATE_CONNECTING;

        synchronized (mInputBuffer) {
            mInputBuffer.clear();
        }

        debug("Connect GATT");
        mBluetoothGatt = getBluetoothDevice().connectGatt(getContext(), false, mGattCallback);

        try {
            synchronized (mCxSyncRoot) {
                mCxSyncRoot.wait(CONNECTION_TIME);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mConnectionState != STATE_CONNECTED) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            throw new IOException("Connection timeout");
        }

        debug("Connection established");
    }
    
    @Override
    public synchronized void close() throws IOException {
        debug("Close InputStream");
        if (mInputStream != null) {
            mInputStream.close();
            mInputStream = null;
        }

        debug("Close OutputStream");
        if (mOutputStream != null) {
            mOutputStream.close();
            mOutputStream = null;
        }

        debug("Close GATT");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }

        mConnectionState = STATE_DISCONNECTED;

        debug("Connector is closed");
    }
    
    @Override
    public synchronized InputStream getInputStream() throws IOException {
        if (mInputStream == null) {
            mInputStream = new InputStreamImpl();
        } 
        
        return mInputStream;
    }
    
    @Override
    public synchronized OutputStream getOutputStream() throws IOException {
        if (mOutputStream == null) {
            mOutputStream = new OutputStreamImpl();
        }
        
        return mOutputStream;
    }

    private class InputStreamImpl extends InputStream {

        private IOException mLastError;

        @Override
        public int available() throws IOException {
            if (mLastError != null) {
                throw mLastError;
            }

            if (mConnectionState == STATE_DISCONNECTED) {
                throw new IOException("Device is not connected");
            }

            synchronized (mInputBuffer) {
                return mInputBuffer.size();
            }
        }

        @Override
        public void close() throws IOException {
            mLastError = new IOException("The stream is closed");
        }

        @Override
        public int read(@NonNull byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int bytesAvailable;

            while ((bytesAvailable = available()) == 0) {
                try {
                    synchronized (mRxSyncRoot) {
                        mRxSyncRoot.wait(100);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            int chunkSize = Math.min(bytesAvailable, byteCount);

            synchronized (mInputBuffer) {
                for (int i = 0; i < chunkSize; i++) {
                    buffer[byteOffset++] = mInputBuffer.remove();
                }
            }

            return chunkSize;
        }

        @Override
        public int read() throws IOException {
            while (available() == 0) {
                try {
                    synchronized (mRxSyncRoot) {
                        mRxSyncRoot.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            synchronized (mInputBuffer) {
                return mInputBuffer.remove();
            }
        }
    }

    private class OutputStreamImpl extends OutputStream {

        private IOException mLastError;

        private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();

        @Override
        public void close() throws IOException {
            mLastError = new IOException("The stream is closed");
        }

        @Override
        public void flush() throws IOException {
            if (mLastError != null) {
                throw mLastError;
            }

            if (mConnectionState != STATE_CONNECTED) {
                throw new IOException("Device is not connected");
            }

            synchronized (mBuffer) {
                byte[] buffer = mBuffer.toByteArray();
                writeCharacteristic(buffer);
                mBuffer.reset();
            }
        }

        @Override
        public void write(@NonNull byte[] buffer) throws IOException {
            write(buffer, 0, buffer.length);
        }

        @Override
        public void write(@NonNull byte[] buffer, int offset, int count) throws IOException {
            synchronized (mBuffer) {
                mBuffer.write(buffer, offset, count);
            }
        }

        @Override
        public void write(int oneByte) throws IOException {
            synchronized (mBuffer) {
                mBuffer.write(oneByte);
            }
        }

    }

}
