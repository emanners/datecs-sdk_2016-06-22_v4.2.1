package com.datecs.readerdemo;

import java.io.IOException;
import java.util.ArrayList;

import com.datecs.universalreader.UniversalReader;
import com.datecs.universalreader.UniversalReaderException;
import com.datecs.universalreader.UniversalReader.BarcodeReader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class BarcodeActivity extends Activity {	
	
    private class BarcodeThread extends Thread {
		private volatile boolean mActive = true;
						
		@Override
		public void run() {
		    final BarcodeReader barcode = getUniveralReader().getBarcodeReader();
		    
			try {				
				while (mActive) {
					boolean pressed = false;					
													
					if (mActive) {
						pressed = getUniveralReader().isButtonPressed();						
					}
						
					if (mActive) {
						if (pressed) {														
							try {
							    String data = barcode.read();
								appendBarcode(data.substring(1));
							} catch (UniversalReaderException e) {
								e.printStackTrace();
								if (e.getErrorCode() != UniversalReader.ERROR_BARCODE_NODATA) {
									throw e;
								}
							}							
						} else {
							try {
								Thread.sleep(40);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}
				}
			} catch (IOException e) {				
			    e.printStackTrace();
				cancelActivity(e.getMessage());					
			}			
		}
		
		void finish() {
			mActive = false;
		}
	}
	
	private Handler mHandler;
	private BarcodeThread mThread;	
	private ArrayList<String> mBarcodes;
	private ArrayAdapter<String> mAdapter;	
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode);
        setResult(RESULT_OK);
        
        mHandler = new Handler();
        mBarcodes = new ArrayList<String>();
        mAdapter = new ArrayAdapter<String>(this, R.layout.listitem_barcode, R.id.data, mBarcodes);
        ((ListView)findViewById(R.id.barcodes)).setAdapter(mAdapter);
    }
            
    @Override
	protected void onResume() {
    	mThread = new BarcodeThread();
    	mThread.start();
		super.onResume();
	}

	@Override
	protected void onPause() {
		mThread.finish();
		try {
			mThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.onPause();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, R.string.clear);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == Menu.FIRST) {
			mBarcodes.clear();
			mAdapter.notifyDataSetChanged();
			return true;
		}
		return false;
	}

	private UniversalReader getUniveralReader() {
		return UniversalReaderActivity.sUniversalReader;
	}
	
	private void appendBarcode(final String barcode) {
		mHandler.post(new Runnable() {			
			@Override
			public void run() {
				mBarcodes.add(barcode);
				mAdapter.notifyDataSetChanged();
			}
		});
    }	
		
	private void cancelActivity(String message) {
        toast(message);        
        finish();
    }
	
	private void toast(final String text) {
        runOnUiThread(new Runnable() {            
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
}
