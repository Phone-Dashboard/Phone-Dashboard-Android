package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKitApplication;
import com.audacious_software.passive_data_kit.generators.device.ForegroundApplication;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.crashes.Crashes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AppApplication extends Application implements PassiveDataKitApplication {
    public static final String TAG = "Phone-Dashboard";

    private static final String IDENTIFIER = "com.audacious_software.phone_dashboard.IDENTIFIER";
    private static final String LAST_CONFIGURATION_REFRESH = "com.audacious_software.phone_dashboard.LAST_CONFIGURATION_REFRESH";
    private static final long LAST_CONFIGURATION_REFRESH_INTERVAL = 60 * 60 * 1000;

    private static final String RECEIVES_SUBSIDY = "com.audacious_software.phone_dashboard.RECEIVES_SUBSIDY";
    private static final boolean RECEIVES_SUBSIDY_DEFAULT = false;

    private static final String BLOCKER_TYPE = "com.audacious_software.phone_dashboard.BLOCKER_TYPE";
    public static final String BLOCKER_TYPE_NONE = "none";
    public static final String BLOCKER_TYPE_FREE_SNOOZE = "free_snooze";
    public static final String BLOCKER_TYPE_COSTLY_SNOOZE = "costly_snooze";
    public static final String BLOCKER_TYPE_FLEXIBLE_SNOOZE = "flexible_snooze";
    public static final String BLOCKER_TYPE_NO_SNOOZE = "no_snooze";

    private static final String TREATMENT_ACTIVE = "com.audacious_software.phone_dashboard.TREATMENT_ACTIVE";
    private static final boolean TREATMENT_ACTIVE_DEFAULT = false;

    private static final String BLOCKER_TYPE_DEFAULT = AppApplication.BLOCKER_TYPE_FLEXIBLE_SNOOZE;

    private static final String SNOOZE_DELAY = "com.audacious_software.phone_dashboard.SNOOZE_DELAY";
    private static final long SNOOZE_DELAY_DEFAULT = 0;

    private static final String TREATMENT_PERIOD_START = "com.audacious_software.phone_dashboard.TREATMENT_PERIOD_START";
    private static final String APP_WARNINING_PREFIX = "com.audacious_software.phone_dashboard.APP_WARNINING_PREFIX";
    private static final String APP_SNOOZE_ACTIVE_PREFIX = "com.audacious_software.phone_dashboard.APP_SNOOZE_ACTIVE_PREFIX.";

    private static final String APP_INFOS = "com.audacious_software.phone_dashboard.APP_INFOS.";

    private static final String APP_SNOOZE_COST = "com.audacious_software.phone_dashboard.APP_SNOOZE_COST.";
    private static final String APP_SNOOZE_UPDATED = "com.audacious_software.phone_dashboard.APP_SNOOZE_UPDATED";

    private static final String INITIAL_SNOOZE_AMOUNT = "com.audacious_software.phone_dashboard.INITIAL_SNOOZE_AMOUNT.";

    private static final String CALCULATION_PERIOD_START = "com.audacious_software.phone_dashboard.CALCULATION_PERIOD_START";
    private static final String CALCULATION_PERIOD_END = "com.audacious_software.phone_dashboard.CALCULATION_PERIOD_END";

    private static final String PRIOR_PERIOD_START = "com.audacious_software.phone_dashboard.PRIOR_PERIOD_START";

    private static final String LAST_APPEARANCE = "com.audacious_software.phone_dashboard.LAST_APPEARANCE";
    private static final int LAST_APPEARANCE_OPPORTUNISTIC_HOUR_START = 2;
    private static final int LAST_APPEARANCE_OPPORTUNISTIC_HOUR_END = 6;
    private static final long LAST_APPEARANCE_IMMEDIATE_INTERVAL = 2 * 24 * 60 * 60 * 1000;
    private static final long LAST_APPEARANCE_OPPORTUNISTIC_INTERVAL = (LAST_APPEARANCE_OPPORTUNISTIC_HOUR_END - LAST_APPEARANCE_OPPORTUNISTIC_HOUR_START - 1) * 60 * 60 * 1000;

    private String mPhonePackage = null;
    private String mMessagingPackage = null;
    private String mSettingsPackage = null;
    private HashMap<String, Long> mBudget = null;

    private Map<String, HashMap<String, Object>> mCachedAppInfos = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        final AppApplication me = this;

        FirebaseApp.initializeApp(me);

        HandlerThread thread = new HandlerThread("app-background-tasks");
        thread.start();

        AppCenter.start(this, this.getString(R.string.key_app_center), Analytics.class, Crashes.class);

        String identifier = this.getIdentifier();

        if (identifier != null) {
            AppCenter.setUserId(identifier);
        }

        new ANRWatchDog().setIgnoreDebugger(true).setANRListener(new ANRWatchDog.ANRListener() {
            @Override
            public void onAppNotResponding(final ANRError error) {
                Logger.getInstance(me).logThrowable(error);
            }
        }).start();
    }

    public String getIdentifier() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getString(AppApplication.IDENTIFIER, null);
    }

    public void setIdentifier(String identifier) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(AppApplication.IDENTIFIER, identifier);
        e.apply();

        Schedule.getInstance(this).setUserId(identifier);

        AppCenter.setUserId(identifier);
    }

    public void enrollEmail(final String email, final Runnable success, final Runnable failure) {
        final AppApplication me = this;

        OkHttpClient client = new OkHttpClient();

        HttpUrl baseUrl = HttpUrl.parse(this.getString(R.string.url_phone_enroll));

        HttpUrl url = new HttpUrl.Builder()
                .scheme(baseUrl.scheme())
                .host(baseUrl.host())
                .encodedPath(baseUrl.encodedPath())
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("email", email)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (failure != null) {
                    failure.run();
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();

                Log.e("PHONE-DASHBOARD", "ID FETCHED: " + responseBody);

                try {
                    JSONObject config = new JSONObject(responseBody);

                    if (config.has("identifier")) {
                        me.setIdentifier(config.getString("identifier"));

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);
                        SharedPreferences.Editor e = prefs.edit();
                        e.putString(Schedule.SAVED_CONFIGURATION, config.toString(2));
                        e.apply();

                        if (success != null) {
                            success.run();

                            return;
                        }

                        me.refreshConfiguration(true, new Runnable() {
                            @Override
                            public void run() {

                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (failure != null) {
                    failure.run();
                }
            }
        });
    }

    public long budgetForPackage(String packageName) {
        if (this.treatmentActive() == false) {
            return -1;
        }

        if (AppApplication.BLOCKER_TYPE_NONE.equals(this.blockerType())) {
            return -1;
        }

        if (this.mBudget == null) {
            this.mBudget = DailyBudgetGenerator.getInstance(this).budgetForDate(new Date(), false);
        }

        if (this.mBudget == null) {
            this.mBudget = DailyBudgetGenerator.getInstance(this).budgetForDate(new Date(), true);
        }

        if (this.mBudget == null) {
            this.mBudget = new HashMap<>();
        }

        if (this.mBudget.containsKey(packageName)) {
            return this.mBudget.get(packageName);
        }

        return -1;
    }

    public void optOut(Runnable next) {
        final AppApplication me = this;

        AppLogger.getInstance(this).log("app-opt-out");

        OkHttpClient client = new OkHttpClient();

        HttpUrl url = HttpUrl.parse(this.getString(R.string.url_opt_out, this.getIdentifier()));

        Request request = new Request.Builder()
                .url(url)
                .build();

        final Handler handler = new Handler(Looper.getMainLooper());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(me, R.string.toast_opt_out_failed, Toast.LENGTH_LONG).show();

                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();

                try {
                    JSONObject config = new JSONObject(responseBody);

                    if (config.has("success")) {
                        if (config.getBoolean("success")) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(me, R.string.toast_opt_out_successful, Toast.LENGTH_LONG).show();

                                }
                            });

                            me.refreshConfiguration(true, next);

                            return;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(me, R.string.toast_opt_out_failed_try_again, Toast.LENGTH_LONG).show();

                    }
                });
            }
        });
    }

    public boolean appIsExempt(String packageName) {
        if ("com.android.vending".equals(packageName)) {
            return true;
        }

        if (this.mPhonePackage == null) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            Uri uri = Uri.parse("tel:+15558675309");
            intent.setData(uri);

            ComponentName component = intent.resolveActivity(this.getPackageManager());

            if (component != null) {
                this.mPhonePackage = component.getPackageName();
            }

            if (this.mPhonePackage == null) {
                this.mPhonePackage = "";
            }
        }

        if (this.mPhonePackage.equals(packageName)) {
            return true;
        }

        if (this.mMessagingPackage == null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("smsto:+15558675309");
            intent.setData(uri);

            ComponentName component = intent.resolveActivity(this.getPackageManager());

            if (component != null) {
                this.mMessagingPackage = component.getPackageName();
            }

            if (this.mMessagingPackage == null) {
                this.mMessagingPackage = "";
            }
        }

        if (this.mMessagingPackage.equals(packageName)) {
            return true;
        }

        if (this.mSettingsPackage == null) {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);

            ComponentName component = intent.resolveActivity(this.getPackageManager());

            if (component != null) {
                this.mSettingsPackage = component.getPackageName();
            }

            if (this.mSettingsPackage == null) {
                this.mSettingsPackage = "";
            }
        }

        if (this.mSettingsPackage.equals(packageName)) {
            return true;
        }

        if (this.getPackageName().equals(packageName)) {
            return true;
        }

        return false;
    }

    public void clearCachedBudgets() {
        this.mBudget = null;
    }

    public void refreshConfiguration(boolean force, final Runnable next) {
        if (this.getIdentifier() == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        long lastFetch = prefs.getLong(AppApplication.LAST_CONFIGURATION_REFRESH, 0);

        long now = System.currentTimeMillis();

        if (force || now - lastFetch > AppApplication.LAST_CONFIGURATION_REFRESH_INTERVAL) {
            SharedPreferences.Editor e = prefs.edit();
            e.putLong(AppApplication.LAST_CONFIGURATION_REFRESH, now);
            e.apply();

            final AppApplication me = this;

            OkHttpClient client = new OkHttpClient();

            HttpUrl baseUrl = HttpUrl.parse(this.getString(R.string.url_phone_study_configuration));

            HttpUrl url = new HttpUrl.Builder()
                    .scheme(baseUrl.scheme())
                    .host(baseUrl.host())
                    .encodedPath(baseUrl.encodedPath())
                    .build();

            RequestBody formBody = new FormBody.Builder()
                    .add("identifier", this.getIdentifier())
                    .add("version", "" + BuildConfig.VERSION_CODE)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (next != null) {
                        Handler handler = new Handler(Looper.getMainLooper());

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(me, R.string.toast_config_update_failed, Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    Logger.getInstance(me).log("config_fetch_failed", new HashMap<>());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();

                    Handler handler = new Handler(Looper.getMainLooper());

                    try {
                        JSONObject config = new JSONObject(responseBody);

                        if (config.has("identifier")) {
                            me.setIdentifier(config.getString("identifier"));

                            Schedule.getInstance(me).setUserId(me.getIdentifier(), true);
                        }

                        if (config.has("receives_subsidy")) {
                            me.setReceivesSubsidy(config.getBoolean("receives_subsidy"));
                        }

                        if (config.has("blocker_type")) {
                            me.setBlockerType(config.getString("blocker_type"));
                        }

                        if (config.has("treatment_active")) {
                            me.setTreatmentActive(config.getBoolean("treatment_active"));
                        }

                        if (config.has("initial_snooze_amount")) {
                            me.setInitialSnoozeAmount((float) config.getDouble("initial_snooze_amount"));
                        }

                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                        if (config.has("start_date")) {
                            try {
                                Date start = format.parse(config.getString("start_date"));

                                Calendar cal = Calendar.getInstance();
                                cal.setTime(start);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);

                                me.setPeriodStart(cal.getTimeInMillis());
                            } catch (ParseException e1) {
                                e1.printStackTrace();
                            }
                        }

                        if (config.has("calculation_start")) {
                            try {
                                Date start = format.parse(config.getString("calculation_start"));

                                Calendar cal = Calendar.getInstance();
                                cal.setTime(start);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);

                                me.setCalculationStart(cal.getTimeInMillis());
                            } catch (ParseException e1) {
                                e1.printStackTrace();
                            }
                        } else {
                            me.setCalculationStart(me.periodStart());
                        }

                        if (config.has("calculation_end")) {
                            try {
                                Date start = format.parse(config.getString("calculation_end"));

                                Calendar cal = Calendar.getInstance();
                                cal.setTime(start);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);

                                me.setCalculationEnd(cal.getTimeInMillis());
                            } catch (ParseException e1) {
                                e1.printStackTrace();
                            }
                        } else {
                            me.setCalculationEnd(Long.MAX_VALUE);
                        }

                        if (config.has("prior_start_date")) {
                            try {
                                Date start = format.parse(config.getString("prior_start_date"));

                                Calendar cal = Calendar.getInstance();
                                cal.setTime(start);
                                cal.set(Calendar.HOUR_OF_DAY, 0);
                                cal.set(Calendar.MINUTE, 0);
                                cal.set(Calendar.SECOND, 0);
                                cal.set(Calendar.MILLISECOND, 0);

                                me.setPriorPeriodStart(cal.getTimeInMillis());
                            } catch (ParseException e1) {
                                e1.printStackTrace();
                            }
                        }

                        if (config.has("snoozes")) {
                            JSONArray snoozes = config.getJSONArray("snoozes");

                            AppSnoozeGenerator appSnoozes = AppSnoozeGenerator.getInstance(me);

                            for (int i = 0; i < snoozes.length(); i++) {
                                JSONObject snooze = snoozes.getJSONObject(i);

                                String packageName = snooze.getString("app_package");
                                long when = snooze.getLong("observed");
                                long duration = snooze.getLong("duration");

                                appSnoozes.insertSnoozeIfMissing(packageName, when, duration);
                            }
                        }

                        if (config.has("snooze_cost")) {
                            try {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

                                long whenSet = config.getLong("snooze_cost_set");

                                long lastSet = prefs.getLong(AppApplication.APP_SNOOZE_UPDATED, 0);

                                if (whenSet > lastSet) {
                                    SharedPreferences.Editor e = prefs.edit();
                                    e.putFloat(AppApplication.APP_SNOOZE_COST, (float) config.getDouble("snooze_cost"));
                                    e.putLong(AppApplication.APP_SNOOZE_UPDATED, whenSet);
                                    e.apply();
                                }
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        }

                        if (config.has("apps")) {
                            try {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

                                SharedPreferences.Editor e = prefs.edit();
                                e.putString(AppApplication.APP_INFOS, config.getJSONArray("apps").toString(2));
                                e.apply();

                                synchronized (me.mCachedAppInfos) {
                                    me.mCachedAppInfos.clear();
                                }
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }

                        }

                        Logger.getInstance(me).log("config_fetch_succeeded", new HashMap<>());

                        if (next != null) {
                            BudgetAdapter.clearInstalledApps();

                            handler.post(next);
                        }

                        return;
                    } catch (JSONException e) {
                        Logger.getInstance(me).log("config_fetch_failed", new HashMap<>());
                        Logger.getInstance(me).logThrowable(e);

                        e.printStackTrace();
                    }

                    if (next != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(me, R.string.toast_config_update_failed, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            });
        }
    }

    private void setInitialSnoozeAmount(float amount) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putFloat(AppApplication.INITIAL_SNOOZE_AMOUNT, amount);
        e.apply();
    }

    private void setPeriodStart(long periodStart) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putLong(AppApplication.TREATMENT_PERIOD_START, periodStart);
        e.apply();
    }

    private void setCalculationStart(long periodStart) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putLong(AppApplication.CALCULATION_PERIOD_START, periodStart);
        e.apply();
    }

    private void setCalculationEnd(long periodEnd) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putLong(AppApplication.CALCULATION_PERIOD_END, periodEnd);
        e.apply();
    }

    public long periodStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getLong(AppApplication.TREATMENT_PERIOD_START, 0);
    }

    private void setPriorPeriodStart(long periodStart) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putLong(AppApplication.PRIOR_PERIOD_START, periodStart);
        e.apply();
    }

    public long priorPeriodStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getLong(AppApplication.PRIOR_PERIOD_START, 0);
    }

    public long calculationStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getLong(AppApplication.CALCULATION_PERIOD_START, 0);
    }

    private void setTreatmentActive(boolean treatmentActive) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(AppApplication.TREATMENT_ACTIVE, treatmentActive);
        e.apply();
    }

    public boolean treatmentActive() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getBoolean(AppApplication.TREATMENT_ACTIVE, AppApplication.TREATMENT_ACTIVE_DEFAULT);
    }

    public long snoozeDelay() {
        if (AppApplication.BLOCKER_TYPE_NO_SNOOZE.equals(this.blockerType())) {
            return -1;
        } else if (AppApplication.BLOCKER_TYPE_FLEXIBLE_SNOOZE.equals(this.blockerType())) {
            return SnoozeDelayGenerator.getInstance(this).currentSnoozeDelay();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getLong(AppApplication.SNOOZE_DELAY, AppApplication.SNOOZE_DELAY_DEFAULT);
    }

    public float initialSnoozeAmount() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getFloat(AppApplication.INITIAL_SNOOZE_AMOUNT, 10);
    }

    private void setBlockerType(String blockerType) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putString(AppApplication.BLOCKER_TYPE, blockerType);
        e.apply();
    }

    public String blockerType() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getString(AppApplication.BLOCKER_TYPE, AppApplication.BLOCKER_TYPE_DEFAULT);
    }

    private void setReceivesSubsidy(boolean receivesSubsidy) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(AppApplication.RECEIVES_SUBSIDY, receivesSubsidy);
        e.apply();
    }

    public boolean receivesSubsidy() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getBoolean(AppApplication.RECEIVES_SUBSIDY, AppApplication.RECEIVES_SUBSIDY_DEFAULT);
    }

    public long getLastAppWarning(String packageName, long periodLength) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getLong(AppApplication.APP_WARNINING_PREFIX + packageName + '-' + periodLength, 0);
    }

    public void setLastAppWarning(String packageName, long when, long periodLength) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putLong(AppApplication.APP_WARNINING_PREFIX + packageName + '-' + periodLength, when);
        e.apply();
    }

    public float snoozeCost() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (this.treatmentActive()) {
            long updated = this.snoozeUpdated();
            long periodStart = this.periodStart();

            if (periodStart < System.currentTimeMillis() && updated < periodStart) {
                return -1;
            }
        }

        return prefs.getFloat(AppApplication.APP_SNOOZE_COST, -1);
    }

    public void setSnoozeCost(float snoozeCost) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putFloat(AppApplication.APP_SNOOZE_COST, snoozeCost);
        e.putLong(AppApplication.APP_SNOOZE_UPDATED, System.currentTimeMillis());
        e.apply();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("snooze-cost", snoozeCost);

        AppLogger.getInstance(this).log("set-snooze-cost", payload);
    }

    private long snoozeUpdated() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        return prefs.getLong(AppApplication.APP_SNOOZE_UPDATED, this.periodStart() - 1);
    }

    public long activeSnooze(String packageName) {
        if (AppApplication.BLOCKER_TYPE_NO_SNOOZE.equals(this.blockerType())) {
            return Long.MAX_VALUE;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        long active = prefs.getLong(AppApplication.APP_SNOOZE_ACTIVE_PREFIX + packageName, Long.MAX_VALUE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (active < calendar.getTimeInMillis()) {
            active = Long.MAX_VALUE;
        }

        return active;
    }

    public void setActiveSnooze(String packageName, long snoozeStarts) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor e = prefs.edit();
        e.putLong(AppApplication.APP_SNOOZE_ACTIVE_PREFIX + packageName, snoozeStarts);
        e.apply();
    }

    public boolean promptForMissingRequirements(final Activity activity) {
        final AppApplication me = this;

        if (this.treatmentActive()) {
            String blockerType = this.blockerType();
            double snoozeCost = this.snoozeCost();
            float initialLimitBudget = this.initialSnoozeAmount();

            if (AppApplication.BLOCKER_TYPE_COSTLY_SNOOZE.equals(blockerType) || AppApplication.BLOCKER_TYPE_FREE_SNOOZE.equals(blockerType) || AppApplication.BLOCKER_TYPE_NO_SNOOZE.equals(blockerType)) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, 1);

                HashMap<String, Long> currentBudget = DailyBudgetGenerator.getInstance(activity).budgetForDate(cal.getTime(), false);

                long latestBudgetUpdate = DailyBudgetGenerator.getInstance(this).latestBudgetUpdate();
                long periodStart = this.periodStart();
                long now = System.currentTimeMillis();

                if (currentBudget == null || currentBudget.size() == 0 || (now > periodStart && latestBudgetUpdate < periodStart)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.title_app_limits_required);
                    builder.setMessage(R.string.message_app_limits_required);

                    builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent budget = new Intent(activity, EditBudgetActivity.class);
                            budget.putExtra(EditBudgetActivity.MODE_INITIAL_SETUP, true);
                            activity.startActivity(budget);

                            AppLogger.getInstance(me).log("continued-to-initial-limits");
                        }
                    });

                    builder.setCancelable(false);

                    builder.create().show();

                    AppLogger.getInstance(this).log("prompted-for-initial-limits");

                    return true;
                } else if (AppApplication.BLOCKER_TYPE_COSTLY_SNOOZE.equals(blockerType)) {
                    if (periodStart < System.currentTimeMillis() && snoozeCost < 0) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setTitle(R.string.title_set_snooze_cost);

                        @SuppressLint("InflateParams")
                        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_snooze_cost_prompt, null, false);

                        TextView message = content.findViewById(R.id.dialog_message_app_snooze_cost);

                        message.setText(me.getString(R.string.dialog_message_app_snooze_cost, me.initialSnoozeAmount()));

                        float cost = me.snoozeCost();

                        final TextInputEditText costField = content.findViewById(R.id.field_snooze_cost);

                        if (cost > 0) {
                            costField.setText(me.getString(R.string.format_dollars, cost));
                        } else {
                            costField.setText(null);
                        }

                        builder.setView(content);

                        builder.setPositiveButton(R.string.action_save_snooze_cost, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                try {
                                    float cost = Float.parseFloat(costField.getText().toString());

                                    if (cost >= 0 && cost <= initialLimitBudget) {
                                        AppLogger.getInstance(me).log("snooze-cost-set");

                                        me.setSnoozeCost(cost);
                                    } else {
                                        AppLogger.getInstance(me).log("snooze-cost-invalid");

                                        String message = me.getString(R.string.toast_budget_limit, 0.0f, initialLimitBudget);

                                        Toast.makeText(me, message, Toast.LENGTH_LONG).show();

                                        me.promptForMissingRequirements(activity);
                                    }
                                } catch (NumberFormatException ex) {
                                    me.promptForMissingRequirements(activity);
                                }
                            }
                        });

                        builder.setCancelable(false);

                        builder.create().show();

                        AppLogger.getInstance(this).log("prompted-for-snooze-cost");

                        return true;
                    }
                }
            }
        }

        return false;
    }

    public float remainingBudget(int additionalSnoozes) {
        long periodStart = this.periodStart();

        int snoozes = AppSnoozeGenerator.getInstance(this).snoozesSince(periodStart);

        snoozes += additionalSnoozes;

        float snoozeCost = this.snoozeCost();

        if (snoozeCost < 0) {
            snoozeCost = 0;
        }

        float startBalance = this.initialSnoozeAmount();

        float remaining = startBalance - (snoozeCost * snoozes);

        if (remaining < 0) {
            remaining = 0;
        }

        return remaining;
    }

    public void logAppAppearance(long timestamp, String source) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(AppApplication.LAST_APPEARANCE, timestamp);
        e.apply();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("source", source);

        AppLogger.getInstance(this).log("log-app-appearance", payload);
    }

    public void forceAppearanceIfNeeded() {
        long now = System.currentTimeMillis();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        long lastAppearance = prefs.getLong(AppApplication.LAST_APPEARANCE, 0);

        long elapsed = now - lastAppearance;

        if (lastAppearance == 0) {
            this.logAppAppearance(System.currentTimeMillis(), "initial-launch");
        } else {
            Intent launchIntent = new Intent(this, DatabaseActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (elapsed > AppApplication.LAST_APPEARANCE_IMMEDIATE_INTERVAL) {
                this.logAppAppearance(System.currentTimeMillis(), "force-immediate-launch");

                this.startActivity(launchIntent);

                AppLogger.getInstance(this).log("force-appearance-immediate");
            } else if (elapsed > AppApplication.LAST_APPEARANCE_OPPORTUNISTIC_INTERVAL) {
                Calendar calendar = Calendar.getInstance();

                int hour = calendar.get(Calendar.HOUR_OF_DAY);

                if (hour > AppApplication.LAST_APPEARANCE_OPPORTUNISTIC_HOUR_START && hour < AppApplication.LAST_APPEARANCE_OPPORTUNISTIC_HOUR_END) {
                    Intent intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

                    int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

                    if (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                        this.logAppAppearance(System.currentTimeMillis(), "force-opportunistic-launch");

                        this.startActivity(launchIntent);

                        AppLogger.getInstance(this).log("force-appearance-opportunistic");
                    }
                }
            }
        }
    }

    @Override
    public void doBackgroundWork() {
        final AppApplication me = this;

        Schedule.getInstance(this).updateSchedule(true, new Runnable() {
            @Override
            public void run() {
                AppLogger.getInstance(me).log("phone-dashboard-remote-notification-update");
            }
        }, false);
    }

    public int fetchPriority(String packageName) {
        Map<String, HashMap<String, Object>> appInfos = this.savedAppInfos();

        if (appInfos.containsKey(packageName)) {
            HashMap<String, Object> appInfo = appInfos.get(packageName);

            if (appInfo != null) {
                if (appInfo.containsKey("order")) {
                    Integer order = (Integer) appInfo.get("order");

                    if (order != null) {
                        return order;
                    }
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    public Set<String> replacementPackages() {
        HashSet<String> replacements = new HashSet<>();

        Map<String, HashMap<String, Object>> appInfos = this.savedAppInfos();

        for (HashMap<String, Object> appInfo : appInfos.values()) {
            String replacement = (String) appInfo.get("replacement");

            if (replacement != null) {
                replacements.add(replacement);

            }
        }

        return replacements;
    }

    public Map<String, HashMap<String, Object>> savedAppInfos() {
        if (this.mCachedAppInfos.size() > 0) {
            HashMap<String, HashMap<String, Object>> saved = new HashMap<>();

            synchronized (this.mCachedAppInfos) {
                saved.putAll(this.mCachedAppInfos);
            }

            return saved;
        }

        synchronized (this.mCachedAppInfos) {
            this.mCachedAppInfos.clear();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            if (prefs.contains(AppApplication.APP_INFOS)) {
                try {
                    ForegroundApplication foreground = ForegroundApplication.getInstance(this);

                    foreground.clearSubstitutions();

                    JSONArray apps = new JSONArray(prefs.getString(AppApplication.APP_INFOS, "[]"));

                    for (int i = 0; i < apps.length(); i++) {
                        JSONObject app = apps.getJSONObject(i);

                        HashMap<String, Object> appInfo = new HashMap<>();

                        appInfo.put("package", app.getString("original_package"));
                        appInfo.put("order", app.getInt("sort_order"));

                        if (app.has("replacement_package")) {
                            appInfo.put("replacement", app.getString("replacement_package"));

                            foreground.addSubstitution(app.getString("original_package"), app.getString("replacement_package"), false);

                            HashMap<String, Object> replacementInfo = new HashMap<>();

                            replacementInfo.put("package", app.getString("replacement_package"));
                            replacementInfo.put("order", app.getInt("sort_order"));

                            this.mCachedAppInfos.put(app.getString("replacement_package"), replacementInfo);
                        }

                        this.mCachedAppInfos.put(app.getString("original_package"), appInfo);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        HashMap<String, HashMap<String, Object>> saved = new HashMap<>();

        synchronized (this.mCachedAppInfos) {
            saved.putAll(this.mCachedAppInfos);
        }

        return saved;
    }

    public boolean hidePackage(String packageName) {
        Map<String, HashMap<String, Object>> appInfos = this.savedAppInfos();

        HashMap<String, Object> appInfo = appInfos.get(packageName);

        if (appInfo != null) {
            return appInfo.containsKey("replacement");

        }

        return false;
    }

    public String labelForReplacement(String packageName) {
        if ("browser".equals(packageName)) {
            return this.getString(R.string.apps_web_browsers);
        } else if ("youtube".equals(packageName)) {
            return this.getString(R.string.apps_youtube);
        } else if ("facebook".equals(packageName)) {
            return this.getString(R.string.apps_facebook);
        } else if ("snapchat".equals(packageName)) {
            return this.getString(R.string.apps_snapchat);
        } else if ("instagram".equals(packageName)) {
            return this.getString(R.string.apps_instagram);
        } else if ("twitter".equals(packageName)) {
            return this.getString(R.string.apps_twitter);
        } else if ("whatsapp".equals(packageName)) {
            return this.getString(R.string.apps_whatsapp);
        }

        return packageName;
    }

    public Drawable iconForReplacement(String packageName) {
        if ("browser".equals(packageName)) {
            return this.getResources().getDrawable(R.drawable.ic_browser);
        } else if ("facebook".equals(packageName)) {
            return this.getResources().getDrawable(R.drawable.ic_facebook);
        } else if ("youtube".equals(packageName)) {
            return this.getResources().getDrawable(R.drawable.ic_youtube);
        } else if ("snapchat".equals(packageName)) {
            return this.getResources().getDrawable(R.drawable.ic_snapchat);
        } else if ("instagram".equals(packageName)) {
            return this.getResources().getDrawable(R.drawable.ic_instagram);
        } else if ("twitter".equals(packageName)) {
            return this.getResources().getDrawable(R.drawable.ic_twitter);
        }

        return null;
    }
}
