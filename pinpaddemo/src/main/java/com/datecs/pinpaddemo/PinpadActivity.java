package com.datecs.pinpaddemo;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import com.datecs.barcode.Barcode;
import com.datecs.emv.EmvTags;
import com.datecs.pinpad.DeviceInfo;
import com.datecs.pinpad.Pinpad;
import com.datecs.pinpad.Pinpad.BarcodeListener;
import com.datecs.pinpad.Pinpad.Emv2Listener;
import com.datecs.pinpad.PinpadException;
import com.datecs.pinpaddemo.connectivity.BluetoothHelper;
import com.datecs.pinpaddemo.processor.Backend;
import com.datecs.pinpaddemo.widget.LogView;
import com.datecs.rfid.ContactlessCard;
import com.datecs.rfid.FeliCaCard;
import com.datecs.rfid.ISO14443Card;
import com.datecs.rfid.ISO15693Card;
import com.datecs.rfid.RC663;
import com.datecs.rfid.RC663.CardListener;
import com.datecs.rfid.RFID;
import com.datecs.rfid.STSRICard;
import com.datecs.tlv.BerTlv;
import com.datecs.tlv.Tag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class PinpadActivity extends AppCompatActivity implements BluetoothHelper.ConnectionListener {

    private static final String LOG_TAG = "PinpadDemo";

    // Default transaction timeout in milliseconds.
    private static int DEFAULT_TIMEOUT = 120000;

    // Terminal Master Key Index
    private static final int TMK_KEY_INDEX = 0;

    // TR31 Master Key Index
    private static final int TR31_MASTER_KEY_INDEX = 1;

    // Data key index
    private static final int DATA_KEY_INDEX = 11;

    // Pin key index
    private static final int PIN_KEY_INDEX = 0;
    private BluetoothHelper bluetoothHelper;



    public interface PinpadRunnable {
        void run(Pinpad pinpad, ProgressDialog dialog) throws IOException;
    }

    private LogView mLogView;
    private EditText amount_;
    private EditText TT_;

    private ProgressDialog mProgressDialog;

    // Thread synchronization.
    private final Object mSyncRoot = new Object();

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pinpad);

        mLogView = (LogView) findViewById(R.id.log);

        amount_ = (EditText) findViewById(R.id.text_id);
        TT_ = (EditText) findViewById(R.id.TT_id);
        findViewById(R.id.btn_load_keys).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                loadKeys();
            }
        });

        findViewById(R.id.btn_load_config).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                loadConfiguration();
            }
        });

        findViewById(R.id.btn_read_card).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                readTag();
            }
        });

        findViewById(R.id.btn_start_transaction).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startTransaction();
            }
        });

        bluetoothHelper = new BluetoothHelper(this);
        bluetoothHelper.enableBluetooth();
        bluetoothHelper.receiverSetup();

    }

    @Override
    public void connected() {
        initPinpad();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //mProgressDialog.dismiss();
        bluetoothHelper.receiverTearDown();
    }

    @Override
    protected void onStop() {
        super.onStop();
        PinpadManager.release();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == BluetoothHelper.REQUEST_ENABLE_BT) {
            bluetoothHelper.init();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pinpad, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_clear_log) {
            mLogView.clear();
            return true;
        } else if (id == R.id.action_init_pinpad) {
            initPinpad();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void fail(final String text) {
        Log.e(LOG_TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogView.addE(text);
            }
        });
    }

    private void warn(final String text) {
        Log.w(LOG_TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogView.addW(text);
            }
        });
    }

    private void log(final String text) {
        log(text, false);
    }

    private void log(final String text, final boolean bold) {
        Log.d(LOG_TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogView.addD(text, bold);
            }
        });
    }

    private void log(String text, List<BerTlv> data) {
        for (BerTlv tlv: data) {
            Tag tag = tlv.getTag();

            if (tag.isConstructed()) {
                String s = Integer.toHexString(tag.toIntValue()) + " (CONSTRUCTED)";

                try {
                    List<BerTlv> list = BerTlv.createList(tlv.getValue());
                    log(text + s, false);
                    log("  " + text, list);
                } catch (Exception e) {
                    log(text + s  + tlv.getValueHexString(), false);
                }
            } else {
                String s = Integer.toHexString(tag.toIntValue()) +  ", " + tlv.getValueHexString();
                log(text + s, false);
            }
        }
    }

    private String byteArrayToHexString(byte[] buffer) {
        char[] tmp = new char[buffer.length * 3];

        for (int i = 0, j = 0; i < buffer.length; i++) {
            int a = (buffer[i] & 0xff) >> 4;
            int b = (buffer[i] & 0x0f);
            tmp[j++] = (char)(a < 10 ? '0' + a : 'A' + a - 10);
            tmp[j++] = (char)(b < 10 ? '0' + b : 'A' + b - 10);
            tmp[j++] = ' ';
        }

        return new String(tmp);
    }

    private void runAsync(final PinpadRunnable r, final String message) {
        mProgressDialog = new ProgressDialog(this);
        if (message != null) {
            mProgressDialog.setMessage(message);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Pinpad pinpad = PinpadManager.pinpad();

                    try {
                        //PinpadManager.showBusy(pinpad);
                        r.run(pinpad, mProgressDialog);
                        PinpadManager.initScreen(pinpad);
                    } catch (PinpadException e) {
                        e.printStackTrace();
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        String stacktrace = sw.toString();
                        warn("Pinpad error: " + stacktrace);
                    } catch (IOException e) {
                        e.printStackTrace();
                        fail("I/O error: " + e.getMessage());
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("Critical error: " + e.getMessage());
                    }
                } finally {
                    if (message != null) {
                        mProgressDialog.dismiss();
                    }
                }
            }
        });
        thread.start();
    }

    private void initPinpad() {
    	log("*** Init Pinpad ***", true);

        // Enable debug information
        Pinpad.setDebug(true);

        // Set listener not notify when Pinpad is disconnected.
        PinpadManager.pinpad().setPinpadListener(new Pinpad.PinpadListener() {
            @Override
            public void onPinpadRelease() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), R.string
                                .msg_pinpad_connection_is_closed, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        });

        runAsync(new PinpadRunnable() {
            @Override
            public void run(final Pinpad pinpad, ProgressDialog dialog) throws IOException {
                log("Get device information");
                DeviceInfo devInfo = pinpad.getIdentification();
                log("  Serial number: " + devInfo.getDeviceSerialNumber());
                log("  Firmware name: " + devInfo.getFirmwareName());
                log("  Application name: " + devInfo.getApplicationName());
                log("  Application version: " + devInfo.getApplicationVersion());

                log("Init display");
                PinpadManager.initScreen(pinpad);

                log("Set time");
                pinpad.sysSetDate(Calendar.getInstance());

                log("Enable events");
                pinpad.sysEnableEvents(Pinpad.ENABLE_BARCODE);

                // Register callback for terminals with barcode engines
                pinpad.setBarcodeListener(new BarcodeListener() {
                    @Override
                    public void onBarcodeRead() {
                        Barcode barcode;
                        try {
                            barcode = pinpad.barGetBarcodeData();
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }

                        String barcodeType = barcode.getTypeString();
                        String barcodeData = barcode.getDataString();
                        log("  Barcode: (" + barcodeType + ") " + barcodeData);
                    }
                });
            }
        }, getString(R.string.msg_please_wait));
    }

    private void readTag() {
        log("*** Read tag ***", true);

        runAsync(new PinpadRunnable() {
            @Override
            public void run(final Pinpad pinpad, final ProgressDialog dialog) throws IOException {
                RC663 rc663 = null;

                // Set RFID module debug output.
                RFID.setDebug(true);

                try {
                    final boolean[] status = { false };

                    // Create instance of RF module.
                    rc663 = pinpad.rfidGetModule();

                    // Update UI to notify the user that must present card. 
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setMessage("PLEASE, PRESENT CARD");
                        }
                    });

                    // Register event to listen for cards presents
                    rc663.setCardListener(new CardListener() {
                        @Override
                        public void onCardDetect(ContactlessCard card) {
                            // Update UI to notify the user what we are doing 
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setMessage("PROCESSING CARD...");
                                }
                            });

                            try {
                                processTag(dialog, card);
                            } catch (IOException e) {
                                e.printStackTrace();
                                fail("Tag processing failed: " + e.getMessage());
                            }

                            status[0] = true;

                            synchronized(mSyncRoot) {
                                mSyncRoot.notify();
                            }
                        }
                    });

                    // Enable RF module.
                    rc663.enable();

                    // Gives some time to complete operation.
                    synchronized (mSyncRoot) {
                        try {
                            mSyncRoot.wait(10000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (!status[0]) {
                        warn("No card detected");
                    }

                    // Disable RF module as soon as with finished with it to save power.
                    rc663.disable();
                } finally {
                    if (rc663 != null) {
                        rc663.close();
                    }
                }
            }
        }, getString(R.string.msg_reading_tag));
    }

    private void processTag(final ProgressDialog dialog,  ContactlessCard contactlessCard) throws
            IOException {
        if (contactlessCard instanceof ISO14443Card) {
            ISO14443Card card = (ISO14443Card)contactlessCard;
            log("  ISO14 card: " + byteArrayToHexString(card.uid));

            /*
             // 16 bytes reading and 16 bytes writing
             // Try to authenticate first with default key
            byte[] key= new byte[] {-1, -1, -1, -1, -1, -1};
            // It is best to store the keys you are going to use once in the device memory, 
            // then use AuthByLoadedKey function to authenticate blocks rather than having the key in your program
            card.authenticate('A', 8, key);
            
            // Write data to the card
            byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F };            
            card.write16(8, input);
            
            // Read data from card
            byte[] result = card.read16(8);
            */
        } else if (contactlessCard instanceof ISO15693Card) {
            ISO15693Card card = (ISO15693Card)contactlessCard;
            log("  ISO15 card: " + byteArrayToHexString(card.uid));
            log("  Block size: " + card.blockSize);
            log("  Max blocks: " + card.maxBlocks);

            /*
            if (card.blockSize > 0) {
                byte[] security = card.getBlocksSecurityStatus(0, 16);
                ...
                
                // Write data to the card
                byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03 };
                card.write(0, input);
                ...
                
                // Read data from card
                byte[] result = card.read(0, 1); 
                ...
            }
            */
        } else if (contactlessCard instanceof FeliCaCard) {
            FeliCaCard card = (FeliCaCard)contactlessCard;
            log("  FeliCa card: " + byteArrayToHexString(card.uid));
               
            /*
            // Write data to the card
            byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F };            
            card.write(0x0900, 0, input);
            ...
            
            // Read data from card
            byte[] result = card.read(0x0900, 0, 1);
            ...
            */
        } else if (contactlessCard instanceof STSRICard) {
            STSRICard card = (STSRICard)contactlessCard;
            log("  STSRI card: " + byteArrayToHexString(card.uid));
            log("  Block size: " + card.blockSize);

            /*
            // Write data to the card
            byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03 };
            card.writeBlock(8, input);
            ...
            
            // Try reading two blocks
            byte[] result = card.readBlock(8);
            ...
            */
        } else {
            log("  Contactless card: " + byteArrayToHexString(contactlessCard.uid));
        }

        log("Wait to remove card");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog.setMessage("PLEASE REMOVE CARD");
            }
        });

        // Wait silently to remove card         
        try {
            contactlessCard.waitRemove();
        } catch (IOException e) {
            e.printStackTrace();
        }

        log("  Card removed");
    }

    private void loadKeys() {
        log("*** Load keys ***", true);

        runAsync(new PinpadRunnable() {
            @Override
            public void run(final Pinpad pinpad, ProgressDialog dialog) throws IOException {
                log("Load TR31 Master Key");
                byte[] masterKeyBlock = Backend.getMasterKeyBlockTR31();
                try {
                    pinpad.cryptoExchangeCBCKey(TMK_KEY_INDEX, TR31_MASTER_KEY_INDEX, Pinpad
                            .KEY_TR31, 1, masterKeyBlock);
                } catch (PinpadException e) {
                    if (e.getErrorCode() != Pinpad.ERROR_DUPLICATE_KEY) {
                        throw e;
                    }
                    warn("TR31 Master Key is already loaded");
                }

                log("Load Data Key");
                byte[] dataKeyBlock = Backend.getDataKeyBlockTR31();
                try {
                    pinpad.cryptoExchangeCBCKey(TR31_MASTER_KEY_INDEX, DATA_KEY_INDEX, Pinpad
                            .KEY_TR31, 1, dataKeyBlock);
                } catch (PinpadException e) {
                    if (e.getErrorCode() != Pinpad.ERROR_DUPLICATE_KEY) {
                        throw e;
                    }
                    warn("Data Key is already loaded");
                }

                log("Load PIN Key");
                byte[] pinKeyBlock = Backend.getDUKPTKeyBlockTR31();
                try {
                    pinpad.cryptoExchangeCBCKey(TR31_MASTER_KEY_INDEX, PIN_KEY_INDEX, Pinpad
                            .KEY_TR31, 1, pinKeyBlock);
                } catch (PinpadException e) {
                    if (e.getErrorCode() != Pinpad.ERROR_DUPLICATE_KEY) {
                        throw e;
                    }
                    warn("PIN Key is already loaded");
                }

                log("Save keys to NVRAM");
                pinpad.cryptoSaveKeysToFlash();
            }
        }, getString(R.string.msg_loading_keys));
    }

    private void loadConfiguration() {
        log("*** Load configuration ***", true);

    	runAsync(new PinpadRunnable() {
			@Override
			public void run(final Pinpad pinpad, ProgressDialog dialog) throws IOException {
                log("Initialize Payment Engine");
                pinpad.emv2Initialize();

                log("Load contactless configuration data");
			    byte[] clData;
                try {
                    clData = Backend.readConfig(getAssets().open("config/cl.xml"));
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Invalid configuration data");
                    return;
                }
                pinpad.emv2LoadContactlessConfiguration(clData);

                log("Load contactless CA keys");
                byte[] clCAKeys;
                try {
                    clCAKeys = Backend.readConfig(getAssets().open("ContactlessPublicCertificationAuthorityKeys.xml"));
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Invalid CA keys");
                    return;
                }
                pinpad.emv2LoadContactlessCAPK(clCAKeys);

                log("Load contact configuration data");
                byte[] chipData;
                try {
                    chipData = Backend.readConfig(getAssets().open("config/chip.xml"));
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Invalid configuration data");
                    return;
                }
                pinpad.emv2LoadContactConfiguration(chipData);

                log("Load contact CA keys");
                byte[] chipCAKeys;
                try {
                    chipCAKeys = Backend.readConfig(getAssets().open("config/ca.xml"));
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Invalid CA keys");
                    return;
                }
                pinpad.emv2LoadContactCAPK(chipCAKeys);

                log("Deinitialise Payment Engine");
                pinpad.emv2Deinitialise();
			}
		}, getString(R.string.msg_loading_configuration));
    }

    private void startTransaction() {
        log("*** Start transaction ***", true);

		runAsync(new PinpadRunnable() {
            @Override
            public void run(final Pinpad pinpad, final ProgressDialog dialog) throws IOException {
                final ByteArrayOutputStream transactionBuffer = new ByteArrayOutputStream();

                log("Initialize Payment Engine");
                pinpad.emv2Initialize();

                log("Lock keyboard");
                pinpad.uiKeyboardControl(true);

                pinpad.setEmv2Listener(new Emv2Listener() {
                    @Override
                    public void onUpdateUserInterface(int id, int status, int hold) {
                        log("Update user interface");

                        final String message;
                        switch (id) {
                            // EMV2 message codes
                            case Pinpad.EMV2_MESSAGE_NOT_WORKING:
                                message = "NOT WORKING";
                                break;
                            case Pinpad.EMV2_MESSAGE_APPROVED:
                                message = "APPROVED";
                                break;
                            case Pinpad.EMV2_MESSAGE_DECLINED:
                                message = "DECLINED";
                                break;
                            case Pinpad.EMV2_MESSAGE_PLEASE_ENTER_PIN:
                                message = "PLEASE ENTER PIN";
                                break;
                            case Pinpad.EMV2_MESSAGE_ERROR_PROCESSING:
                                message = "ERROR PROCESSING";
                                break;
                            case Pinpad.EMV2_MESSAGE_REMOVE_CARD:
                                message = "REMOVE CARD";
                                break;
                            case Pinpad.EMV2_MESSAGE_PRESENT_CARD:
                                message = "PRESENT CARD";
                                break;
                            case Pinpad.EMV2_MESSAGE_IDLE:
                                message = "IDLE";
                                break;
                            case Pinpad.EMV2_MESSAGE_PROCESSING:
                                message = "PROCESSING";
                                break;
                            case Pinpad.EMV2_MESSAGE_CARD_READ_OK_REMOVE:
                                message = "CARD READ OK REMOVE";
                                break;
                            case Pinpad.EMV2_MESSAGE_TRY_OTHER_INTERFACE:
                                message = "TRY OTHER INTERFACE";
                                break;
                            case Pinpad.EMV2_MESSAGE_CARD_COLLISION:
                                message = "CARD COLLISION";
                                break;
                            case Pinpad.EMV2_MESSAGE_SIGN_APPROVED:
                                message = "SIGN APPROVED";
                                break;
                            case Pinpad.EMV2_MESSAGE_ONLINE_AUTHORISATION:
                                message = "ONLINE AUTHORISATION";
                                break;
                            case Pinpad.EMV2_MESSAGE_TRY_OTHER_CARD:
                                message = "TRY OTHER CARD";
                                break;
                            case Pinpad.EMV2_MESSAGE_INSERT_CARD:
                                message = "INSERT CARD";
                                break;
                            case Pinpad.EMV2_MESSAGE_CLEAR_DISPLAY:
                                message = "CLEAR DISPLAY";
                                break;
                            case Pinpad.EMV2_MESSAGE_SEE_PHONE:
                                message = "SEE PHONE";
                                break;
                            case Pinpad.EMV2_MESSAGE_PRESENT_CARD_AGAIN:
                                message = "PRESENT CARD AGAIN";
                                break;
                            case Pinpad.EMV2_MESSAGE_NA:
                                message = "N/A";
                                break;
                            default:
                                message = "N/A " + id;
                        }

                        log("  Message: " + message);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                dialog.setMessage(message);
                            }
                        });
                    }

                    @Override
                    public void onTransactionFinish(byte[] data) {
                        log("Finish transaction");

                        synchronized (mSyncRoot) {
                            log("  @Tag: ", BerTlv.createList(data));
                            try {
                                transactionBuffer.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            int[] tagList = Backend.ONLINE_REQUEST_TAGS;
                            log("Read EMV tags");
                            byte[] encTags;
                            //try {
                                //encTags = pinpad.emv2GetTagsEncrypted(tagList,
                                //        Pinpad.TAGS_FORMAT_DATECS,
                                //        Pinpad.KEY_TYPE_3DES_CBC,
                                //        DATA_KEY_INDEX,
                                //        0xAABBCCDD);
                            //} catch (IOException e) {
                             //   e.printStackTrace();
                             //   fail("Pinpad error: " + e.getMessage());
                            //    try {
                            //        pinpad.emv2CancelTransaction();
                            //    } catch (IOException e1) {
                            //        e1.printStackTrace();
                             //   }
                             //   return;
                            // }

                            // Decryption is made with demonstration purpose only.
                            byte[] decTags = null;
                            //try {
                                //decTags = Backend.decryptData(encTags);
                             //   log("  EMV DATA on Finish: " + byteArrayToHexString(decTags));
                            //} catch (Exception e) {
                             //   warn("Failed to decrypt data");
                            //}
                          //  PrintData(decTags);
                            mSyncRoot.notify();
                        }
                    }

                    @Override
                    public void onOnlineAuthorisationRequest(byte[] data) {
                        log("Process ONLINE request");
                        log("  @Tag: ", BerTlv.createList(data));

                        // Sample tag list: 5F2A,5F34,82,95,9A,9C,9F02,...
                        int[] tagList = Backend.ONLINE_REQUEST_TAGS;
                       int[]  tagListPlain = {
                             /*   EmvTags.TAG_5F20_CARDHOLDER_NAME,
                                EmvTags.TAG_8E_CARDHOLDER_VERIFICATION_METHOD_LIST,
                                EmvTags.TAG_9F34_CARDHOLDER_VERIFICATION_METHOD_RESULTS,
                                EmvTags.TAG_50_APPLICATION_LABEL,
                                EmvTags.TAG_84_DEDICATED_FILE_NAME,
                                EmvTags.TAG_4F_APPLICATION_IDENTIFIER*/
                                EmvTags.TAG_57_TRACK_2_EQUIVALENT_DATA,
                                EmvTags.TAG_5A_APPLICATION_PRIMARY_ACCOUNT_NUMBER,
                                EmvTags.TAG_84_DEDICATED_FILE_NAME
                        };

                        log("Read EMV tags");
                        byte[] encTags=null;
                        //ENCRYPTED_TAGS
                        try {
                            byte[] ksn = pinpad.dukptGenerateKeyOnline(PIN_KEY_INDEX);
                            encTags = pinpad.emv2GetTagsEncrypted(tagList,
                                    Pinpad.TAGS_FORMAT_DATECS,
                                    Pinpad.KEY_TYPE_DUKPT_3DES_CBC,
                                    00,
                                    0x00000000);

                            if (encTags.length == 68) {
                                System.out.println("Tag list read from the card is EMPTY !");
                                pinpad.emv2CancelTransaction();
                                return;
                            }

                            System.out.println("Encrypted : " + byteArrayToHexString(encTags));
                            byte[] key = Backend.calculateDataKey(ksn, Backend.IPEK);
                            //byte[] decTags = Backend.decryptData(encTags,key);
                            //log("\nDecrypted tags DUKPT : "+ byteArrayToHexString(decTags)+"\n");
                            //encTags = Arrays.copyOfRange(encTags, 4, encTags.length);
                            //System.out.println("After copy :" + byteArrayToHexString(encTags));
                        } catch (IOException e) {
                            e.printStackTrace();
                            fail("Pinpad error: " + e.getMessage());
                            try {
                                pinpad.emv2CancelTransaction();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            return;
                        }
                        byte[] PlaineTags=null;
                        try {
                            PlaineTags = pinpad.emv2GetTagsPlain(tagListPlain);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        log("Plain tags " + byteArrayToHexString(PlaineTags)+"\n");
                        // Decryption is made with demonstration purpose only.
                        byte[] decTags = null;
                        try {

                          //  decTags = Backend.decryptData(encTags);
                           // log("  EMV DATA: " + byteArrayToHexString(decTags));

                        } catch (Exception e) {
                            warn("Failed to decrypt data");
                        }
//                        PrintData(decTags);
                        // Get Cardholder Verification Result
                        byte[] pinKSN = null, pinBlock = null;
                        BerTlv tagC2 = BerTlv.find(data, EmvTags.TAG_C2_CVM_RESULT);
                        int cvmResult = tagC2.getValueInt();
                        if (cvmResult == Pinpad.EMV2_CVM_ONLINE_PIN) {
                            log("  CVM Result: ONLINE PIN");

                            // Get ISO0 pin block
                            try {
                                // Generate new DUKPT key, which stay active up to 3 minutes and
                                // will be erased after method uiVerifyPINOnline is called.
                                pinKSN = pinpad.dukptGenerateKeyOnline(PIN_KEY_INDEX);
                                log(" KSN: " + byteArrayToHexString(pinKSN));

                                pinBlock = pinpad.uiVerifyPINOnline(Pinpad.DUKPT,
                                        Pinpad.ISO1, new byte[16], PIN_KEY_INDEX, null);

                                // Decryption is made with demonstration purpose only.
                                byte[] pin = Backend.decryptPin(pinKSN, pinBlock);
                                log("  PIN (ISO0): " + byteArrayToHexString(pin));
                            } catch (IOException e) {
                                e.printStackTrace();
                                return;
                            }
                        } else if (cvmResult == Pinpad.EMV2_CVM_CONFIG_CODE_VERIFIED) {
                            log("  CVM Result: CONFIG CODE VERIFIED");
                        } else if (cvmResult == Pinpad.EMV2_CVM_OBTAIN_SIGNATURE) {
                            log("  CVM Result: OBTAIN SIGNATURE");
                        } else if (cvmResult == Pinpad.EMV2_CVM_NO_CVM) {
                            log("  CVM Result: NO CVM");
                        } else if (cvmResult == Pinpad.EMV2_CVM_OFFLINE_PIN) {
                            log("  CVM Result: OFFLINE PIN");
                        }

                        // Get Payment interface
                        BerTlv tagC3 = BerTlv.find(data, EmvTags.TAG_C3_PAYMENT_INTERFACE);
                        int paymentInterface = tagC3.getValueInt();
                        if (paymentInterface == Pinpad.EMV2_INTERFACE_CONTACT) {
                            log("  Payment Interface: CONTACT");
                        } else if (paymentInterface == Pinpad.EMV2_INTERFACE_CONTACTLESS) {
                            log("  Payment Interface: CONTACTLESS");
                        } else if (paymentInterface == Pinpad.EMV2_INTERFACE_MAGNETIC) {
                            log("  Payment Interface: INTERFACE_MAGNETIC");
                            // Get track2 equivalent data.

                            if (decTags != null) {
                                BerTlv tag57 = BerTlv.find(decTags, EmvTags.TAG_57_TRACK_2_EQUIVALENT_DATA);

                                String track = tag57.getValueHexString();
                                // Convert from EMV track data to magnetic track data.
                                if (track.endsWith("F")) {
                                    track = track.substring(0, track.length() - 1);
                                }
                                track = ";" + track.replaceAll("D", "=") + "?";
                                log("  TRACK2: " + track + "\n");
                            }

                        } else if (paymentInterface == Pinpad.EMV2_INTERFACE_MANUAL) {
                            log("  Payment Interface: MANUAL");
                        }

                        // Get transaction host result
                        byte[] response = Backend.processOnline(pinKSN, pinBlock, encTags);

                        List<BerTlv> onlineResult = new ArrayList<>();
                        if (response != null) {
                            onlineResult.add(new BerTlv(EmvTags.TAG_C2_ONLINE_RESULT, "01"));
                            onlineResult.add(new BerTlv(EmvTags.TAG_E6_ONLINE_DATA, response));
                        } else {
                            onlineResult.add(new BerTlv(EmvTags.TAG_C2_ONLINE_RESULT, "00"));
                        }

                        try {
                            log("Set online result");
                            log("  @Tag: ", onlineResult);
                            pinpad.emv2SetOnlineResult(BerTlv.listToByteArray(onlineResult));
                        } catch (IOException e) {
                            e.printStackTrace();
                            log("Pinpad error: " + e.getMessage());
                        }
                    }
                });

                // Constructs input parameters
                List<BerTlv> inputParams = new ArrayList<>();
                // Mandatory tags

                byte[] am = new byte[6];
                byte[] TT= new byte[1];
                byte[] ref= {0x20};
                String  ID = TT_.getText().toString();
                am = Backend.hexStringToByteArray(amount_.getText().toString());
                TT = Backend.hexStringToByteArray(TT_.getText().toString());
                System.out.println("TT : "+ byteArrayToHexString(TT)+" " );
                if (Arrays.equals(TT,ref)){
                    System.out.println("HERE ! ! ! ");
                    log("Loading refund configurations\n");
                    byte[] clData;
                    try {
                        clData = Backend.readConfig(getAssets().open("refund.xml"));
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("Invalid configuration data");
                        return;
                    }
                    pinpad.emv2LoadContactlessConfiguration(clData);

                }
                inputParams.add(new BerTlv(EmvTags.TAG_9C_TRANSACTION_TYPE, parse(TT,1)));
                inputParams.add(new BerTlv(EmvTags.TAG_9F02_AMOUNT_AUTHORISED, parse(am, 6)));
                inputParams.add(new BerTlv(EmvTags.TAG_5F2A_TRANSACTION_CURRENCY_CODE, "0978"));
                // Optional tags
                //inputParams.add(new BerTlv(EmvTags.TAG_9F33_TERMINAL_CAPABILITIES, "6060C8"));
                //inputParams.add(new BerTlv(EmvTags.TAG_C1_TRANSACTION_CONFIGURATION, "01"));
                //inputParams.add(new BerTlv(0xC2, "20"));

                int paymentInterface = Pinpad.EMV2_INTERFACE_CONTACT |
                        Pinpad.EMV2_INTERFACE_CONTACTLESS |
                        Pinpad.EMV2_INTERFACE_MAGNETIC |
                        Pinpad.EMV2_INTERFACE_MANUAL;

                pinpad.emv2StartTransaction(paymentInterface,
                        0 /* Flags */,
                        BerTlv.listToByteArray(inputParams),
                        DEFAULT_TIMEOUT / 1000 /* Seconds */);

                synchronized (mSyncRoot) {
                    while (transactionBuffer.size() == 0) {
                        if (pinpad.isActive()) {
                            try {
                                mSyncRoot.wait(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            throw new IOException("Connection closed");
                        }
                    }
                }
                byte[] transactionData = transactionBuffer.toByteArray();

                BerTlv tagC1 = BerTlv.find(transactionData, EmvTags.TAG_C1_TRANSACTION_RESULT);
                int transactionResult = tagC1.getValueInt();
                if (transactionResult == Pinpad.EMV2_RESULT_ABORTED) {
                    warn("  Result: ABORTED");
                } else if (transactionResult == Pinpad.EMV2_RESULT_APPROVED) {
                    log("  Result: APPROVED");
                } else if (transactionResult == Pinpad.EMV2_RESULT_DECLINED) {
                    warn("  Result: DECLINED");
                } else if (transactionResult == Pinpad.EMV2_RESULT_TRY_ANOTHER_INTERFACE) {
                    warn("  Result: TRY ANOTHER INTERFACE");
                } else if (transactionResult == Pinpad.EMV2_RESULT_END_APPLICATION) {
                    warn("  Result: END APPLICATION");
                }

                if (transactionResult != Pinpad.EMV2_RESULT_APPROVED) {
                    // Use tag C4 to determinate failure reason.
                    BerTlv tagC4 = BerTlv.find(transactionData, EmvTags.TAG_C4_TRANSACTION_FAILURE_REASON);
                    if (tagC4 != null) {
                        // First byte from tag C4 specify transaction failure result.
                        int failureReason = tagC4.getValueInt() >> 8;

                        if (failureReason == Pinpad.EMV2_FAILURE_REASON_FAILED) {
                            log("  Failure reason: Transaction failed");

                            // Second byte from tag C4 specify Pinpad error code
                            int errorCode = tagC4.getValueInt() & 0xff;
                            log("  Error code: " + errorCode);
                        } else if (failureReason == Pinpad.EMV2_FAILURE_REASON_TIMEOUT) {
                            log("  Failure reason: Transaction timeout");
                        } else if (failureReason == Pinpad.EMV2_FAILURE_REASON_CANCELED) {
                            log("  Failure reason: Transaction is canceled");
                        }
                    }
                }

                log("Deinitialise Payment Engine");
                pinpad.emv2Deinitialise();
            }
        }, getString(R.string.msg_processing_transaction));
    }
    private void PrintData(byte[] tags) {
        final Map<Tag, byte[]> tagMap = BerTlv.createMap(tags);

        byte[] tag56Value = tagMap.get(new Tag(0x56));
        if (tag56Value != null) {
            log("  Track 1: " + byteArrayToHexString(tag56Value) + "\n");
        }

        byte[] tag57Value = tagMap.get(new Tag(0x57));
        if (tag57Value != null) {
            log("  Track 2: " + byteArrayToHexString(tag57Value) + "\n");
        }

        byte[] tag5AValue = tagMap.get(new Tag(0x5A));
        if (tag5AValue != null) {
            log("  PAN: " + byteArrayToHexString(tag5AValue) + "\n");
        }
        System.out.println("Printing : TVR");
        byte[] tag95Value = tagMap.get(new Tag(0x95));
        if (tag95Value != null) {
            log("  TVR: " + byteArrayToHexString(tag95Value) + "\n");
        }
        System.out.println("Printing : Expirety");
        byte[] tag5F24Value = tagMap.get(new Tag(0x5F24));
        if (tag5F24Value != null) {
            log("  Expiry: " + byteArrayToHexString(tag5F24Value) + "\n");
        }
        System.out.println("Printing : Amount");
        byte[] tag9F02Value = tagMap.get(new Tag(0x9F02));
        if (tag9F02Value != null) {
            log("  Amount: " + byteArrayToHexString(tag9F02Value) + "\n");
        }else log("\nNo Amount ! \n");

        System.out.println("Printing : cardhodlder name");
        byte[] tag5F20Value = tagMap.get(new Tag(0x5F20));
        if (tag5F20Value != null) {
            log("  Name: " + new String(tag5F20Value) + "\n");
        } else log("\nNo cardholder name! \n");
    }
    public static int[] ENCRYPTED_TAGS = {
            EmvTags.TAG_57_TRACK_2_EQUIVALENT_DATA,
            EmvTags.TAG_5A_APPLICATION_PRIMARY_ACCOUNT_NUMBER
    };
    public static final byte[] parse(byte[] data,int lenght)
    {
        if (data.length==lenght){
            return data;
        }else {
            byte[] sup = new byte[lenght];
            System.arraycopy(data,0,sup,lenght-data.length,data.length);
            return sup;
        }

    }
}
