package com.datecs.readerdemo;

import java.io.IOException;
import java.util.ArrayList;

import com.datecs.universalreader.UniversalReader;
import com.datecs.universalreader.UniversalReaderException;
import com.datecs.universalreader.UniversalReader.MifareReader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class MifareActivity extends Activity {
	
    private class MifareThread extends Thread {
		private volatile boolean mRun = true;
						
		@Override
		public void run() {			
			MifareReader mifare = getUniveralReader().getMifareReader();
			int cardSerial = 0;
			
			try {
			    if (mRun) {
				    mifare.setPower(true);
				    mifare.config();
				}
				
				while (mRun) {
					if (mRun) {
						try {
						    mifare.request(false);
							cardSerial = mifare.anticollision();
							mifare.select(cardSerial);		
							mifare.halt();
							appendCard(String.format("#%08X", cardSerial));	
						} catch (UniversalReaderException e) {
							if (!e.getMessage().startsWith("Mifare")) {
								throw e;
							}														
						}
					}
					
					if (mRun) {
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {								
							e.printStackTrace();
						}
					}
				}
				
				mifare.setPower(false);				
				
			} catch (IOException e) {						
				e.printStackTrace();
				cancelActivity(e.getMessage());								
			}						
		}
		
		public void finish() {
			mRun = false;
		}
	}
	
	private Handler mHandler;
	private MifareThread mThread;	
	private ArrayList<String> mCards;
	private ArrayAdapter<String> mAdapter;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mifare);
        setResult(RESULT_OK);
        
        mHandler = new Handler();
        mCards = new ArrayList<String>();
        mAdapter = new ArrayAdapter<String>(this, R.layout.listitem_mifare, R.id.data, mCards);
        ((ListView)findViewById(R.id.mifares)).setAdapter(mAdapter);               	        
    }
            
    @Override
	protected void onResume() {
    	mThread = new MifareThread();
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
			mCards.clear();
			mAdapter.notifyDataSetChanged();
			return true;
		}
		return false;
	}
	
	private UniversalReader getUniveralReader() {
		return UniversalReaderActivity.sUniversalReader;
	}
	
	private void appendCard(final String card) {
		mHandler.post(new Runnable() {			
			@Override
			public void run() {
				mCards.add(card);
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
