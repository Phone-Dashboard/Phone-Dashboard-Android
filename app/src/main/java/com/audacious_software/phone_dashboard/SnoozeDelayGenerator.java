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
import java.util.Date;
import java.util.List;

public class SnoozeDelayGenerator extends Generator {
    private static final String GENERATOR_IDENTIFIER = "snooze-delay";

    private static final String DATABASE_PATH = "snooze-delay.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";

    public static final String HISTORY_FETCHED = "fetched";
    public static final String HISTORY_TRANSMITTED = "transmitted";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_EFFECTIVE_ON = "effective_on";
    public static final String HISTORY_SNOOZE_DELAY = "snooze_delay";

    private SQLiteDatabase mDatabase = null;

    private static SnoozeDelayGenerator sInstance = null;

    public static synchronized SnoozeDelayGenerator getInstance(Context context) {
        if (SnoozeDelayGenerator.sInstance == null) {
            SnoozeDelayGenerator.sInstance = new SnoozeDelayGenerator(context.getApplicationContext());
        }

        return SnoozeDelayGenerator.sInstance;
    }

    private SnoozeDelayGenerator(Context context) {
        super(context);

        this.mContext = context;

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, SnoozeDelayGenerator.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.generator_snooze_delay_create_history_table));
        }

        if (version != SnoozeDelayGenerator.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, SnoozeDelayGenerator.DATABASE_VERSION);
        }
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        SnoozeDelayGenerator.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        Generators.getInstance(this.mContext).registerCustomViewClass(SnoozeDelayGenerator.GENERATOR_IDENTIFIER, SnoozeDelayGenerator.class);
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public static boolean isEnabled(Context context) {
        return true;
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public static boolean isRunning(Context context) {
        return true;
    }

    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        SnoozeDelayGenerator me = SnoozeDelayGenerator.getInstance(context);

        Cursor c = me.mDatabase.query(SnoozeDelayGenerator.TABLE_HISTORY, null, null, null, null, null, SnoozeDelayGenerator.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        ArrayList<Bundle> payloads = new ArrayList<>();

        Cursor c = this.mDatabase.query(SnoozeDelayGenerator.TABLE_HISTORY, null, null, null, null, null, SnoozeDelayGenerator.HISTORY_OBSERVED + " DESC");

        while (c.moveToNext()) {
            payloads.add(this.getBundle(c));
        }

        c.close();

        return payloads;
    }

    private Bundle getBundle(Cursor c) {
        Bundle bundle = new Bundle();

        bundle.putLong(SnoozeDelayGenerator.HISTORY_FETCHED, c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_FETCHED)));
        bundle.putLong(SnoozeDelayGenerator.HISTORY_TRANSMITTED, c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_TRANSMITTED)));
        bundle.putLong(SnoozeDelayGenerator.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_OBSERVED)));
        bundle.putLong(SnoozeDelayGenerator.HISTORY_EFFECTIVE_ON, c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_EFFECTIVE_ON)));
        bundle.putLong(SnoozeDelayGenerator.HISTORY_SNOOZE_DELAY, c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_SNOOZE_DELAY)));

        return bundle;
    }

    public long latestSnoozeDelay() {
        Cursor c = this.mDatabase.query(SnoozeDelayGenerator.TABLE_HISTORY, null, null, null, null, null, SnoozeDelayGenerator.HISTORY_OBSERVED + " DESC");

        long updated = 0;

        if (c.moveToNext()) {
            updated = c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_SNOOZE_DELAY));
        }

        c.close();

        return updated;
    }

    public long currentSnoozeDelay() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, 1);

        String where = SnoozeDelayGenerator.HISTORY_EFFECTIVE_ON + " < ?";
        String[] args = { "" + cal.getTimeInMillis() };

        Cursor c = this.mDatabase.query(SnoozeDelayGenerator.TABLE_HISTORY, null, where, args, null, null, SnoozeDelayGenerator.HISTORY_OBSERVED + " DESC");

        long updated = 0;

        if (c.moveToNext()) {
            updated = c.getLong(c.getColumnIndex(SnoozeDelayGenerator.HISTORY_SNOOZE_DELAY));
        }

        c.close();

        return updated;
    }

    public boolean addSnoozeDelay(long snoozeDelay) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, 1);

        long when = cal.getTimeInMillis();

        String where = SnoozeDelayGenerator.HISTORY_EFFECTIVE_ON + " = ?";
        String[] args = { "" + when };

        this.mDatabase.delete(SnoozeDelayGenerator.TABLE_HISTORY, where, args);

        ContentValues value = new ContentValues();
        value.put(SnoozeDelayGenerator.HISTORY_OBSERVED, System.currentTimeMillis());
        value.put(SnoozeDelayGenerator.HISTORY_EFFECTIVE_ON, when);
        value.put(SnoozeDelayGenerator.HISTORY_SNOOZE_DELAY, snoozeDelay);

        this.mDatabase.insert(SnoozeDelayGenerator.TABLE_HISTORY, null, value);

        Bundle update = new Bundle();

        update.putLong(SnoozeDelayGenerator.HISTORY_OBSERVED, System.currentTimeMillis());
        update.putLong(SnoozeDelayGenerator.HISTORY_EFFECTIVE_ON, cal.getTimeInMillis());
        update.putLong(SnoozeDelayGenerator.HISTORY_SNOOZE_DELAY, snoozeDelay);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(SnoozeDelayGenerator.GENERATOR_IDENTIFIER, update);

        return true;
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
        return SnoozeDelayGenerator.GENERATOR_IDENTIFIER;
    }
}
