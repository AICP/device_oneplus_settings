/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.doze;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

public class DozeSettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener,
        CompoundButton.OnCheckedChangeListener {

    private static final int CONFIGID_POCKET = com.android.internal.R.bool.config_dozePulseProximity;
    private static final int CONFIGID_TILT = com.android.internal.R.bool.config_dozePulseTilt;

    private TextView mTextView;
    private View mSwitchBar;

    private SwitchPreference mAlwaysOnDisplayPreference;
    private ListPreference mPickUpPreference;
    private SwitchPreference mPocketPreference;

    private Handler mHandler = new Handler();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.doze_settings);
        final ActionBar actionBar = getActivity().getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        SharedPreferences prefs = getActivity().getSharedPreferences("doze_settings",
                Activity.MODE_PRIVATE);
        if (savedInstanceState == null && !prefs.getBoolean("first_help_shown", false)) {
            showHelp();
        }

        boolean dozeEnabled = Utils.isDozeEnabled(getActivity());

        mAlwaysOnDisplayPreference = (SwitchPreference) findPreference(Utils.ALWAYS_ON_DISPLAY);
        mAlwaysOnDisplayPreference.setEnabled(dozeEnabled);
        mAlwaysOnDisplayPreference.setChecked(Utils.isAlwaysOnEnabled(getActivity()));
        mAlwaysOnDisplayPreference.setOnPreferenceChangeListener(this);

        PreferenceCategory pickupSensorCategory = (PreferenceCategory) getPreferenceScreen().
                findPreference(Utils.CATEG_PICKUP_SENSOR);
        PreferenceCategory proximitySensorCategory = (PreferenceCategory) getPreferenceScreen().
                findPreference(Utils.CATEG_PROX_SENSOR);

        mPickUpPreference = (ListPreference) findPreference(Utils.GESTURE_PICK_UP_KEY);
        if (getActivity().getResources().getBoolean(CONFIGID_TILT) != true) {
            mPickUpPreference.getParent().removePreference(mPickUpPreference);
            pickupSensorCategory.getParent().removePreference(pickupSensorCategory);
        } else {
            mPickUpPreference.setEnabled(dozeEnabled);
            mPickUpPreference.setOnPreferenceChangeListener(this);
        }

        mPocketPreference = (SwitchPreference) findPreference(Utils.GESTURE_POCKET_KEY);
        if (getActivity().getResources().getBoolean(CONFIGID_POCKET) != true) {
            mPocketPreference.getParent().removePreference(mPocketPreference);
            proximitySensorCategory.getParent().removePreference(proximitySensorCategory);
        } else {
            mPocketPreference.setEnabled(dozeEnabled);
            mPocketPreference.setOnPreferenceChangeListener(this);
        }

        // Hide AOD if not supported and set all its dependents otherwise
        if (!Utils.alwaysOnDisplayAvailable(getActivity())) {
            getPreferenceScreen().removePreference(mAlwaysOnDisplayPreference);
        } else {
            pickupSensorCategory.setDependency(Utils.ALWAYS_ON_DISPLAY);
            proximitySensorCategory.setDependency(Utils.ALWAYS_ON_DISPLAY);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getContext()).inflate(R.layout.doze, container, false);
        ((ViewGroup) view).addView(super.onCreateView(inflater, container, savedInstanceState));
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean dozeEnabled = Utils.isDozeEnabled(getActivity());

        mTextView = view.findViewById(R.id.switch_text);
        mTextView.setText(getString(dozeEnabled ?
                R.string.switch_bar_on : R.string.switch_bar_off));

        mSwitchBar = view.findViewById(R.id.switch_bar);
        Switch switchWidget = mSwitchBar.findViewById(android.R.id.switch_widget);
        switchWidget.setChecked(dozeEnabled);
        switchWidget.setOnCheckedChangeListener(this);
        mSwitchBar.setActivated(dozeEnabled);
        mSwitchBar.setOnClickListener(v -> {
            switchWidget.setChecked(!switchWidget.isChecked());
            mSwitchBar.setActivated(switchWidget.isChecked());
        });
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (Utils.ALWAYS_ON_DISPLAY.equals(preference.getKey())) {
            Utils.enableAlwaysOn(getActivity(), (Boolean) newValue);
        }
        mHandler.post(() -> Utils.checkDozeService(getActivity()));

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        Utils.enableDoze(getActivity(), isChecked);
        Utils.checkDozeService(getActivity());

        mTextView.setText(getString(isChecked ? R.string.switch_bar_on : R.string.switch_bar_off));
        mSwitchBar.setActivated(isChecked);

        if (!isChecked) {
            Utils.enableAlwaysOn(getActivity(), false);
            mAlwaysOnDisplayPreference.setChecked(false);
        }
        mAlwaysOnDisplayPreference.setEnabled(isChecked);

        mPickUpPreference.setEnabled(isChecked);
        mPocketPreference.setEnabled(isChecked);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return false;
    }

    public static class HelpDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.doze_settings_help_title)
                    .setMessage(R.string.doze_settings_help_text)
                    .setNegativeButton(R.string.dialog_ok, (dialog, which) -> dialog.cancel())
                    .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            getActivity().getSharedPreferences("doze_settings", Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean("first_help_shown", true)
                    .commit();
        }
    }

    private void showHelp() {
        HelpDialogFragment fragment = new HelpDialogFragment();
        fragment.show(getFragmentManager(), "help_dialog");
    }
}
