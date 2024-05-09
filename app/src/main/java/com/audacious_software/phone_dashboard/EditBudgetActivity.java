package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class EditBudgetActivity extends AppCompatActivity {
    public static final String MODE_INITIAL_SETUP = "com.audacious_software.phone_dashboard.MODE_INITIAL_SETUP";

    private boolean mInitialSetup = false;
    private boolean mIntroShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_budget);

        Bundle extras = this.getIntent().getExtras();

        if (extras != null && extras.containsKey(EditBudgetActivity.MODE_INITIAL_SETUP)) {
            this.mInitialSetup = extras.getBoolean(EditBudgetActivity.MODE_INITIAL_SETUP);
        }

        DailyBudgetGenerator dailyBudgets = DailyBudgetGenerator.getInstance(this);

        if (dailyBudgets.hasExistingBudgets() == false) {
            this.mInitialSetup = true;
        }

        this.getSupportActionBar().setTitle(R.string.title_app_limits);
        this.getSupportActionBar().setSubtitle(R.string.subtitle_app_limits);

        if (this.mInitialSetup == false) {
            this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView budgets = this.findViewById(R.id.list_user_budgets);

        budgets.setHasFixedSize(true);
        budgets.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.refreshApps();

        if (this.mInitialSetup && this.mIntroShown == false) {
            this.mIntroShown = true;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_initial_limit_setup);
            builder.setMessage(R.string.message_initial_limit_setup);

            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });

            builder.create().show();
        }

        AppLogger.getInstance(this).log("edit-budget-screen-visited");
    }

    private void refreshApps() {
        Calendar calendar = Calendar.getInstance();

//        if (this.mInitialSetup == false) {
            calendar.add(Calendar.DATE, 1);
//        }

        RecyclerView budgets = this.findViewById(R.id.list_user_budgets);
        budgets.setAdapter(new EditBudgetAdapter(this, true, false)); // this.mInitialSetup));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.menu_budget, menu);

        return true;
    }

    public void confirmDirtyExit() {
        final EditBudgetActivity me = this;

        RecyclerView budgets = this.findViewById(R.id.list_user_budgets);

        EditBudgetAdapter adapter = (EditBudgetAdapter) budgets.getAdapter();

        if (adapter.isDirty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_title_confirm_dirty_save);
            builder.setMessage(R.string.dialog_message_confirm_dirty_save);

            builder.setPositiveButton(R.string.action_save_budget, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    me.saveBudget();
                }
            });

            builder.setNegativeButton(R.string.action_skip, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    me.finish();
                }
            });

            builder.show();
        } else {
            me.finish();
        }
    }

    public void onBackPressed() {
        this.confirmDirtyExit();
    }

    @SuppressLint("InflateParams")
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.confirmDirtyExit();
        } else if (item.getItemId() == R.id.action_save_budget) {
            this.saveBudget();
        }

        return true;
    }

    private void saveBudget() {
        final EditBudgetActivity me = this;
        final AppApplication app = (AppApplication) this.getApplication();

        AppLogger.getInstance(this).log("edit-budget-save");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Date when = calendar.getTime();

        final DailyBudgetGenerator budgets = DailyBudgetGenerator.getInstance(this);

        RecyclerView budgetsView = this.findViewById(R.id.list_user_budgets);
        EditBudgetAdapter adapter = (EditBudgetAdapter) budgetsView.getAdapter();

        if (this.mInitialSetup) {
            final HashMap<String, Long> budget = adapter.getTomorrowBudget(); // .getTodayBudget();

            final int budgetSize = budget.size();

            HashMap<String, Object> payload = new HashMap<>();
            payload.put("limit-count", budgetSize);

            AppLogger.getInstance(this).log("edit-budget-save-limits", payload);

            if (budgetSize == 0) {
                budget.put("placeholder.app.does.not.exist", Long.MAX_VALUE);
                budgets.addBudgetForDate(when, budget);

                this.finish();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.title_limits_initial_updated);

                if (budgetSize == 1) {
                    builder.setMessage(R.string.message_limits_initial_updated_single);
                } else {
                    builder.setMessage(me.getString(R.string.message_limits_initial_updated, budget.size()));
                }

                AppLogger.getInstance(this).log("edit-budget-initial-limit-popup", payload);

                builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        budgets.addBudgetForDate(when, budget);
                        me.finish();
                    }
                });

                builder.setNeutralButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });

                builder.create().show();
            }
        } else {
            budgets.addBudgetForDate(when, adapter.getTomorrowBudget());

            final HashMap<String, Long> budget = adapter.getTomorrowBudget(); // .getTodayBudget();

            final int budgetSize = budget.size();

            HashMap<String, Object> payload = new HashMap<>();
            payload.put("limit-count", budgetSize);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.title_limits_updated);
            builder.setMessage(R.string.message_limits_updated);

            AppLogger.getInstance(this).log("edit-budget-regular-limit-popup", payload);

            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    me.finish();
                }
            });

            builder.create().show();

            budgets.addBudgetForDate(when, budget);
        }

        BudgetAdapter.clearInstalledApps();
    }
}
