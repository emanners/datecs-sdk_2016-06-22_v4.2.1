package com.datecs.android.hardware.usb;

import com.datecs.linea.LineaPro;

public interface LineaConnection {

    void onLineaConnected(LineaPro lineaPro);

    void onLineaDisconnected(LineaPro lineaPro);

    void onLineaDebug(String message);

}
