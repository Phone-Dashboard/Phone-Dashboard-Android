package com.audacious_software.phone_dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.activities.DiagnosticsActivity;
import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.google.android.material.tabs.TabLayout;

import java.util.Calendar;
import java.util.HashMap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {
    private Handler mHandler = null;
    private Menu mMenu = null;

    private BudgetAdapter mTodayBudgetAdapter = null;
    private BudgetAdapter mWeekBudgetAdapter = null;

    // private BudgetAdapter mPhaseBudgetAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger.getInstance(this).log("main-screen-init", new HashMap<>());

        Schedule.getInstance(this).updateSchedule(true, null, false);

        this.setContentView(R.layout.activity_main_tabs);

        final RecyclerView appBudgets = this.findViewById(R.id.app_budgets);
        appBudgets.setHasFixedSize(true);
        appBudgets.setLayoutManager(new LinearLayoutManager(this));

        TabLayout tabs = this.findViewById(R.id.tabs_main);

        final MainActivity me = this;

        me.getSupportActionBar().setTitle(R.string.app_name);

        this.mTodayBudgetAdapter = new BudgetAdapter(this, System.currentTimeMillis(), 1, true);
        this.mWeekBudgetAdapter = new BudgetAdapter(this, System.currentTimeMillis(), 7, false);

        final AppApplication app = (AppApplication) this.getApplication();

        long periodStart = app.calculationStart();
        long when = System.currentTimeMillis();

        int periodDays = (int) Math.ceil(((double) when - periodStart) / (24 * 60 * 60 * 1000));

        if (periodDays < 1) {
            when = periodStart;

            periodStart = app.priorPeriodStart() + (24 * 60 * 60 * 1000);

            periodDays = (int) Math.ceil(((double) when - periodStart) / (24 * 60 * 60 * 1000));
        }

        // this.mPhaseBudgetAdapter = new BudgetAdapter(this, when, periodDays, false);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        me.mWeekBudgetAdapter.setVisible(false);
                        // me.mPhaseBudgetAdapter.setVisible(false);
                        me.mTodayBudgetAdapter.setVisible(true);

                        appBudgets.setAdapter(me.mTodayBudgetAdapter);

                        me.mTodayBudgetAdapter.refreshUsage(me);

                        break;
                    case 1:
                        me.mTodayBudgetAdapter.setVisible(false);
                        // me.mPhaseBudgetAdapter.setVisible(false);
                        me.mWeekBudgetAdapter.setVisible(true);

                        appBudgets.setAdapter(me.mWeekBudgetAdapter);

                        me.mWeekBudgetAdapter.refreshUsage(me);

                        break;
                    // case 2:
                    //    me.mTodayBudgetAdapter.setVisible(false);
                    //    me.mWeekBudgetAdapter.setVisible(false);
                    //    me.mPhaseBudgetAdapter.setVisible(true);
                    //
                    //    appBudgets.setAdapter(me.mPhaseBudgetAdapter);
                    //
                    //    me.mPhaseBudgetAdapter.refreshUsage(me);
                    //
                    //    break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                this.onTabSelected(tab);
            }
        });

        tabs.getTabAt(0).select();

        this.mHandler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        final MainActivity me = this;

        final AppApplication app = (AppApplication) this.getApplication();

        app.clearCachedBudgets();

        if (OnboardingActivity.requiresOnboarding(app)) {
            Log.e("PHONE DASHBOARD", "STARTING ONBOARDING");
            this.startActivity(new Intent(this, OnboardingActivity.class));
            this.finish();
        } else {
            Logger.getInstance(this).log("main-screen-appeared", new HashMap<>());

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    if (me.mMenu != null) {
                        MenuItem appLimits = me.mMenu.findItem(R.id.action_budget);

                        if (app.treatmentActive() == false || AppApplication.BLOCKER_TYPE_NONE.equals(app.blockerType())) {
                            appLimits.setVisible(false);
                        } else {
                            appLimits.setVisible(true);
                        }
                    } else {
                        me.mHandler.postDelayed(this, 50);
                    }
                }
            };

            this.mHandler.post(r);

            this.mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    me.refreshApps();

                    me.mHandler.postDelayed(this, 5000);
                }
            }, 5000);

            if (app.promptForMissingRequirements(this) == false) {
                AppUpdater appUpdater = new AppUpdater(this);
                appUpdater.setUpdateFrom(UpdateFrom.JSON);
                appUpdater.setUpdateJSON(this.getString(R.string.url_latest_version));

                appUpdater.start();
            }
        }

        app.logAppAppearance(System.currentTimeMillis(), "main-activity");
    }

    protected void onPause() {
        super.onPause();

        this.mHandler.removeCallbacksAndMessages(null);
    }

    private void refreshApps() {
        this.mTodayBudgetAdapter.refreshUsage(this);
        this.mWeekBudgetAdapter.refreshUsage(this);
        // this.mPhaseBudgetAdapter.refreshUsage(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.menu_main, menu);

        this.mMenu = menu;

        DiagnosticsActivity.setUpDiagnosticsItem(this, menu, true, false);

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        final MainActivity me = this;

        if (id == R.id.action_settings) {
            Intent settings = new Intent(this, SettingsActivity.class);
            this.startActivity(settings);
        } else if (id == R.id.action_budget) {
            Intent budget = new Intent(this, EditBudgetActivity.class);
            this.startActivity(budget);
        } else if (id == R.id.action_refresh_configuration) {
            final AppApplication app = (AppApplication) this.getApplication();

            app.refreshConfiguration(true, new Runnable() {
                @Override
                public void run() {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            if (me.mMenu != null) {
                                MenuItem appLimits = me.mMenu.findItem(R.id.action_budget);

                                if (app.treatmentActive() == false || AppApplication.BLOCKER_TYPE_NONE.equals(app.blockerType())) {
                                    appLimits.setVisible(false);
                                } else {
                                    appLimits.setVisible(true);
                                }
                            } else {
                                me.mHandler.postDelayed(this, 50);
                            }

                            app.promptForMissingRequirements(me);

                            Toast.makeText(me, R.string.toast_config_update_succeeded, Toast.LENGTH_LONG).show();
                        }
                    };

                    me.mHandler.post(r);

                    me.refreshApps();
                }
            });

            Toast.makeText(this, R.string.toast_refreshing_configuration, Toast.LENGTH_LONG).show();
//        } else if (id == R.id.action_diagnostics) {
//            Intent diagnosticsIntent = new Intent(this, DiagnosticsActivity.class);
//            this.startActivity(diagnosticsIntent);
//
//            return super.onOptionsItemSelected(item);
        }

        return super.onOptionsItemSelected(item);
    }

}
