package com.datecs.readerdemo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.UUID;

import com.datecs.BuildInfo;
import com.datecs.printer.ProtocolAdapter;
import com.datecs.universalreader.UniversalReader;
import com.datecs.universalreader.UniversalReader.ConnectionListener;
import com.datecs.universalreader.UniversalReaderException;
import com.datecs.readerdemo.network.PrinterServer;
import com.datecs.readerdemo.network.PrinterServerListener;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class UniversalReaderActivity extends Activity {
    // Debug
    private static final String LOG_TAG = "UniversalReader";     
    
    // Request to get the bluetooth device
    private static final int REQUEST_GET_DEVICE = 0; 
    
    // Request to show barcode activity
    private static final int REQUEST_BARCODE = 1; 
    
    // Request to show smart card activity
    private static final int REQUEST_SMARTCARD = 2; 
    
    // Request to show mifare activity
    private static final int REQUEST_MIFARE = 3; 
    
    // Request to show mifare activity
    private static final int REQUEST_TOUCHSCREEN = 4; 
    
    // Request to get the bluetooth device
    private static final int DEFAULT_NETWORK_PORT = 9100; 
        
    // Result code for error
    public static final int RESULT_ERROR = RESULT_FIRST_USER + 0;
    
    private ProtocolAdapter mProtocolAdapter;        
    private PrinterServer mPrinterServer;
    private BluetoothSocket mBluetoothSocket;
    private Socket mPrinterSocket;
	
    // Member variables
    public static UniversalReader sUniversalReader;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); 
        setContentView(R.layout.activity_universal_reader);
        
        // Show Android device information and API version.
        final TextView txtVersion = (TextView) findViewById(R.id.txt_version);
        txtVersion.setText(Build.MANUFACTURER + " " + Build.MODEL + ", Datecs API " + BuildInfo.VERSION);
                
        findViewById(R.id.barcode).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    Intent intent = new Intent(UniversalReaderActivity.this, BarcodeActivity.class);
		        startActivityForResult(intent, REQUEST_BARCODE);				
			}        	
        });
        
        findViewById(R.id.smartcard).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    Intent intent = new Intent(UniversalReaderActivity.this, SmartCardActivity.class);
		        startActivityForResult(intent, REQUEST_SMARTCARD);		
			}        	
        });
        
        findViewById(R.id.mifare).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    Intent intent = new Intent(UniversalReaderActivity.this, MifareActivity.class);
		        startActivityForResult(intent, REQUEST_MIFARE);				
			}        	
        });    
        
        findViewById(R.id.touchscreen).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    Intent intent = new Intent(UniversalReaderActivity.this, TouchscreenActivity.class);
		        startActivityForResult(intent, REQUEST_TOUCHSCREEN);
			}        	
        });
            
        waitForConnection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();        
        closeActiveConnection();
    }   
            
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GET_DEVICE) {
            if (resultCode == DeviceListActivity.RESULT_OK) {   
                String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                //address = "192.168.11.136:9100";
                if (BluetoothAdapter.checkBluetoothAddress(address)) {
                    establishBluetoothConnection(address);
                } else {
                    establishNetworkConnection(address);
                }
            } else if (resultCode == RESULT_CANCELED) {
                
            } else {
                finish();
            }
        }
    }
		
	private void toast(final String text) {
        runOnUiThread(new Runnable() {            
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();                
            }
        });
    }
       
    private void error(final String text, final boolean restart) {        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {        
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
                
                if (!isFinishing() && restart) {
                    waitForConnection();
                }
            }           
        });
    }
    
    private void runTask(final Runnable r, final int message) {
        // Start the job from main thread
        runOnUiThread(new Runnable() {            
            @Override
            public void run() {
                // Progress dialog available due job execution
                final ProgressDialog dialog = new ProgressDialog(UniversalReaderActivity.this);
                dialog.setTitle(getString(R.string.title_please_wait));
                dialog.setMessage(getString(message));
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
                
                Thread t = new Thread(new Runnable() {            
                    @Override
                    public void run() {                
                        try {
                            r.run();                        
                        } finally {
                            dialog.dismiss();
                        }
                    }
                });
                t.start();   
            }
        });                     
    }
    
    protected void initUniversalReader(boolean attachedToPrinter, InputStream inputStream, OutputStream outputStream) throws IOException { 
        UniversalReader.setDebug(true);
        
        if (attachedToPrinter) {
            mProtocolAdapter = new ProtocolAdapter(inputStream, outputStream);
            
            if (mProtocolAdapter.isProtocolEnabled()) {
                ProtocolAdapter.Channel channel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_UNIVERSAL_READER);
                try { channel.close(); }
                catch (IOException e) { }
                try { channel.open(); }
                catch (IOException e) { }                
                sUniversalReader = new UniversalReader(channel.getInputStream(), channel.getOutputStream());
            } else {
                throw new IOException("Protocol mode must be enabled");
            }
            
        } else {
            sUniversalReader = new UniversalReader(inputStream, outputStream);
            sUniversalReader.setButton(UniversalReader.BUTTON_MODE_NONE);            
        }
        
        final String info;        
        try {
            info = sUniversalReader.getIdentification();
        } catch (UniversalReaderException e) {
            throw new IOException(e.getMessage());
        }
        
        runOnUiThread(new Runnable() {          
            @Override
            public void run() {
                ((ImageView)findViewById(R.id.icon)).setImageResource(R.drawable.ic_launcher);
                ((TextView)findViewById(R.id.name)).setText(info);
            }
        });
        
        sUniversalReader.setConnectionListener(new ConnectionListener() {
            @Override
            public void onDisconnect() {
                toast("Device is disconnected");
                
                runOnUiThread(new Runnable() {             
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            waitForConnection();
                        }
                    }
                });
            }
        });        
    }
    
	public synchronized void waitForConnection() {
        closeActiveConnection();
        
        // Show dialog to select a Bluetooth device. 
        startActivityForResult(new Intent(this, DeviceListActivity.class), REQUEST_GET_DEVICE);
        
        // Start server to listen for network connection.
        try {
            mPrinterServer = new PrinterServer(new PrinterServerListener() {                
                @Override
                public void onConnect(Socket socket) {
                    Log.d(LOG_TAG, "Accept connection from " + socket.getRemoteSocketAddress().toString());
                    
                    // Close Bluetooth selection dialog
                    finishActivity(REQUEST_GET_DEVICE);                    
                    
                    mPrinterSocket = socket;
                    try {
                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        initUniversalReader(true, in, out);
                    } catch (IOException e) {   
                        e.printStackTrace();
                        error("FAILED to init: " + e.getMessage(), true);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }   
    
    private void establishBluetoothConnection(final String address) {
        closePrinterServer();
        
        runTask(new Runnable() {           
            @Override
            public void run() {      
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();                
                BluetoothDevice device = adapter.getRemoteDevice(address);                
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                InputStream in = null;
                OutputStream out = null;
                
                adapter.cancelDiscovery();
                
                try {
                    Log.d(LOG_TAG, "Connect to " + device.getName());
                    mBluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
                    mBluetoothSocket.connect();
                    in = mBluetoothSocket.getInputStream();
                    out = mBluetoothSocket.getOutputStream();                                        
                } catch (IOException e) {    
                    e.printStackTrace();
                    error("FAILED to connect: " + e.getMessage(), true);
                    return;
                }                                  
                
                try {
                    boolean printer = device.getName().contains("DPP");
                    initUniversalReader(printer, in, out);
                } catch (IOException e) {
                    e.printStackTrace();
                    error("FAILED to init: " + e.getMessage(), true);
                    return;
                }
            }
        }, R.string.msg_connecting); 
    }
    
    private void establishNetworkConnection(final String address) {
        closePrinterServer();
        
        runTask(new Runnable() {           
            @Override
            public void run() {             
                Socket s = null;
                try {
                    String[] url = address.split(":");
                    int port = DEFAULT_NETWORK_PORT;
                    
                    try {
                        if (url.length > 1)  {
                            port = Integer.parseInt(url[1]);
                        }
                    } catch (NumberFormatException e) { }
                    
                    s = new Socket(url[0], port);
                    s.setKeepAlive(true);
                    s.setTcpNoDelay(true);
                } catch (UnknownHostException e) {
                    error("FAILED to connect: " + e.getMessage(), true);
                    return;
                } catch (IOException e) {
                    error("FAILED to connect: " + e.getMessage(), true);
                    return;
                }            
                
                InputStream in = null;
                OutputStream out = null;
                
                try {
                    Log.d(LOG_TAG, "Connect to " + address);
                    mPrinterSocket = s;                    
                    in = mPrinterSocket.getInputStream();
                    out = mPrinterSocket.getOutputStream();                                        
                } catch (IOException e) {                    
                    error("FAILED to connect: " + e.getMessage(), true);
                    return;
                }                                  
                
                try {
                    initUniversalReader(true, in, out);
                } catch (IOException e) {
                    error("FAILED to init: " + e.getMessage(), true);
                    return;
                }
            }
        }, R.string.msg_connecting); 
    }
    
    private synchronized void closeBlutoothConnection() {        
        // Close Bluetooth connection 
        BluetoothSocket s = mBluetoothSocket;
        mBluetoothSocket = null;
        if (s != null) {
            Log.d(LOG_TAG, "Close Blutooth socket");
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }        
    }
    
    private synchronized void closeNetworkConnection() {
        // Close network connection
        Socket s = mPrinterSocket;
        mPrinterSocket = null;
        if (s != null) {
            Log.d(LOG_TAG, "Close Network socket");
            try {
                s.shutdownInput();
                s.shutdownOutput();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }            
        }
    }
    
    private synchronized void closePrinterServer() {
        closeNetworkConnection();
        
        // Close network server
        PrinterServer ps = mPrinterServer;
        mPrinterServer = null;
        if (ps != null) {
            Log.d(LOG_TAG, "Close Network server");
            try {
                ps.close();
            } catch (IOException e) {                
                e.printStackTrace();
            }            
        }     
    }
    
    private synchronized void closeUniversalReaderConnection() {
        if (sUniversalReader != null) {
            sUniversalReader.close();
        }
        
        if (mProtocolAdapter != null) {
            mProtocolAdapter.close();
        }
    }
    
	private synchronized void closeActiveConnection() {
	    closeUniversalReaderConnection();
        closeBlutoothConnection();
        closeNetworkConnection();  
        closePrinterServer();
    }

}
