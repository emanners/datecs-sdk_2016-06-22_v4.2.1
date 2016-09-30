package com.datecs.android.hardware.usb;

import com.datecs.hub.Hub;

public interface HubConnection {

    void onHubConnected(Hub hub);

    void onHubDisconnected(Hub hub);

    void onHubDebug(String message);

}
