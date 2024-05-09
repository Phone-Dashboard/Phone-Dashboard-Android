package com.audacious_software.phone_dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.generators.device.ForegroundApplication;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.ViewHolder> {
    public static final String APP_PACKAGE = "com.audacious_software.phone_dashboard.APP_PACKAGE";
    public static final String APP_LABEL = "com.audacious_software.phone_dashboard.APP_LABEL";
    public static final String APP_USAGE = "com.audacious_software.phone_dashboard.APP_USAGE";
    public static final String APP_TIME_BUDGET = "com.audacious_software.phone_dashboard.APP_TIME_BUDGET";

    public static final String APP_CATEGORY = "com.audacious_software.phone_dashboard.APP_CATEGORY";
    public static final String APP_CATEGORY_UNKNOWN = "unknown";
    public static final String APP_CATEGORY_AUDIO = "audio";
    public static final String APP_CATEGORY_GAME = "game";
    public static final String APP_CATEGORY_IMAGE = "image";
    public static final String APP_CATEGORY_MAPS = "maps";
    public static final String APP_CATEGORY_NEWS = "news";
    public static final String APP_CATEGORY_PRODUCTIVITY = "productivity";
    public static final String APP_CATEGORY_SOCIAL = "social";
    public static final String APP_CATEGORY_VIDEO = "video";
    public static final String DAYS_OBSERVED = "days_observed";

    private static final String APP_PRIORITY = "com.audacious_software.phone_dashboard.BudgetAdapter.APP_PRIORITY";

    public static final String ACTION_HOMESCREEN = "com.audacious_software.phone_dashboard.BudgetAdapter.ACTION_HOMESCREEN";

    private static final String REFRESH_TOKEN = "com.audacious_software.phone_dashboard.BudgetAdapter.REFRESH_TOKEN";

    private static HashMap<String, Intent> sLaunchIntents = new HashMap<>();
    private static List<ApplicationInfo> sInstalledApps = new ArrayList<>();
    private static Set<String> sCachedReplacements = new HashSet<>();

    private Context mContext = null;
    private long mStart = 0;
    private int mDays = 1;
    private boolean mInitialized = false;
    private boolean mUpdating = false;

    private ArrayList<String> mInstalledPackages = new ArrayList<>();
    private final HashMap<String, HashMap<String, Object>> mAppInfos = new HashMap<>();
    private final HashMap<String, HashMap<String, Object>> mCachedAppInfos = new HashMap<>();

    private static HandlerThread sHandlerThread = null;
    private static Handler sHandler = null;
    private long mLastNotified = 0;
    private boolean mVisible = false;

    private static HashMap<String, Drawable> sIconCache = new HashMap<>();

    public static void clearInstalledApps() {
        synchronized (BudgetAdapter.sInstalledApps) {
            BudgetAdapter.sInstalledApps.clear();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mView;

        public ViewHolder(View view) {
            super(view);

            this.mView = view;
        }

        public void updateView(String title, String subtitle) {
            View appInfo = this.mView.findViewById(R.id.content_app_info);
            View summary = this.mView.findViewById(R.id.content_summary);

            appInfo.setVisibility(View.GONE);
            summary.setVisibility(View.VISIBLE);

            TextView titleView = summary.findViewById(R.id.summary_title);
            titleView.setText(title);

            TextView subtitleView = summary.findViewById(R.id.summary_subtitle);
            subtitleView.setText(subtitle);

            ImageView fitsbyIcon = this.mView.findViewById(R.id.icon_fitsby);
            fitsbyIcon.setVisibility(View.GONE);
        }

        public void updateView(HashMap<String, Object> appDefinition) {
            Context context = this.mView.getContext();

            View appInfo = this.mView.findViewById(R.id.content_app_info);
            View summary = this.mView.findViewById(R.id.content_summary);

            appInfo.setVisibility(View.VISIBLE);
            summary.setVisibility(View.GONE);

            TextView appName = this.mView.findViewById(R.id.app_name);
            ImageView appIcon = this.mView.findViewById(R.id.app_icon);

            String packageName = (String) appDefinition.get(BudgetAdapter.APP_PACKAGE);
            String appLabel = (String) appDefinition.get(BudgetAdapter.APP_LABEL);

            AppApplication app = (AppApplication) context.getApplicationContext();

            if (BudgetAdapter.sCachedReplacements.size() == 0) {
                Set<String> replacements = app.replacementPackages();

                if (replacements.size() > 0) {
                    BudgetAdapter.sCachedReplacements.addAll(replacements);
                }
            }

            if (BudgetAdapter.sCachedReplacements.contains(packageName)) {
                appLabel = app.labelForReplacement(packageName);
            }

            appName.setText(context.getString(R.string.numbered_list_app, this.getAdapterPosition(), appLabel));

            if (context.getString(R.string.label_home_screen).equals(appLabel)) {
                appIcon.setImageResource(R.drawable.ic_home_screen);
            } else {
                Drawable icon = BudgetAdapter.sIconCache.get(packageName);

                if (icon == null) {

                    try {
                        icon = context.getPackageManager().getApplicationIcon(packageName);
                    } catch (PackageManager.NameNotFoundException e) {
                        appName.setText(packageName);
                    }

                    if (icon == null && BudgetAdapter.sCachedReplacements.contains(packageName)) {
                        icon = app.iconForReplacement(packageName);
                    }

                    if (icon != null) {
                        BudgetAdapter.sIconCache.put(packageName, icon);
                    }

                }

                appIcon.setImageDrawable(icon);
            }

            TextView appDuration = this.mView.findViewById(R.id.app_used_duration);

            Double usage = (Double) appDefinition.get(BudgetAdapter.APP_USAGE);
            long appUsage = 0;

            if (usage != null) {
                appUsage = (long) usage.doubleValue();

                String usageLabel = context.getString(R.string.label_app_used_today, DurationFormatUtils.formatDurationWords(appUsage, true, true));

                appDuration.setText(usageLabel);
            } else {
                appDuration.setText(R.string.label_app_not_used_today);
            }

            TextView appRemaining = this.mView.findViewById(R.id.app_remaining_duration);

            Long budget = (Long) appDefinition.get(BudgetAdapter.APP_TIME_BUDGET);

            if (budget != null) {
                appRemaining.setVisibility(View.VISIBLE);

                long remaining = budget - appUsage;

                if (remaining > 0) {
                    String usageLabel = context.getString(R.string.label_app_usage_remaining, DurationFormatUtils.formatDurationWords(remaining, true, true));

                    appRemaining.setText(usageLabel);
                } else {
                    appRemaining.setText(R.string.label_app_usage_remaining_none);
                }
            } else {
                appRemaining.setVisibility(View.GONE);

                appRemaining.setText(R.string.label_app_no_usage_resriction);
            }

            ImageView fitsbyIcon = this.mView.findViewById(R.id.icon_fitsby);
            fitsbyIcon.setVisibility(View.GONE);

            if (((Integer) appDefinition.get(BudgetAdapter.APP_PRIORITY)).intValue() < 100) {
                fitsbyIcon.setVisibility(View.VISIBLE);
            } else {
                fitsbyIcon.setVisibility(View.GONE);
            }
        }
    }

    public BudgetAdapter(Context context, long start, int days, boolean visible) {
        this.mContext = context;
        this.mDays = days;
        this.mStart = start;
        this.mVisible = visible;

        if (BudgetAdapter.sHandlerThread == null) {
            BudgetAdapter.sHandlerThread = new HandlerThread("budget-update");
            BudgetAdapter.sHandlerThread.start();

            BudgetAdapter.sHandler = new Handler(BudgetAdapter.sHandlerThread.getLooper());
        }

        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.addCategory(Intent.CATEGORY_DEFAULT);

        PackageManager manager = mContext.getPackageManager();
        List<ResolveInfo> startMatches = manager.queryIntentActivities(startMain, 0);

        for (ResolveInfo info : startMatches) {
            if (info.activityInfo.packageName != null && BudgetAdapter.sLaunchIntents.containsKey(info.activityInfo.packageName) == false) {
                startMain.setAction(BudgetAdapter.ACTION_HOMESCREEN);

                BudgetAdapter.sLaunchIntents.put(info.activityInfo.packageName, startMain);
            }
        }

        Intent intent = new Intent(Settings.ACTION_SETTINGS);

        List<ResolveInfo> settings = manager.queryIntentActivities(intent, 0);

        for (ResolveInfo info : settings) {
           if (BudgetAdapter.sLaunchIntents.containsKey(info.activityInfo.packageName)) {
               BudgetAdapter.sLaunchIntents.remove(info.activityInfo.packageName);
           }
        }

        this.refreshUsage(context);
    }

    public void setVisible(boolean visible) {
        this.mVisible = visible;

        if (this.mVisible) {
            this.mLastNotified = 0;
        }
    }

    public void refreshUsage(Context context) {
        if (this.mUpdating || this.mVisible == false) {
            return;
        }

        this.mUpdating = true;

        final BudgetAdapter me = this;
        final AppApplication app = (AppApplication) context.getApplicationContext();

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                long delay = now - me.mLastNotified;

                if (delay > 500) {
                    me.mLastNotified = now;

                    me.notifyDataSetChanged();
                }
            }
        });

        BudgetAdapter.sHandler.post(new Runnable() {
            @Override
            public void run() {
                PackageManager packages = context.getPackageManager();

                long refreshStart = -1;

                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(me.mStart);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                long when = cal.getTimeInMillis();

                double daysObserved = 1.0;

                if (me.mDays == 0) {
                    when = 0;
                } else if (me.mDays > 1) {
                    cal.add(Calendar.DATE, 0 - me.mDays + 1);

                    when = cal.getTimeInMillis();
                }

                cal.setTimeInMillis(System.currentTimeMillis());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                cal.add(Calendar.DATE, 1);

                long end = cal.getTimeInMillis();

                if (app.replacementPackages().size() == 0) {
                    BudgetAdapter.clearInstalledApps();
                }

                synchronized (BudgetAdapter.sInstalledApps) {
                    if (BudgetAdapter.sInstalledApps.size() == 0) {
                        refreshStart = System.currentTimeMillis();

                        BudgetAdapter.sInstalledApps.addAll(packages.getInstalledApplications(PackageManager.GET_META_DATA));

                        for (String replacement : app.replacementPackages()) {
                            ApplicationInfo info = new ApplicationInfo();

                            info.packageName = replacement;

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                info.category = ApplicationInfo.CATEGORY_UNDEFINED;
                            }

                            BudgetAdapter.sInstalledApps.add(info);
                        }
                    }

                    Set<ApplicationInfo> toRemove = new HashSet<>();

                    for (ApplicationInfo appInfo : BudgetAdapter.sInstalledApps) {
                        if (app.hidePackage(appInfo.packageName)) {
                            toRemove.add(appInfo);
                        }
                    }

                    BudgetAdapter.sInstalledApps.removeAll(toRemove);
                }

                long earliest = ForegroundApplication.getInstance(context).earliestTimestamp();

                if (earliest > 0) {
                    daysObserved = (end - earliest) / (double) (24 * 60 * 60 * 1000);

                    daysObserved = Math.ceil(daysObserved);
                }

                if (daysObserved > me.mDays) {
                    daysObserved = me.mDays;
                }

                Runnable refreshRunnable = new Runnable() {
                    @Override
                    public void run() {
                        me.notifyDataSetChanged();
                    }
                };

                Set<String> replacements = app.replacementPackages();

                synchronized (BudgetAdapter.sInstalledApps) {
                    for (ApplicationInfo info : BudgetAdapter.sInstalledApps) {
                        Intent launchIntent = BudgetAdapter.sLaunchIntents.get(info.packageName);

                        if (replacements.contains(info.packageName)) {
                            launchIntent = new Intent(info.packageName);

                            HashMap<String, Object> appInfo = me.mAppInfos.get(info.packageName);

                            if (appInfo != null) {
                                long appBudget = app.budgetForPackage(info.packageName);

                                if (appBudget >= 0) {
                                    appInfo.put(BudgetAdapter.APP_TIME_BUDGET, appBudget);
                                }

                            }

                            BudgetAdapter.sLaunchIntents.put(info.packageName, launchIntent);
                        } else {
                            if (launchIntent == null) {
                                launchIntent = packages.getLaunchIntentForPackage(info.packageName);

                                if (launchIntent == null) {
                                    launchIntent = new Intent("");
                                }

                                BudgetAdapter.sLaunchIntents.put(info.packageName, launchIntent);
                            }
                        }

                        if ("".equals(launchIntent.getAction()) == false) {
                            if (me.mInstalledPackages.contains(info.packageName) == false) {
                                HashMap<String, Object> appInfo = new HashMap<>();

                                appInfo.put(BudgetAdapter.APP_PACKAGE, info.packageName);

                                if (BudgetAdapter.ACTION_HOMESCREEN.equals(launchIntent.getAction())) {
                                    appInfo.put(BudgetAdapter.APP_LABEL, me.mContext.getString(R.string.label_home_screen));
                                } else {
                                    CharSequence label = packages.getApplicationLabel(info);

                                    if (label != null) {
                                        appInfo.put(BudgetAdapter.APP_LABEL, label.toString());
                                    } else {
                                        appInfo.put(BudgetAdapter.APP_LABEL, info.packageName);
                                    }
                                }

                                appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_UNKNOWN);

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    switch (info.category) {
                                        case ApplicationInfo.CATEGORY_AUDIO:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_AUDIO);
                                            break;
                                        case ApplicationInfo.CATEGORY_GAME:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_GAME);
                                            break;
                                        case ApplicationInfo.CATEGORY_IMAGE:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_IMAGE);
                                            break;
                                        case ApplicationInfo.CATEGORY_MAPS:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_MAPS);
                                            break;
                                        case ApplicationInfo.CATEGORY_NEWS:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_NEWS);
                                            break;
                                        case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_PRODUCTIVITY);
                                            break;
                                        case ApplicationInfo.CATEGORY_SOCIAL:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_SOCIAL);
                                            break;
                                        case ApplicationInfo.CATEGORY_VIDEO:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_VIDEO);
                                            break;
                                        case ApplicationInfo.CATEGORY_UNDEFINED:
                                            appInfo.put(BudgetAdapter.APP_CATEGORY, BudgetAdapter.APP_CATEGORY_UNKNOWN);
                                            break;
                                    }
                                }

                                appInfo.put(BudgetAdapter.APP_PRIORITY, app.fetchPriority(info.packageName));

                                me.mInstalledPackages.add(info.packageName);

                                synchronized (me.mAppInfos) {
                                    me.mAppInfos.put(info.packageName, appInfo);
                                }
                            }

                            int usageDaysObserved = ForegroundApplication.getInstance(context).fetchUsageDaysBetween(when, end, true);

                            synchronized (me.mAppInfos) {
                                HashMap<String, Object> appInfo = me.mAppInfos.get(info.packageName);

                                long appBudget = app.budgetForPackage(info.packageName);

                                if (appBudget >= 0) {
                                    appInfo.put(BudgetAdapter.APP_TIME_BUDGET, appBudget);
                                }

                                double usage = (double) ForegroundApplication.getInstance(context).fetchUsageBetween(info.packageName, when, end, true);

                                usage = usage / (double) usageDaysObserved;

                                if (Double.isNaN(usage)) {
                                    usage = 0;
                                }

                                appInfo.put(BudgetAdapter.APP_USAGE, usage);
                            }


                            if (me.mInitialized == false) {
                                Collections.sort(me.mInstalledPackages, new Comparator<String>() {
                                    @Override
                                    public int compare(String one, String two) {
                                        Integer onePriority = (Integer) me.mAppInfos.get(one).get(BudgetAdapter.APP_PRIORITY);
                                        Integer twoPriority = (Integer) me.mAppInfos.get(two).get(BudgetAdapter.APP_PRIORITY);

                                        int result = onePriority.compareTo(twoPriority);

                                        if (result != 0) {
                                            return result;
                                        }

                                        Double oneUsage = (Double) me.mAppInfos.get(one).get(BudgetAdapter.APP_USAGE);
                                        Double twoUsage = (Double) me.mAppInfos.get(two).get(BudgetAdapter.APP_USAGE);

                                        result = twoUsage.compareTo(oneUsage);

                                        if (result == 0) {
                                            String oneName = (String) me.mAppInfos.get(one).get(BudgetAdapter.APP_LABEL);
                                            String twoName = (String) me.mAppInfos.get(two).get(BudgetAdapter.APP_LABEL);

                                            result = oneName.compareTo(twoName);
                                        }

                                        return result;
                                    }
                                });
                            }
                        }
                    }
                }

                if (me.mInitialized) {
                    Collections.sort(me.mInstalledPackages, new Comparator<String>() {
                        @Override
                        public int compare(String one, String two) {
                            Integer onePriority = (Integer) me.mAppInfos.get(one).get(BudgetAdapter.APP_PRIORITY);
                            Integer twoPriority = (Integer) me.mAppInfos.get(two).get(BudgetAdapter.APP_PRIORITY);

                            int result = onePriority.compareTo(twoPriority);

                            if (result != 0) {
                                return result;
                            }

                            Double oneUsage = (Double) me.mAppInfos.get(one).get(BudgetAdapter.APP_USAGE);
                            Double twoUsage = (Double) me.mAppInfos.get(two).get(BudgetAdapter.APP_USAGE);

                            result = twoUsage.compareTo(oneUsage);

                            if (result == 0) {
                                String oneName = (String) me.mAppInfos.get(one).get(BudgetAdapter.APP_LABEL);
                                String twoName = (String) me.mAppInfos.get(two).get(BudgetAdapter.APP_LABEL);

                                result = oneName.compareTo(twoName);
                            }

                            return result;
                        }
                    });
                }

                synchronized(me.mCachedAppInfos) {
                    me.mCachedAppInfos.clear();
                    me.mCachedAppInfos.putAll(me.mAppInfos);
                }

                if (refreshStart > 0) {
                    long elapsed = System.currentTimeMillis() - refreshStart;

                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("time-elapsed", elapsed);
                    payload.put("days", me.mDays);
                    payload.put("packages", me.mInstalledPackages.size());

                    AppLogger.getInstance(context).log("budget-adapter-init", payload);
                }

                mainHandler.post(refreshRunnable);

                me.mInitialized = true;
                me.mUpdating = false;
            }
        });
    }

    @NonNull
    @Override
    public BudgetAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_app_budget, parent, false);

        return new BudgetAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetAdapter.ViewHolder holder, int position) {
        synchronized(this.mCachedAppInfos) {
            if (this.mCachedAppInfos.size() == 0) {
                this.refreshUsage(this.mContext);
            }
        }

        if (position == 0) {
            long totalUsage = 0;

            synchronized (this.mCachedAppInfos) {
                for (String packageName : this.mCachedAppInfos.keySet()) {
                    HashMap<String, Object> appInfo = this.mCachedAppInfos.get(packageName);

                    Double usage = (Double) appInfo.get(BudgetAdapter.APP_USAGE);

                    if (usage != null) {
                        totalUsage += (long) usage.doubleValue();
                    }
                }
            }

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            cal.add(Calendar.DATE, 1);

            long end = cal.getTimeInMillis();

            double daysObserved = 1.0;

            long earliest = ForegroundApplication.getInstance(this.mContext).earliestTimestamp();

            if (earliest > 0) {
                daysObserved = (end - earliest) / (double) (24 * 60 * 60 * 1000);

                daysObserved = Math.ceil(daysObserved);
            }

            if (daysObserved > this.mDays) {
                daysObserved = this.mDays;
            }

            DateFormat format = android.text.format.DateFormat.getMediumDateFormat(this.mContext);

            cal = Calendar.getInstance();
            cal.setTimeInMillis(this.mStart);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long when = cal.getTimeInMillis();

            if (this.mDays == 0) {
                when = 0;
            } else if (this.mDays > 1) {
                cal.add(Calendar.DATE, 0 - ((int) daysObserved) + 1);

                when = cal.getTimeInMillis();
            }

            long now = System.currentTimeMillis();

            String start = format.format(new Date(when));
            String finish = format.format(new Date(now));

            if (when > now) {
                start = finish;
            }

            String title = this.mContext.getString(R.string.title_average_daily_usage, start, finish);

            long averageUsage = totalUsage;

            if (this.mDays == 1) {
                title = this.mContext.getString(R.string.title_average_daily_usage_today);
            } else if (start.equals(finish)) {
                title = this.mContext.getString(R.string.title_average_daily_usage_single, start);
            }

            if (averageUsage < 0) {
                averageUsage = 0;
            }

            String subtitle = DurationFormatUtils.formatDurationWords(averageUsage, true, true);

            holder.updateView(title, subtitle);
        } else {
            String packageName = this.mInstalledPackages.get(position - 1);

            synchronized (this.mCachedAppInfos) {
                holder.updateView(this.mCachedAppInfos.get(packageName));
            }
        }
    }

    @Override
    public int getItemCount() {
        int size = 1;

        synchronized (this.mCachedAppInfos) {
            size = this.mCachedAppInfos.size() + 1;
        }

        return size;
    }
}
