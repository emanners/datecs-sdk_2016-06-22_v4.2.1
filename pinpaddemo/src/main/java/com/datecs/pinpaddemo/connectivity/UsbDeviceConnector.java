package com.datecs.pinpaddemo.connectivity;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

public class UsbDeviceConnector extends AbstractConnector {

    private static final boolean DEBUG = false;

    private class InputStreamImpl extends InputStream {
        private static final int TIMEOUT = 100;
        
        private UsbDeviceConnection mConnection;
        private UsbEndpoint mEndPoint;          
        private List<Byte> mDataBuffer;
        private String mLastError;
        
        public InputStreamImpl(UsbDeviceConnection conn, UsbEndpoint ep) {
            if (conn == null) 
                throw new NullPointerException("The 'conn' is null");
            
            if (ep == null) 
                throw new NullPointerException("The 'ep' is null");
            
            if (ep.getDirection() != UsbConstants.USB_DIR_IN) 
                throw new IllegalArgumentException("The endpoint direction is incorrect");
            
            this.mConnection = conn;        
            this.mEndPoint = ep;        
            this.mDataBuffer = new LinkedList<Byte>();

            final Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] tmp = new byte[2048];
                    
                    while (mLastError == null) {
                        long ms = System.currentTimeMillis() + TIMEOUT / 2;
                        int len = mConnection.bulkTransfer(mEndPoint, tmp, tmp.length, TIMEOUT);
                                                
                        if (len < 0) {
                            debug("Read bulkTransfer failed: " + len);

                            try {
                                if (ms > System.currentTimeMillis()) {
                                    mLastError = "Read failed";
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else if (len > 0) {
                            debug("Read " + len + " bytes");

                            synchronized (mDataBuffer) {
                                for (int i = 0; i < len; i++) {
                                    mDataBuffer.add(tmp[i]);
                                }
                            }
                        }
                    }                
                }
            });
            t.start();
        }
        
        @Override
        public int available() throws IOException {  
            if (mLastError != null) {
                throw new IOException(mLastError);
            }        
            
            synchronized (mDataBuffer) {
                return mDataBuffer.size();
            }
        }

        @Override
        public void close() throws IOException {
            mLastError = "The stream is closed";
        }
        
