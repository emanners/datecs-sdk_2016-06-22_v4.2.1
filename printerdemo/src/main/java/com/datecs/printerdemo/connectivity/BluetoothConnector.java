package com.datecs.printerdemo.connectivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

public abstract class BluetoothConnector extends AbstractConnector {

    private BluetoothAdapter mBtAdapter;
    
    private BluetoothDevice mBtDevice;
    
    public BluetoothConnector(Context context, BluetoothAdapter btAdapter, BluetoothDevice btDevice) {
        super(context);        
        this.mBtAdapter = btAdapter;
        this.mBtDevice = btDevice;        
    }

    public BluetoothDevice getBluetoothDevice() {
        return this.mBtDevice;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return this.mBtAdapter;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof BluetoothConnector) {
            return mBtDevice.equals(((BluetoothConnector)o).mBtDevice);
        }

        return false;
    }
}
