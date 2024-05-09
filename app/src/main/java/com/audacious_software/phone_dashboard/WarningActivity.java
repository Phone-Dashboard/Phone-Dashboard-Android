package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class WarningActivity extends Activity {
    public static final String PACKAGE_NAME = "com.audacious_software.phone_dashboard.WarningActivity.DIALOG_TITLE";
    public static final String PACKAGE_LABEL = "com.audacious_software.phone_dashboard.WarningActivity.PACKAGE_LABEL";
    public static final String WARNING_DURATION = "com.audacious_software.phone_dashboard.WarningActivity.WARNING_DURATION";
    public static final String TOTAL_LIMIT_MINUTES = "com.audacious_software.phone_dashboard.WarningActivity.TOTAL_LIMIT_MINUTES";

    private int mDate = -1;

    private Handler mHandler = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Calendar cal = Calendar.getInstance();

        this.mDate = cal.get(Calendar.DATE);

        this.mHandler = new Handler(Looper.getMainLooper());
    }

    private void checkSameDate() {
        final WarningActivity me = this;

        Calendar cal = Calendar.getInstance();

        int date = cal.get(Calendar.DATE);

        if (date != this.mDate) {
            // Date changed - kill activity

            this.mHandler.removeCallbacksAndMessages(null);

            this.finish();
        } else {
            this.mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    me.checkSameDate();
                }
            }, 5000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        this.mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.checkSameDate();

        final WarningActivity me = this;
        final AppApplication app = (AppApplication) this.getApplication();

        Bundle extras = this.getIntent().getExtras();

        final String packageName = extras.getString(WarningActivity.PACKAGE_NAME);
        final String packageLabel = extras.getString(WarningActivity.PACKAGE_LABEL);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(app.labelForReplacement(packageLabel));

        long warningPeriod = 0;
        int totalLimit = 0;

        if (extras.containsKey(WarningActivity.WARNING_DURATION)) {
            warningPeriod = extras.getLong(WarningActivity.WARNING_DURATION);
        }

        if (extras.containsKey(WarningActivity.TOTAL_LIMIT_MINUTES)) {
            totalLimit = extras.getInt(WarningActivity.TOTAL_LIMIT_MINUTES);
        }

        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("package", packageName);

        if (warningPeriod == 0) {
            long snoozeDelay = app.snoozeDelay();

            int minutes = (int) snoozeDelay / (60 * 1000);

            payload.put("snooze-minutes", minutes);
            payload.put("snooze-delay", snoozeDelay);

            long activeSnooze = app.activeSnooze(packageName);

            if (activeSnooze != Long.MAX_VALUE && activeSnooze > System.currentTimeMillis()){
                Date when = new Date(activeSnooze);
                DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

                builder.setMessage(this.getString(R.string.dialog_message_snooze_active, packageLabel, timeFormat.format(when)));

                AppLogger.getInstance(this).log("app-blocked-delayed", payload);

                builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        AppLogger.getInstance(me).log("closed-delay-warning", payload);

                        me.finish();
                    }
                });

                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        AppLogger.getInstance(me).log("closed-delay-warning", payload);

                        me.finish();
                    }
                });

            } else if (snoozeDelay >= 0) {
                if (totalLimit == 1) {
                    builder.setMessage(this.getString(R.string.message_limit_reached_with_snooze_single, minutes));
                } else{
                    builder.setMessage(this.getString(R.string.message_limit_reached_with_snooze, totalLimit, minutes));
                }

                AppLogger.getInstance(this).log("app-blocked-can-snooze", payload);

                builder.setNeutralButton(R.string.action_snooze_limit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        me.snoozePackage(snoozeDelay);
                    }
                });

                builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        AppLogger.getInstance(me).log("skipped-snooze", payload);

                        me.finish();
                    }
                });

                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        AppLogger.getInstance(me).log("skipped-snooze", payload);

                        me.finish();
                    }
                });
            } else {
                AppLogger.getInstance(this).log("app-blocked-no-snooze", payload);

                if (totalLimit == 1) {
                    builder.setMessage(R.string.message_limit_reached_single);
                } else {
                    builder.setMessage(this.getString(R.string.message_limit_reached, totalLimit));
                }

                builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        AppLogger.getInstance(me).log("app-blocked-no-snooze-closed", payload);

                        me.finish();
                    }
                });

                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        AppLogger.getInstance(me).log("app-blocked-no-snooze-closed", payload);

                        me.finish();
                    }
                });
            }
        } else {
            int minutesRemaining = (int) warningPeriod / (60 * 1000);

            payload.put("minutes-remaining", minutesRemaining);

            AppLogger.getInstance(this).log("app-block-warning", payload);

            if (minutesRemaining == 1) {
                builder.setMessage(this.getString(R.string.message_limit_approaching_single));
            } else {
                builder.setMessage(this.getString(R.string.message_limit_approaching, minutesRemaining));
            }

            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    AppLogger.getInstance(me).log("closed-warning", payload);

                    me.finish();
                }
            });

            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    AppLogger.getInstance(me).log("closed-warning", payload);

                    me.finish();
                }
            });
        }

        builder.create().show();

        app.logAppAppearance(System.currentTimeMillis(), "app-warning-activity");
    }

    private void snoozePackage(long snoozeDelay) {
        final WarningActivity me = this;

        Bundle extras = this.getIntent().getExtras();

        final String packageName = extras.getString(WarningActivity.PACKAGE_NAME);
        final String packageLabel = extras.getString(WarningActivity.PACKAGE_LABEL);

        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("package", packageName);

        final AppApplication app = (AppApplication) this.getApplication();

        long snoozeStart = System.currentTimeMillis() + snoozeDelay;

        Date when = new Date(snoozeStart);
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_snooze_limit);

        @SuppressLint("InflateParams")
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_snooze_prompt, null, false);

        TextView appMessage = content.findViewById(R.id.message_app_snooze);

        String message = this.getString(R.string.dialog_message_snooze_free,  packageLabel, timeFormat.format(when));

        double remainingBudget = app.remainingBudget(0);

        if (remainingBudget > 0 && AppApplication.BLOCKER_TYPE_COSTLY_SNOOZE.equals(app.blockerType())) {
            float snoozeCost = app.snoozeCost();

            message = this.getString(R.string.dialog_message_snooze_costly,  packageLabel, timeFormat.format(when), snoozeCost);
        }

        appMessage.setText(message);

        final TextInputEditText minuteField = content.findViewById(R.id.field_minutes);

        builder.setView(content);

        builder.setPositiveButton(R.string.action_snooze_app, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try {
                    long minutes = Long.parseLong(minuteField.getText().toString());

                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    cal.add(Calendar.DATE, 1);

                    long remainingMinutes = (cal.getTimeInMillis() - System.currentTimeMillis()) / (60 * 1000);

                    if (minutes > remainingMinutes) {
                        minutes = remainingMinutes;
                    }

                    if (minutes > 0) {
                        long delayUntil = System.currentTimeMillis() + snoozeDelay;

                        if (delayUntil > cal.getTimeInMillis()) {
                            delayUntil = cal.getTimeInMillis();

                            minutes = 0;

                            payload.put("day-end-truncated", true);
                        }

                        payload.put("snooze-minutes", minutes);
                        payload.put("snooze-delay", snoozeDelay);

                        AppLogger.getInstance(me).log("snoozed-app-limit", payload);

                        AppSnoozeGenerator.getInstance(me).addSnooze(packageName, minutes * 60 * 1000L);

                        app.setActiveSnooze(packageName, delayUntil);

                        Toast.makeText(me, R.string.toast_app_snoozed, Toast.LENGTH_LONG).show();
                    }

                    me.finish();
                } catch (NumberFormatException ex) {
                    Toast.makeText(me, R.string.toast_cannot_parse_snooze, Toast.LENGTH_LONG).show();

                    me.snoozePackage(snoozeDelay);
                }
            }
        });

        builder.setNeutralButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                AppLogger.getInstance(me).log("cancelled-snooze", payload);
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                AppLogger.getInstance(me).log("cancelled-snooze", payload);

                me.finish();
            }
        });

        builder.create().show();
    }
}
