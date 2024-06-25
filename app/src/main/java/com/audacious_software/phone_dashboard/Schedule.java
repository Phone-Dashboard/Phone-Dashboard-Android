package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.device.Battery;
import com.audacious_software.passive_data_kit.generators.device.ForegroundApplication;
import com.audacious_software.passive_data_kit.generators.device.NotificationEvents;
import com.audacious_software.passive_data_kit.generators.device.ScreenState;
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;
import com.audacious_software.passive_data_kit.generators.diagnostics.SystemStatus;
import com.audacious_software.passive_data_kit.transmitters.HttpTransmitter;
import com.audacious_software.passive_data_kit.transmitters.Transmitter;
import com.google.firebase.FirebaseApp;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Schedule implements Generators.GeneratorUpdatedListener {
    private static final String LAST_TRANSMISSION = "com.audacious_software.phone_dashboard.Schedule.LAST_TRANSMISSION";

    private static final String LAST_FULL_USAGE_TRANSMISSION = "com.audacious_software.phone_dashboard.Schedule.LAST_FULL_USAGE_TRANSMISSION";
    private static final long FULL_USAGE_TRANSMISSION_INTERVAL = 1000 * 60 * 15;

    public static final String NOTIFICATION_CHANNEL_ID = "phone-dashboard";
    public static final String SAVED_CONFIGURATION = "com.audacious_software.phone_dashboard.Schedule.SAVED_CONFIGURATION";
    private static final String SAVED_CONFIGURATION_APPS = "apps";

    private static final String LAST_FULL_BUDGET_TRANSMISSION = "com.audacious_software.phone_dashboard.Schedule.LAST_FULL_BUDGET_TRANSMISSION";
    private static final long FULL_BUDGET_TRANSMISSION_INTERVAL = 4 * 60 * 60 * 1000;

    private static final String LAST_ATTENTION_PROMPT = "com.audacious_software.phone_dashboard.Schedule.LAST_ATTENTION_PROMPT";
    private static final long LAST_ATTENTION_PROMPT_INTERVAL = 1000 * 60 * 15;
    private static final int ATTENTION_NOTIFICATION_ID = 6755769;

    @SuppressLint("StaticFieldLeak")
    private static Schedule sInstance = null;
    private AppApplication mApplication = null;

    private Context mContext = null;
    private List<Transmitter> mTransmitters = new ArrayList<>();
    private boolean mFetchingConfig = false;

    private Handler mUsageHandler = null;
    private HandlerThread mHandlerThread = null;

    private long mCurrentDayStart = 0;

    public static Schedule getInstance(Context context) {
        if (Schedule.sInstance == null) {
            Schedule.sInstance = new Schedule(context.getApplicationContext());
        }

        return Schedule.sInstance;
    }

    private Schedule(final Context context) {
        this.mContext = context.getApplicationContext();
        this.mApplication = (AppApplication) context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager noteManager = (NotificationManager) this.mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            if (noteManager.getNotificationChannel(context.getString(R.string.foreground_channel_id)) == null) {
                NotificationChannel channel = new NotificationChannel(context.getString(R.string.foreground_channel_id), this.mContext.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                noteManager.createNotificationChannel(channel);
            }
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.remove(Schedule.LAST_TRANSMISSION);
        e.remove(Schedule.LAST_FULL_USAGE_TRANSMISSION);
        e.remove(Schedule.LAST_FULL_BUDGET_TRANSMISSION);
        e.apply();

        this.mHandlerThread = new HandlerThread("usage-compiler");
        this.mHandlerThread.start();
        this.mUsageHandler = new Handler(this.mHandlerThread.getLooper());
    }

    public void updateSchedule(boolean force, final Runnable next, final boolean isService) {
        final Schedule me = this;

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (this.mCurrentDayStart != calendar.getTimeInMillis()) {
            this.mApplication.clearCachedBudgets();

            this.mCurrentDayStart = calendar.getTimeInMillis();
        }

        final long now = System.currentTimeMillis();

        String userId = this.mApplication.getIdentifier();

        if (userId != null) {
            this.setUserId(userId);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

            long lastFullBudgetTransmitted = prefs.getLong(Schedule.LAST_FULL_BUDGET_TRANSMISSION, 0);

            if (now - lastFullBudgetTransmitted > Schedule.FULL_BUDGET_TRANSMISSION_INTERVAL) {
                SharedPreferences.Editor e = prefs.edit();
                e.putLong(Schedule.LAST_FULL_BUDGET_TRANSMISSION, now);
                e.apply();

                DailyBudgetGenerator.getInstance(this.mContext).transmitFullBudgetLog();

                this.transmitData();
            }

            long lastFullUsageTransmitted = prefs.getLong(Schedule.LAST_FULL_USAGE_TRANSMISSION, 0);

            if (now - lastFullUsageTransmitted > Schedule.FULL_USAGE_TRANSMISSION_INTERVAL) {
                SharedPreferences.Editor e = prefs.edit();
                e.putLong(Schedule.LAST_FULL_USAGE_TRANSMISSION, now);
                e.apply();

                this.transmitUsageSummary(this.mContext, true, true, true, true, true, true, false);
            }

            long lastTransmission = prefs.getLong(Schedule.LAST_TRANSMISSION, 0);

            if (force) {
                lastTransmission = 0;
            }

            long transmissionInterval = Long.parseLong(prefs.getString(SettingsActivity.TRANSMISSION_INTERVAL, SettingsActivity.TRANSMISSION_INTERVAL_DEFAULT));

            if (transmissionInterval > 0 && now - lastTransmission > transmissionInterval) {
                SharedPreferences.Editor e = prefs.edit();
                e.putLong(Schedule.LAST_TRANSMISSION, now);
                e.apply();

                if (isService) {
                    me.transmitData();

                    AppLogger.getInstance(me.mContext).log("schedule_manager_transmit_data_via_service");
                } else {
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(3000);

                                me.transmitData();

                                AppLogger.getInstance(me.mContext).log("schedule_transmit_data");

                                Handler mainHandler = new Handler(Looper.getMainLooper());

                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (next != null) {
                                            next.run();
                                        }
                                    }
                                });
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            }
                        }
                    });

                    t.start();
                }
            }

            if (OnboardingActivity.requiresOnboarding(this.mApplication)) {
                long lastAttentionPrompt = prefs.getLong(Schedule.LAST_ATTENTION_PROMPT, 0);

                if (now - lastAttentionPrompt > Schedule.LAST_ATTENTION_PROMPT_INTERVAL) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putLong(Schedule.LAST_ATTENTION_PROMPT, now);
                    e.apply();

                    this.showAttentionPrompt();
                }
            } else {
                this.dismissAttentionPrompt();
            }

            this.mApplication.refreshConfiguration(false, null);
        } else {
            Handler handler = new Handler(Looper.getMainLooper());

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    me.updateSchedule(force, next, isService);
                }
            }, 5000);
        }

        this.mApplication.forceAppearanceIfNeeded();
    }

    private void showAttentionPrompt() {
        Intent intent = new Intent(this.mContext, MainActivity.class);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.mContext, Schedule.NOTIFICATION_CHANNEL_ID)
                .setContentIntent(PendingIntent.getActivity(this.mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(this.mContext, R.color.colorNotification))
                .setContentTitle(this.mContext.getString(R.string.note_title_attention_needed))
                .setContentText(this.mContext.getString(R.string.note_message_attention_needed))
                .setPriority(NotificationCompat.PRIORITY_MAX);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this.mContext);

        if (ActivityCompat.checkSelfPermission(this.mContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        notificationManager.notify(Schedule.ATTENTION_NOTIFICATION_ID, builder.build());

        HashMap<String, Object> payload = new HashMap<>();

        StringBuilder issues = new StringBuilder();

        for (String issue : OnboardingActivity.outstandingIssues(this.mApplication)) {
            if (issues.length() > 0) {
                issues.append(";");
            }

            issues.append(issue);
        }

        payload.put("issues", issues.toString());

        Logger.getInstance(this.mContext).log("nyu-app-issue-notification", payload);
    }

    private void dismissAttentionPrompt() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this.mContext);

        try {
            notificationManager.cancel(Schedule.ATTENTION_NOTIFICATION_ID);
        } catch (NullPointerException ex) {

        }
    }

    public void transmitData() {
        synchronized (this.mTransmitters) {
            for (Transmitter transmitter : this.mTransmitters) {
                if (transmitter != null) {
                    transmitter.transmit(true);
                }
            }
        }
    }

    private void start(final String userId) {
        PassiveDataKit pdkInstance = PassiveDataKit.getInstance(this.mContext);

        pdkInstance.setAlwaysNotify(true);
        pdkInstance.setStartForegroundService(true);
        pdkInstance.setForegroundServiceIcon(R.drawable.ic_notification);
        pdkInstance.setForegroundServiceColor(ContextCompat.getColor(this.mContext, R.color.colorNotification));

        SystemStatus.getInstance(this.mContext).setEnableInstalledPackages(true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        try {
            JSONObject config = new JSONObject(prefs.getString(Schedule.SAVED_CONFIGURATION, null));

            synchronized (this.mTransmitters) {
                this.mTransmitters.addAll(pdkInstance.fetchTransmitters(userId, this.mContext.getString(R.string.app_name), config));

                for (Transmitter transmitter : this.mTransmitters) {
                    if (transmitter instanceof HttpTransmitter) {
                        HttpTransmitter httpTransmitter = (HttpTransmitter) transmitter;

                        httpTransmitter.setMaxBundleSize(32);
                    }
                }
            }

            pdkInstance.updateGenerators(config);

            AppEvent.getInstance(this.mContext).setCachedDataRetentionPeriod(2 * 7 * 24 * 60 * 60 * 1000);
            Battery.getInstance(this.mContext).setCachedDataRetentionPeriod(2 * 7 * 24 * 60 * 60 * 1000);
            ForegroundApplication.getInstance(this.mContext).setCachedDataRetentionPeriod(4L * 7 * 24 * 60 * 60 * 1000);
            ScreenState.getInstance(this.mContext).setCachedDataRetentionPeriod(2 * 7 * 24 * 60 * 60 * 1000);
            SystemStatus.getInstance(this.mContext).setCachedDataRetentionPeriod(2 * 7 * 24 * 60 * 60 * 1000);
            NotificationEvents.getInstance(this.mContext).setCachedDataRetentionPeriod(2 * 7 * 24 * 60 * 60 * 1000);

            pdkInstance.start();

            pdkInstance.transmitTokens();

            Generators.getInstance(this.mContext).addNewGeneratorUpdatedListener(this);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Intent fireIntent = new Intent(KeepAliveService.ACTION_KEEP_ALIVE, null, this.mContext, KeepAliveService.class);

        KeepAliveService.enqueueWork(this.mContext, KeepAliveService.class, KeepAliveService.JOB_ID, fireIntent);
    }

        public void setUserId(final String userId) {
            this.setUserId(userId, false);
        }

        public void setUserId(final String userId, boolean skipInitialization) {
        final Schedule me = this;

        if (userId != null && this.mFetchingConfig == false && (this.mTransmitters.size() == 0 || skipInitialization)) {
            this.mFetchingConfig = true;

            OkHttpClient client = new OkHttpClient();

            HttpUrl baseUrl = HttpUrl.parse(me.mContext.getString(R.string.url_phone_dashboard_configuration));

            HttpUrl url = new HttpUrl.Builder()
                    .scheme(baseUrl.scheme())
                    .host(baseUrl.host())
                    .encodedPath(baseUrl.encodedPath())
                    .addQueryParameter("id", userId)
                    .addQueryParameter("context", this.mContext.getPackageName())
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    me.mFetchingConfig = false;

                    if (skipInitialization == false) {
                        me.start(userId);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        me.mFetchingConfig = false;

                        String body = response.body().string();

                        JSONObject config = new JSONObject(body);

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                        SharedPreferences.Editor e = prefs.edit();
                        e.putString(Schedule.SAVED_CONFIGURATION, config.toString(2));
                        e.apply();

                        PassiveDataKit.getInstance(me.mContext).updateGenerators(config);

                        if (skipInitialization == false) {
                            me.start(userId);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

            AppLogger.getInstance(me.mContext).log("schedule_inited");
        }
    }

    @Override
    public void onGeneratorUpdated(String identifier, long timestamp, Bundle data) {
        final Schedule me = this;
        long now = System.currentTimeMillis();

        ForegroundApplication foreground = ForegroundApplication.getInstance(this.mContext);

        if (foreground.getIdentifier().equals(identifier)) {
            final String packageName = data.getString("application");

            if (packageName != null) {
                long budget = this.mApplication.budgetForPackage(packageName);
                long extensions = AppSnoozeGenerator.getInstance(this.mContext).todayExtensions(packageName);
                long latestExtension = AppSnoozeGenerator.getInstance(this.mContext).todayLatestExtensions(packageName);

                budget += extensions;

                if (budget >= 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    long usage = foreground.fetchUsageBetween(packageName, cal.getTimeInMillis(), System.currentTimeMillis(), true);

                    final long activeSnooze = me.mApplication.activeSnooze(packageName);

                    long limitTime = latestExtension;

                    if (limitTime == 0) {
                        limitTime = budget;
                    }

                    final int budgetMinutes = (int) (limitTime / (60 * 1000));

                    if (usage >= budget || (activeSnooze != Long.MAX_VALUE && activeSnooze > now)) {
                        final Handler handler = new Handler(Looper.getMainLooper());

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Intent startMain = new Intent(Intent.ACTION_MAIN);
                                startMain.addCategory(Intent.CATEGORY_HOME);
                                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                me.mContext.startActivity(startMain);

                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        String appName = packageName;

                                        PackageManager packages = me.mContext.getPackageManager();

                                        try {
                                            ApplicationInfo applicationInfo = packages.getApplicationInfo(packageName, 0);

                                            if (applicationInfo != null) {
                                                appName = packages.getApplicationLabel(applicationInfo).toString();
                                            }
                                        } catch (final PackageManager.NameNotFoundException e) {

                                        }

                                        Intent intent = new Intent(me.mContext, WarningActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        intent.putExtra(WarningActivity.PACKAGE_NAME, packageName);
                                        intent.putExtra(WarningActivity.PACKAGE_LABEL, appName);
                                        intent.putExtra(WarningActivity.TOTAL_LIMIT_MINUTES, budgetMinutes);

                                        me.mContext.startActivity(intent);
                                    }
                                }, 1000);
                            }
                        });

                        HashMap<String, Object> payload = new HashMap<>();

                        payload.put("app", packageName);
                        payload.put("budget", budget);
                        payload.put("usage", usage);

                        AppLogger.getInstance(this.mContext).log("blocked_app", payload);
                    } else {
                        long[] warningPeriods = { 5 * 60 * 1000L, 60 * 1000L };

                        for (long warningPeriod : warningPeriods) {
                            if (warningPeriod <= budget && (usage + warningPeriod) >= budget) {
                                long lastAppWarning = me.mApplication.getLastAppWarning(packageName, warningPeriod);

                                Calendar warnCal = Calendar.getInstance();
                                warnCal.setTimeInMillis(lastAppWarning);

                                Calendar nowCal = Calendar.getInstance();

                                if (nowCal.get(Calendar.DAY_OF_MONTH) != warnCal.get(Calendar.DAY_OF_MONTH) ||
                                        nowCal.get(Calendar.MONTH) != warnCal.get(Calendar.MONTH) ||
                                        nowCal.get(Calendar.YEAR) != warnCal.get(Calendar.YEAR)) {

                                    final Handler handler = new Handler(Looper.getMainLooper());

                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            String appName = packageName;

                                            PackageManager packages = me.mContext.getPackageManager();

                                            try {
                                                ApplicationInfo applicationInfo = packages.getApplicationInfo(packageName, 0);

                                                if (applicationInfo != null) {
                                                    appName = packages.getApplicationLabel(applicationInfo).toString();
                                                }
                                            } catch (final PackageManager.NameNotFoundException e) {

                                            }

                                            Intent intent = new Intent(me.mContext, WarningActivity.class);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            intent.putExtra(WarningActivity.PACKAGE_NAME, packageName);
                                            intent.putExtra(WarningActivity.PACKAGE_LABEL, appName);
                                            intent.putExtra(WarningActivity.WARNING_DURATION, warningPeriod);
                                            intent.putExtra(WarningActivity.TOTAL_LIMIT_MINUTES, budgetMinutes);

                                            me.mContext.startActivity(intent);

                                            me.mApplication.setLastAppWarning(packageName, System.currentTimeMillis(), warningPeriod);
                                        }
                                    }, 1000);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.updateSchedule(false, null, false);
    }

    public void transmitUsageSummary(final Context context, boolean includeYesterdayHourly, boolean includeYesterdaySummary, boolean includeBlocks, boolean includeWarningEvents, boolean includeBudgetEvents, boolean includeSnoozes, boolean forceTransmit) {
        this.mUsageHandler.post(new Runnable() {
            @Override
            public void run() {
                PackageManager packages = context.getPackageManager();

                final AppApplication app = (AppApplication) context.getApplicationContext();

                List<ApplicationInfo> appsList = packages.getInstalledApplications(PackageManager.GET_META_DATA);

                List<ApplicationInfo> installedApps = new ArrayList<>();
                HashMap<String, Intent> launchIntents = new HashMap<>();

                installedApps.addAll(appsList);

                HashMap<String, Object> payload = new HashMap<>();

                ArrayList<String> installedPackages = new ArrayList<>();
                HashMap<String, HashMap<String, Object>> appInfos = new HashMap<>();

                Set<String> replacements = app.replacementPackages();

                for (String replacement : replacements) {
                    ApplicationInfo info = new ApplicationInfo();
                    info.packageName = replacement;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        info.category = ApplicationInfo.CATEGORY_UNDEFINED;
                    }

                    installedApps.add(info);
                }

                HashSet<String> homePackages = new HashSet<>();

                Intent startMain = new Intent(Intent.ACTION_MAIN);
                startMain.addCategory(Intent.CATEGORY_HOME);
                startMain.addCategory(Intent.CATEGORY_DEFAULT);

                PackageManager manager = mContext.getPackageManager();
                List<ResolveInfo> startMatches = manager.queryIntentActivities(startMain, 0);

                for (ResolveInfo match : startMatches) {
                    if (match.activityInfo.packageName != null) {
                        homePackages.add(match.activityInfo.packageName);
                    }
                }

                long start = System.currentTimeMillis();

                int[] days = {1, 7, 0};

                long periodStart = app.calculationStart();
                long when = System.currentTimeMillis();

                int periodDays = (int) Math.ceil(((double) when - periodStart) / (24 * 60 * 60 * 1000));

                if (periodDays < 1) {
                    when = periodStart;

                    periodStart = app.priorPeriodStart() + (24 * 60 * 60 * 1000);

                    periodDays = (int) Math.ceil(((double) when - periodStart) / (24 * 60 * 60 * 1000));
                }

                days[2] = periodDays;

                int index = 0;

                for (int day : days) {
                    HashMap<String, Object> dayPayload = new HashMap<>();

                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(start);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    when = cal.getTimeInMillis();

                    double daysObserved = 1.0;

                    if (day == 0) {
                        when = 0;
                    } else if (day > 1) {
                        cal.add(Calendar.DATE, 0 - day + 1);

                        when = cal.getTimeInMillis();
                    }

                    cal.setTimeInMillis(System.currentTimeMillis());
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    cal.add(Calendar.DATE, 1);

                    long end = cal.getTimeInMillis();

                    long earliest = ForegroundApplication.getInstance(context).earliestTimestamp();

                    if (earliest > 0) {
                        daysObserved = (end - earliest) / (double) (24 * 60 * 60 * 1000);

                        daysObserved = Math.ceil(daysObserved);
                    }

                    if (daysObserved > day) {
                        daysObserved = day;
                    }

                    for (ApplicationInfo info : installedApps) {
                        Intent launchIntent = launchIntents.get(info.packageName);

                        if (launchIntent == null) {
                            launchIntent = packages.getLaunchIntentForPackage(info.packageName);

                            if (launchIntent == null) {
                                if (replacements.contains(info.packageName)) {
                                    launchIntent = new Intent("replacement-package");
                                } else {
                                    launchIntent = new Intent("");
                                }
                            }

                            launchIntents.put(info.packageName, launchIntent);
                        }

                        if ("".equals(launchIntent.getAction()) == false || homePackages.contains(info.packageName)) {
                            if (installedApps.contains(info.packageName) == false) {
                                HashMap<String, Object> appInfo = new HashMap<>();

                                appInfo.put("package", info.packageName);

                                if (BudgetAdapter.ACTION_HOMESCREEN.equals(launchIntent.getAction())) {
                                    appInfo.put("label", context.getString(R.string.label_home_screen));
                                } else {
                                    appInfo.put("label", packages.getApplicationLabel(info).toString());

                                    if (replacements.contains(info.packageName)) {
                                        appInfo.put("label", app.labelForReplacement(info.packageName));
                                    } else {
                                        appInfo.put("label", packages.getApplicationLabel(info).toString());
                                    }
                                }

                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_UNKNOWN);

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    switch (info.category) {
                                        case ApplicationInfo.CATEGORY_AUDIO:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_AUDIO);
                                            break;
                                        case ApplicationInfo.CATEGORY_GAME:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_GAME);
                                            break;
                                        case ApplicationInfo.CATEGORY_IMAGE:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_IMAGE);
                                            break;
                                        case ApplicationInfo.CATEGORY_MAPS:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_MAPS);
                                            break;
                                        case ApplicationInfo.CATEGORY_NEWS:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_NEWS);
                                            break;
                                        case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_PRODUCTIVITY);
                                            break;
                                        case ApplicationInfo.CATEGORY_SOCIAL:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_SOCIAL);
                                            break;
                                        case ApplicationInfo.CATEGORY_VIDEO:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_VIDEO);
                                            break;
                                        case ApplicationInfo.CATEGORY_UNDEFINED:
                                            appInfo.put("category", BudgetAdapter.APP_CATEGORY_UNKNOWN);
                                            break;
                                    }
                                }

                                installedPackages.add(info.packageName);
                                appInfos.put(info.packageName, appInfo);
                            }

                            int usageDaysObserved = ForegroundApplication.getInstance(context).fetchUsageDaysBetween(when, end, true);

                            HashMap<String, Object> appInfo = appInfos.get(info.packageName);

                            long appBudget = app.budgetForPackage(info.packageName);

                            if (appBudget >= 0) {
                                appInfo.put(BudgetAdapter.APP_TIME_BUDGET, appBudget);
                            }

                            double usage = (double) ForegroundApplication.getInstance(context).fetchUsageBetween(info.packageName, when, end, true);

                            usage = usage / (double) usageDaysObserved;

                            if (Double.isNaN(usage)) {
                                usage = 0;
                            }

                            appInfo.put("usage_ms", usage);
                            appInfo.put("days_observed", daysObserved);

                            dayPayload.put(info.packageName, appInfo);
                        }
                    }

                    if (index == 0) {
                        payload.put("day", dayPayload);
                    } else if (index == 1) {
                        payload.put("week", dayPayload);
                    } else if (index == 2) {
                        payload.put("phase", dayPayload);
                    }

                    index += 1;
                }

                if (includeYesterdayHourly) {
                    HashMap<String, Object> yesterday = new HashMap<>();

                    Calendar todayCalendar = Calendar.getInstance();
                    todayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                    todayCalendar.set(Calendar.MINUTE, 0);
                    todayCalendar.set(Calendar.SECOND, 0);
                    todayCalendar.set(Calendar.MILLISECOND, 0);

                    Calendar indexCalendar = Calendar.getInstance();

                    indexCalendar.set(Calendar.HOUR_OF_DAY, 0);
                    indexCalendar.set(Calendar.MINUTE, 0);
                    indexCalendar.set(Calendar.SECOND, 0);
                    indexCalendar.set(Calendar.MILLISECOND, 0);

                    indexCalendar.add(Calendar.DATE, -1);

                    SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

                    yesterday.put("start-range", isoFormat.format(indexCalendar.getTime()));

                    while (indexCalendar.getTimeInMillis() < todayCalendar.getTimeInMillis()) {
                        long hourStart = indexCalendar.getTimeInMillis();

                        indexCalendar.add(Calendar.HOUR_OF_DAY, 1);

                        long hourEnd = indexCalendar.getTimeInMillis();

                        HashMap<String, Object> hourPayload = new HashMap<>();

                        for (ApplicationInfo info : installedApps) {
                            Intent launchIntent = launchIntents.get(info.packageName);

                            if (launchIntent == null) {
                                launchIntent = packages.getLaunchIntentForPackage(info.packageName);

                                if (launchIntent == null) {
                                    if (replacements.contains(info.packageName)) {
                                        launchIntent = new Intent("replacement-package");
                                    } else {
                                        launchIntent = new Intent("");
                                    }
                                }

                                launchIntents.put(info.packageName, launchIntent);
                            }

                            if ("".equals(launchIntent.getAction()) == false || homePackages.contains(info.packageName)) {
                                double usage = (double) ForegroundApplication.getInstance(context).fetchUsageBetween(info.packageName, hourStart, hourEnd, true);

                                if (usage > 0) {
                                    if (installedApps.contains(info.packageName) == false) {
                                        HashMap<String, Object> appInfo = new HashMap<>();

                                        appInfo.put("package", info.packageName);

                                        if (BudgetAdapter.ACTION_HOMESCREEN.equals(launchIntent.getAction())) {
                                            appInfo.put("label", context.getString(R.string.label_home_screen));
                                        } else {
                                            appInfo.put("label", packages.getApplicationLabel(info).toString());
                                            if (replacements.contains(info.packageName)) {
                                                appInfo.put("label", app.labelForReplacement(info.packageName));
                                            } else {
                                                appInfo.put("label", packages.getApplicationLabel(info).toString());
                                            }
                                        }

                                        appInfo.put("category", BudgetAdapter.APP_CATEGORY_UNKNOWN);

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            switch (info.category) {
                                                case ApplicationInfo.CATEGORY_AUDIO:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_AUDIO);
                                                    break;
                                                case ApplicationInfo.CATEGORY_GAME:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_GAME);
                                                    break;
                                                case ApplicationInfo.CATEGORY_IMAGE:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_IMAGE);
                                                    break;
                                                case ApplicationInfo.CATEGORY_MAPS:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_MAPS);
                                                    break;
                                                case ApplicationInfo.CATEGORY_NEWS:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_NEWS);
                                                    break;
                                                case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_PRODUCTIVITY);
                                                    break;
                                                case ApplicationInfo.CATEGORY_SOCIAL:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_SOCIAL);
                                                    break;
                                                case ApplicationInfo.CATEGORY_VIDEO:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_VIDEO);
                                                    break;
                                                case ApplicationInfo.CATEGORY_UNDEFINED:
                                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_UNKNOWN);
                                                    break;
                                            }
                                        }

                                        installedPackages.add(info.packageName);
                                        appInfos.put(info.packageName, appInfo);
                                    }

                                    HashMap<String, Object> appInfo = appInfos.get(info.packageName);

                                    long appBudget = app.budgetForPackage(info.packageName);

                                    if (appBudget >= 0) {
                                        appInfo.put(BudgetAdapter.APP_TIME_BUDGET, appBudget);
                                    }

                                    if (Double.isNaN(usage)) {
                                        usage = 0;
                                    }

                                    appInfo.put("usage_ms", usage);
                                    appInfo.put("days_observed", (1 / 24.0));

                                    hourPayload.put(info.packageName, appInfo);
                                }
                            }
                        }

                        yesterday.put(hourFormat.format(new Date(hourStart)), hourPayload);
                    }

                    yesterday.put("end-range", isoFormat.format(indexCalendar.getTime()));

                    payload.put("yesterday", yesterday);
                }

                if (includeYesterdaySummary) {
                    HashMap<String, Object> yesterday = new HashMap<>();

                    Calendar todayCalendar = Calendar.getInstance();
                    todayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                    todayCalendar.set(Calendar.MINUTE, 0);
                    todayCalendar.set(Calendar.SECOND, 0);
                    todayCalendar.set(Calendar.MILLISECOND, 0);

                    Calendar indexCalendar = Calendar.getInstance();

                    indexCalendar.set(Calendar.HOUR_OF_DAY, 0);
                    indexCalendar.set(Calendar.MINUTE, 0);
                    indexCalendar.set(Calendar.SECOND, 0);
                    indexCalendar.set(Calendar.MILLISECOND, 0);

                    indexCalendar.add(Calendar.DATE, -1);

                    SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

                    yesterday.put("start-range", isoFormat.format(indexCalendar.getTime()));

                    long summaryStart = indexCalendar.getTimeInMillis();

                    long summaryEnd = todayCalendar.getTimeInMillis();

                    HashMap<String, Object> appsPayload = new HashMap<>();

                    for (ApplicationInfo info : installedApps) {
                        Intent launchIntent = launchIntents.get(info.packageName);

                        if (launchIntent == null) {
                            launchIntent = packages.getLaunchIntentForPackage(info.packageName);

                            if (launchIntent == null) {
                                if (replacements.contains(info.packageName)) {
                                    launchIntent = new Intent("replacement-package");
                                } else {
                                    launchIntent = new Intent("");
                                }
                            }

                            launchIntents.put(info.packageName, launchIntent);
                        }

                        if ("".equals(launchIntent.getAction()) == false || homePackages.contains(info.packageName)) {
                            double usage = (double) ForegroundApplication.getInstance(context).fetchUsageBetween(info.packageName, summaryStart, summaryEnd, true);

                            if (usage > 0) {
                                if (installedApps.contains(info.packageName) == false) {
                                    HashMap<String, Object> appInfo = new HashMap<>();

                                    appInfo.put("package", info.packageName);

                                    if (BudgetAdapter.ACTION_HOMESCREEN.equals(launchIntent.getAction())) {
                                        appInfo.put("label", context.getString(R.string.label_home_screen));
                                    } else {
                                        appInfo.put("label", packages.getApplicationLabel(info).toString());
                                        if (replacements.contains(info.packageName)) {
                                            appInfo.put("label", app.labelForReplacement(info.packageName));
                                        } else {
                                            appInfo.put("label", packages.getApplicationLabel(info).toString());
                                        }
                                    }

                                    appInfo.put("category", BudgetAdapter.APP_CATEGORY_UNKNOWN);

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        switch (info.category) {
                                            case ApplicationInfo.CATEGORY_AUDIO:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_AUDIO);
                                                break;
                                            case ApplicationInfo.CATEGORY_GAME:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_GAME);
                                                break;
                                            case ApplicationInfo.CATEGORY_IMAGE:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_IMAGE);
                                                break;
                                            case ApplicationInfo.CATEGORY_MAPS:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_MAPS);
                                                break;
                                            case ApplicationInfo.CATEGORY_NEWS:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_NEWS);
                                                break;
                                            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_PRODUCTIVITY);
                                                break;
                                            case ApplicationInfo.CATEGORY_SOCIAL:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_SOCIAL);
                                                break;
                                            case ApplicationInfo.CATEGORY_VIDEO:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_VIDEO);
                                                break;
                                            case ApplicationInfo.CATEGORY_UNDEFINED:
                                                appInfo.put("category", BudgetAdapter.APP_CATEGORY_UNKNOWN);
                                                break;
                                        }
                                    }

                                    installedPackages.add(info.packageName);
                                    appInfos.put(info.packageName, appInfo);
                                }

                                HashMap<String, Object> appInfo = appInfos.get(info.packageName);

                                long appBudget = app.budgetForPackage(info.packageName);

                                if (appBudget >= 0) {
                                    appInfo.put(BudgetAdapter.APP_TIME_BUDGET, appBudget);
                                }

                                if (Double.isNaN(usage)) {
                                    usage = 0;
                                }

                                appInfo.put("usage_ms", usage);
                                appInfo.put("days_observed", 1);

                                appsPayload.put(info.packageName, appInfo);
                            }
                        }
                    }

                    yesterday.put("apps-usage", appsPayload);
                    yesterday.put("end-range", isoFormat.format(todayCalendar.getTime()));
                    payload.put("yesterday-summary", yesterday);
                }

                if (includeBlocks) {
                    HashMap<String, Object> blocks = new HashMap<>();

                    String where = AppEvent.HISTORY_EVENT_NAME + " = ?";
                    String[] args = {"blocked_app"};

                    Cursor c = AppEvent.getInstance(context).queryHistory(null, where, args, null);

                    while (c.moveToNext()) {
                        long observed = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED));
                        String details = c.getString(c.getColumnIndex(AppEvent.HISTORY_EVENT_DETAILS));

                        try {
                            JSONObject detailsJson = new JSONObject(details);

                            HashMap<String, Object> block = new HashMap<>();

                            if (detailsJson.has("app")) {
                                block.put("app", detailsJson.getString("app"));
                            }

                            if (detailsJson.has("budget")) {
                                block.put("time_budget", detailsJson.getLong("budget"));
                            }

                            if (detailsJson.has("usage")) {
                                block.put("time_usage", detailsJson.getLong("usage"));
                            }

                            String dateKey = DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(observed);

                            blocks.put(dateKey, block);
                        } catch (JSONException ex) {
                            ex.printStackTrace();
                        }
                    }

                    c.close();

                    payload.put("blocks", blocks);
                }

                if (includeSnoozes) {
                    HashMap<String, Object> snoozes = new HashMap<>();

                    Cursor c = AppSnoozeGenerator.getInstance(context).queryHistory(null, null, null, null);

                    while (c.moveToNext()) {
                        long observed = c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_OBSERVED));

                        HashMap<String, Object> snooze = new HashMap<>();

                        snooze.put(AppSnoozeGenerator.HISTORY_OBSERVED, observed);
                        snooze.put(AppSnoozeGenerator.HISTORY_APP_PACKAGE, c.getString(c.getColumnIndex(AppSnoozeGenerator.HISTORY_APP_PACKAGE)));
                        snooze.put(AppSnoozeGenerator.HISTORY_DURATION, c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_DURATION)));

                        if (c.isNull(c.getColumnIndex(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET)) == false) {
                            snooze.put(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET, c.getDouble(c.getColumnIndex(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET)));
                        }

                        if (c.isNull(c.getColumnIndex(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET)) == false) {
                            snooze.put(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET, c.getDouble(c.getColumnIndex(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET)));
                        }

                        String dateKey = DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(observed);

                        snoozes.put(dateKey, snooze);
                    }

                    payload.put("snoozes", snoozes);
                }

                if (includeWarningEvents) {
                    String[] events = {
                        "app-blocked-delayed",
                        "closed-delay-warning",
                        "closed-warning",
                        "app-blocked-can-snooze",
                        "skipped-snooze",
                        "snoozed-app-limit",
                        "app-blocked-no-snooze",
                        "app-blocked-no-snooze-closed",
                        "app-block-warning",
                        "app-block-warning",
                        "cancelled-snooze"
                    };

                    HashMap<String, Object> warningEvents = new HashMap<>();

                    StringBuffer query = new StringBuffer();

                    for (int i = 0; i < events.length; i++) {
                        if (query.length() > 0) {
                            query.append(" OR ");
                        }

                        query.append(AppEvent.HISTORY_EVENT_NAME + " = ?");
                    }

                    String where = query.toString();

                    Cursor c = AppEvent.getInstance(context).queryHistory(null, where, events, null);

                    while (c.moveToNext()) {
                        long observed = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED));
                        String event = c.getString(c.getColumnIndex(AppEvent.HISTORY_EVENT_NAME));
                        String details = c.getString(c.getColumnIndex(AppEvent.HISTORY_EVENT_DETAILS));

                        HashMap<String, Object> warningEvent = new HashMap<>();

                        String dateKey = DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(observed);

                        warningEvent.put("event", event);
                        warningEvent.put("date", dateKey);
                        warningEvent.put("details", details);

                        warningEvents.put(dateKey, warningEvent);
                    }

                    c.close();

                    payload.put("warning-events", warningEvents);
                }

                if (includeBudgetEvents) {
                    String[] events = {
                        "edit-budget-screen-visited",
                        "edit-budget-save",
                        "edit-budget-save-limits",
                        "edit-budget-intitial-limit-popup",
                        "edit-budget-initial-limit-popup",
                        "edit-budget-regular-limit-popup",
                    };

                    HashMap<String, Object> budgetEvents = new HashMap<>();

                    StringBuffer query = new StringBuffer();

                    for (int i = 0; i < events.length; i++) {
                        if (query.length() > 0) {
                            query.append(" OR ");
                        }

                        query.append(AppEvent.HISTORY_EVENT_NAME + " = ?");
                    }

                    String where = query.toString();

                    Cursor c = AppEvent.getInstance(context).queryHistory(null, where, events, null);

                    while (c.moveToNext()) {
                        long observed = c.getLong(c.getColumnIndex(AppEvent.HISTORY_OBSERVED));
                        String event = c.getString(c.getColumnIndex(AppEvent.HISTORY_EVENT_NAME));
                        String details = c.getString(c.getColumnIndex(AppEvent.HISTORY_EVENT_DETAILS));

                        HashMap<String, Object> warningEvent = new HashMap<>();

                        String dateKey = DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(observed);

                        warningEvent.put("event", event);
                        warningEvent.put("date", dateKey);
                        warningEvent.put("details", details);

                        budgetEvents.put(dateKey, warningEvent);
                    }

                    c.close();

                    payload.put("budget-events", budgetEvents);
                }

                AppLogger.getInstance(context).log("app-usage-summary", payload);

                if (forceTransmit) {
                    Schedule.getInstance(context).transmitData();
                }
            }
        });
    }
}
