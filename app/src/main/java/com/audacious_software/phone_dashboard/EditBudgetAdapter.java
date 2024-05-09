package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

public class EditBudgetAdapter extends RecyclerView.Adapter<EditBudgetAdapter.ViewHolder> {
    private static final String APP_PACKAGE = "com.audacious_software.phone_dashboard.APP_PACKAGE";
    private static final String APP_LABEL = "com.audacious_software.phone_dashboard.APP_LABEL";
    private static final String APP_TIME_BUDGET_TODAY = "com.audacious_software.phone_dashboard.APP_TIME_BUDGET_TODAY";
    private static final String APP_TIME_BUDGET_TOMORROW = "com.audacious_software.phone_dashboard.APP_TIME_BUDGET_TOMORROW";

    private static final String APP_CATEGORY = "com.audacious_software.phone_dashboard.APP_CATEGORY";
    private static final String APP_CATEGORY_UNKNOWN = "unknown";
    private static final String APP_CATEGORY_AUDIO = "audio";
    private static final String APP_CATEGORY_GAME = "game";
    private static final String APP_CATEGORY_IMAGE = "image";
    private static final String APP_CATEGORY_MAPS = "maps";
    private static final String APP_CATEGORY_NEWS = "news";
    private static final String APP_CATEGORY_PRODUCTIVITY = "productivity";
    private static final String APP_CATEGORY_SOCIAL = "social";
    private static final String APP_CATEGORY_VIDEO = "video";

    private static final String APP_PRIORITY = "com.audacious_software.phone_dashboard.EditBudgetAdapter.APP_PRIORITY";

    private Context mContext = null;

    private boolean mEditable = true;
    private boolean mForToday = false;

    private HashMap<String, Long> mTodayBudget = null;
    private HashMap<String, Long> mTomorrowBudget = null;

    private ArrayList<String> mDirtyPackages = new ArrayList<>();

