package com.datecs.pinpaddemo.connectivity;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class NetworkConnector extends AbstractConnector {

    private String mHost;
    private int mPort;
    private Socket mSocket;

    public NetworkConnector(Context context, String host, int port) {
        super(context);
        mHost = host;
        mPort = port;
        mSocket = new Socket();
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    @Override
    public void connect() throws IOException {
        SocketAddress address = new InetSocketAddress(mHost, mPort);
        mSocket.connect(address);
        mSocket.setSoTimeout(0);
        mSocket.setTcpNoDelay(true);
    }

    @Override
    public void close() throws IOException {
        mSocket.shutdownInput();
        mSocket.shutdownOutput();
        mSocket.close();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return mSocket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return mSocket.getOutputStream();
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof NetworkConnector) {
            NetworkConnector connector = (NetworkConnector)o;
            return mHost.equals(connector.mHost) && mPort == connector.mPort;
        }
        return false;
    }
}
