package com.datecs.lineademo;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.datecs.linea.LineaPro;
import com.datecs.lineademo.view.BatteryView;
import com.datecs.lineademo.view.LogView;

/**
 *
 */
public class MainFragment extends Fragment {
    private Handler mHandler;
    private View mRootView;
    private TextView mVersionView;
    private BatteryView mBatteryView;
    private LogView mLogView;
    private LineaPro.BatteryInfo mBatteryInfo;
    private LineaAction mCallbacks;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (mRootView != null) {
            return mRootView;
        }

        mHandler = new Handler();
        mRootView = inflater.inflate(R.layout.fragment_main, container, false);
        mVersionView = (TextView) mRootView.findViewById(R.id.version);
        mVersionView.setText(BuildConfig.VERSION_NAME);
        mBatteryView = (BatteryView) mRootView.findViewById(R.id.battery);
        mBatteryView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mBatteryInfo == null)
                            return;

                        LineaPro.BatteryInfo.Fuelgauge fuelgauge = mBatteryInfo.getFuelgauge();
                        mLogView.add("<I>Battery Information");
                        if (fuelgauge != null) {
                            mLogView.add("<I>  Temperature: " + fuelgauge.getTemperature() + "K");
                            mLogView.add("<I>  Internal temperature: " + fuelgauge.getInternalTemperature() + "K");
                            mLogView.add("<I>  Voltage: " + fuelgauge.getVoltage() + "mV");
                            mLogView.add("<I>  Nominal available capacity: " + fuelgauge.getNominalAvailableCapacity() + "mAh");
                            mLogView.add("<I>  Full available capacity: " + fuelgauge.getFullAvailableCapacity() + "mAh");
                            mLogView.add("<I>  Remaining capacity: " + fuelgauge.getRemainingCapacity() + "mAh");
                            mLogView.add("<I>  Full charge capacity: " + fuelgauge.getFullChargeCapacity() + "mAh");
                            mLogView.add("<I>  Average current: " + fuelgauge.getAverageCurrent() + "mA");
                            mLogView.add("<I>  Standby current: " + fuelgauge.getStandbyCurrent() + "mA");
                            mLogView.add("<I>  Max load current: " + fuelgauge.getMaxLoadCurrent() + "mA");
                            mLogView.add("<I>  Average power: " + fuelgauge.getAveragePower() + "mW");
                            mLogView.add("<I>  State of charge: " + fuelgauge.getStateOfCharge() + "%");
                            mLogView.add("<I>  State of health: " + fuelgauge.getStateOfHealth() + "%");
                        } else {
                            mLogView.add("<I>  Voltage: " + mBatteryInfo.getVoltage() + "mV");
                            mLogView.add("<I>  Capacity: " + mBatteryInfo.getCapacity() + "%");
                            mLogView.add("<I>  Initial capacity: " + mBatteryInfo.getInitialCapacity() + "mAh");
                            mLogView.add("<I>  State of health: " + mBatteryInfo.getHealthLevel() + "%");
                            if (mBatteryInfo.isCharging()) {
                                mLogView.add("<I>  Battery is charging");
                            }
                            mLogView.add("<W>  Fuelgauge not available");
                        }
                    }
                }, 500);
            }
        });
        mLogView = (LogView) mRootView.findViewById(R.id.log);
        mLogView.showTime(false);

        mRootView.findViewById(R.id.btn_scan).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mCallbacks.actionStartScan();
                        break;
                    case MotionEvent.ACTION_UP:
                        mCallbacks.actionStopScan();
                        break;
                }
                return false;
            }
        });

        mRootView.findViewById(R.id.btn_read_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallbacks.actionReadInformation();
            }
        });

        mRootView.findViewById(R.id.btn_turn_off).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mCallbacks.actionTurnOff();
                } catch (Exception ex) {
                    Toast.makeText(getActivity(), ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        mRootView.findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final FragmentManager fragmentManager = getFragmentManager();
                final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.content, new SettingsFragment());
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
            }
        });

        return mRootView;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // This makes sure that the container activity has implemented
            // the callback interface. If not, it throws an exception
            try {
                mCallbacks = (LineaAction) getActivity();
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity().toString()
                        + " must implement LineaAction");
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mCallbacks = (LineaAction) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()
                    + " must implement LineaAction");
        }
    }

    public void addLog(String text) {
        mLogView.add(text);
    }

    public void clearLog() {
        mLogView.clear();
    }

    public void resetBattery() {
        mBatteryView.reset();
    }

    public void updateBattery(LineaPro.BatteryInfo batteryInfo) {
        mBatteryInfo = batteryInfo;
        mBatteryView.update(mBatteryInfo);
    }

}

