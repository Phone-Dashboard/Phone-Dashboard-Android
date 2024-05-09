package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsAcknowledgementsActivity extends AppCompatActivity {
    private PhoneDashboardPreferenceFragment mSettingsFragment = null;

    public static class PhoneDashboardPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener  {
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            this.setPreferencesFromResource(R.xml.settings_acknowledgements, rootKey);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

        }
    }

    @SuppressWarnings("ConstantConditions")
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.setTitle(R.string.title_settings_acknowledgements);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FragmentManager fragment = this.getSupportFragmentManager();

        FragmentTransaction transaction = fragment.beginTransaction();

        this.mSettingsFragment = new PhoneDashboardPreferenceFragment();

        transaction.replace(android.R.id.content, this.mSettingsFragment);

        transaction.commit();
    }

    protected void onResume() {
        super.onResume();

        AppLogger.getInstance(this).log("settings_acknowledgements_screen_appeared");
    }

    protected void onPause() {
        super.onPause();

        AppLogger.getInstance(this).log("settings_acknowledgements_screen_dismissed");
    }

    @SuppressLint("InflateParams")
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
        {
            AppLogger.getInstance(this).log("settings_acknowledgements_menu_home");

            this.finish();
        }

        return true;
    }
}
