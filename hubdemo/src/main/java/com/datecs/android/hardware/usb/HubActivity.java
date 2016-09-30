package com.datecs.android.hardware.usb;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * This activity is used as a start point to Accessory connection.
 */
public class HubActivity extends AppCompatActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent serviceIntent = new Intent(this, HubService.class);
        serviceIntent.fillIn(getIntent(), 0);
        startService(serviceIntent);
        finish();
    }
}
