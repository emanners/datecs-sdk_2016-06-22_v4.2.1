package com.datecs.lineademo.view;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.datecs.linea.LineaPro;
import com.datecs.lineademo.R;

/**
 * Implements a battery view control.
 */
public class BatteryView extends LinearLayout {

    private ImageView mBatteryView;
    private ImageView mStateView;
    private TextView mTextView;

    public BatteryView(Context context) {
        super(context);
        initViews(context, null);
    }

    public BatteryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViews(context, attrs);
    }

    public BatteryView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
        initViews(context, attrs);
    }

    private void initViews(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.battery, this);

        mBatteryView = (ImageView) this.findViewById(R.id.battery_percent);
        mStateView = (ImageView) this.findViewById(R.id.battery_state);
        mTextView = (TextView) this.findViewById(R.id.battery_text);
        update(null);
    }

    public void update(LineaPro.BatteryInfo batteryInfo) {
        if (batteryInfo == null) {
            mBatteryView.setImageResource(R.drawable.battery3);
            mStateView.setImageResource(R.drawable.question);
            mTextView.setText("");
        } else {
            LineaPro.BatteryInfo.Fuelgauge fuelgauge = batteryInfo.getFuelgauge();
            if (fuelgauge != null) {
                mTextView.setTextColor(Color.WHITE);
            } else {
                mTextView.setTextColor(Color.parseColor("#FFA600"));
            }

            int voltage = batteryInfo.getVoltage();
            int initialCapacity = batteryInfo.getInitialCapacity();
            int capacity = batteryInfo.getCapacity();
            int healthLevel = batteryInfo.getHealthLevel();
            boolean isCharging = batteryInfo.isCharging();

            if (isCharging) {
                mStateView.setImageResource(R.drawable.flash);
            } else {
                mStateView.setImageDrawable(null);
            }

            if (capacity <= 15) {
                mBatteryView.setImageResource(R.drawable.battery1);
            } else if (capacity <= 30) {
                mBatteryView.setImageResource(R.drawable.battery2);
            } else if (capacity <= 45) {
                mBatteryView.setImageResource(R.drawable.battery3);
            } else if (capacity <= 60) {
                mBatteryView.setImageResource(R.drawable.battery4);
            } else if (capacity <= 75) {
                mBatteryView.setImageResource(R.drawable.battery5);
            } else {
                mBatteryView.setImageResource(R.drawable.battery6);
            }

            StringBuffer sb = new StringBuffer();
            sb.append("" + voltage + "mV\n");
            sb.append("" + initialCapacity + "mAh\n");
            sb.append("SoC: " + capacity + "%\n");
            sb.append("SoH: " + healthLevel + "%");
            mTextView.setText(sb.toString());
        }
    }

    public void reset() {
        update(null);
    }
}