    public boolean isDirty() {
        return this.mDirtyPackages.size() > 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mView;

        public ViewHolder(View view) {
            super(view);

            this.mView = view;
        }

        public void updateView(final HashMap<String, Object> appDefinition, final EditBudgetAdapter adapter) {
            final Activity activity = (Activity) this.mView.getContext();

            final AppApplication app = (AppApplication) activity.getApplication();

            TextView appName = this.mView.findViewById(R.id.app_name);
            ImageView appIcon = this.mView.findViewById(R.id.app_icon);

            String packageName = (String) appDefinition.get(EditBudgetAdapter.APP_PACKAGE);

            appName.setText(appDefinition.get(EditBudgetAdapter.APP_LABEL).toString());

            try {
                appIcon.setImageDrawable(activity.getPackageManager().getApplicationIcon(packageName));
            } catch (PackageManager.NameNotFoundException e) {
                appIcon.setImageDrawable(app.iconForReplacement(packageName));
            }

            TextView todayBudget = this.mView.findViewById(R.id.app_today_budget);

            Long todayMillis = (Long) appDefinition.get(EditBudgetAdapter.APP_TIME_BUDGET_TODAY);

            if (todayMillis == null) {
                todayMillis = Long.parseLong("-1");
            }

            if (todayMillis >= 0) {
                int minutes = (int) (todayMillis / (1000 * 60));

                todayBudget.setText(activity.getString(R.string.budget_duration, minutes));
            } else if (todayMillis == Long.MIN_VALUE) {
                todayBudget.setText(R.string.budget_exempt);
            } else {
                todayBudget.setText(R.string.budget_unlimited);
            }

            this.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    adapter.promptForLimit(appDefinition, activity);
                }
            });

            TextView tomorrowBudget = this.mView.findViewById(R.id.app_tomorrow_budget);

            Long tomorrowMillis = (Long) appDefinition.get(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW);

            if (tomorrowMillis == null) {
                tomorrowMillis = todayMillis;
            }

            if (tomorrowMillis >= 0) { // && tomorrowMillis.longValue() != todayMillis.longValue()) {
                tomorrowBudget.setVisibility(View.VISIBLE);
                int minutes = (int) (tomorrowMillis / (1000 * 60));

                tomorrowBudget.setText(activity.getString(R.string.budget_duration_tomorrow, minutes));
            } else if (tomorrowMillis == -2) {
                tomorrowBudget.setVisibility(View.VISIBLE);
                tomorrowBudget.setText(R.string.budget_duration_tomorrow_none);
            } else {
                tomorrowBudget.setVisibility(View.GONE);
            }

            this.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    adapter.promptForLimit(appDefinition, activity);
                }
            });

            ImageView fitsbyIcon = this.mView.findViewById(R.id.icon_fitsby);
            fitsbyIcon.setVisibility(View.GONE);

            if (((Integer) appDefinition.get(EditBudgetAdapter.APP_PRIORITY)).intValue() < 100) {
                fitsbyIcon.setVisibility(View.VISIBLE);
            } else {
                fitsbyIcon.setVisibility(View.GONE);
            }
        }
    }

    private ArrayList<String> mInstalledPackages = new ArrayList<>();
    private HashMap<String, HashMap<String, Object>> mAppInfos = new HashMap<>();

    public EditBudgetAdapter(Context context, boolean editable, boolean forToday) {
        this.mForToday = forToday;

        this.mContext = context;

        final AppApplication app = (AppApplication) context.getApplicationContext();

        Calendar calendar = Calendar.getInstance();

        this.mTodayBudget = DailyBudgetGenerator.getInstance(context).budgetForDate(calendar.getTime(), false);

        calendar.add(Calendar.DATE, 1);

        this.mTomorrowBudget = DailyBudgetGenerator.getInstance(context).budgetForDate(calendar.getTime(), false);

        this.mEditable = editable;

        if (this.mTodayBudget == null) {
            this.mTodayBudget = new HashMap<>();
        }

        if (this.mTomorrowBudget == null) {
            this.mTomorrowBudget = new HashMap<>();
        }

        PackageManager packages = context.getPackageManager();
        List<ApplicationInfo> appsList = packages.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo info : appsList) {
            HashMap<String, Object> appInfo = new HashMap<>();

            if (packages.getLaunchIntentForPackage(info.packageName) != null) {
                appInfo.put(EditBudgetAdapter.APP_PACKAGE, info.packageName);
                appInfo.put(EditBudgetAdapter.APP_LABEL, packages.getApplicationLabel(info).toString());

                if (app.appIsExempt(info.packageName)) {
                    appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, Long.MIN_VALUE);
                    appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, Long.MIN_VALUE);
                } else {
                    if (this.mTodayBudget.containsKey(info.packageName)) {
                        appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, this.mTodayBudget.get(info.packageName));
                    } else {
                        appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, Long.valueOf(-1));
                    }

                    if (this.mTomorrowBudget.containsKey(info.packageName)) {
                        appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, this.mTomorrowBudget.get(info.packageName));
                    } else {
                        appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, Long.valueOf(-1));
                    }
                }

                appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_UNKNOWN);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    switch (info.category) {
                        case ApplicationInfo.CATEGORY_AUDIO:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_AUDIO);
                            break;
                        case ApplicationInfo.CATEGORY_GAME:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_GAME);
                            break;
                        case ApplicationInfo.CATEGORY_IMAGE:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_IMAGE);
                            break;
                        case ApplicationInfo.CATEGORY_MAPS:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_MAPS);
                            break;
                        case ApplicationInfo.CATEGORY_NEWS:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_NEWS);
                            break;
                        case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_PRODUCTIVITY);
                            break;
                        case ApplicationInfo.CATEGORY_SOCIAL:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_SOCIAL);
                            break;
                        case ApplicationInfo.CATEGORY_VIDEO:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_VIDEO);
                            break;
                        case ApplicationInfo.CATEGORY_UNDEFINED:
                            appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_UNKNOWN);
                            break;
                    }
                }

                appInfo.put(EditBudgetAdapter.APP_PRIORITY, app.fetchPriority(info.packageName));

                this.mInstalledPackages.add(info.packageName);
                this.mAppInfos.put(info.packageName, appInfo);
            }
        }

        for (String replacement : app.replacementPackages()) {
            HashMap<String, Object> appInfo = new HashMap<>();

            appInfo.put(EditBudgetAdapter.APP_PACKAGE, replacement);
            appInfo.put(EditBudgetAdapter.APP_LABEL, app.labelForReplacement(replacement));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.put(EditBudgetAdapter.APP_CATEGORY, EditBudgetAdapter.APP_CATEGORY_UNKNOWN);
            }

            if (this.mTodayBudget.containsKey(replacement)) {
                appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, this.mTodayBudget.get(replacement));
            } else {
                appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, Long.valueOf(-1));
            }

            if (this.mTomorrowBudget.containsKey(replacement)) {
                appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, this.mTomorrowBudget.get(replacement));
            } else {
                appInfo.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, Long.valueOf(-1));
            }

            this.mInstalledPackages.add(replacement);

            appInfo.put(EditBudgetAdapter.APP_PRIORITY, app.fetchPriority(replacement));

            this.mAppInfos.put(replacement, appInfo);
        }


        Set<String> toRemove = new HashSet<>();

        for (String packageName : this.mInstalledPackages) {
            if (app.hidePackage(packageName)) {
                toRemove.add(packageName);
            }
        }

        this.mInstalledPackages.removeAll(toRemove);

        this.sortPackages();
    }

    private void sortPackages() {
        final EditBudgetAdapter me = this;

        Collections.sort(this.mInstalledPackages, new Comparator<String>() {
            @Override
            public int compare(String one, String two) {
                HashMap<String, Object> oneDef = me.mAppInfos.get(one);
                HashMap<String, Object> twoDef = me.mAppInfos.get(two);

                Integer onePriority = (Integer) me.mAppInfos.get(one).get(EditBudgetAdapter.APP_PRIORITY);
                Integer twoPriority = (Integer) me.mAppInfos.get(two).get(EditBudgetAdapter.APP_PRIORITY);

                int result = onePriority.compareTo(twoPriority);

                if (result != 0) {
                    return result;
                }

                Long budgetOne = (Long) oneDef.get(EditBudgetAdapter.APP_TIME_BUDGET_TODAY);
                Long budgetTwo = (Long) twoDef.get(EditBudgetAdapter.APP_TIME_BUDGET_TODAY);

                if (budgetOne == null && budgetTwo == null) {
                    return oneDef.get(EditBudgetAdapter.APP_LABEL).toString().compareTo(twoDef.get(EditBudgetAdapter.APP_LABEL).toString());
                } else if (budgetOne == null) {
                    return 1;
                } else if (budgetTwo == null) {
                    return -1;
                }

                int compare = budgetTwo.compareTo(budgetOne);

                if (compare == 0) {
                    Long tomorrowOne = (Long) oneDef.get(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW);
                    Long tomorrowTwo = (Long) twoDef.get(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW);

                    if (tomorrowOne == null && tomorrowTwo == null) {
                        return oneDef.get(EditBudgetAdapter.APP_LABEL).toString().compareTo(twoDef.get(EditBudgetAdapter.APP_LABEL).toString());
                    } else if (tomorrowOne == null) {
                        return 1;
                    } else if (tomorrowTwo == null) {
                        return -1;
                    }

                    compare = tomorrowTwo.compareTo(tomorrowOne);

                    if (compare == 0) {
                        compare = oneDef.get(EditBudgetAdapter.APP_LABEL).toString().compareTo(twoDef.get(EditBudgetAdapter.APP_LABEL).toString());
                    }
                }

                return compare;
            }
        });
    }

    @NonNull
    @Override
    public EditBudgetAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_app_edit_budget, parent, false);

        return new EditBudgetAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EditBudgetAdapter.ViewHolder holder, int position) {
        String packageName = this.mInstalledPackages.get(position);

        holder.updateView(this.mAppInfos.get(packageName), this);
    }

    @Override
    public int getItemCount() {
        return this.mInstalledPackages.size();
    }

    public HashMap<String, Long> getTodayBudget() {
        HashMap<String, Long> today = new HashMap<>(this.mTodayBudget);

        ArrayList<String> keys = new ArrayList<>();

        for (String key : today.keySet()) {
            long duration = today.get(key);

            if (duration < 0) {
                keys.add(key);
            }
        }

        for (String key : keys) {
            today.remove(key);
        }

        return today;
    }

    public HashMap<String, Long> getTomorrowBudget() {
        HashMap<String, Long> budget = this.getTodayBudget();

        HashMap<String, Long> tomorrow = new HashMap<>(this.mTomorrowBudget);

        ArrayList<String> keys = new ArrayList<>();

        for (String key : tomorrow.keySet()) {
            long duration = tomorrow.get(key);

            if (duration < 0) {
                keys.add(key);
            } else {
                budget.put(key, duration);
            }
        }

        for (String key : keys) {
            budget.remove(key);
        }

        return budget;
    }

    private void promptForLimit(final HashMap<String, Object> appDefinition, Activity activity) {
        if (this.mEditable == false) {
            return;
        }

        final EditBudgetAdapter me = this;

        Long budgetMillis = (Long) appDefinition.get(EditBudgetAdapter.APP_TIME_BUDGET_TODAY);

        if (budgetMillis == Long.MIN_VALUE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setTitle(appDefinition.get(EditBudgetAdapter.APP_LABEL).toString());
            builder.setMessage(R.string.message_app_exempt);

            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });

            builder.create().show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setTitle(appDefinition.get(EditBudgetAdapter.APP_LABEL).toString());

            @SuppressLint("InflateParams")
            View content = LayoutInflater.from(activity).inflate(R.layout.dialog_limit_prompt, null, false);

            final TextInputEditText minuteField = content.findViewById(R.id.field_minutes);

            if (appDefinition.containsKey(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW)) {
                long duration = (Long) appDefinition.get(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW);

                if (duration >= 0) {
                    minuteField.setText("" + (int) duration / (60 * 1000));
                }
            } else if (appDefinition.containsKey(EditBudgetAdapter.APP_TIME_BUDGET_TODAY)) {
                long duration = (Long) appDefinition.get(EditBudgetAdapter.APP_TIME_BUDGET_TODAY);

                if (duration >= 0) {
                    minuteField.setText("" + (int) duration / (60 * 1000));
                }
            }

            builder.setView(content);

            builder.setPositiveButton(R.string.action_set_limit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        String packageName = appDefinition.get(EditBudgetAdapter.APP_PACKAGE).toString();

                        int minutes = -1;

                        String minuteText = minuteField.getText().toString();

                        if (minuteText.trim().length() > 0) {
                            minutes = Integer.parseInt(minuteText);
                        }

                        if (minutes >= 0) {
                            long duration = 1000L * 60L * minutes;

                            if (me.mForToday) {
                                appDefinition.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, duration);
                                me.mTodayBudget.put(packageName, duration);
                            } else {
                                appDefinition.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, duration);
                                me.mTomorrowBudget.put(packageName, duration);
                            }

                            if (me.mDirtyPackages.contains(packageName) == false) {
                                me.mDirtyPackages.add(packageName);
                            }
                        } else {
                            if (me.mForToday) {
                                appDefinition.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, Long.valueOf(-1));
                                me.mTodayBudget.put(packageName, Long.valueOf(-1));
                            } else {
                                appDefinition.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, Long.valueOf(-1));
                                me.mTomorrowBudget.put(appDefinition.get(EditBudgetAdapter.APP_PACKAGE).toString(), Long.valueOf(-1));
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Do nothing if string is entered.
                    }

                    me.sortPackages();

                    me.notifyDataSetChanged();
                }
            });

            builder.setNeutralButton(R.string.action_remove_limit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String packageName = appDefinition.get(EditBudgetAdapter.APP_PACKAGE).toString();

                    if (me.mForToday) {
                        appDefinition.put(EditBudgetAdapter.APP_TIME_BUDGET_TODAY, Long.valueOf(-2));
                        me.getTodayBudget().put(packageName, Long.valueOf(-1));
                    } else {
                        appDefinition.put(EditBudgetAdapter.APP_TIME_BUDGET_TOMORROW, Long.valueOf(-2));
                        me.mTomorrowBudget.put(packageName, Long.valueOf(-1));
                    }

                    me.sortPackages();

                    me.notifyDataSetChanged();

                    if (me.mDirtyPackages.contains(packageName)) {
                        me.mDirtyPackages.remove(packageName);
                    } else {
                        me.mDirtyPackages.add(packageName);
                    }
                }
            });

            builder.create().show();
        }
    }
}
