package com.audacious_software.phone_dashboard;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.audacious_software.passive_data_kit.PassiveDataKit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class KeepAliveJobService extends JobService {
    public static final int JOB_ID = 12345;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Intent fireIntent = new Intent(KeepAliveService.ACTION_KEEP_ALIVE, null, this, KeepAliveService.class);

        PassiveDataKit.getInstance(this).annotateForegroundIntent(fireIntent);

        KeepAliveService.enqueueWork(this, KeepAliveService.class, KeepAliveService.JOB_ID, fireIntent);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return true;
    }
}
