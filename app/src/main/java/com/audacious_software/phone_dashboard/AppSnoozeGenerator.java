package com.audacious_software.phone_dashboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppSnoozeGenerator extends Generator {
    private static final String GENERATOR_IDENTIFIER = "app-snooze";

    private static final String DATABASE_PATH = "app-snooze.sqlite";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_HISTORY = "history";

    public static final String HISTORY_FETCHED = "fetched";
    public static final String HISTORY_TRANSMITTED = "transmitted";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_APP_PACKAGE = "app_package";
    public static final String HISTORY_DURATION = "duration";

    public static final String HISTORY_ORIGINAL_BUDGET = "original_budget";
    public static final String HISTORY_REMAINING_BUDGET = "remaining_budget";

    private SQLiteDatabase mDatabase = null;

    private static AppSnoozeGenerator sInstance = null;

    public static synchronized AppSnoozeGenerator getInstance(Context context) {
        if (AppSnoozeGenerator.sInstance == null) {
            AppSnoozeGenerator.sInstance = new AppSnoozeGenerator(context.getApplicationContext());
        }

        return AppSnoozeGenerator.sInstance;
    }

    private AppSnoozeGenerator(Context context) {
        super(context);

        this.mContext = context;

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, AppSnoozeGenerator.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.generator_app_snooze_create_history_table));
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.generator_app_snooze_add_original_budget));
                this.mDatabase.execSQL(this.mContext.getString(R.string.generator_app_snooze_add_remaining_budget));
        }

        if (version != AppSnoozeGenerator.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, AppSnoozeGenerator.DATABASE_VERSION);
        }
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        AppSnoozeGenerator.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        Generators.getInstance(this.mContext).registerCustomViewClass(AppSnoozeGenerator.GENERATOR_IDENTIFIER, AppSnoozeGenerator.class);
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public static boolean isEnabled(Context context) {
        return true;
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public static boolean isRunning(Context context) {
        return true;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        ArrayList<Bundle> payloads = new ArrayList<>();

        Cursor c = this.mDatabase.query(AppSnoozeGenerator.TABLE_HISTORY, null, null, null, null, null, AppSnoozeGenerator.HISTORY_OBSERVED + " DESC");

        while (c.moveToNext()) {
            payloads.add(this.getBundle(c));
        }

        c.close();

        return payloads;
    }

    private Bundle getBundle(Cursor c) {
        Bundle bundle = new Bundle();

        bundle.putLong(AppSnoozeGenerator.HISTORY_FETCHED, c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_FETCHED)));
        bundle.putLong(AppSnoozeGenerator.HISTORY_TRANSMITTED, c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_TRANSMITTED)));
        bundle.putLong(AppSnoozeGenerator.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_OBSERVED)));
        bundle.putLong(AppSnoozeGenerator.HISTORY_DURATION, c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_DURATION)));
        bundle.putString(AppSnoozeGenerator.HISTORY_APP_PACKAGE, c.getString(c.getColumnIndex(AppSnoozeGenerator.HISTORY_APP_PACKAGE)));

        if (c.isNull(c.getColumnIndex(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET)) == false) {
            bundle.putDouble(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET, c.getDouble(c.getColumnIndex(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET)));
        }

        if (c.isNull(c.getColumnIndex(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET)) == false) {
            bundle.putDouble(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET, c.getDouble(c.getColumnIndex(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET)));
        }

        return bundle;
    }

    public boolean addSnooze(String packageName, long duration) {
        AppApplication app = (AppApplication) this.mContext.getApplicationContext();

        long now = System.currentTimeMillis();

        ContentValues value = new ContentValues();
        value.put(AppSnoozeGenerator.HISTORY_OBSERVED, now);
        value.put(AppSnoozeGenerator.HISTORY_DURATION, duration);
        value.put(AppSnoozeGenerator.HISTORY_APP_PACKAGE, packageName);
        value.put(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET, app.initialSnoozeAmount());
        value.put(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET, app.remainingBudget(1));

        Bundle update = new Bundle();

        update.putLong(AppSnoozeGenerator.HISTORY_OBSERVED, now);
        update.putLong(AppSnoozeGenerator.HISTORY_DURATION, duration);
        update.putString(AppSnoozeGenerator.HISTORY_APP_PACKAGE, packageName);
        update.putDouble(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET, app.initialSnoozeAmount());
        update.putDouble(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET, app.remainingBudget(1));

        this.mDatabase.insert(AppSnoozeGenerator.TABLE_HISTORY, null, value);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(AppSnoozeGenerator.GENERATOR_IDENTIFIER, update);

        return true;
    }

    public int snoozesSince(long timestamp) {
        String where = AppSnoozeGenerator.HISTORY_OBSERVED + " >= ?";
        String[] args = { "" + timestamp};

        Cursor c = this.mDatabase.query(AppSnoozeGenerator.TABLE_HISTORY, null, where, args, null, null, AppSnoozeGenerator.HISTORY_OBSERVED + " DESC");

        int count = c.getCount();

        c.close();

        return count;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(AppSnoozeGenerator.TABLE_HISTORY, null, where, args, null, null, orderBy);
    }

    public long todayExtensions(String appPackage) {
        if (appPackage == null) {
            return 0;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long start  = cal.getTimeInMillis();

        cal.add(Calendar.DATE, 1);

        long end = cal.getTimeInMillis();

        long duration = 0;

        String where = AppSnoozeGenerator.HISTORY_OBSERVED + " >= ? AND " + AppSnoozeGenerator.HISTORY_OBSERVED + " < ? AND " + AppSnoozeGenerator.HISTORY_APP_PACKAGE + " = ?";
        String[] args = { "" + start, "" + end, appPackage};
        String[] cols =  { AppSnoozeGenerator.HISTORY_DURATION };

        Cursor c = this.mDatabase.query(AppSnoozeGenerator.TABLE_HISTORY, cols, where, args, null, null, AppSnoozeGenerator.HISTORY_OBSERVED + " DESC");

        while (c.moveToNext()) {
            if (c.isNull(0) == false) {
                duration += c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_DURATION));
            }
        }

        c.close();

        return duration;
    }

    public long todayLatestExtensions(String appPackage) {
        if (appPackage == null) {
            return 0;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long start  = cal.getTimeInMillis();

        cal.add(Calendar.DATE, 1);

        long end = cal.getTimeInMillis();

        long duration = 0;

        String where = AppSnoozeGenerator.HISTORY_OBSERVED + " >= ? AND " + AppSnoozeGenerator.HISTORY_OBSERVED + " < ? AND " + AppSnoozeGenerator.HISTORY_APP_PACKAGE + " = ?";
        String[] args = { "" + start, "" + end, appPackage};
        String[] cols =  { AppSnoozeGenerator.HISTORY_DURATION };

        Cursor c = this.mDatabase.query(AppSnoozeGenerator.TABLE_HISTORY, cols, where, args, null, null, AppSnoozeGenerator.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            if (c.isNull(0) == false) {
                duration = c.getLong(c.getColumnIndex(AppSnoozeGenerator.HISTORY_DURATION));
            }
        }

        c.close();

        return duration;
    }

    public void insertSnoozeIfMissing(String packageName, long when, long duration) {
        String where = AppSnoozeGenerator.HISTORY_OBSERVED + " = ? AND " + AppSnoozeGenerator.HISTORY_DURATION + " = ? AND " + AppSnoozeGenerator.HISTORY_APP_PACKAGE + " = ?";
        String[] args = { "" + when, "" + duration, packageName};

        Cursor c = this.mDatabase.query(AppSnoozeGenerator.TABLE_HISTORY, null, where, args, null, null, null);

        boolean missing = c.getCount() == 0;

        c.close();

        if (missing) {
            AppApplication app = (AppApplication) this.mContext.getApplicationContext();

            ContentValues value = new ContentValues();
            value.put(AppSnoozeGenerator.HISTORY_OBSERVED, when);
            value.put(AppSnoozeGenerator.HISTORY_DURATION, duration);
            value.put(AppSnoozeGenerator.HISTORY_APP_PACKAGE, packageName);

            value.put(AppSnoozeGenerator.HISTORY_ORIGINAL_BUDGET, app.initialSnoozeAmount());
            value.put(AppSnoozeGenerator.HISTORY_REMAINING_BUDGET, app.remainingBudget(1));

            this.mDatabase.insert(AppSnoozeGenerator.TABLE_HISTORY, null, value);
        }
    }

    @Override
    protected void flushCachedData() {
        // Do nothing - indefinate retention...
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        // Do nothing - indefinate retention...
    }

    @Override
    public String getIdentifier() {
        return AppSnoozeGenerator.GENERATOR_IDENTIFIER;
    }
}
