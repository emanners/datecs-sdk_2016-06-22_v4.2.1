package com.datecs.printerdemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.datecs.BuildInfo;
import com.datecs.biometric.AnsiIso;
import com.datecs.biometric.TouchChip;
import com.datecs.biometric.TouchChip.Identity;
import com.datecs.biometric.TouchChip.ImageReceiver;
import com.datecs.biometric.TouchChipException;
import com.datecs.card.FinancialCard;
import com.datecs.emsr.EMSR;
import com.datecs.emsr.EMSR.EMSRInformation;
import com.datecs.emsr.EMSR.EMSRKeyInformation;
import com.datecs.printer.Printer;
import com.datecs.printer.Printer.ConnectionListener;
import com.datecs.printer.PrinterInformation;
import com.datecs.printer.ProtocolAdapter;
import com.datecs.printerdemo.view.FingerprintView;
import com.datecs.rfid.ContactlessCard;
import com.datecs.rfid.FeliCaCard;
import com.datecs.rfid.ISO14443Card;
import com.datecs.rfid.ISO15693Card;
import com.datecs.rfid.RC663;
import com.datecs.rfid.RC663.CardListener;
import com.datecs.rfid.STSRICard;

import java.io.IOException;

public class PrinterActivity extends Activity {

    private static final String TAG = "PrinterDemo";

    // Interface, used to invoke asynchronous printer operation.
    private interface PrinterRunnable {
        void run(ProgressDialog dialog, Printer printer) throws IOException;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer);

        // Show Android device information and API version.
        final TextView txtVersion = (TextView) findViewById(R.id.txt_version);
        txtVersion.setText(Build.MANUFACTURER + " " + Build.MODEL + ", Datecs API "
                + BuildInfo.VERSION);

