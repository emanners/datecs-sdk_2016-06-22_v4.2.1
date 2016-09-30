package com.datecs.lineademo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private LineaAction mCallbacks;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        bindPreference("beep_upon_scan");
        bindPreference("scan_button");
        bindPreference("battery_charge");
        bindPreference("power_max_current");
        bindPreference("external_speaker");
        bindPreference("external_speaker_button");
        bindPreference("vibrate_upon_scan");
        bindPreference("device_timeout_period");
        bindPreference("code128_symbology");
        bindPreference("barcode_scan_mode");
        bindPreference("barcode_scope_scale_mode");

        findPreference("reset_barcode_engine").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCallbacks.actionResetBarcodeEngine();
                return true;
            }
        });

        findPreference("switch_all_in_green").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCallbacks.actionSetLed(false, true, false);
                return true;
            }
        });

        findPreference("switch_all_in_red").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCallbacks.actionSetLed(true, false, false);
                return true;
            }
        });

        findPreference("switch_all_in_yellow").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCallbacks.actionSetLed(true, true, false);
                return true;
            }
        });

        findPreference("switch_all_in_blue").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCallbacks.actionSetLed(false, false, true);
                return true;
            }
        });

        findPreference("switch_all_in_white").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mCallbacks.actionSetLed(false, false, false);
                return true;
            }
        });

        findPreference("update_firmware").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final File[] fileList = listFirmwareFiles("");

                if (fileList.length == 0) {
                    Toast.makeText(getActivity(), R.string.msg_firmware_not_found, Toast.LENGTH_SHORT).show();
                } else if (fileList.length > 1) {
                    Dialog dialog = createListFileDialog(fileList, 0);
                    dialog.show();
                } else {
                    updateFirmware(fileList[0], 0);
                }

                return true;
            }
        });

        findPreference("update_barcode_firmware").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final File[] fileList = listFirmwareFiles("/Barcode");

                if (fileList.length == 0) {
                    Toast.makeText(getActivity(), R.string.msg_firmware_not_found, Toast.LENGTH_SHORT).show();
                } else if (fileList.length > 1) {
                    Dialog dialog = createListFileDialog(fileList, 1);
                    dialog.show();
                } else {
                    updateFirmware(fileList[0], 1);
                }

                return true;
            }
        });

        findPreference("about").setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                String message = getString(R.string.app_version, BuildConfig.VERSION_NAME);
                AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.app_name)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create();
                dialog.show();
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
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

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = findPreference(key);
        if (preference == null)
            return;

        if (preference instanceof ListPreference) {
            ((ListPreference) preference).setValue(sharedPreferences.getString(key, ""));
        } else if (preference instanceof CheckBoxPreference) {
            ((CheckBoxPreference) preference).setChecked(sharedPreferences.getBoolean(key, false));
        }
    }

    private void bindPreference(String key) {
        Preference preference = findPreference(key);

        if (preference instanceof ListPreference) {
            preference.setSummary(((ListPreference) preference).getEntry());
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int i = ((ListPreference) preference).findIndexOfValue(newValue.toString());
                    CharSequence[] entries = ((ListPreference) preference).getEntries();
                    preference.setSummary(entries[i]);
                    setSetting(preference.getKey(), (String) newValue);
                    return true;
                }
            });
        }
        else if (preference instanceof CheckBoxPreference) {
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setSetting(preference.getKey(), newValue.toString());
                    return true;
                }
            });
        }
    }

    private void setSetting(String key, String value) {
        mCallbacks.actionUpdateSetting(key, value);
    }

    private File[] listFirmwareFiles(String subPath) {
        String path = Environment.getExternalStorageDirectory().toString() + "/" + getString(R.string.app_name) + subPath;
        File[] fileList = new File(path).listFiles();
        List<File> firmwareFiles = new ArrayList<>();

        if (fileList != null) {
            for (File file: fileList) {
                if (file.isFile()) {
                    firmwareFiles.add(file);
                }
            }
        }

        File[] result = new File[firmwareFiles.size()];
        return firmwareFiles.toArray(result);
    }

    private Dialog createListFileDialog(final File[] files, final int mode) {
        String[] fileList = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileList[i] = files[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.msg_select_firmware);
        builder.setSingleChoiceItems(fileList, -1, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                updateFirmware(files[whichButton], mode);
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                updateFirmware(files[whichButton], mode);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        return builder.create();
    }

    private void updateFirmware(File file, final int mode) {
        final String name = file.getName();
        final String path = file.getAbsolutePath();

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.msg_question_update_firmware, name))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        getActivity().onBackPressed();
                        mCallbacks.actionUpdateFirmware(path, mode);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();
        dialog.show();
    }


}
