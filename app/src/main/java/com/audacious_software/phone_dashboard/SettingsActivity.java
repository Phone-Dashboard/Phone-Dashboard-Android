package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.DataDisclosureActivity;
import com.audacious_software.passive_data_kit.activities.DataStreamActivity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {
    public static final String TRANSMISSION_INTERVAL = "com.audacious_software.phone_dashboard.SettingsActivity.TRANSMISSION_INTERVAL";
    public static final String TRANSMISSION_INTERVAL_DEFAULT = "" + (5 * 60 * 1000);

    public static final String USER_SNOOZE_DELAY = "com.audacious_software.phone_dashboard.SettingsActivity.USER_SNOOZE_DELAY";
    public static final long USER_SNOOZE_DELAY_DURATION_DEFAULT = 0;

    private static final String TRANSMIT_DATA = "com.audacious_software.phone_dashboard.SettingsActivity.TRANSMIT_DATA";

    private static final String DATA_DISCLOSURE = "com.audacious_software.phone_dashboard.SettingsActivity.DATA_DISCLOSURE";
    private static final String DATA_STREAM = "com.audacious_software.phone_dashboard.SettingsActivity.DATA_STREAM";
    private static final String UPLOAD_DATA = "com.audacious_software.phone_dashboard.SettingsActivity.UPLOAD_DATA";

    private static final String APP_VERSION = "com.audacious_software.phone_dashboard.SettingsActivity.APP_VERSION";

    // private static final String RECEIVES_SUBSIDY = "com.audacious_software.phone_dashboard.SettingsActivity.RECEIVES_SUBSIDY";
    // private static final String BLOCKER_TYPE = "com.audacious_software.phone_dashboard.SettingsActivity.BLOCKER_TYPE";
    // private static final String REMAINING_BUDGET = "com.audacious_software.phone_dashboard.SettingsActivity.REMAINING_BUDGET";
    private static final String SNOOZE_DELAY = "com.audacious_software.phone_dashboard.SettingsActivity.SNOOZE_DELAY";
    private static final String CHANGE_SNOOZE_DELAY = "com.audacious_software.phone_dashboard.SettingsActivity.CHANGE_SNOOZE_DELAY";
    private static final String REFRESH_STUDY_CONFIG = "com.audacious_software.phone_dashboard.SettingsActivity.REFRESH_STUDY_CONFIG";
    private static final String BLOCKING_STATUS = "com.audacious_software.phone_dashboard.SettingsActivity.BLOCKING_STATUS";
    private static final String TREATMENT_STATUS = "com.audacious_software.phone_dashboard.SettingsActivity.TREATMENT_STATUS";
    private static final String APP_CODE = "com.audacious_software.phone_dashboard.SettingsActivity.APP_CODE";
    // private static final String PERIOD_START = "com.audacious_software.phone_dashboard.SettingsActivity.PERIOD_START";
    private static final String ACKNOWLEDGEMENTS = "com.audacious_software.phone_dashboard.SettingsActivity.ACKNOWLEDGEMENTS";
    private static final String SHOW_SNOOZE_DELAY_MESSAGE = "com.audacious_software.phone_dashboard.SettingsActivity.SHOW_SNOOZE_DELAY_MESSAGE";

    private PhoneDashboardPreferenceFragment mSettingsFragment = null;

    public static class PhoneDashboardPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener  {
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            this.setPreferencesFromResource(R.xml.settings, rootKey);

            final SettingsActivity me = (SettingsActivity) this.getActivity();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        public void onPause()
        {
            final SettingsActivity me = (SettingsActivity) this.getActivity();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);
            prefs.unregisterOnSharedPreferenceChangeListener(this);

            super.onPause();
        }

        public boolean onPreferenceTreeClick(final Preference preference)
        {
            final PhoneDashboardPreferenceFragment fragment = this;

            final SettingsActivity me = (SettingsActivity) this.getActivity();

            final AppApplication app = (AppApplication) me.getApplication();

            String key = preference.getKey();

            if (SettingsActivity.UPLOAD_DATA.equals(key) || SettingsActivity.TRANSMIT_DATA.equals(key)) {
                AppLogger.getInstance(me).log("settings_transmit_data");

                Schedule.getInstance(me).transmitUsageSummary(me, true, true, true, true, true, true, true);
                Schedule.getInstance(me).transmitData();

                Toast.makeText(me, R.string.toast_uploading_data, Toast.LENGTH_LONG).show();

                return true;
            } else if (SettingsActivity.DATA_DISCLOSURE.equals(key)) {
                me.startActivity(new Intent(me, DataDisclosureActivity.class));

                AppLogger.getInstance(me).log("settings_launched_data_disclosure");

                return true;
            } else if (SettingsActivity.DATA_STREAM.equals(key)) {
                me.startActivity(new Intent(me, DataStreamActivity.class));

                AppLogger.getInstance(me).log("settings_launched_data_stream");

                return true;
            } else if (SettingsActivity.REFRESH_STUDY_CONFIG.equals(key)) {
                app.refreshConfiguration(true, new Runnable() {
                    @Override
                    public void run() {
                        fragment.refresh();

                        Toast.makeText(me, R.string.toast_config_update_succeeded, Toast.LENGTH_LONG).show();
                    }
                });
            } else if (SettingsActivity.TREATMENT_STATUS.equals(key)) {
                if (app.treatmentActive() == true) {
                    ContextThemeWrapper wrapper = new ContextThemeWrapper(me, R.style.AppTheme);

                    AlertDialog.Builder builder = new AlertDialog.Builder(wrapper);
                    builder.setTitle(R.string.title_opt_out);
                    builder.setMessage(R.string.message_opt_out);

                    builder.setPositiveButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    });

                    builder.setNeutralButton(R.string.action_opt_out, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            app.optOut(new Runnable() {
                                @Override
                                public void run() {
                                    fragment.refresh();
                                }
                            });
                        }
                    });

                    builder.create().show();

                    return true;
                }
            } else if (SettingsActivity.APP_CODE.equals(key)) {
                String identifier = app.getIdentifier();

                String message = me.getString(R.string.toast_app_code_copied, identifier);

                Toast.makeText(me, message, Toast.LENGTH_LONG).show();

                ClipboardManager clipboard = (ClipboardManager) me.getSystemService(CLIPBOARD_SERVICE);

                ClipData clip = ClipData.newPlainText("Phone Dashboard App Code", identifier);

                clipboard.setPrimaryClip(clip);

                return true;
            } else if (SettingsActivity.ACKNOWLEDGEMENTS.equals(key)) {
                me.startActivity(new Intent(me, SettingsAcknowledgementsActivity.class));
                return true;
            } else if (SettingsActivity.APP_VERSION.equals(key)) {
                me.startActivity(new Intent(me, DataStreamActivity.class));
                return true;
            } else if (SettingsActivity.CHANGE_SNOOZE_DELAY.equals(key)) {
                if (AppApplication.BLOCKER_TYPE_FLEXIBLE_SNOOZE.equals(app.blockerType())) {
                    final AlertDialog.Builder listDialog = new AlertDialog.Builder(me);

                    listDialog.setTitle(R.string.title_change_snooze_delay);

                    String[] labels = me.getResources().getStringArray(R.array.snooze_delay_options);
                    String[] values = me.getResources().getStringArray(R.array.snooze_delay_values);

                    listDialog.setItems(labels, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            long snoozeDelay = Long.parseLong(values[which]);

                            if (snoozeDelay < 0) {
                                SnoozeDelayGenerator.getInstance(me).addSnoozeDelay(-1);

                                Toast.makeText(me, R.string.toast_confirm_snooze_disabled, Toast.LENGTH_LONG).show();

                                fragment.refresh();
                            } else if (snoozeDelay == 0) {
                                SnoozeDelayGenerator.getInstance(me).addSnoozeDelay(0);

                                Toast.makeText(me, R.string.toast_confirm_snooze_none, Toast.LENGTH_LONG).show();

                                fragment.refresh();
                            } else {
                                SnoozeDelayGenerator.getInstance(me).addSnoozeDelay(snoozeDelay * 60 * 1000);

                                String confirm = me.getString(R.string.toast_confirm_snooze, "" + snoozeDelay);

                                Toast.makeText(me, confirm, Toast.LENGTH_LONG).show();

                                fragment.refresh();
                            }
                        }
                    });

                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

                    if (prefs.getBoolean(SettingsActivity.SHOW_SNOOZE_DELAY_MESSAGE, true)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(me);

                        builder.setTitle(R.string.title_change_snooze_delay);
                        View content = LayoutInflater.from(me).inflate(R.layout.dialog_change_snooze_delay, null, false);

                        final CheckBox dontShow = content.findViewById(R.id.check_dont_show);

                        builder.setView(content);

                        builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (dontShow.isChecked()) {
                                    SharedPreferences.Editor e = prefs.edit();
                                    e.putBoolean(SettingsActivity.SHOW_SNOOZE_DELAY_MESSAGE, false);
                                    e.apply();
                                }

                                listDialog.show();
                            }
                        });

                        builder.create().show();
                    } else {
                        listDialog.show();
                    }
                }
            }

            return super.onPreferenceTreeClick(preference);
        }

        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {

        }

        public void onResume() {
            super.onResume();

            this.refresh();
        }

        private void refresh() {
            final SettingsActivity me = (SettingsActivity) this.getActivity();

            if (me == null) {
                return;
            }

            final AppApplication app = (AppApplication) me.getApplication();

            Preference appCode = this.findPreference(SettingsActivity.APP_CODE);
            appCode.setTitle(app.getIdentifier());

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

            Preference version = this.findPreference(SettingsActivity.APP_VERSION);

            try {
                version.setTitle(me.getPackageManager().getPackageInfo(me.getPackageName(), 0).versionName);
            } catch (PackageManager.NameNotFoundException ignored) {

            }

            Preference transmitData = this.findPreference(SettingsActivity.TRANSMIT_DATA);
            String transmitTitle = me.getString(R.string.action_transmit_data_pending, PassiveDataKit.getInstance(me).pendingTransmissions());
            transmitData.setTitle(transmitTitle);

            transmitData.setVisible(false);

            // Preference subsidy = this.findPreference(SettingsActivity.RECEIVES_SUBSIDY);
            // Preference blockerType = this.findPreference(SettingsActivity.BLOCKER_TYPE);
            Preference snoozeDelay = this.findPreference(SettingsActivity.SNOOZE_DELAY);
            Preference changeSnoozeDelay = this.findPreference(SettingsActivity.CHANGE_SNOOZE_DELAY);
            Preference treatmentStatus = this.findPreference(SettingsActivity.TREATMENT_STATUS);

            Preference blockStatus = this.findPreference(SettingsActivity.BLOCKING_STATUS);
            // Preference remainingBudget = this.findPreference(SettingsActivity.REMAINING_BUDGET);

            // remainingBudget.setTitle(this.getString(R.string.value_app_remaining_budget, app.remainingBudget(0)));

            boolean treatmentActive = app.treatmentActive();

            if (treatmentActive) {
                treatmentStatus.setTitle(R.string.label_app_treatment_status_active);
            } else {
                treatmentStatus.setTitle(R.string.label_app_treatment_status_inactive);
            }

            if (treatmentActive) {
                /* if (app.receivesSubsidy()) {
                    subsidy.setTitle(R.string.value_yes);
                } else {
                    subsidy.setTitle(R.string.value_no);
                } */

                long minuteDelay =  app.snoozeDelay() / (60 * 1000);

                String delayString = this.getString(R.string.label_snooze_delay_minutes, (int) minuteDelay);

                if (minuteDelay == 0) {
                    delayString = this.getString(R.string.label_snooze_delay_immediate);
                } else if (minuteDelay < 0) {
                    delayString = this.getString(R.string.label_snooze_delay_none);
                } else if (minuteDelay == 1) {
                    delayString = this.getString(R.string.label_snooze_delay_one_minute);
                }
                snoozeDelay.setTitle(delayString);

                String blocker = app.blockerType();

                changeSnoozeDelay.setVisible(false);

                if (AppApplication.BLOCKER_TYPE_NONE.equals(blocker) == false) {
                    blockStatus.setTitle(R.string.label_app_budget_status_active);
                    blockStatus.setVisible(true);
                    // subsidy.setVisible(true);
                    treatmentStatus.setVisible(true);
                    snoozeDelay.setVisible(true);

                    if (AppApplication.BLOCKER_TYPE_FLEXIBLE_SNOOZE.equals(blocker)) {
                        changeSnoozeDelay.setVisible(true);

                        long latestSnoozeDelay = SnoozeDelayGenerator.getInstance(me).latestSnoozeDelay();

                        if (latestSnoozeDelay < 0) {
                            changeSnoozeDelay.setSummary(R.string.label_app_change_snooze_delay_summary_disabled);
                        } else if (latestSnoozeDelay > 0) {
                            latestSnoozeDelay = latestSnoozeDelay / (60 * 1000);

                            if (latestSnoozeDelay == 1) {
                                changeSnoozeDelay.setSummary(R.string.label_app_change_snooze_delay_summary_one_minute);
                            } else {
                                changeSnoozeDelay.setSummary(me.getString(R.string.label_app_change_snooze_delay_summary, latestSnoozeDelay));
                            }
                        } else {
                            changeSnoozeDelay.setSummary(R.string.label_app_change_snooze_delay_summary_none);
                        }
                    }
                } else {
                    blockStatus.setTitle(R.string.label_app_budget_status_inactive);
                    blockStatus.setVisible(false);
                    // subsidy.setVisible(false);
                    treatmentStatus.setVisible(false);
                    snoozeDelay.setVisible(false);
                }

                if (AppApplication.BLOCKER_TYPE_NONE.equals(blocker)) {
                    // blockerType.setTitle(R.string.blocker_type_none);
                    blockStatus.setVisible(false);
                    // blockerType.setVisible(false);
                    // remainingBudget.setVisible(false);
                } else if (AppApplication.BLOCKER_TYPE_FREE_SNOOZE.equals(blocker)) {
                    // blockerType.setTitle(R.string.blocker_type_free_snooze);
                    blockStatus.setVisible(true);
                    // blockerType.setVisible(true);
                    // remainingBudget.setVisible(false);
                } else if (AppApplication.BLOCKER_TYPE_NO_SNOOZE.equals(blocker)) {
                    // blockerType.setTitle(R.string.blocker_type_no_snooze);
                    blockStatus.setVisible(true);
                    // blockerType.setVisible(true);
                    // remainingBudget.setVisible(false);
                    snoozeDelay.setVisible(false);
                } else if (AppApplication.BLOCKER_TYPE_COSTLY_SNOOZE.equals(blocker)) {
                    // blockerType.setTitle(R.string.blocker_type_costly_snooze);
                    blockStatus.setVisible(true);
                    // blockerType.setVisible(true);
                    // remainingBudget.setVisible(true);
                }
            } else {
                // subsidy.setVisible(false);
                // blockerType.setVisible(false);
                snoozeDelay.setVisible(false);
                blockStatus.setVisible(false);
                treatmentStatus.setVisible(false);
                // remainingBudget.setVisible(false);
            }

            // subsidy.setVisible(false);
            // blockerType.setVisible(false);

            /* Preference periodStart = this.findPreference(SettingsActivity.PERIOD_START);

            long whenStarted = app.periodStart();

            if (whenStarted > 0) {
                DateFormat format = android.text.format.DateFormat.getLongDateFormat(me);
                Date when = new Date(whenStarted);

                periodStart.setTitle(format.format(when));
            } else {
                periodStart.setTitle(R.string.period_start_unknown);
            } */

            this.onSharedPreferenceChanged(prefs, null);

            prefs.registerOnSharedPreferenceChangeListener(this);
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.setTitle(R.string.title_settings);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FragmentManager fragment = this.getSupportFragmentManager();

        FragmentTransaction transaction = fragment.beginTransaction();

        this.mSettingsFragment = new PhoneDashboardPreferenceFragment();

        transaction.replace(android.R.id.content, this.mSettingsFragment);

        transaction.commit();
    }

    protected void onResume() {
        super.onResume();

        final SettingsActivity me = this;

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                me.mSettingsFragment.onResume();
            }
        }, 1000);

        AppLogger.getInstance(this).log("settings_screen_appeared");
    }

    protected void onPause() {
        super.onPause();

        AppLogger.getInstance(this).log("settings_screen_dismissed");
    }

    @SuppressLint("InflateParams")
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
        {
            AppLogger.getInstance(this).log("settings_menu_home");

            this.finish();
        }

        return true;
    }
}