        @Override
        public int read() throws IOException {
            do {
                if (mLastError != null) {
                    throw new IOException(mLastError);
                }

                synchronized (mDataBuffer) {
                    int count = mDataBuffer.size();

                    if (count > 0) {
                        return mDataBuffer.remove(0) & 0xFF;
                    }
                }

                SystemClock.sleep(10);
            } while (true);
        }
        
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            do {
                if (mLastError != null) {
                    throw new IOException(mLastError);
                }

                synchronized (mDataBuffer) {
                    int count = mDataBuffer.size();

                    if (count > 0) {
                        int chunkSize = Math.min(length, count);

                        for (int i = 0; i < chunkSize; i++) {
                            byte value = mDataBuffer.remove(0).byteValue();
                            buffer[offset + i] = value;
                        }

                        return chunkSize;
                    }
                }

                SystemClock.sleep(10);
            } while (true);
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }
    }

    private class OutputStreamImpl extends OutputStream {
        private UsbDeviceConnection mConnection;
        private UsbEndpoint mEndPoint;
        private byte[] mPacketBuffer;
        private int mPacketLength;  
        private String mLastError;
        
        public OutputStreamImpl(UsbDeviceConnection conn, UsbEndpoint ep) {
           if (conn == null) 
                throw new NullPointerException("The 'conn' is null");
            
            if (ep == null) 
                throw new NullPointerException("The 'ep' is null");
            
            if (ep.getDirection() != UsbConstants.USB_DIR_OUT) 
                throw new IllegalArgumentException("The endpoint direction is incorrect");
                    
            this.mConnection = conn;        
            this.mEndPoint = ep;
            this.mPacketBuffer = new byte[2048];
            this.mPacketLength = 0;        
        }

        @Override
        public synchronized void write(int oneByte) throws IOException {
            if (mLastError != null) {
                throw new IOException(mLastError);
            }
            
            if (mPacketBuffer.length == mPacketLength) {
                flush();
            }
            
            mPacketBuffer[mPacketLength++] = (byte)oneByte;        
        }

        @Override
        public void close() throws IOException {
            mLastError = "The stream is closed";
        }

        @Override
        public synchronized void flush() throws IOException {
            while (mPacketLength > 0) {
                if (mLastError != null) {
                    throw new IOException(mLastError);
                }
                
                int len = mConnection.bulkTransfer(mEndPoint, mPacketBuffer, mPacketLength, 100);
                
                if (len < 0) {
                    debug("Write bulkTransfer failed: " + len);
                    mLastError = "Write error " + len;
                } else {
                    mPacketLength -= len;
                    System.arraycopy(mPacketBuffer,  len, mPacketBuffer, 0, mPacketLength);
                }
            }
        }
        
    }
    
    private UsbManager mUsbManager;    
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mDeviceConn;
    private UsbEndpoint[] mEndpoints;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    public UsbDeviceConnector(Context context, UsbManager manager, UsbDevice device) {
        super(context);
        this.mUsbManager = manager;
        this.mUsbDevice = device;
        this.mEndpoints = new UsbEndpoint[2];        
    }

    private static void debug(String text) {
        if (DEBUG) {
            System.out.println("<UsbDeviceConnector> " + text);
        }
    }

    private UsbDeviceConnection openConnection(UsbDevice device, UsbEndpoint[] endpoints) throws IOException {
        // Can we connect to device ?
        if (!mUsbManager.hasPermission(device)) {
            throw new IOException("Permission denied");
        }
                
        // Enumerate interfaces            
        for (int i = 0; i <  device.getInterfaceCount(); i++) {                
            final UsbInterface iface = device.getInterface(i);
            UsbEndpoint usbEpInp = null;        
            UsbEndpoint usbEpOut = null;
            
            // Enumerate end points
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint endpoint = iface.getEndpoint(j);
                
                // Check interface type
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                                    
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    usbEpInp = endpoint;
                }
                
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    usbEpOut = endpoint;
                }
            }
            
            if (usbEpInp != null || usbEpOut != null) {
                final UsbDeviceConnection usbDevConn = mUsbManager.openDevice(device);                
               
                // Check connection
                if (usbDevConn == null) {
                    throw new IOException("Open failed");
                }
                
                if (!usbDevConn.claimInterface(iface, true)) {
                    usbDevConn.close();
                    throw new IOException("Access denied");                    
                }
                
                endpoints[0] = usbEpInp;
                endpoints[1] = usbEpOut;
                return usbDevConn;                   
            }            
        }
        
        throw new IOException("Open failed");
    }      

    public UsbDevice getDevice() {
        return mUsbDevice;
    }

    public synchronized void connect() throws IOException {
        mDeviceConn = openConnection(mUsbDevice, mEndpoints);
        mInputStream = null;
        mOutputStream = null;
    }
    
    public synchronized void close() throws IOException {
        if (mDeviceConn != null) {
            try {
                mDeviceConn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public synchronized InputStream getInputStream() throws IOException {
        if (mInputStream == null) {
            mInputStream = new InputStreamImpl(mDeviceConn, mEndpoints[0]);
        }
        
        return mInputStream;
    }
    
    public synchronized OutputStream getOutputStream() throws IOException {
        if (mOutputStream == null) {
            mOutputStream = new OutputStreamImpl(mDeviceConn, mEndpoints[1]);
        }
        
        return mOutputStream;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof UsbDeviceConnector) {
            return mUsbDevice.equals(((UsbDeviceConnector)o).mUsbDevice);
        }

        return false;
    }
}
