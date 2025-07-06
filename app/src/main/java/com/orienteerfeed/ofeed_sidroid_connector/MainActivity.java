/*
 * Copyright 2025 OFeed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orienteerfeed.ofeed_sidroid_connector;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.webkit.URLUtil.isHttpsUrl;
import static com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.SI_DROID_PING_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.SI_DROID_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.USER_AGENT;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

import java.time.LocalDate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Preferences prefs;
    private ResultsServiceManager serviceManager;
    private Button startServiceButton;
    private TextView serviceStatus, serviceStatusHelp, httpCallStatus;
    private ImageView serviceStatusIcon, httpCallStatusIcon;
    private SimpleTimer serviceStateTimer;

    // ********************************************************************************************
    // Lifecycle.
    // ********************************************************************************************
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() |
                        WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemGestures());
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                mlp.leftMargin = bars.left;
                mlp.topMargin = bars.top;
                mlp.rightMargin = bars.right;
                mlp.bottomMargin = bars.bottom;
                view.setLayoutParams(mlp);
                return WindowInsetsCompat.CONSUMED;
            });

            // Fix: Android's status bar shows white icons/texts on its white background.
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int appearance = isNightMode() ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                controller.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }

        prefs = new Preferences(this);
        prefs.get();
        if (prefs.showNews) showNews();

        catchBackButtonAndConfirmExit();

        startServiceButton = findViewById(R.id.main_start_button);
        serviceStatus = findViewById(R.id.main_service_status);
        serviceStatusHelp = findViewById(R.id.main_service_status_help);
        serviceStatusIcon = findViewById(R.id.main_service_status_icon);
        updateServiceState();
        httpCallStatus = findViewById(R.id.main_http_call_status);
        httpCallStatusIcon = findViewById(R.id.main_http_call_status_icon);
        updateLatestStatus();

        if (!hasNotificationsPermission()) requestNotificationsPermission();
        // In-app update.
        inAppUpdateCallback = inAppUpdateRegisterCallback();    // Must be called from onCreate().

        GooglePlayServicesUtil.checkBarcodeScanner(this, prefs);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBatteryRestriction();
        monitorServiceStateStart();

        // In-app update.
        inAppUpdateResumeIfStalled();
        if (!inAppUpdateIsPostponed()) inAppUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitorServiceStateStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopOFeedResultsService();
    }

    private void catchBackButtonAndConfirmExit() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            boolean exitSnackbarIsVisible = false;

            @Override
            public void handleOnBackPressed() {
                if (exitSnackbarIsVisible) return;
                View root = findViewById(R.id.main_snackbar_anchor);
                Snackbar.make(root, R.string.back_button_exit_confirmation, Snackbar.LENGTH_LONG)
                        .setAction(android.R.string.ok, v -> finish())
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onShown(Snackbar sb) {
                                exitSnackbarIsVisible = true;
                            }

                            @Override
                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                exitSnackbarIsVisible = false;
                            }
                        })
                        .show();
            }
        });
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES;
    }

    // ********************************************************************************************
    // Service state.
    // ********************************************************************************************
    private void monitorServiceStateStart() {
        serviceStateTimer = new SimpleTimer(997, () -> {
            updateServiceState();
            serviceStateTimer.startTimer();   // Restart timer.
        });
        serviceStateTimer.startTimer();
    }

    private void monitorServiceStateStop() {
        if (serviceStateTimer != null) serviceStateTimer.stopTimer();
    }

    private void updateServiceState() {
        if (!isValidSettings()) {
            serviceStatus.setText(R.string.status_not_configured);
            serviceStatusIcon.setImageResource(R.drawable.status_warning);
            startServiceButton.setText(R.string.settings);
            startServiceButton.setOnClickListener(v -> settings());
        } else if (!ResultsService.isRunning) {
            String pingUrl = String.format(Locale.US, SI_DROID_PING_URL, prefs.siDroidPort);
            new HttpPing(pingUrl, USER_AGENT, isReachable ->
                    runOnUiThread(() -> {
                        if (isReachable) {
                            serviceStatus.setText(R.string.status_stopped);
                            serviceStatusIcon.setImageResource(R.drawable.status_stopped);
                            serviceStatusHelp.setVisibility(GONE);
                            startServiceButton.setText(R.string.start_uploading);
                            startServiceButton.setEnabled(true);
                            startServiceButton.setOnClickListener(v -> startOFeedResultsService());
                        } else {
                            serviceStatus.setText(R.string.si_droid_unreachable_title);
                            serviceStatusIcon.setImageResource(R.drawable.error_red);
                            serviceStatusHelp.setVisibility(VISIBLE);
                            startServiceButton.setText(R.string.start_uploading);
                            startServiceButton.setEnabled(false);
                        }
                    })).ping();
        } else {
            serviceStatus.setText(R.string.status_running);
            serviceStatusIcon.setImageResource(R.drawable.status_ok);
            startServiceButton.setText(R.string.stop_uploading);
            startServiceButton.setOnClickListener(v -> stopOFeedResultsService());
        }
    }

    private void updateLatestStatus() {
        if (serviceManager != null) {
            String s = serviceManager.getLatestStatus();
            if (!s.isEmpty()) {
                int iconResId = s.startsWith("S") ? R.drawable.status_ok : R.drawable.error_red;
                httpCallStatusIcon.setImageResource(iconResId);
                httpCallStatus.setText(s.substring(1));
            }
        }
    }

    // ********************************************************************************************
    // Menu.
    // ********************************************************************************************
    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.main_menu_settings) settings();
        else if (itemId == R.id.main_menu_log) showLog();
        else if (itemId == R.id.main_menu_http_log) showHttpLog();
        else if (itemId == R.id.main_menu_help) help();
        else if (itemId == R.id.main_menu_license) new LicenseDialog(this).show();
        else if (itemId == R.id.main_menu_about) new AboutDialog(this).show();
        else return super.onOptionsItemSelected(item);
        return true;
    }

    //**********************************************************************************************
    // Help.
    //**********************************************************************************************
    private void help() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.dialog_with_scroll_view, null);
        TextView message = layout.findViewById(R.id.dialog_with_scroll_view_text);
        message.setText(R.string.help_general);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(layout)
                .setIcon(R.drawable.help)
                .setTitle(R.string.help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ********************************************************************************************
    // OFeed results service.
    // ********************************************************************************************
    private void startOFeedResultsService() {
        String siDroidUrl = String.format(Locale.US, SI_DROID_URL, prefs.siDroidPort);
        serviceManager = new ResultsServiceManager(this, siDroidUrl,
                prefs.oFeedServer, prefs.oFeedEventId, prefs.oFeedEventPassword, USER_AGENT, prefs.uploadIntervalSec,
                new int[]{prefs.httpConnectTimeoutSec, prefs.httpReadTimeoutSec, prefs.httpWriteTimeoutSec, prefs.httpCallTimeoutSec},
                new ResultsService.ResultsServiceStatus() {
                    @Override
                    public void onSuccess(String status) {
                        runOnUiThread(() -> {
                            httpCallStatus.setText(status);
                            httpCallStatusIcon.setImageResource(R.drawable.status_ok);
                        });
                    }

                    @Override
                    public void onFailure(String status) {
                        runOnUiThread(() -> {
                            httpCallStatus.setText(status);
                            httpCallStatusIcon.setImageResource(R.drawable.error_red);
                        });
                    }
                });
        serviceManager.startOFeedResultsService();
        serviceManager.bindOFeedResultsService();
    }

    private void stopOFeedResultsService() {
        if (serviceManager != null) {
            serviceManager.unbindOFeedResultsService();
            serviceManager.stopOFeedResultsService();
        }
    }

    // ********************************************************************************************
    // Settings.
    // ********************************************************************************************
    private void settings() {
        stopOFeedResultsService();
        new SettingsDialog(this, prefs, this::updateServiceState).show();
    }

    /**
     * Checks that all configuration parameters have valid values.
     */
    private boolean isValidSettings() {
        return prefs.siDroidPort >= 1025 && prefs.siDroidPort <= 65535 &&
                !prefs.oFeedServer.isEmpty() &&
                isHttpsUrl(prefs.oFeedServer) &&
                !prefs.oFeedEventId.isEmpty() &&
                !prefs.oFeedEventPassword.isEmpty();
    }

    // ********************************************************************************************
    // Log.
    // ********************************************************************************************
    private void showLog() {
        if (serviceManager != null) {
            String log = serviceManager.getLog();
            if (!log.isEmpty()) {
                showLog(R.string.log, log);
            } else {
                showSnackbarEmptyLog();
            }
        } else {
            showSnackbarEmptyLog();
        }
    }

    private void showHttpLog() {
        if (serviceManager != null) {
            String log = serviceManager.getHttpLog();
            if (!log.isEmpty()) {
                showLog(R.string.show_http_log, log);
            } else {
                showSnackbarEmptyLog();
            }
        } else {
            showSnackbarEmptyLog();
        }
    }

    private void showLog(int titleResId, String log) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.dialog_with_scroll_view, null);
        TextView message = layout.findViewById(R.id.dialog_with_scroll_view_text);
        message.setText(log);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(layout)
                .setTitle(titleResId)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showSnackbarEmptyLog() {
        View root = findViewById(R.id.main_snackbar_anchor);
        Snackbar.make(root, R.string.log_is_empty, Snackbar.LENGTH_LONG).show();
    }

    // ********************************************************************************************
    // Permissions.
    // To display the notification icon in Android 33+ the POST_NOTIFICATIONS permission is required.
    // ********************************************************************************************
    private final static int PERMISSIONS_REQUEST_CODE = 69365;  // Random number.

    private boolean hasNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.notifications_permission_title))
                    .setMessage(getString(R.string.notifications_permission_message))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, id) ->
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSIONS_REQUEST_CODE))
                    .create().show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (permissions.length == 0 || grantResults.length == 0) {
                // Cancelled by user.
                finish();
            } else {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_DENIED) {
                        View root = findViewById(R.id.main_snackbar_anchor);
                        Snackbar.make(root, R.string.notifications_permission_not_granted, Snackbar.LENGTH_INDEFINITE)
                                .setAction(android.R.string.ok, v -> {
                                })
                                .show();
                    }
                }
            }
        }
    }

    // ********************************************************************************************
    // New features.
    // ********************************************************************************************
    private void showNews() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.dialog_with_scroll_view, null);
        TextView message = layout.findViewById(R.id.dialog_with_scroll_view_text);
        message.setText(R.string.news_message);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(layout)
                .setTitle(R.string.news)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.do_not_show_again, (dialog, which) -> {
                    prefs.showNews = false;
                    prefs.save();
                })
                .show();
    }

    // ********************************************************************************************
    // Battery restriction.
    // ********************************************************************************************

    /**
     * Check Android battery restriction, which can stop {@link ResultsService} from running.
     */
    private void checkBatteryRestriction() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !prefs.checkBatteryRestriction) {
            return;
        }

        if (isBatteryRestricted()) {
            ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_ofeed_sidroid_connector);
            View layout = LayoutInflater.from(themedContext).inflate(R.layout.dialog_with_scroll_view, null);
            TextView message = layout.findViewById(R.id.dialog_with_scroll_view_text);
            message.setText(R.string.battery_restriction);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setView(layout)
                    .setIcon(R.drawable.battery)
                    .setTitle(R.string.battery_restriction_title)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        // Redirect user to Android's settings to remove background restriction.
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    })
                    .setNeutralButton(R.string.do_not_show_again, (dialog, which) -> {
                        prefs.checkBatteryRestriction = false;
                        prefs.save();
                    })
                    .show();
        }
    }

    /**
     * Check if this app is battery restricted.
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    private boolean isBatteryRestricted() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) return am.isBackgroundRestricted();
        return false;
    }

    // ********************************************************************************************
    // In-app update. Must reside within an AppCompatActivity.
    // https://developer.android.com/guide/playcore/in-app-updates/kotlin-java#java
    // https://medium.com/@KaushalVasava/in-app-update-in-android-2023-c47beb1e3a7e
    // ********************************************************************************************

    /**
     * Callback to be invoked when the in-app update has been completed.
     * Must be registered in {@link #onCreate(Bundle)} through {@link #inAppUpdateRegisterCallback()}.
     */
    private ActivityResultLauncher<IntentSenderRequest> inAppUpdateCallback;

    /**
     * Register callback {@link #inAppUpdateCallback}, which is invoked when the in-app update has
     * been completed. Must be called from {@link #onCreate(Bundle)}.
     */
    private ActivityResultLauncher<IntentSenderRequest> inAppUpdateRegisterCallback() {
        return registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                    int resultCode = result.getResultCode();
                    int msgResId = 0;
                    if (resultCode == RESULT_OK) {
                        // The user has accepted the update. Might not be received for immediate updates.
                        msgResId = R.string.update_ok;
                    } else if (resultCode == RESULT_CANCELED) {
                        // The user has denied or canceled the update.
                        msgResId = R.string.update_canceled;
                    } else if (resultCode == RESULT_IN_APP_UPDATE_FAILED) {
                        // Some other error prevented either the user from providing consent or the update from proceeding.
                        msgResId = R.string.update_failed;
                    }
                    if (msgResId != 0) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setIcon(R.drawable.update)
                                .setTitle(R.string.update_title)
                                .setMessage(msgResId)
                                .setPositiveButton(android.R.string.ok, null)
                                .create().show();
                    }
                });
    }

    /**
     * Check if an update is available on Google Play Store. Download and install it at the user's discretion.
     * Requires a registered callback set by {@link #inAppUpdateRegisterCallback()}.
     */
    private void inAppUpdate() {
        AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(this);

        // Check if an update is available.
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                // An update is available. Ask if the user wants the new version.
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.update_title)
                        .setMessage(R.string.update_available)
                        .setIcon(R.drawable.update)

                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            // Yes, the user wants the new version. Request the update.
                            appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    inAppUpdateCallback,
                                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
                        })

                        .setNegativeButton(android.R.string.cancel, null)
                        // No, the user does not want the new version at this moment.

                        .setNeutralButton(R.string.update_postpone_one_week, (dialog, which) -> {
                            // Disable upgrade check for one week.
                            LocalDate postponeUntil = LocalDate.now().plusWeeks(1);
                            prefs.inAppUpdatePostponedUntilYear = postponeUntil.getYear();
                            prefs.inAppUpdatePostponedUntilMonth = postponeUntil.getMonthValue();
                            prefs.inAppUpdatePostponedUntilDay = postponeUntil.getDayOfMonth();
                            prefs.save();
                        })

                        // Show the update dialog.
                        .create().show();
            }
        });
    }

    /**
     * Check that the update has not stalled, and if so, resume the update.
     * Requires a registered callback set by {@link #inAppUpdateRegisterCallback()}.
     * Must be called from {@link #onResume()} (and any other entry point).
     */
    private void inAppUpdateResumeIfStalled() {
        AppUpdateManagerFactory.create(this).getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(this);
                appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        inAppUpdateCallback,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
            }
        });
    }

    /**
     * Determine if the in-app update has been postponed.
     */
    private boolean inAppUpdateIsPostponed() {
        LocalDate inAppUpdatePostponedUntil = LocalDate.of(prefs.inAppUpdatePostponedUntilYear,
                prefs.inAppUpdatePostponedUntilMonth, prefs.inAppUpdatePostponedUntilDay);
        return inAppUpdatePostponedUntil.isAfter(LocalDate.now());
    }
}
