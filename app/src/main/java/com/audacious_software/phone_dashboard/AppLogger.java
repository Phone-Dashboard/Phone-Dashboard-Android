package com.audacious_software.phone_dashboard;

import android.content.Context;

import com.audacious_software.passive_data_kit.Logger;

import java.util.HashMap;
import java.util.Map;

public class AppLogger {
    private static AppLogger sInstance = null;

    private Context mContext = null;

    public static AppLogger getInstance(Context context) {
        if (AppLogger.sInstance == null) {
            AppLogger.sInstance = new AppLogger(context.getApplicationContext());
        }

        return AppLogger.sInstance;
    }

    @SuppressWarnings("unused")
    public static void resetInstance(Context context) {
        AppLogger.sInstance = null;

        AppLogger.getInstance(context);
    }

    private AppLogger(Context context) {
        this.mContext  = context.getApplicationContext();
    }

    public void log(String event, Map<String, Object> details) {
        if (details == null) {
            details = new HashMap<>();
        }

        Logger.getInstance(this.mContext).log(event, details);
    }

    public void log(String event) {
        this.log(event, null);
    }
}
