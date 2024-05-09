package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;

public class DatabaseActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();

        final DatabaseActivity me = this;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        @SuppressLint("InflateParams")
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_database, null, false);

        ProgressBar progress = content.findViewById(R.id.database_bar);

        progress.setIndeterminate(true);

        builder.setView(content);

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                me.finish();
            }
        });

        builder.create().show();

        AppLogger.getInstance(this).log("maintenance-dialog-shown");

        Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                me.finish();
            }
        }, 5000);

        AppApplication app = (AppApplication) this.getApplication();

        app.logAppAppearance(System.currentTimeMillis(), "database-message-activity");
    }
}
