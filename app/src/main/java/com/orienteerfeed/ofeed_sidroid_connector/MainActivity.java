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
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.webkit.URLUtil.isHttpsUrl;
import static com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.SI_DROID_PING_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.SI_DROID_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.UPLOAD_TO_OFEED;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.USER_AGENT;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsService.XML_IDS_FILENAME;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsService.resultServiceIsRunning;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.parseOFeedCredentials;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
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
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private Preferences prefs;
    private ResultsServiceManager serviceManager;
    private Button startServiceButton;
    private TextView serviceStatus;
    private ImageView serviceStatusIcon, serviceStatusHelp;
    private SimpleTimer serviceStateTimer;
    // Status list.
    private StatusListItemAdapter statusListAdapter;
    private LinearLayoutManager statusListLayoutManager;
    // Progress indicator which shows the countdown towards next upload of results.
    private CountdownIndicator countdownIndicator;
    private TextView countdownIndicatorText;
    private ImageView countdownUploadIcon, countdownOkIcon, countdownErrorIcon;
    private Animatable countdownUploadIconAnimation;
    // Log.
    private String resultServiceLogSaved, resultServiceHttpLogSaved;

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

        toolbar = findViewById(R.id.main_toolbar);
        toolbar.post(() -> {
            toolbar.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.main_menu_settings) settings();
                else if (itemId == R.id.main_menu_file) uploadXmlFileFromDisk();
                else if (itemId == R.id.main_menu_clear_ofeed) clearOFeed();
                else if (itemId == R.id.main_menu_log) showLog();
                else if (itemId == R.id.main_menu_http_log) showHttpLog();
                else if (itemId == R.id.main_menu_help) showDialogLargeText(R.string.help_title, R.string.help_message);
                else if (itemId == R.id.main_menu_license) new LicenseDialog(this).show();
                else if (itemId == R.id.main_menu_about) new AboutDialog(this).show();
                else return false;
                return true;
            });
            setAppTitle();
        });

        startServiceButton = findViewById(R.id.main_start_button);
        serviceStatus = findViewById(R.id.main_service_status);
        serviceStatusIcon = findViewById(R.id.main_service_status_icon);
        serviceStatusHelp = findViewById(R.id.main_service_status_help);
        serviceStatusHelp.setOnClickListener(v ->
                showDialogLargeText(R.string.si_droid_unreachable_title, R.string.si_droid_unreachable_message));
        updateServiceState();
        // Status list.
        RecyclerView statusListRecyclerView = findViewById(R.id.main_status_recycler_view);
        statusListLayoutManager = new LinearLayoutManager(this);
        statusListRecyclerView.setLayoutManager(statusListLayoutManager);
        ArrayList<StatusListItem> statusListItems = new ArrayList<>();
        statusListAdapter = new StatusListItemAdapter(statusListItems);
        statusListRecyclerView.setAdapter(statusListAdapter);
        // Countdown and upload animations.
        CircularProgressIndicator progressIndicator = findViewById(R.id.main_countdown_indicator);
        countdownIndicatorText = findViewById(R.id.main_countdown_text);
        countdownIndicator = new CountdownIndicator(progressIndicator, countdownIndicatorText);
        countdownUploadIcon = findViewById(R.id.main_countdown_upload_icon);
        Drawable d = countdownUploadIcon.getDrawable();
        if (d instanceof Animatable) {
            countdownUploadIconAnimation = (Animatable) (d);
        } else {
            countdownUploadIconAnimation = null;
            countdownUploadIcon.setImageResource(R.drawable.upload_static); // Fall back to static version.
        }
        countdownOkIcon = findViewById(R.id.main_countdown_ok_icon);
        countdownErrorIcon = findViewById(R.id.main_countdown_error_icon);


        if (!hasNotificationsPermission()) requestNotificationsPermission();
        // In-app update.
        inAppUpdateCallback = inAppUpdateRegisterCallback();    // Must be called from onCreate().

        GooglePlayServicesUtil.checkBarcodeScanner(this, prefs);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        // App link from QR code. Store new intent, if not getIntent() will return the old one.
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBatteryRestriction();
        monitorServiceStateStart();

        // Deep link scanned by camera.
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action != null && action.equals(Intent.ACTION_VIEW)) {
            Uri uri = intent.getData();
            if (uri != null) {
                appLinkCommon(uri.toString());
            }
        }

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
                View root = findViewById(android.R.id.content);
                Snackbar snackbar = Snackbar.make(root, R.string.back_button_exit_confirmation, Snackbar.LENGTH_LONG)
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
                        });
                TextView tv = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                tv.setMaxLines(3);
                snackbar.show();
            }
        });
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Update main screen in accordance with {@link Preferences#uploadTo}
     * (set app title, enable/disable Clear OFeed menu item).
     */
    private void setAppTitle() {
        boolean isOFeed = prefs.uploadTo == UPLOAD_TO_OFEED;
        toolbar.setTitle(isOFeed ? R.string.app_title_ofeed_connector : R.string.app_title_oresults_connector);
        toolbar.getMenu().findItem(R.id.main_menu_clear_ofeed).setEnabled(isOFeed);
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
        setAppTitle();
        if (!isValidSettings()) {
            serviceStatus.setText(R.string.status_not_configured);
            serviceStatusIcon.setImageResource(R.drawable.status_warning);
            startServiceButton.setText(R.string.settings);
            startServiceButton.setOnClickListener(v -> settings());
        } else if (!resultServiceIsRunning) {
            String pingUrl = String.format(Locale.US, SI_DROID_PING_URL, prefs.siDroidPort);
            new HttpPing(pingUrl, USER_AGENT, isReachable ->
                    runOnUiThread(() -> {
                        if (isReachable) {
                            serviceStatus.setText(R.string.status_stopped);
                            serviceStatusIcon.setImageResource(R.drawable.status_stopped);
                            serviceStatusHelp.setVisibility(GONE);
                            startServiceButton.setText(R.string.start_uploading);
                            startServiceButton.setEnabled(true);
                            startServiceButton.setOnClickListener(v -> {
                                startServiceButton.setEnabled(false); // Disable until service has started.
                                startOFeedResultsService();
                            });
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

    // ********************************************************************************************
    // OFeed results service.
    // ********************************************************************************************

    private void startOFeedResultsService() {
        resultServiceLogSaved = null;
        resultServiceHttpLogSaved = null;

        String siDroidUrl = String.format(Locale.US, SI_DROID_URL, prefs.siDroidPort);
        String oFeedUrl = getEndpointUpload();
        if (oFeedUrl == null) {
            showDialogLargeText(R.string.ofeed, R.string.server_incorrect_path);
            return;
        }
        serviceManager = new ResultsServiceManager(this, prefs.uploadTo, siDroidUrl,
                oFeedUrl, prefs.oFeedEventId, prefs.oFeedEventPassword, prefs.oResultsApiKey, USER_AGENT, prefs.uploadIntervalSec,
                new int[]{prefs.httpConnectTimeoutSec, prefs.httpReadTimeoutSec, prefs.httpWriteTimeoutSec, prefs.httpCallTimeoutSec},
                prefs.createXmlId,
                new ResultsService.ResultsServiceUpdateStatus() {
                    private long updateStartTimeMs;

                    @Override
                    public void onUpdateStart(long timeMs) {
                        runOnUiThread(() -> {
                            // Countdown and upload animations.
                            updateStartTimeMs = timeMs;
                            countdownUploadIcon.setVisibility(VISIBLE);
                            if (countdownUploadIconAnimation != null) countdownUploadIconAnimation.start();
                            countdownOkIcon.setVisibility(INVISIBLE);
                            countdownErrorIcon.setVisibility(INVISIBLE);
                            countdownIndicatorText.setVisibility(INVISIBLE);
                            countdownIndicator.start(prefs.uploadIntervalSec);
                            // Service has started.
                            startServiceButton.setEnabled(true);
                        });
                    }

                    @Override
                    public void onUpdateSuccess(long timeMs, String status) {
                        runOnUiThread(() -> {
                            // Countdown and upload animations.
                            int deltaTimeMs = (int) (timeMs - updateStartTimeMs);
                            countdownUploadAnimation(deltaTimeMs, countdownOkIcon);
                            // Status list.
                            addStatusListItem(status, R.drawable.status_ok);
                        });
                    }

                    @Override
                    public void onUpdateFailure(long timeMs, String status) {
                        runOnUiThread(() -> {
                            // Countdown and upload animations.
                            int deltaTimeMs = (int) (timeMs - updateStartTimeMs);
                            countdownUploadAnimation(deltaTimeMs, countdownErrorIcon);
                            // Status list.
                            addStatusListItem(status, R.drawable.error_red);
                        });
                    }
                });
        // Start the service.
        serviceManager.startOFeedResultsService();
        serviceManager.bindOFeedResultsService();
        // Animate first countdown.
        countdownIndicatorText.setVisibility(VISIBLE);
        countdownIndicator.start(ResultsService.TIME_TO_FIRST_UPDATE_SEC);
    }

    private void stopOFeedResultsService() {
        countdownUploadAnimationStop();
        if (serviceManager != null) {
            resultServiceLogSaved = serviceManager.getLog();
            resultServiceHttpLogSaved = serviceManager.getHttpLog();
            serviceManager.unbindOFeedResultsService();
            serviceManager.stopOFeedResultsService();
        }
    }

    private void addStatusListItem(String status, int iconResId) {
        statusListAdapter.addListItemAtTop(new StatusListItem(status, iconResId));
        if (statusListLayoutManager.findFirstVisibleItemPosition() <= 1) {
            statusListLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    /**
     * Get endpoint for uploading results to OFeed.
     *
     * @return ".../rest/v1/upload/iof", or
     * null if {@link Preferences#oFeedUrl} does not end with "/rest/v1/events".
     */
    private String getEndpointUpload() {
        if (!prefs.oFeedUrl.endsWith("/rest/v1/events")) return null;
        int i = prefs.oFeedUrl.lastIndexOf("/events");
        return prefs.oFeedUrl.substring(0, i) + "/upload/iof";
    }

    /**
     * Get endpoint for deleting all competitors of this event in OFeed.
     *
     * @return ".../rest/v1/events/{eventId}/competitors", or
     * null if {@link Preferences#oFeedUrl} does not end with "/rest/v1/events".
     */
    private String getOFeedEndpointDelete() {
        if (!prefs.oFeedUrl.endsWith("/rest/v1/events")) return null;
        return prefs.oFeedUrl + "/" + prefs.oFeedEventId + "/" + "competitors";
    }

    // ********************************************************************************************
    // Delete OFeed competitors.
    // ********************************************************************************************

    /**
     * Delete all competitors of this event in OFeed
     * Handy if duplicates have appeared in OFeed; the next upload will restore the competitors.
     */
    private void clearOFeed() {
        boolean wasRunning = resultServiceIsRunning;
        if (resultServiceIsRunning) stopOFeedResultsService();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_ofeed)
                .setMessage(R.string.clear_ofeed_competitors)
                .setPositiveButton(android.R.string.ok, (d, which) -> deleteOFeedCompetitors())
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    if (wasRunning) startOFeedResultsService();
                })
                .setCancelable(false)
                .show();
        setDialogTextSize(dialog);
    }

    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);

    /**
     * Do the actual deletion of OFeed competitors.
     */
    private void deleteOFeedCompetitors() {
        String url = getOFeedEndpointDelete();
        if (url == null) {
            showDialogLargeText(R.string.ofeed, R.string.server_incorrect_path);
            return;
        }

        new OFeedClear(url, prefs.oFeedEventId, prefs.oFeedEventPassword, USER_AGENT, (isCleared, message) ->
                runOnUiThread(() -> {
                    String status = LocalTime.now().format(HH_MM_SS) + " ";
                    int iconResId;
                    if (isCleared) {
                        SerializableManager.delete(this, XML_IDS_FILENAME);
                        iconResId = R.drawable.status_ok;
                        status += getString(R.string.cleared_ok);
                    } else {
                        iconResId = R.drawable.error_red;
                        status += message;
                    }
                    addStatusListItem(status, iconResId);
                })).delete();
    }

    // ********************************************************************************************
    // Countdown and upload animations.
    // ********************************************************************************************
    private void countdownUploadAnimation(int deltaTimeMs, ImageView icon) {
        if (deltaTimeMs >= 1_000) {
            // Upload took more than one second, just show the result.
            countdownUploadAnimationShowResult(icon);
        } else {
            // Upload completed in less than a second, continue to show upload icon for a full second.
            new SimpleTimer(1_000 - deltaTimeMs, () -> {
                // One second has elapsed.
                if (isAnimating()) {
                    countdownUploadAnimationShowResult(icon);
                }
            }).startTimer();
        }
    }

    private void countdownUploadAnimationShowResult(ImageView icon) {
        if (countdownUploadIconAnimation != null) countdownUploadIconAnimation.stop();
        countdownUploadIcon.setVisibility(INVISIBLE);
        icon.setVisibility(VISIBLE);
        // Show result icon for five seconds, then show the countdown.
        new SimpleTimer(5_000, () -> {
            if (isAnimating()) {
                icon.setVisibility(INVISIBLE);
                countdownIndicatorText.setVisibility(VISIBLE);
            }
        }).startTimer();
    }

    private void countdownUploadAnimationStop() {
        countdownIndicator.stop();
        if (countdownUploadIconAnimation != null) countdownUploadIconAnimation.stop();
        countdownUploadIcon.setVisibility(INVISIBLE);
        countdownOkIcon.setVisibility(INVISIBLE);
        countdownErrorIcon.setVisibility(INVISIBLE);
        countdownIndicatorText.setVisibility(INVISIBLE);
    }

    private boolean isAnimating() {
        return countdownIndicator.isRunning();
    }

    // ********************************************************************************************
    // App link/QR code/Share.
    // ********************************************************************************************
    private void appLinkCommon(@Nullable String qrCode) {
        if (qrCode == null) return;
        String[] credentials = parseOFeedCredentials(this, qrCode);
        if (credentials.length != 3) {
            showDialog(R.string.qr_code_invalid, credentials[0] + "\n\n" + qrCode);
            return;
        }

        // Remove trailing "/" from server URL, if present.
        String s = credentials[0];
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);

        // Set the OFeed event.
        prefs.oFeedUrl = s;
        prefs.oFeedEventId = credentials[1];
        prefs.oFeedEventPassword = credentials[2];
        prefs.uploadTo = UPLOAD_TO_OFEED;
        prefs.save();
        showSnackbar(R.string.ofeed_settings_updated_ok);
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
        if (prefs.siDroidPort < 1025 || prefs.siDroidPort > 65535) return false;
        if (prefs.uploadTo == UPLOAD_TO_OFEED) {
            return !prefs.oFeedUrl.isEmpty() &&
                    isHttpsUrl(prefs.oFeedUrl) &&
                    !prefs.oFeedEventId.isEmpty() &&
                    !prefs.oFeedEventPassword.isEmpty();
        } else {
            return !prefs.oResultsApiKey.isEmpty();
        }
    }

    // ********************************************************************************************
    // Upload XML results file from local storage to OFeed/OResults, once only.
    // ********************************************************************************************

    private void uploadXmlFileFromDisk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml");   // Or "*/*".
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/xml", "application/xml"});
        openXmlLauncher.launch(intent);
    }

    final ActivityResultLauncher<Intent> openXmlLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent != null) {
                        Uri uri = intent.getData();
                        if (uri != null) {
                            int modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, modeFlags);
                            uploadXmlFile(uri);
                        }
                    }
                }
            }
    );

    private void uploadXmlFile(Uri localXmlFile) {
        if (resultServiceIsRunning) stopOFeedResultsService();

        long startTimeMs = System.currentTimeMillis();
        uploadXmlFileAnimationStart();
        LocalXmlFileUploader uploader = new LocalXmlFileUploader(this, USER_AGENT,
                (status, message) ->
                        runOnUiThread(() -> {
                            int deltaTimeMs = (int) (System.currentTimeMillis() - startTimeMs);
                            if (status == LocalXmlFileUploader.LocalXmlFileUploaderListener.OK) {
                                addStatusListItem(message, R.drawable.status_ok);
                                uploadXmlFileAnimationStop(deltaTimeMs, countdownOkIcon);
                            } else if (status == LocalXmlFileUploader.LocalXmlFileUploaderListener.INFO) {
                                addStatusListItem(message, R.drawable.info);
                                uploadXmlFileAnimationStop(deltaTimeMs, countdownOkIcon);
                            } else {
                                addStatusListItem(message, R.drawable.error_red);
                                uploadXmlFileAnimationStop(deltaTimeMs, countdownErrorIcon);
                            }
                        }));

        if (prefs.uploadTo == UPLOAD_TO_OFEED) {
            String oFeedUrl = getEndpointUpload();
            if (oFeedUrl == null) {
                showDialogLargeText(R.string.ofeed, R.string.server_incorrect_path);
                return;
            }
            uploader.setOFeedParams(oFeedUrl, prefs.oFeedEventId, prefs.oFeedEventPassword);
            uploader.uploadToOFeed(localXmlFile, prefs.createXmlId);
        } else {
            uploader.setOResultsParams(prefs.oResultsApiKey);
            uploader.uploadToOResults(localXmlFile, prefs.createXmlId);
        }
    }

    private void uploadXmlFileAnimationStart() {
        countdownUploadIcon.setVisibility(VISIBLE);
        if (countdownUploadIconAnimation != null) countdownUploadIconAnimation.start();
    }

    private void uploadXmlFileAnimationStop(int deltaTimeMs, ImageView icon) {
        if (deltaTimeMs >= 1_000) {
            // Upload took more than one second, just show the result.
            uploadXmlFileAnimationShowResult(icon);
        } else {
            // Upload completed in less than a second, continue to show upload icon for a full second.
            new SimpleTimer(1_000 - deltaTimeMs, () -> {
                // One second has elapsed.
                uploadXmlFileAnimationShowResult(icon);
            }).startTimer();
        }
    }

    private void uploadXmlFileAnimationShowResult(ImageView icon) {
        if (countdownUploadIconAnimation != null) countdownUploadIconAnimation.stop();
        countdownUploadIcon.setVisibility(INVISIBLE);
        fadeIn(icon);
        // Show result icon for some seconds.
        new SimpleTimer(3_000, () -> fadeOut(icon)).startTimer();
    }

    private void fadeIn(ImageView icon) {
        icon.setAlpha(0f);
        icon.setVisibility(VISIBLE);
        icon.animate().alpha(1f).setDuration(500).start();
    }

    private void fadeOut(ImageView icon) {
        icon.animate().alpha(0f).setDuration(500)
                .withEndAction(() -> icon.setVisibility(INVISIBLE)).start();
    }

    // ********************************************************************************************
    // Log.
    // ********************************************************************************************
    private void showLog() {
        // Try to get the log from the result service manager.
        if (serviceManager != null) {
            String log = serviceManager.getLog();
            if (!log.isEmpty()) {
                showDialog(R.string.log, log);
                return;
            }
        }
        // Try the log that was saved when stopping the service manager.
        if (resultServiceLogSaved != null && !resultServiceLogSaved.isEmpty()) {
            showDialog(R.string.log, resultServiceLogSaved);
            return;
        }
        // No log available.
        showSnackbar(R.string.log_is_empty);
    }

    private void showHttpLog() {
        // Try to get the log from the result service manager.
        if (serviceManager != null) {
            String log = serviceManager.getHttpLog();
            if (!log.isEmpty()) {
                showDialog(R.string.show_http_log, log.replace("\n", "\n\n"));
                return;
            }
        }
        // Try the log that was saved when stopping the service manager.
        if (resultServiceHttpLogSaved != null && !resultServiceHttpLogSaved.isEmpty()) {
            showDialog(R.string.show_http_log, resultServiceHttpLogSaved.replace("\n", "\n\n"));
            return;
        }
        // No log available.
        showSnackbar(R.string.log_is_empty);
    }

    private void showSnackbar(int textResId) {
        View root = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(root, textResId, Snackbar.LENGTH_LONG);
        TextView tv = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        snackbar.show();
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
            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.notifications_permission_title))
                    .setMessage(getString(R.string.notifications_permission_message))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (d, id) ->
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSIONS_REQUEST_CODE))
                    .show();
            setDialogTextSize(dialog);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (permissions.length == 0 || grantResults.length == 0) {
                // Canceled by user.
                finish();
            } else {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_DENIED) {
                        showDialogLargeText(R.string.notifications_permission_title, R.string.notifications_permission_not_granted);
                    }
                }
            }
        }
    }

    // ********************************************************************************************
    // New features.
    // ********************************************************************************************
    private void showNews() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.news)
                .setMessage(R.string.news_message)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.do_not_show_again, (d, which) -> {
                    prefs.showNews = false;
                    prefs.save();
                })
                .show();
        setDialogTextSize(dialog);
    }

    // ********************************************************************************************
    // Utilities: Show alert dialog variations.
    // ********************************************************************************************

    /**
     * Show alert dialog with OK button and larger text.
     */
    private void showDialogLargeText(int titleResId, int messageResId) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        setDialogTextSize(dialog);
    }

    /**
     * Show alert dialog with OK button and default text (more compact).
     */
    private void showDialog(int titleResId, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleResId)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void setDialogTextSize(AlertDialog dialog) {
        TextView tv = dialog.findViewById(android.R.id.message);
        if (tv != null) tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        tv = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (tv != null) tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
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
            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.battery_restriction_title)
                    .setMessage(R.string.battery_restriction)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        // Redirect user to Android's settings to remove background restriction.
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    })
                    .setNeutralButton(R.string.do_not_show_again, (d, which) -> {
                        prefs.checkBatteryRestriction = false;
                        prefs.save();
                    })
                    .show();
            setDialogTextSize(dialog);
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
                        showDialogLargeText(R.string.update_title, msgResId);
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
                AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.update_title)
                        .setMessage(R.string.update_available)
                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                            // Yes, the user wants the new version. Request the update.
                            appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    inAppUpdateCallback,
                                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        // No, the user does not want the new version at this moment.
                        .setNeutralButton(R.string.update_postpone_one_week, (d, which) -> {
                            // Disable upgrade check for one week.
                            LocalDate postponeUntil = LocalDate.now().plusWeeks(1);
                            prefs.inAppUpdatePostponedUntilYear = postponeUntil.getYear();
                            prefs.inAppUpdatePostponedUntilMonth = postponeUntil.getMonthValue();
                            prefs.inAppUpdatePostponedUntilDay = postponeUntil.getDayOfMonth();
                            prefs.save();
                        })

                        // Show the update dialog.
                        .show();
                setDialogTextSize(dialog);
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
