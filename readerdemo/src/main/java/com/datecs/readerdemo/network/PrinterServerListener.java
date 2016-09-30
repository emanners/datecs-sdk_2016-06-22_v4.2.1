package com.datecs.readerdemo.network;

import java.net.Socket;

public interface PrinterServerListener {
    public void onConnect(Socket socket);
}
