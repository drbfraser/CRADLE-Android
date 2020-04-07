package com.cradle.neptune.view.ui.settings;


import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.view.LoginActivity;
import com.cradle.neptune.view.ui.settings.ui.settingnamedpairs.HealthFacilitiesActivity;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * A simple {@link androidx.fragment.app.Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    // Data Model
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    Settings settings;
    @Inject
    ReadingManager readingManager;

//    private SharedPreferences sharedPreferences;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        // inject:
        ((MyApp) getActivity().getApplication()).getAppComponent().inject(this);

        // initialize
        setPreferencesFromResource(R.xml.preferences, rootKey);

        setupHealthCentres();
        setupSignOut();
    }

    private void setupSignOut() {
        Preference signout = findPreference("signout");
        if (signout != null) {
            signout.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    List<Reading> unUploadedReadings = readingManager.getUnuploadedReadings();

                    String description = getString(R.string.normalSignoutMessage);
                    if (!unUploadedReadings.isEmpty()) {
                        description = unUploadedReadings.size() + getString(R.string.unUploadedReadingSignoutMessage);
                    }
                    AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                            .setTitle("Sign out?").setMessage(description)
                            .setPositiveButton("Yes", (dialog, which) ->
                                    signoutTheUser())
                            .setNegativeButton("No", null).setIcon(R.drawable.ic_sync)
                            .create();
                    alertDialog.show();
                    return true;

                }
            });
        }
    }

    private void signoutTheUser() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(LoginActivity.LOGIN_EMAIL, "");
        editor.putInt(LoginActivity.LOGIN_PASSWORD, LoginActivity.DEFAULT_PASSWORD);
        editor.putString(LoginActivity.TOKEN, "");
        editor.putString(LoginActivity.USER_ID, "");
        editor.apply();
        readingManager.deleteAllData(getActivity());
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivity(intent);
        getActivity().finishAffinity();
    }

    private void setupHealthCentres() {
        Preference hcPref = findPreference("setting_health_centres");

        if (hcPref != null) {
            hcPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Log.d("bugg", "You clicked " + preference.getKey());

                Intent intent = new Intent(getActivity(), HealthFacilitiesActivity.class);

                   // Intent intent = SettingNamedPairsActivity.makeLaunchIntent(getContext(),
                     //       SettingNamedPairsActivity.SelectPair.SELECT_PAIR_HEALTH_CENTRES);
                    startActivity(intent);
                    return true;
                }
            });
        }
    }


    // source: https://stackoverflow.com/a/44110510/3475174
    @Override
    public void onResume() {
        super.onResume();

        // register as observer
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        initSummary(getPreferenceScreen());
    }

    @Override
    public void onPause() {
        // unregister as observer
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }


    // observer callback
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefsArg, String key) {
        Preference pref = getPreferenceScreen().findPreference(key);
        if (pref != null) {
            setSummary(pref);
        }
        Log.d("bugg", "setting: " + pref);
        settings.loadFromSharedPrefs();

    }


    // update whole screen
    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            setSummary(p);
        }
    }


    /**
     * Summaries
     */
    private void setSummary(Preference pref) {
        // special case prefs

        // PIN
        if (pref.getKey().equals("setting_pin")) {
            String pin = ((EditTextPreference) pref).getText();
            String stars = pin.length() > 0 ?
                    Util.makeRepeatString("*", pin.length())
                    : "Not set";
            pref.setSummary(stars);
        }

        // health centres
        else if (pref.getKey().equals("setting_health_centres")) {
            String summary = readingManager.getUserSelectedFacilities().size() + " configured health centres";
            pref.setSummary(summary);
        }

        // generic
        else if (pref instanceof EditTextPreference) {
            updateSummary((EditTextPreference) pref);
        } else if (pref instanceof ListPreference) {
            updateSummary((ListPreference) pref);
        } else if (pref instanceof MultiSelectListPreference) {
            updateSummary((MultiSelectListPreference) pref);
        }
    }

    private void updateSummary(MultiSelectListPreference pref) {
        pref.setSummary(Arrays.toString(pref.getValues().toArray()));
    }

    private void updateSummary(ListPreference pref) {
        pref.setSummary(pref.getValue());
    }

    private void updateSummary(EditTextPreference preference) {
        preference.setSummary(preference.getText());
    }
}