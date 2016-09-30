package com.datecs.printerdemo.connectivity;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class AbstractConnector {

    private Context mContext;
    
    public AbstractConnector(Context context) {
        this.mContext = context;
    }
    
    public Context getContext() {
        return mContext;        
    }
    
    public abstract void connect() throws IOException;
    
    public abstract void close() throws IOException;
    
    public abstract InputStream getInputStream() throws IOException;
    
    public abstract OutputStream getOutputStream() throws IOException;

}
