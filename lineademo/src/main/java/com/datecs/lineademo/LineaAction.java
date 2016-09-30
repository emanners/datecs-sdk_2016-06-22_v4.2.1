package com.datecs.lineademo;

public interface LineaAction {
    void actionResetBarcodeEngine();
    void actionUpdateSetting(String key, String value);
    void actionSetLed(boolean red, boolean green, boolean blue);
    void actionUpdateFirmware(String path, int mode);
    void actionTurnOff();
    void actionStartScan();
    void actionStopScan();
    void actionReadInformation();
}
