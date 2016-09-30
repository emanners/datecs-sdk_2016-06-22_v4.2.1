package com.datecs.pinpaddemo.connectivity;

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
import android.os.SystemClock;
import android.support.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeConnector extends BluetoothConnector {
    private static final int MAX_CHARACTERISTIC_SIZE = 19;
    private static final int CONNECTION_TIME = 10000;

    public final static UUID[] UUID_SERVICES = {
            // Datecs Pinpad
            UUID.fromString("d839fc3c-84dd-4c36-9126-187b07255125"),
            // SumUp Air Lite
            UUID.fromString("d839fc3c-84dd-4c36-9126-187b07255126"),
            // SumUp Air
            UUID.fromString("d839fc3c-84dd-4c36-9126-187b07255127"),
    };
    public final static UUID UUID_RX_CHAR = UUID.fromString("1f6b14c9-97fa-4f1e-aaa6-7e152fdd04f4");
    public final static UUID UUID_TX_CHAR = UUID.fromString("b378db85-4ec3-4daa-828e-1b99607bd6a0");
    public final static UUID UUID_CN_CHAR = UUID.fromString("f953144b-e33a-4079-b202-e3d7c1f3dbb0");
    public final static UUID UUID_PW_CHAR = UUID.fromString("22ffc547-1bef-48e2-aa87-b87e23ac0bbd");

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final boolean DEBUG = true;

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothService;
    private BluetoothGattCharacteristic mReadCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mPowerStateCharacteristic;
    private BluetoothGattCharacteristic mConnectionCharacteristic;

    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private int mConnectionState = STATE_DISCONNECTED;

    private final Queue<Byte> mInputBuffer = new LinkedList<>();
    private final Object mCxSyncRoot = new Object();
    private final Object mRxSyncRoot = new Object();

    private CountDownLatch mTxLatch;

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            debug("onCharacteristicChanged " + characteristic.getUuid().toString());

            if (characteristic == mReadCharacteristic) {
                debug("Reading data from device");
                byte[] data = characteristic.getValue();

                synchronized (mInputBuffer) {
                    for (byte b : data) {
                        mInputBuffer.add(b);
                    }
                }

                synchronized (mRxSyncRoot) {
                    mRxSyncRoot.notify();
                }
            } else if (characteristic == mPowerStateCharacteristic) {
                boolean powerState = characteristic.getValue()[0] == 0x31;

                if (powerState) {
                    debug("Device is powered ON");
                    if (mConnectionState == STATE_CONNECTING) {
                        setConnectionState(STATE_CONNECTED);
                    }
                } else {
                    debug("Device is powered OFF");
                    if (mConnectionState == STATE_CONNECTING) {
                        if (mConnectionCharacteristic != null) {
                            String passKey = mBluetoothGatt.getDevice().getName();
                            debug("Trying to power on device " + passKey);
                            mConnectionCharacteristic.setValue(passKey);
                            boolean status = mBluetoothGatt.writeCharacteristic(mConnectionCharacteristic);
                            debug("Write characteristic returns " + status);
                        }
                    } else {
                        debug("Close bluetooth connection");
                        mBluetoothGatt.disconnect();
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            debug("onCharacteristicWrite: " + characteristic.getUuid() + ", status=" + status);

            if (status == 0) {
                if (characteristic == mWriteCharacteristic) {
                    debug("Writing completed");
                    if (mTxLatch != null) {
                        mTxLatch.countDown();
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            debug("onCharacteristicRead: "  + characteristic.getUuid() + ", status=" + status);

            if (status == 0 && characteristic == mPowerStateCharacteristic) {
                debug("Enable power state characteristic notification");
                setCharacteristicNotification(mPowerStateCharacteristic, true);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            debug("onDescriptorRead: "  + descriptor.getUuid().toString());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            debug("onDescriptorWrite: "  + descriptor.getUuid().toString());
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            debug("onConnectionStateChange status=" + status + ", newState=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                debug("GATT is connected");
                mConnectionState = STATE_CONNECTING;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                debug("GATT is disconnected");
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
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            debug("onServicesDiscovered " + status);

            // List GATT services
            debug("GATT services:");
            for (BluetoothGattService service: mBluetoothGatt.getServices()) {
                debug("  -> " + service.getUuid().toString());
            }

            for (UUID uuid: UUID_SERVICES) {
                mBluetoothService = mBluetoothGatt.getService(uuid);

                if (mBluetoothService != null) {
                    break;
                }
            }
            if (mBluetoothService == null) {
                debug("GATT service not found");
                gatt.disconnect();
                gatt.close();
                return;
            } else {
                debug("GATT service found: " + mBluetoothService.getUuid().toString());
            }

            // List GATT characteristics
            debug("GATT service characteristics:");
            for (BluetoothGattCharacteristic characteristic: mBluetoothService.getCharacteristics()) {
                debug("  -> " + characteristic.getUuid().toString());
            }

            mWriteCharacteristic = mBluetoothService.getCharacteristic(UUID_TX_CHAR);
            if (mWriteCharacteristic == null) {
                debug("TX characteristic not found");
                gatt.disconnect();
                gatt.close();
                return;
            } else {
                debug("TX characteristic found: " + mWriteCharacteristic.getUuid().toString());
                mWriteCharacteristic = mWriteCharacteristic;
            }

            mReadCharacteristic = mBluetoothService.getCharacteristic(UUID_RX_CHAR);
            if (mReadCharacteristic == null) {
                debug("RX characteristic not found");
                gatt.disconnect();
                gatt.close();
                return;
            } else {
                debug("RX characteristic found: " + mReadCharacteristic.getUuid().toString());
            }

            mConnectionCharacteristic = mBluetoothService.getCharacteristic(UUID_CN_CHAR);
            if (mConnectionCharacteristic != null) {
                debug("CN characteristic found: " + mConnectionCharacteristic.getUuid().toString());
            }

            mPowerStateCharacteristic = mBluetoothService.getCharacteristic(UUID_PW_CHAR);
            if (mPowerStateCharacteristic != null) {
                debug("PW characteristic found: " + mPowerStateCharacteristic.getUuid().toString());
            }

            if (mPowerStateCharacteristic != null) {
                debug("Read power state characteristic");
                readCharacteristic(mPowerStateCharacteristic);
            } else {
                setConnectionState(STATE_CONNECTED);
            }
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

    // Helper method for logging binary data
    private String byteArrayToHexString(byte[] data, int offset, int length) {
        final char[] hex = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        char[] buf = new char[length * 3];
        int offs = 0;

        for (int i = 0; i < length; i++) {
            buf[offs++] = hex[(data[offset + i] >> 4) & 0xf];
            buf[offs++] = hex[(data[offset + i] >> 0) & 0xf];
            buf[offs++] = ' ';
        }

        return new String(buf, 0, offs);
    }

    private void setConnectionState(int connectionState) {
        debug("Setting connection state to " + connectionState);
        mConnectionState = connectionState;

        synchronized (mCxSyncRoot) {
            mCxSyncRoot.notify();
        }

        synchronized (mRxSyncRoot) {
            mRxSyncRoot.notify();
        }
    }

    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        debug("Trying to read characteristic: " + characteristic.getUuid());
        boolean status = mBluetoothGatt.readCharacteristic(characteristic);
        debug("Read characteristic returns " + status);
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        debug("Enabling Notifications: " + characteristic.getUuid());
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
        if (!descriptors.isEmpty()) {
            BluetoothGattDescriptor descriptor = descriptors.get(0);

            if (enabled) {
                debug("Enabling notification for characteristic: " + characteristic.getUuid());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                debug("Disabling notification for characteristic: " + characteristic.getUuid());
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }

            mBluetoothGatt.writeDescriptor(descriptor);
            debug("All notifications are enabled");
        }

        SystemClock.sleep(250);
    }

    private void writeCharacteristic(byte[] data) throws IOException {
        boolean status;
        int offset = 0;
        int count = 0;

        debug("Write: " + byteArrayToHexString(data, 0, data.length) + " (" + data.length + ")");
        while (offset < data.length) {
            int chunkSize = Math.min(data.length - offset, MAX_CHARACTERISTIC_SIZE);
            byte[] buffer = new byte[chunkSize];
            System.arraycopy(data, offset, buffer, 0, buffer.length);

            debug("Set characteristic value: " + mWriteCharacteristic.setValue(buffer));
            if ((count % 20) == 0) {
                debug("Set characteristic write type: WRITE_TYPE_DEFAULT");
                mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                debug("Set characteristic write type: WRITE_TYPE_NO_RESPONSE");
                mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }

            mTxLatch = new CountDownLatch(1);

            debug("Writing characteristic");
            status = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
            debug("Write characteristic returns " + status);
            if (status) {
                offset += chunkSize;
                count++;
            } else {
                break;
            }

            try {
                status = mTxLatch.await(CONNECTION_TIME, TimeUnit.MILLISECONDS);

                if (!status) {
                    throw new IOException("Write timeout");
                }

                if (mConnectionState != STATE_CONNECTED) {
                    throw new IOException("Write failed");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new IOException("Write interrupted");
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

        debug("Wait to establish connection...");
        try {
            synchronized (mCxSyncRoot) {
                mCxSyncRoot.wait(CONNECTION_TIME);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mConnectionState != STATE_CONNECTED) {
            debug("Close GATT object");
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            throw new IOException("Connection timeout");
        }

        debug("Set read characteristic notification");
        setCharacteristicNotification(mReadCharacteristic, true);

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
                        mRxSyncRoot.wait(100);
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
