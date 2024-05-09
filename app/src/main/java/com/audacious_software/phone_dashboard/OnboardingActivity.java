package com.audacious_software.phone_dashboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.device.ForegroundApplication;
import com.audacious_software.passive_data_kit.generators.device.NotificationEvents;
import com.google.android.material.textfield.TextInputEditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {
    private static final String SHOWN_EXPLANATION = "com.audacious_software.phone_dashboard.OnboardingActivity.SHOWN_EXPLANATION";
    private static final String SHOWN_BUDGET = "com.audacious_software.phone_dashboard.OnboardingActivity.SHOWN_BUDGET";
    private static final String SHOWN_CONCLUSION = "com.audacious_software.phone_dashboard.OnboardingActivity.SHOWN_CONCLUSION";
    private static final String NOTIFICATION_SHOWN = "com.audacious_software.phone_dashboard.OnboardingActivity.NOTIFICATION_SHOWN";

    private AppApplication mApp;

    private int[] mAllPages = {
            R.id.step_welcome,
            R.id.step_webview
    };

    private Toolbar mToolbar;
    private MenuItem mNextItem = null;
    private View.OnClickListener mNextListener = null;
    private SharedPreferences mPreferences = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.activity_onboarding);
        this.mToolbar = findViewById(R.id.toolbar);
        this.setSupportActionBar(this.mToolbar);

        this.mApp = (AppApplication) this.getApplication();

        this.mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.mApp.logAppAppearance(System.currentTimeMillis(), "onboarding-activity");

        this.refresh();
    }

    public static boolean requiresOnboarding(AppApplication app) {
        return (OnboardingActivity.outstandingIssues(app).size() > 0);
    }

    public static List<String> outstandingIssues(AppApplication app) {
        ArrayList<String> issues = new ArrayList<>();

        if (app.getIdentifier() == null) {
            issues.add("missing-identifier");
        }

        if (OnboardingActivity.shownExplanation(app) == false) {
            issues.add("no-explanation");
        }

        if (NotificationEvents.areNotificationsVisible(app) == false) {
            issues.add("invisible-notifications");
        }

        if (ForegroundApplication.hasPermissions(app) == false) {
            issues.add("missing-app-usage");
        }

        if (OnboardingActivity.hasWindowPermission(app) == false) {
            issues.add("missing-window-permissions");
        }

        return issues;
    }

    private void refresh() {
        Log.e("Phone Dashboard", "REFRESH");

        String identifier = this.mApp.getIdentifier();

        boolean shownExplanation = OnboardingActivity.shownExplanation(this);
        boolean hasUsagePermission = ForegroundApplication.hasPermissions(this.mApp);
        boolean hasWindowPermission = OnboardingActivity.hasWindowPermission(this);
        boolean hasNotificationPermission = NotificationEvents.areNotificationsEnabled(this);
        boolean areNotificationVisible = NotificationEvents.areNotificationsVisible(this);

        boolean shownConclusion = OnboardingActivity.shownConclusion(this);

        if (identifier == null) {
            this.showWelcome();
        } else if (!shownExplanation) {
            this.showExplanation();
        } else if (!hasUsagePermission) {
            this.fetchUsagePermissions();
        } else if (!areNotificationVisible) {
            this.fetchNotificationPermissions();
        } else if (!hasWindowPermission) {
            this.fetchWindowPermissions();
        } else if (!shownConclusion) {
            this.showConclusion();
        } else {
            this.startActivity(new Intent(this, MainActivity.class));

            Logger.getInstance(this).log("nyu-onboarding-complete");

            this.finish();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void fetchWindowPermissions() {
        final OnboardingActivity me = this;

        this.mToolbar.setTitle(R.string.title_window_management);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        WebView webView = this.findViewById(R.id.step_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/html/onboarding_window_management.html");

        this.showPage(R.id.step_webview, R.string.action_next_arrow, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);

                    me.startActivity(intent);
                }
            }
        });
    }

    private static boolean hasWindowPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context) == false) {
                return false;
            }
        }

        return true;
    }

    private static boolean shownExplanation(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(OnboardingActivity.SHOWN_EXPLANATION, false);
    }

    private static boolean shownConclusion(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(OnboardingActivity.SHOWN_CONCLUSION, false);
    }

    private void showWelcome() {
        final OnboardingActivity me = this;
        final AppApplication app = (AppApplication) me.getApplication();

        this.mToolbar.setTitle(R.string.title_onboarding);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        final TextInputEditText emailField = this.findViewById(R.id.field_email);

        this.showPage(R.id.step_welcome, R.string.action_sign_in, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String email = emailField.getText().toString().trim();

                if (TextUtils.isEmpty(email) || Patterns.EMAIL_ADDRESS.matcher(email).matches() == false) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(me);
                    builder.setTitle(R.string.title_invalid_email);
                    builder.setMessage(R.string.message_invalid_email);

                    builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    });

                    builder.create().show();

                    return;
                }

                me.mApp.enrollEmail(email, new Runnable() {
                    @Override
                    public void run() {
                        me.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder builder = new AlertDialog.Builder(me);
                                builder.setTitle(R.string.title_email_validated);

                                String message = me.getString(R.string.message_email_validated, app.getIdentifier());

                                builder.setMessage(message);

                                builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        me.refresh();
                                    }
                                });

                                builder.create().show();

                                Schedule.getInstance(me).updateSchedule(true, null, false);
                            }
                        });
                    }
                }, new Runnable () {
                    @Override
                    public void run() {
                        me.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(me, R.string.toast_identifier_invalid, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showExplanation() {
        final OnboardingActivity me = this;

        this.mToolbar.setTitle(R.string.title_about_app);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        WebView webView = this.findViewById(R.id.step_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/html/onboarding_app_explain.html");

        this.showPage(R.id.step_webview, R.string.action_next_arrow, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor e = me.mPreferences.edit();
                e.putBoolean(OnboardingActivity.SHOWN_EXPLANATION, true);
                e.apply();

                me.refresh();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void fetchUsagePermissions() {
        final OnboardingActivity me = this;

        this.mToolbar.setTitle(R.string.title_app_usage_permissions);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        WebView webView = this.findViewById(R.id.step_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/html/onboarding_app_usage.html");

        this.showPage(R.id.step_webview, R.string.action_next_arrow, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ForegroundApplication.fetchPermissions(me.mApp);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void fetchNotificationPermissions() {
        final OnboardingActivity me = this;

        Log.e("PHONE DASHBOARD", "fetchNotificationPermissions");

        this.mToolbar.setTitle(R.string.title_notification_permissions);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me);

        if (NotificationEvents.areNotificationsEnabled(me) == false && prefs.getBoolean(OnboardingActivity.NOTIFICATION_SHOWN, false) == false) {
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(OnboardingActivity.NOTIFICATION_SHOWN, true);
            e.apply();

            NotificationEvents.fetchPemissions(me);
        } else {
            WebView webView = this.findViewById(R.id.step_webview);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.loadUrl("file:///android_asset/html/onboarding_app_notifications.html");

            this.showPage(R.id.step_webview, R.string.action_next_arrow, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    NotificationEvents.enableVisibility(me);
                }
            });
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void showBudget() {
        final OnboardingActivity me = this;

        this.mToolbar.setTitle(R.string.title_setup_budget);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        WebView webView = this.findViewById(R.id.step_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/html/onboarding_app_limits.html");

        this.showPage(R.id.step_webview, R.string.action_next_arrow, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor e = me.mPreferences.edit();
                e.putBoolean(OnboardingActivity.SHOWN_BUDGET, true);
                e.apply();

                Intent budgetIntent = new Intent(me, EditBudgetActivity.class);
                budgetIntent.putExtra(EditBudgetActivity.MODE_INITIAL_SETUP, true);

                me.startActivity(budgetIntent);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showConclusion() {
        final OnboardingActivity me = this;

        this.mToolbar.setTitle(R.string.title_onboarding_conclusion);
        this.mToolbar.setSubtitle(R.string.subtitle_onboarding);

        WebView webView = this.findViewById(R.id.step_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/html/onboarding_app_conclusion.html");

        this.showPage(R.id.step_webview, R.string.action_done, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor e = me.mPreferences.edit();
                e.putBoolean(OnboardingActivity.SHOWN_CONCLUSION, true);
                e.apply();

                me.startActivity(new Intent(me, MainActivity.class));

                me.finish();
            }
        });
    }

    private void showPage(int pageResource, final int nextButtonTitle, View.OnClickListener nextAction) {
        for (int id : this.mAllPages) {
            View page = this.findViewById(id);

            if (id == pageResource) {
                page.setVisibility(View.VISIBLE);
            } else {
                page.setVisibility(View.GONE);
            }
        }

        final OnboardingActivity me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (me.mNextItem == null) {
                    handler.postDelayed(this, 50);
                } else {
                    me.mNextItem.setTitle(nextButtonTitle);
                }
            }
        }, 50);

        this.mNextListener = nextAction;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.menu_onboarding, menu);

        this.mNextItem = menu.findItem(R.id.action_next);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_next && this.mNextListener != null) {
            this.mNextListener.onClick(null);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
