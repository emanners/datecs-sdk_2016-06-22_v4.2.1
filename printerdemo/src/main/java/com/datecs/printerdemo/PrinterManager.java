package com.datecs.printerdemo;

import android.util.Log;

import com.datecs.emsr.EMSR;
import com.datecs.printer.Printer;
import com.datecs.printer.ProtocolAdapter;
import com.datecs.printerdemo.connectivity.AbstractConnector;
import com.datecs.rfid.RC663;

import java.io.IOException;

public class PrinterManager {

    private static final String TAG = "PrinterManager";

    private AbstractConnector mConnector;

    private ProtocolAdapter mProtocolAdapter;
    private ProtocolAdapter.Channel mPrinterChannel;
    private ProtocolAdapter.Channel mEMSRChannel;
    private ProtocolAdapter.Channel mRFIDChannel;

    private Printer mPrinter;
    private EMSR mEMSR;
    private RC663 mRC663;

    public static final PrinterManager instance;

    static  {
        instance = new PrinterManager();
    }

    private PrinterManager() { }

    public void init(AbstractConnector connector) throws IOException {
        Log.d(TAG, "Initialize printer...");

        mConnector = connector;

        // Here you can enable various debug information
        // ProtocolAdapter.setDebug(true); // Provides massive debug output
        // Printer.setDebug(true);
        // EMSR.setDebug(true);

        // Check if printer is into protocol mode. Ones the object is created it can not be released
        // without closing base streams.
        mProtocolAdapter = new ProtocolAdapter(mConnector.getInputStream(), mConnector.getOutputStream());
        if (mProtocolAdapter.isProtocolEnabled()) {
            Log.d(TAG, "Protocol mode is enabled");

            // Get the channel associated with printer.
            mPrinterChannel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_PRINTER);
            mPrinter = new Printer(mPrinterChannel.getInputStream(), mPrinterChannel.getOutputStream());

            // Check if printer has encrypted magnetic head
            mEMSRChannel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_EMSR);
            try {
                // Close channel silently if it is already opened.
                try {
                    mEMSRChannel.close();
                } catch (IOException e) {
                }

                // Try to open EMSR channel. If method failed, then EMSR is not supported
                // on this device.
                mEMSRChannel.open();

                mEMSR = new EMSR(mEMSRChannel.getInputStream(), mEMSRChannel.getOutputStream());
                EMSR.EMSRKeyInformation keyInfo = mEMSR.getKeyInformation(EMSR.KEY_AES_DATA_ENCRYPTION);
                if (!keyInfo.tampered && keyInfo.version == 0) {
                    Log.d(TAG, "Missing encryption key");
                    // If key version is zero we can load a new key in plain mode.
                    byte[] keyData = CryptographyHelper.createKeyExchangeBlock(0xFF,
                            EMSR.KEY_AES_DATA_ENCRYPTION, 1, CryptographyHelper.AES_DATA_KEY_BYTES,
                            null);
                    mEMSR.loadKey(keyData);
                }
                mEMSR.setEncryptionType(EMSR.ENCRYPTION_TYPE_AES256);
                mEMSR.enable();
                Log.d(TAG, "Encrypted magnetic stripe reader is available");
            } catch (IOException e) {
                if (mEMSR != null) {
                    mEMSR.close();
                    mEMSR = null;
                }
            }

            // Check if printer has encrypted magnetic head
            mRFIDChannel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_RFID);
            try {
                // Close channel silently if it is already opened.
                try {
                    mRFIDChannel.close();
                } catch (IOException e) {
                }

                // Try to open RFID channel. If method failed, then RFID is not supported
                // on this device.
                mRFIDChannel.open();

                mRC663 = new RC663(mRFIDChannel.getInputStream(), mRFIDChannel.getOutputStream());
                mRC663.enable();
                Log.d(TAG, "RC663 reader is available");
            } catch (IOException e) {
                if (mRC663 != null) {
                    mRC663.close();
                    mRC663 = null;
                }
            }
        } else {
            Log.d(TAG, "Protocol mode is disabled");

            // Protocol mode it not enables, so we should use the row streams.
            mPrinter = new Printer(
                    mProtocolAdapter.getRawInputStream(),
                    mProtocolAdapter.getRawOutputStream());
        }

        // Check printer
        mPrinter.getInformation();
    }

    public void close() {
        if (mConnector != null) {
            try {
                mConnector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public ProtocolAdapter getProtocolAdapter() {
        return mProtocolAdapter;
    }

    public ProtocolAdapter.Channel getPrinterChannel() {
        return mPrinterChannel;
    }

    public Printer getPrinter() {
        return mPrinter;
    }

    public EMSR getEMSR() {
        return mEMSR;
    }

    public RC663 getRC663() {
        return mRC663;
    }

}
