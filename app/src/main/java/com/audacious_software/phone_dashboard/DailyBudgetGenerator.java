package com.audacious_software.phone_dashboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class DailyBudgetGenerator extends Generator {
    private static final String GENERATOR_IDENTIFIER = "daily-app-budget";
    private static final String FULL_LOG_IDENTIFIER = "full-app-budgets";

    private static final String DATABASE_PATH = "daily-app-budget.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";

    public static final String HISTORY_FETCHED = "fetched";
    public static final String HISTORY_TRANSMITTED = "transmitted";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_EFFECTIVE_ON = "effective_on";
    public static final String HISTORY_BUDGET = "budget";

    private static final String HISTORY_BUDGETS = "budgets";

    private SQLiteDatabase mDatabase = null;

    private static DailyBudgetGenerator sInstance = null;

    public static synchronized DailyBudgetGenerator getInstance(Context context) {
        if (DailyBudgetGenerator.sInstance == null) {
            DailyBudgetGenerator.sInstance = new DailyBudgetGenerator(context.getApplicationContext());
        }

        return DailyBudgetGenerator.sInstance;
    }

    private DailyBudgetGenerator(Context context) {
        super(context);

        this.mContext = context;

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, DailyBudgetGenerator.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.generator_daily_app_budget_create_history_table));
        }

        if (version != DailyBudgetGenerator.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, DailyBudgetGenerator.DATABASE_VERSION);
        }
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        DailyBudgetGenerator.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        Generators.getInstance(this.mContext).registerCustomViewClass(DailyBudgetGenerator.GENERATOR_IDENTIFIER, DailyBudgetGenerator.class);
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

        DailyBudgetGenerator me = DailyBudgetGenerator.getInstance(context);

        Cursor c = me.mDatabase.query(DailyBudgetGenerator.TABLE_HISTORY, null, null, null, null, null, DailyBudgetGenerator.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        ArrayList<Bundle> payloads = new ArrayList<>();

        Cursor c = this.mDatabase.query(DailyBudgetGenerator.TABLE_HISTORY, null, null, null, null, null, DailyBudgetGenerator.HISTORY_OBSERVED + " DESC");

        while (c.moveToNext()) {
            payloads.add(this.getBundle(c));
        }

        c.close();

        return payloads;
    }

    private Bundle getBundle(Cursor c) {
        Bundle bundle = new Bundle();

        bundle.putLong(DailyBudgetGenerator.HISTORY_FETCHED, c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_FETCHED)));
        bundle.putLong(DailyBudgetGenerator.HISTORY_TRANSMITTED, c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_TRANSMITTED)));
        bundle.putLong(DailyBudgetGenerator.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_OBSERVED)));
        bundle.putLong(DailyBudgetGenerator.HISTORY_EFFECTIVE_ON, c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_EFFECTIVE_ON)));
        bundle.putString(DailyBudgetGenerator.HISTORY_BUDGET, c.getString(c.getColumnIndex(DailyBudgetGenerator.HISTORY_BUDGET)));

        return bundle;
    }

    public long latestBudgetUpdate() {
        Cursor c = this.mDatabase.query(DailyBudgetGenerator.TABLE_HISTORY, null, null, null, null, null, DailyBudgetGenerator.HISTORY_OBSERVED + " DESC");

        long updated = 0;

        if (c.moveToNext()) {
            updated = c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_OBSERVED));
        }

        c.close();

        return updated;
    }

    public boolean addBudgetForDate(Date date, HashMap<String, Long> budget) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long when = cal.getTimeInMillis();

        String where = DailyBudgetGenerator.HISTORY_EFFECTIVE_ON + " = ?";
        String[] args = { "" + when };

        this.mDatabase.delete(DailyBudgetGenerator.TABLE_HISTORY, where, args);

        try {
            JSONObject budgetJson = new JSONObject();

            for (String key : budget.keySet()) {
                budgetJson.put(key, budget.get(key));
            }

            ContentValues value = new ContentValues();
            value.put(DailyBudgetGenerator.HISTORY_OBSERVED, System.currentTimeMillis());
            value.put(DailyBudgetGenerator.HISTORY_EFFECTIVE_ON, when);
            value.put(DailyBudgetGenerator.HISTORY_BUDGET, budgetJson.toString(2));

            this.mDatabase.insert(DailyBudgetGenerator.TABLE_HISTORY, null, value);

            if (budget.size() == 1) {
                if ("placeholder.app.does.not.exist".equals(budget.keySet().toArray()[0])) {
                    return true;
                }
            }

            Bundle update = new Bundle();

            update.putLong(DailyBudgetGenerator.HISTORY_OBSERVED, System.currentTimeMillis());
            update.putLong(DailyBudgetGenerator.HISTORY_EFFECTIVE_ON, cal.getTimeInMillis());
            update.putString(DailyBudgetGenerator.HISTORY_BUDGET, budgetJson.toString());

            Generators.getInstance(this.mContext).notifyGeneratorUpdated(DailyBudgetGenerator.GENERATOR_IDENTIFIER, update);

            return true;

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    public HashMap<String, Long> budgetForDate(Date date, boolean matchDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        String where = DailyBudgetGenerator.HISTORY_EFFECTIVE_ON + " <= ?";

        if (matchDate) {
            where = DailyBudgetGenerator.HISTORY_EFFECTIVE_ON + " = ?";
        }

        HashMap<String, Long> budget = null;

        String[] args = {"" + cal.getTimeInMillis()};

        Cursor c = this.mDatabase.query(DailyBudgetGenerator.TABLE_HISTORY, null, where, args, null, null, DailyBudgetGenerator.HISTORY_OBSERVED + " DESC", "1");

        if (c.moveToNext()) {
            budget = this.budgetFromCursor(c);
        }

        c.close();

        return budget;
    }

    public HashMap<String, Long> budgetFromCursor(Cursor c) {
        HashMap<String, Long> budget = new HashMap<>();

        try {
            JSONObject budgetJson = new JSONObject(c.getString(c.getColumnIndex(DailyBudgetGenerator.HISTORY_BUDGET)));

            Iterator<String> keys = budgetJson.keys();

            while (keys.hasNext()) {
                String key = keys.next();

                long value = budgetJson.getLong(key);

                budget.put(key, value);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return budget;
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
        return DailyBudgetGenerator.GENERATOR_IDENTIFIER;
    }

    public boolean hasExistingBudgets() {
        Cursor c = this.mDatabase.query(DailyBudgetGenerator.TABLE_HISTORY, null, null, null, null, null, null);

        boolean hasBudgets = c.getCount() > 0;

        c.close();

        return hasBudgets;
    }

    public void transmitFullBudgetLog() {
        Bundle update = new Bundle();

        update.putLong(DailyBudgetGenerator.HISTORY_OBSERVED, System.currentTimeMillis());

        ArrayList<Bundle> budgets = new ArrayList<>();

        Cursor c = this.mDatabase.query(DailyBudgetGenerator.TABLE_HISTORY, null, null, null, null, null, DailyBudgetGenerator.HISTORY_OBSERVED);

        while (c.moveToNext()) {
            Bundle budget = new Bundle();
            budget.putLong(DailyBudgetGenerator.HISTORY_OBSERVED, c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_OBSERVED)));
            budget.putLong(DailyBudgetGenerator.HISTORY_EFFECTIVE_ON, c.getLong(c.getColumnIndex(DailyBudgetGenerator.HISTORY_EFFECTIVE_ON)));
            budget.putString(DailyBudgetGenerator.HISTORY_BUDGET, c.getString(c.getColumnIndex(DailyBudgetGenerator.HISTORY_BUDGET)));

            budgets.add(budget);
        }

        c.close();

        update.putParcelableArrayList(DailyBudgetGenerator.HISTORY_BUDGETS, budgets);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(DailyBudgetGenerator.FULL_LOG_IDENTIFIER, update);
    }
}
