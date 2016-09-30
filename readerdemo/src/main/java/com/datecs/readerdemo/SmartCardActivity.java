package com.datecs.readerdemo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.datecs.smartcard.AnswerToReset;
import com.datecs.smartcard.ResponseAPDU;
import com.datecs.universalreader.UniversalReader;
import com.datecs.universalreader.UniversalReader.SmartCardReader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class SmartCardActivity extends Activity {
	
    private class SmartCardThread extends Thread {
		private volatile boolean mActive = true;
						
		@Override
		public void run() {
		    SmartCardReader smartcard = getUniversalReader().getSmartCardReader();
		    
			try {
			   while (mActive) {							
					boolean cardPresent;
															
					cardPresent = false;
					setCaption(true);
					while (mActive && cardPresent == false) {
						if (mActive) {
							cardPresent = smartcard.isCardPresent();
						}
						
						if (mActive) {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {									
								e.printStackTrace();
							}
						}
					}
					
					if (mActive) {
						smartcard.select(0);
						
					    AnswerToReset atr = smartcard.reset();	
						
					    //SystemClock.sleep(100);
					    System.out.println("---------------------------------------------------------------------");
					    
						// Check new method
						ByteArrayOutputStream o = new ByteArrayOutputStream();						
					    o.write(0x00);
					    o.write(0x84);
					    o.write(0x00);
					    o.write(0x00);
					    o.write(0x00);
						
					    ArrayList<byte[]> input = new ArrayList<byte[]>();
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    input.add(o.toByteArray());
					    
					    List<ResponseAPDU> response = smartcard.transmit(input);
					    for (ResponseAPDU responseAPDU: response) {
					        System.out.println("ResponseAPDU -> " + responseAPDU);
					    }
						//
						
						setAnswerToReset(atr);
						setCaption(false);
					}
										
					while (mActive && cardPresent) {
						if (mActive) {
							cardPresent = smartcard.isCardPresent();
						}
						
						if (mActive) {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {									
								e.printStackTrace();
							}
						}
					}
					
					setAnswerToReset(null);					
				}
			} catch (IOException e) {						
				e.printStackTrace();	
				cancelActivity(e.getMessage());		
			}			
		}
		
		public void finish() {
			mActive = false;
		}
	}
	
	private Handler mHandler;
	private SmartCardThread mThread;	
	
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smartcard);
        setResult(RESULT_OK);
        
        mHandler = new Handler();
    }
    
    @Override
	protected void onResume() {
    	if (getUniversalReader() != null) {
    		mThread = new SmartCardThread();
    		mThread.start();
    	} else {
    		finish();
    	}
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

	private UniversalReader getUniversalReader() {
		return UniversalReaderActivity.sUniversalReader;
	}
    
    private void setCaption(final boolean put) {
    	final TextView tvCaption = (TextView)findViewById(R.id.caption);
    	final TextView tvResData = (TextView)findViewById(R.id.reset_data);
    	
    	mHandler.post(new Runnable() {			
			@Override
			public void run() {
				if (put) {
				    tvResData.setVisibility(View.INVISIBLE);
					tvCaption.setText(R.string.put_card);					
				} else {					
				    tvResData.setVisibility(View.VISIBLE);
					tvCaption.setText(R.string.remove_card);
				}
			}
		});
    }
    
    private void setAnswerToReset(final AnswerToReset atr) {
    	final TextView tvData = (TextView)findViewById(R.id.data);
    	
		mHandler.post(new Runnable() {			
			@Override
			public void run() {
				if (atr != null) {
					StringBuffer sb = new StringBuffer();
					for (Byte b : atr.getData()) {
						sb.append(String.format("%02X ", (int)b & 0xff));
					}
					tvData.setText(sb.toString());
				} else {
					tvData.setText("");
				}
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