        findViewById(R.id.btn_read_information).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                readInformation();
            }
        });

        findViewById(R.id.btn_print_self_test).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                printSelfTest();
            }
        });

        findViewById(R.id.btn_print_text).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                printText();
            }
        });

        findViewById(R.id.btn_print_image).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                printImage();
            }
        });

        findViewById(R.id.btn_print_page).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                printPage();
            }
        });

        findViewById(R.id.btn_print_barcode).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                printBarcode();
            }
        });

        findViewById(R.id.btn_read_card).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                readCard();
            }
        });
        
        findViewById(R.id.btn_read_barcode).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Read barcode waiting 10 seconds
                readBarcode(10);
            }
        });

        findViewById(R.id.btn_fingerprint).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.content_buttons).setVisibility(View.INVISIBLE);
                findViewById(R.id.content_fingerprint).setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.btn_fingerprint_enrol).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String identity = ((EditText)findViewById(R.id.edit_fingerprint_identity)).getText().toString();
                int identityLen = identity.length();
                
                if (identityLen >= 1 && identityLen <= 100) {
                    ((FingerprintView) findViewById(R.id.fingerprint)).setText(""); // Clear content
                    enrolNewIdentity(identity);
                } else {
                    warning(getString(R.string.msg_invalid_identity));
                }
            }
        });
        
        findViewById(R.id.btn_fingerprint_delete_all).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAllIdentities();
            }
        });
        
        findViewById(R.id.btn_fingerprint_check).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ((FingerprintView) findViewById(R.id.fingerprint)).setText(""); // Clear content
                checkIdentity();
            }
        });
        
        findViewById(R.id.btn_fingerprint_get).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ((FingerprintView) findViewById(R.id.fingerprint)).setText(""); // Clear content
                getIdentity();
            }
        });

        init();
    }

    @Override
    protected void onStop() {
        super.onStop();
        PrinterManager.instance.close();
        finish();
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.content_buttons).getVisibility() == View.INVISIBLE) {
            findViewById(R.id.content_buttons).setVisibility(View.VISIBLE);
            findViewById(R.id.content_fingerprint).setVisibility(View.INVISIBLE);            
        } else {
            super.onBackPressed();
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

    private void init() {
        ProtocolAdapter protocolAdapter = PrinterManager.instance.getProtocolAdapter();
        if (protocolAdapter.isProtocolEnabled()) {
            protocolAdapter.setPrinterListener(new ProtocolAdapter.PrinterListener() {
                @Override
                public void onThermalHeadStateChanged(boolean overheated) {
                    if (overheated) {
                        Log.d(TAG, "Thermal head is overheated");
                        status("OVERHEATED");
                    } else {
                        status(null);
                    }
                }

                @Override
                public void onPaperStateChanged(boolean noPaper) {
                    if (noPaper) {
                        Log.d(TAG, "Event: Paper out");
                        status("PAPER OUT");
                    } else {
                        status(null);
                    }
                }

                @Override
                public void onBatteryStateChanged(boolean lowBattery) {
                    if (lowBattery) {
                        Log.d(TAG, "Low battery");
                        status("LOW BATTERY");
                    } else {
                        status(null);
                    }
                }
            });

            // Set barcode listener if  printer support barcode reader.
            protocolAdapter.setBarcodeListener(new ProtocolAdapter.BarcodeListener() {
                @Override
                public void onReadBarcode() {
                    Log.d(TAG, "On read barcode");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            readBarcode(0);
                        }
                    });
                }
            });

            protocolAdapter.setCardListener(new ProtocolAdapter.CardListener() {
                @Override
                public void onReadCard(boolean encrypted) {
                    Log.d(TAG, "On read card(entrypted=" + encrypted + ")");

                    if (encrypted) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                readCardEncrypted();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                readCard();
                            }
                        });
                    }
                }
            });
        }

        RC663 rc663 = PrinterManager.instance.getRC663();
        if (rc663 != null) {
            rc663.setCardListener(new CardListener() {
                @Override
                public void onCardDetect(ContactlessCard card) {
                    processContactlessCard(card);
                }
            });
        }

        Printer printer = PrinterManager.instance.getPrinter();
        printer.setConnectionListener(new ConnectionListener() {
            @Override
            public void onDisconnect() {
                warning("Printer is disconnected");
                finish();
            }
        });
    }

    private void warning(final String text) {
        Log.d(TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void error(final String text) {
        Log.w(TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void dialog(final int iconResId, final String title, final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(PrinterActivity.this);
                builder.setIcon(iconResId);
                builder.setTitle(title);
                builder.setMessage(msg);
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });

                AlertDialog dlg = builder.create();
                dlg.show();
            }
        });
    }

    private void status(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (text != null) {
                    findViewById(R.id.panel_status).setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.txt_status)).setText(text);
                } else {
                    findViewById(R.id.panel_status).setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private void runTask(final PrinterRunnable r, final int msgResId) {
        final ProgressDialog dialog = new ProgressDialog(PrinterActivity.this);
        dialog.setTitle(getString(R.string.title_please_wait));
        dialog.setMessage(getString(msgResId));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Printer printer = PrinterManager.instance.getPrinter();
                    r.run(dialog, printer);
                } catch (IOException e) {
                    e.printStackTrace();
                    error("I/O error occurs: " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                    error("Critical error occurs: " + e.getMessage());
                    finish();
                } finally {
                    dialog.dismiss();
                }
            }
        });
        t.start();
    }

    private void readInformation() {
        Log.d(TAG, "Read information");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {                                
                StringBuffer textBuffer = new StringBuffer();
                PrinterInformation pi = printer.getInformation();

                textBuffer.append("PRINTER:");
                textBuffer.append("\n");
                textBuffer.append("Name: " + pi.getName());
                textBuffer.append("\n");
                textBuffer.append("Version: " + pi.getFirmwareVersionString());
                textBuffer.append("\n");
                textBuffer.append("\n");

                EMSR emsr = PrinterManager.instance.getEMSR();
                if (emsr != null) {
                    EMSRInformation devInfo = emsr.getInformation();
                    EMSRKeyInformation kekInfo = emsr.getKeyInformation(EMSR.KEY_AES_KEK);
                    EMSRKeyInformation aesInfo = emsr.getKeyInformation(EMSR.KEY_AES_DATA_ENCRYPTION);
                    EMSRKeyInformation desInfo = emsr.getKeyInformation(EMSR.KEY_DUKPT_MASTER);

                    textBuffer.append("ENCRYPTED MAGNETIC HEAD:");
                    textBuffer.append("\n");
                    textBuffer.append("Name: " + devInfo.name);
                    textBuffer.append("\n");
                    textBuffer.append("Serial: " + devInfo.serial);
                    textBuffer.append("\n");
                    textBuffer.append("Version: " + devInfo.version);
                    textBuffer.append("\n");
                    textBuffer.append("KEK Version: "
                            + (kekInfo.tampered ? "Tampered" : kekInfo.version));
                    textBuffer.append("\n");
                    textBuffer.append("AES Version: "
                            + (aesInfo.tampered ? "Tampered" : aesInfo.version));
                    textBuffer.append("\n");
                    textBuffer.append("DUKPT Version: "
                            + (desInfo.tampered ? "Tampered" : desInfo.version));
                }
              
                dialog(R.drawable.ic_info, getString(R.string.printer_info), textBuffer.toString());                
            }
        }, R.string.title_read_information);
    }

    private void printSelfTest() {
        Log.d(TAG, "Print Self Test");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                printer.printSelfTest();
                printer.flush();
            }
        }, R.string.msg_printing_self_test);
    }

    private void printText() {
        Log.d(TAG, "Print Text");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                StringBuffer textBuffer = new StringBuffer();
                textBuffer.append("{reset}{center}{w}{h}RECEIPT");
                textBuffer.append("{br}");
                textBuffer.append("{br}");
                textBuffer.append("{reset}1. {b}First item{br}");
                textBuffer.append("{reset}{right}{h}$0.50 A{br}");
                textBuffer.append("{reset}2. {u}Second item{br}");
                textBuffer.append("{reset}{right}{h}$1.00 B{br}");
                textBuffer.append("{reset}3. {i}Third item{br}");
                textBuffer.append("{reset}{right}{h}$1.50 C{br}");
                textBuffer.append("{br}");
                textBuffer.append("{reset}{right}{w}{h}TOTAL: {/w}$3.00  {br}");
                textBuffer.append("{br}");
                textBuffer.append("{reset}{center}{s}Thank You!{br}");

                printer.reset();
                printer.printTaggedText(textBuffer.toString());
                printer.feedPaper(110);
                printer.flush();                                
            }
        }, R.string.msg_printing_text);
    }

    private void printImage() {
        Log.d(TAG, "Print Image");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;

                final AssetManager assetManager = getApplicationContext().getAssets();
                final Bitmap bitmap = BitmapFactory.decodeStream(assetManager.open("sample.png"),
                        null, options);
                final int width = bitmap.getWidth();
                final int height = bitmap.getHeight();
                final int[] argb = new int[width * height];
                bitmap.getPixels(argb, 0, width, 0, 0, width, height);
                bitmap.recycle();

                printer.reset();
                printer.printCompressedImage(argb, width, height, Printer.ALIGN_CENTER, true);
                printer.feedPaper(110);
                printer.flush();
            }
        }, R.string.msg_printing_image);
    }

    private void printPage() {
        Log.d(TAG, "Print Page");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                PrinterInformation pi = printer.getInformation();

                if (!pi.isPageSupported()) {
                    dialog(R.drawable.ic_page, getString(R.string.title_warning),
                            getString(R.string.msg_unsupport_page_mode));
                    return;
                }

                printer.reset();
                printer.selectPageMode();

                printer.setPageRegion(0, 0, 160, 320, Printer.PAGE_LEFT);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH I{br}");
                printer.drawPageRectangle(0, 0, 160, 32, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from left to right"
                        + ", feed to bottom. Starting point in left top corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.setPageRegion(160, 0, 160, 320, Printer.PAGE_TOP);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH II{br}");
                printer.drawPageRectangle(160 - 32, 0, 32, 320, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from top to bottom"
                        + ", feed to left. Starting point in right top corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.setPageRegion(160, 320, 160, 320, Printer.PAGE_RIGHT);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH III{br}");
                printer.drawPageRectangle(0, 320 - 32, 160, 32, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from right to left"
                        + ", feed to top. Starting point in right bottom corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.setPageRegion(0, 320, 160, 320, Printer.PAGE_BOTTOM);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH IV{br}");
                printer.drawPageRectangle(0, 0, 32, 320, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from bottom to top"
                        + ", feed to right. Starting point in left bottom corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.printPage();
                printer.selectStandardMode();
                printer.feedPaper(110);
                printer.flush();
            }
        }, R.string.msg_printing_page);
    }

    private void printBarcode() {
        Log.d(TAG, "Print Barcode");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                printer.reset();

                printer.setBarcode(Printer.ALIGN_CENTER, false, 2, Printer.HRI_BELOW, 100);
                printer.printBarcode(Printer.BARCODE_CODE128AUTO, "123456789012345678901234");
                printer.feedPaper(38);

                printer.setBarcode(Printer.ALIGN_CENTER, false, 2, Printer.HRI_BELOW, 100);
                printer.printBarcode(Printer.BARCODE_EAN13, "123456789012");
                printer.feedPaper(38);

                printer.setBarcode(Printer.ALIGN_CENTER, false, 2, Printer.HRI_BOTH, 100);
                printer.printBarcode(Printer.BARCODE_CODE128, "ABCDEF123456");
                printer.feedPaper(38);

                printer.setBarcode(Printer.ALIGN_CENTER, false, 2, Printer.HRI_NONE, 100);
                printer.printBarcode(Printer.BARCODE_PDF417, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                printer.feedPaper(38);

                printer.setBarcode(Printer.ALIGN_CENTER, false, 2, Printer.HRI_NONE, 100);
                printer.printQRCode(4, 3, "http://www.datecs.bg");
                printer.feedPaper(38);

                printer.feedPaper(110);
                printer.flush();
            }
        }, R.string.msg_printing_barcode);
    }

    private void readCard() { 
        Log.d(TAG, "Read card");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                PrinterInformation pi = printer.getInformation();
                FinancialCard card = null;

                String[] tracks;
                if (pi.getName().startsWith("CMP-10")) {
                    // The printer CMP-10 can read only two tracks at once.
                    tracks = printer.readCard(true, true, false, 15000);
                } else {
                    tracks = printer.readCard(true, true, true, 15000);
                }

                if (tracks != null) {
                    StringBuilder textBuffer = new StringBuilder();

                    if (tracks[0] == null && tracks[1] == null && tracks[2] == null) {
                        textBuffer.append(getString(R.string.no_card_read));
                    } else {
                        if (tracks[0] != null) {
                            card = new FinancialCard(tracks[0]);
                        } else if (tracks[1] != null) {
                            card = new FinancialCard(tracks[1]);
                        }

                        if (card != null) {
                            textBuffer.append(getString(R.string.card_no) + ": " + card.getNumber());
                            textBuffer.append("\n");
                            textBuffer.append(getString(R.string.holder) + ": " + card.getName());
                            textBuffer.append("\n");
                            textBuffer.append(getString(R.string.exp_date)
                                    + ": "
                                    + String.format("%02d/%02d", card.getExpiryMonth(),
                                            card.getExpiryYear()));
                            textBuffer.append("\n");
                        }

                        if (tracks[0] != null) {
                            textBuffer.append("\n");
                            textBuffer.append(tracks[0]);

                        }
                        if (tracks[1] != null) {
                            textBuffer.append("\n");
                            textBuffer.append(tracks[1]);
                        }
                        if (tracks[2] != null) {
                            textBuffer.append("\n");
                            textBuffer.append(tracks[2]);
                        }
                    }

                    dialog(R.drawable.ic_card, getString(R.string.card_info), textBuffer.toString()); 
                }
            }
        }, R.string.msg_reading_magstripe);
    }

    private void readCardEncrypted() {
        Log.d(TAG, "Read card encrypted");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                EMSR emsr = PrinterManager.instance.getEMSR();
                byte[] buffer = emsr.readCardData(EMSR.MODE_READ_TRACK1 | EMSR.MODE_READ_TRACK2
                        | EMSR.MODE_READ_TRACK3 | EMSR.MODE_READ_PREFIX);
                StringBuffer textBuffer = new StringBuffer();

                int encryptionType = (buffer[0] >>> 3);
                // Trim extract encrypted block.
                byte[] encryptedData = new byte[buffer.length - 1];
                System.arraycopy(buffer, 1, encryptedData, 0, encryptedData.length);

                if (encryptionType == EMSR.ENCRYPTION_TYPE_OLD_RSA
                        || encryptionType == EMSR.ENCRYPTION_TYPE_RSA) {
                    try {
                        String[] result = CryptographyHelper.decryptTrackDataRSA(encryptedData);
                        textBuffer.append("Track2: " + result[0]);
                        textBuffer.append("\n");
                    } catch (Exception e) {
                        error("Failed to decrypt RSA data: " + e.getMessage());
                        return;
                    }
                } else if (encryptionType == EMSR.ENCRYPTION_TYPE_AES256) {
                    try {
                        String[] result = CryptographyHelper.decryptAESBlock(encryptedData);

                        textBuffer.append("Random data: " + result[0]);
                        textBuffer.append("\n");
                        textBuffer.append("Serial number: " + result[1]);
                        textBuffer.append("\n");
                        if (result[2] != null) {
                            textBuffer.append("Track1: " + result[2]);
                            textBuffer.append("\n");
                        }
                        if (result[3] != null) {
                            textBuffer.append("Track2: " + result[3]);
                            textBuffer.append("\n");
                        }
                        if (result[4] != null) {
                            textBuffer.append("Track3: " + result[4]);
                            textBuffer.append("\n");
                        }
                    } catch (Exception e) {
                        error("Failed to decrypt AES data: " + e.getMessage());
                        return;
                    }
                } else if (encryptionType == EMSR.ENCRYPTION_TYPE_IDTECH) {
                    try {
                        String[] result = CryptographyHelper.decryptIDTECHBlock(encryptedData);

                        textBuffer.append("Card type: " + result[0]);
                        textBuffer.append("\n");
                        if (result[1] != null) {
                            textBuffer.append("Track1: " + result[1]);
                            textBuffer.append("\n");
                        }
                        if (result[2] != null) {
                            textBuffer.append("Track2: " + result[2]);
                            textBuffer.append("\n");
                        }
                        if (result[3] != null) {
                            textBuffer.append("Track3: " + result[3]);
                            textBuffer.append("\n");
                        }
                    } catch (Exception e) {
                        error("Failed to decrypt IDTECH data: " + e.getMessage());
                        return;
                    }
                } else {
                    textBuffer.append("Encrypted block: " + byteArrayToHexString(buffer));
                    textBuffer.append("\n");
                }

                dialog(R.drawable.ic_card, getString(R.string.card_info), textBuffer.toString());
            }
        }, R.string.msg_reading_magstripe);
    }

    private void readBarcode(final int timeout) {
        Log.d(TAG, "Read Barcode");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                String barcode = printer.readBarcode(timeout);

                if (barcode != null) {
                    dialog(R.drawable.ic_read_barcode, getString(R.string.barcode), barcode);
                }
            }
        }, R.string.msg_reading_barcode);
    }
    
    private void enrolNewIdentity(final String identity) {
        Log.d(TAG, "Enrol new identity");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                TouchChip tc = printer.getTouchChip();

                try {
                    tc.enrolIdentity(identity);
                } catch (TouchChipException e) {
                    error("Failed to enrol identity: " + e.getMessage());
                }
                
            }
        }, R.string.msg_enrol_identity);
    }
    
    private void deleteAllIdentities() {
        Log.d(TAG, "Delete all identities");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                final TouchChip tc = printer.getTouchChip();

                try {
                    int[] slots = tc.listSlots();
                    
                    for (int slot: slots) {
                        tc.deleteIdentity(slot);
                    }
                } catch (TouchChipException e) {
                    error("Failed to delete fingerprints: " + e.getMessage());
                }
                
            }
        }, R.string.msg_delete_all_identities);
    }
    
    private void checkIdentity() {
        Log.d(TAG, "Check identity");

        runTask(new PrinterRunnable() {
            @Override
            public void run(final ProgressDialog dialog, Printer printer) throws IOException {
                final TouchChip tc = printer.getTouchChip();
                
                final Identity identity;                
                try {
                    identity = tc.checkIdentity();
                    
                } catch (TouchChipException e) {
                    error("Failed to check identity: " + e.getMessage());
                    return;
                }          
                                
                runOnUiThread(new Runnable() {
                    public void run() {
                        FingerprintView v = (FingerprintView) findViewById(R.id.fingerprint);
                        v.setText(identity.getIdentityAsString());
                    }
                });
            }
        }, R.string.msg_check_identity);
    }
    
    private void getIdentity() {
        Log.d(TAG, "Get identity");

        runTask(new PrinterRunnable() {
            @Override
            public void run(final ProgressDialog dialog, Printer printer) throws IOException {
                final TouchChip tc = printer.getTouchChip();                
                
                final AnsiIso ansiIso;
                try {
                    ImageReceiver receiver = new ImageReceiver() {                        
                        @Override
                        public void onDataReceived(int totalSize, int bytesRecv, byte[] data) {
                            final String message = getString(R.string.msg_downloading_image) + (100 * bytesRecv / totalSize) + "%";
        
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    dialog.setMessage(message);                                   
                                }
                            });                            
                        }
                    };
                    ansiIso = tc.getIdentity(TouchChip.IMAGE_SIZE_SMALL, TouchChip.IMAGE_FORMAT_ISO, TouchChip.IMAGE_COMPRESSION_NONE, receiver);
                } catch (TouchChipException e) {
                    error("Failed to get identity: " + e.getMessage());
                    return;
                }          
                
                runOnUiThread(new Runnable() {                    
                    @Override
                    public void run() {                        
                        System.out.println("General Header: " + ansiIso.getHeaderAsHexString());
                        System.out.println("Format identifier: " + ansiIso.getFormatIdentifierAsString());
                        System.out.println("Version number: " + ansiIso.getVersionNumberAsString());
                        System.out.println("Record length: " + ansiIso.getRecordLength());
                        System.out.println("CBEFF Product Identifier: " + ansiIso.getProductIdentifierAsHexString());
                        System.out.println("Capture device ID: " + ansiIso.getCaptureDeviceIDAsHexString());
                        System.out.println("Number of fingers/palms: " + ansiIso.getNumberOfFingers());
                        System.out.println("Scale Units: " + ansiIso.getScaleUnits());
                        System.out.println("Scan resolution (horiz): " + ansiIso.getScanResolutionHorz());
                        System.out.println("Scan resolution (vert): " + ansiIso.getScanResolutionVert());
                        System.out.println("Image resolution (horiz): " + ansiIso.getImageResolutionHorz());
                        System.out.println("Image resolution (vert): " + ansiIso.getImageResolutionVert());
                        System.out.println("Pixel depth: " + ansiIso.getPixelDepth());
                        System.out.println("Image compression algorithm: " + ansiIso.getImageCompressionAlgorithm());
                        System.out.println("Length of finger data block: " + ansiIso.getFingerDataBlockLength());
                        System.out.println("Finger/palm position: " + ansiIso.getFingerPosition());
                        System.out.println("Count of views: " + ansiIso.getCountOfViews());
                        System.out.println("View number: " + ansiIso.getViewNumber());
                        System.out.println("Finger/palm image quality: " + ansiIso.getFingerImageQuality());
                        System.out.println("Impression type: " + ansiIso.getImpressionType());
                        System.out.println("Horizontal line length: " + ansiIso.getHorizontalLineLength());
                        System.out.println("Vertical line length: " + ansiIso.getVerticalLineLength());
                        
                        FingerprintView v = (FingerprintView) findViewById(R.id.fingerprint);
                        v.setImage(ansiIso.getHorizontalLineLength(), ansiIso.getVerticalLineLength(), ansiIso.getImageData());
                    }
                });
            }
        }, R.string.msg_get_identity);
    }
    
    private void processContactlessCard(ContactlessCard contactlessCard) { 
        final StringBuilder msgBuf = new StringBuilder();

        if (contactlessCard instanceof ISO14443Card) {            
            ISO14443Card card = (ISO14443Card)contactlessCard;            
            msgBuf.append("ISO14 card: " + byteArrayToHexString(card.uid) + "\n");
            msgBuf.append("ISO14 type: " +card.type + "\n");
                              
            if (card.type == ContactlessCard.CARD_MIFARE_DESFIRE) {
                // Speed up card reading
                PrinterManager.instance.getPrinterChannel().suspend();
                try {
                    
                    card.getATS();                    
                    Log.d(TAG, "Select application");
                    card.DESFire().selectApplication(0x78E127);
                    Log.d(TAG, "Application is selected");
                    msgBuf.append("DESFire Application: " + Integer.toHexString(0x78E127) + "\n");
                } catch (IOException e) {
                    Log.e(TAG, "Select application", e);
                } finally {
                    PrinterManager.instance.getPrinterChannel().resume();
                }
            }
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
            
            msgBuf.append("ISO15 card: " + byteArrayToHexString(card.uid) + "\n");
            msgBuf.append("Block size: " + card.blockSize + "\n");
            msgBuf.append("Max blocks: " + card.maxBlocks + "\n");

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
            
            msgBuf.append("FeliCa card: " + byteArrayToHexString(card.uid) + "\n");
               
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
            
            msgBuf.append("STSRI card: " + byteArrayToHexString(card.uid) + "\n");
            msgBuf.append("Block size: " + card.blockSize + "\n");

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
            msgBuf.append("Contactless card: " + byteArrayToHexString(contactlessCard.uid));
        }
                
        dialog(R.drawable.ic_tag, getString(R.string.tag_info), msgBuf.toString());
        
        // Wait silently to remove card 
        try {
            contactlessCard.waitRemove();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
