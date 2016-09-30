package com.datecs.pinpaddemo;

import android.os.SystemClock;

import com.datecs.pinpad.DeviceInfo;
import com.datecs.pinpad.Pinpad;
import com.datecs.pinpad.PinpadException;
import com.datecs.pinpaddemo.connectivity.AbstractConnector;

import java.io.IOException;

public class PinpadManager {

    private static AbstractConnector sConnector;
    private static Pinpad sPinpad;
    private static DeviceInfo sDeviceInfo;

    public static void init(AbstractConnector connector) throws IOException {
        sConnector = connector;
        sPinpad = new Pinpad(sConnector.getInputStream(), sConnector.getOutputStream());
        sDeviceInfo = sPinpad.getIdentification();
    }

    public static void release() {
        if (sPinpad != null) {
            sPinpad.release();
        }
        if (sConnector != null) {
            try {
                sConnector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Pinpad pinpad() {
        return sPinpad;
    }

    public static DeviceInfo getDeviceInfo() {
        return sDeviceInfo;
    }

    // Show initial application screen.
    public static void initScreen(Pinpad pinpad) throws PinpadException, IOException {
        pinpad.uiInitScreen();
        pinpad.uiKeyboardControl(false);

        if (pinpad.getDisplayHeight() > 32) {
            pinpad.uiOpenTextWindow(0, 0, 16, 4, Pinpad.FONT_8X16, Pinpad.CP_LATIN1);
        } else {
            // Small screen
            pinpad.uiOpenTextWindow(0, 0, 16, 2, Pinpad.FONT_8X16, Pinpad.CP_LATIN1);
        }
    }

    // Clear screen.
    public static void clearScreen(Pinpad pinpad) throws PinpadException, IOException {
        pinpad.sysStatusLine(false);
        pinpad.uiStopAnimation(-1);
        pinpad.uiFillScreen(Pinpad.COLOR_WHITE);
    }

    /** Play sound that imitate 'SUCCESS'. */
    public static void beepSuccess(Pinpad pinpad) throws PinpadException, IOException {
        pinpad.sysBeep(5300, 500, 50);
        SystemClock.sleep(500);
    }

    /** Play sound that imitate 'FAILURE'. */
    public static void beepFailure(Pinpad pinpad) throws PinpadException, IOException {
        pinpad.sysBeep(2000, 100, 100);
        SystemClock.sleep(20);
        pinpad.sysBeep(2000, 100, 100);
        SystemClock.sleep(20);
        pinpad.sysBeep(2000, 100, 100);
        SystemClock.sleep(20);
        pinpad.sysBeep(2000, 100, 100);
        SystemClock.sleep(20);
        pinpad.sysBeep(2000, 100, 100);
        SystemClock.sleep(20);
    }

    /** Play sound that imitate 'ATTENTION'. */
    public static void beepAttention(Pinpad pinpad) throws PinpadException, IOException {
        pinpad.sysBeep(2000, 250, 50);
        SystemClock.sleep(250);
    }

    /** Shows that transaction is aborted. */
    public static void showAborted(Pinpad pinpad) throws PinpadException, IOException {
        clearScreen(pinpad);
        pinpad.uiDrawString("\u0001  TRANSACTION\n    ABORTED");
        beepFailure(pinpad);
        SystemClock.sleep(1000);
        initScreen(pinpad);
    }

    /** Shows that transaction is canceled. */
    public static void showCanceled(Pinpad pinpad) throws PinpadException, IOException {
        clearScreen(pinpad);
        pinpad.uiDrawString("\u0001  TRANSACTION\n   CANCELLED");
        beepFailure(pinpad);
        SystemClock.sleep(1000);
        initScreen(pinpad);
    }

    /** Shows that transaction complete successful. */
    public static void showSuccessful(Pinpad pinpad) throws PinpadException, IOException  {
        clearScreen(pinpad);
        pinpad.uiDrawString("\u0001  TRANSACTION\n   SUCCESSFUL");
        beepSuccess(pinpad);
        SystemClock.sleep(1000);
        initScreen(pinpad);
    }

    /** Shows that transaction is declined. */
    public static void showDeclined(Pinpad pinpad) throws PinpadException, IOException  {
        clearScreen(pinpad);
        pinpad.uiDrawString("\u0001  TRANSACTION\n   DECLINED");
        beepFailure(pinpad);
        SystemClock.sleep(1000);
        initScreen(pinpad);
    }

    /** Shows that operation is in progress. */
    public static final void showBusy(Pinpad pinpad) throws PinpadException, IOException  {
        clearScreen(pinpad);
        pinpad.uiDrawString("\u0001   PLEASE WAIT");
    }

}
