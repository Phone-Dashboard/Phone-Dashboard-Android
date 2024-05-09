package com.audacious_software.phone_dashboard;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.audacious_software.passive_data_kit.ForegroundService;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

public class KeepAliveService extends JobIntentService {
    public static final String ACTION_KEEP_ALIVE = "com.audacious_software.phone_dashboard.KeepAliveService.ACTION_KEEP_ALIVE";
    public static final int JOB_ID = 12343;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        if (KeepAliveService.ACTION_KEEP_ALIVE.equals(intent.getAction())) {
            AppLogger.getInstance(this).log("keep_alive_called");

            try {
                Schedule.getInstance(this).updateSchedule(false, null, true);
            } catch (RuntimeException e) {
                // Likely memory exhaustion - try again in the next cycle...
            }

            Intent fireIntent = new Intent(KeepAliveService.ACTION_KEEP_ALIVE, null, this, KeepAliveService.class);

            long now = System.currentTimeMillis();

            long fireInterval = (300 * 1000);

            Notification note = ForegroundService.getForegroundNotification(this, fireIntent);

            if (note != null) {
                boolean notify = true;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notify = (note.getChannelId() != null);
                }

                if (notify) {
                    NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.notify(ForegroundService.getNotificationId(), note);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                JobScheduler jobScheduler = (JobScheduler) this.getSystemService(Context.JOB_SCHEDULER_SERVICE);

                if (jobScheduler.getPendingJob(KeepAliveJobService.JOB_ID) == null) {
                    ComponentName component = new ComponentName(this, KeepAliveJobService.class);

                    JobInfo.Builder builder = new JobInfo.Builder(KeepAliveJobService.JOB_ID, component)
                            .setPeriodic(fireInterval);

                    jobScheduler.schedule(builder.build());
                }
            } else {
                AlarmManager alarms = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

                PendingIntent pi = PendingIntent.getService(this, 0, fireIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                alarms.cancel(pi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + fireInterval, pi);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarms.setExact(AlarmManager.RTC_WAKEUP, now + fireInterval, pi);
                } else {
                    alarms.set(AlarmManager.RTC_WAKEUP, now + fireInterval, pi);
                }
            }
        }
    }
}
