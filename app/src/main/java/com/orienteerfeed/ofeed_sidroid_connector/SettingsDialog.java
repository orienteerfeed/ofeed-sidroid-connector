package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_OFEED_HOST;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_OFEED_PATH;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_OFEED_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.UPLOAD_TO_OFEED;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.UPLOAD_TO_ORESULTS;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.extractUrl;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.parseOFeedCredentials;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.setupNumberPicker;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.string2Int;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.timeFormatter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

class SettingsDialog {

    //**********************************************************************************************
    // Interface.
    //**********************************************************************************************

    /**
     * Callback to signal that the settings dialog has been closed.
     */
    interface SettingsDialogClosed {
        void onSettingsDialogClosed();
    }

    //**********************************************************************************************
    // Fields.
    //**********************************************************************************************
    // Constructor parameters.
    private final Activity activity;
    private final Preferences prefs;
    private final SettingsDialogClosed listener;

    // Keep old values to restore changes, if user cancels this dialog.
    private int oldUploadIntervalSec, oldHttpConnectTimeoutSec, oldHttpReadTimeoutSec, oldHttpWriteTimeoutSec, oldHttpCallTimeoutSec;

    // These are shared with the QR code scanner.
    private EditText oFeedServer, oFeedEventId, oFeedEventPassword;

    //**********************************************************************************************
    // Constructor.
    //**********************************************************************************************
    SettingsDialog(Activity activity, Preferences prefs, @NonNull SettingsDialogClosed listener) {
        this.activity = activity;
        this.prefs = prefs;
        this.listener = listener;
    }

    //**********************************************************************************************
    // Show settings dialog.
    //**********************************************************************************************
    void show() {
        View layout = activity.getLayoutInflater().inflate(R.layout.settings_dialog, null);
        // Keep old values to restore changes, if user cancels this dialog.
        oldUploadIntervalSec = prefs.uploadIntervalSec;
        oldHttpConnectTimeoutSec = prefs.httpConnectTimeoutSec;
        oldHttpReadTimeoutSec = prefs.httpReadTimeoutSec;
        oldHttpWriteTimeoutSec = prefs.httpWriteTimeoutSec;
        oldHttpCallTimeoutSec = prefs.httpCallTimeoutSec;

        // Upload interval.
        Button uploadIntervalButton = layout.findViewById(R.id.settings_upload_interval);
        updateTimeButton(prefs.uploadIntervalSec, uploadIntervalButton);
        uploadIntervalButton.setOnClickListener(v -> onUploadIntervalClicked(uploadIntervalButton));

        // HTTP timeouts.
        Button httpTimeoutsButton = layout.findViewById(R.id.settings_upload_http_timeouts);
        httpTimeoutsButton.setOnClickListener(v -> new SettingsHttpTimeoutsDialog(activity, prefs).show());

        // Create id.
        ImageView createXmlId = layout.findViewById(R.id.settings_create_xml_id);
        createXmlId.setSelected(prefs.createXmlId);
        createXmlId.setOnClickListener( v->
                createXmlId.setSelected(!createXmlId.isSelected()));
        layout.findViewById(R.id.settings_create_xml_id_help).setOnClickListener(v ->
                showDialog(R.string.xml_id_help));

        // SI-Droid port number.
        EditText port = layout.findViewById(R.id.settings_si_droid_port);
        port.setText(String.valueOf(prefs.siDroidPort));
        layout.findViewById(R.id.settings_si_droid_port_help).setOnClickListener(v ->
                showDialog(R.string.port_number_help));

        // OFeed server.
        String oldOFeedUrl = prefs.oFeedUrl;
        oFeedServer = layout.findViewById(R.id.settings_ofeed_server);
        try {
            String[] parsedUrl = parseUrl(prefs.oFeedUrl);
            if (isDefaultUrl(parsedUrl)) {
                // Only display the host part of the URL when it's the default URL.
                oFeedServer.setText((parsedUrl[1]));
            } else {
                oFeedServer.setText(prefs.oFeedUrl);
            }
        } catch (MalformedURLException e) {
            oFeedServer.setText(prefs.oFeedUrl);
        }
        layout.findViewById(R.id.settings_ofeed_server_help).setOnClickListener(v ->
                showDialog(R.string.server_help));

        // OFeed event id.
        oFeedEventId = layout.findViewById(R.id.settings_ofeed_event_id);
        oFeedEventId.setText(prefs.oFeedEventId);

        // OFeed event password.
        oFeedEventPassword = layout.findViewById(R.id.settings_ofeed_event_password);
        oFeedEventPassword.setText(prefs.oFeedEventPassword);
        // Password visibility.
        ImageView eventPasswordVisibility = layout.findViewById(R.id.settings_ofeed_password_visibility);
        eventPasswordVisibility.setSelected(false);
        eventPasswordVisibility.setOnClickListener(v -> {
            boolean isVisible = eventPasswordVisibility.isSelected();
            eventPasswordVisibility.setSelected(!isVisible);
            if (isVisible) {
                oFeedEventPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            } else {
                oFeedEventPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }

        });

        // Scan QR code.
        Button scanQrCodeButton = layout.findViewById(R.id.settings_ofeed_scan_qr_code);
        scanQrCodeButton.setOnClickListener(v -> scanOFeedQrCode());

        // Paste link.
        Button pasteButton = layout.findViewById(R.id.settings_ofeed_paste_link);
        pasteButton.setOnClickListener(v -> pasteOFeedLink());

        // Help OFeed login details (scan QR code, paste link).
        layout.findViewById(R.id.settings_ofeed_login_details_help).setOnClickListener(v ->
                showDialog(R.string.login_details_help));

        // OResults api key.
        EditText oResultsApiKey = layout.findViewById(R.id.settings_oresults_api_key);
        oResultsApiKey.setText(prefs.oResultsApiKey);

        // OFeed and OResults tabs.
        initTabAnimations(layout);
        showActiveTab();
        layout.findViewById(R.id.settings_ofeed_title).setOnClickListener(v -> animateOFeedTab());
        layout.findViewById(R.id.settings_oresults_title).setOnClickListener(v -> animateOResultsTab());

        // Show the settings dialog.
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(layout)
                .setTitle(R.string.settings)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    // Dummy, will be overridden below.
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    // Restore changes for upload intervals and timeouts.
                    prefs.uploadIntervalSec = oldUploadIntervalSec;
                    prefs.httpConnectTimeoutSec = oldHttpConnectTimeoutSec;
                    prefs.httpReadTimeoutSec = oldHttpReadTimeoutSec;
                    prefs.httpWriteTimeoutSec = oldHttpWriteTimeoutSec;
                    prefs.httpCallTimeoutSec = oldHttpCallTimeoutSec;
                    // Done.
                    listener.onSettingsDialogClosed();
                })
                .setCancelable(false)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Upload interval and timeouts: Already set.
            // Create id is set below.
            // SI-Droid port number.
            int newPort = string2Int(port.getText().toString());
            if (newPort < 1025 || newPort > 65535) {
                showDialog(activity.getString(R.string.port_number_error));
                return;
            }

            if (isActiveOFeedTab()) {
                // OFeed server.
                String newOFeedUrl = oFeedServer.getText().toString().trim();
                if (newOFeedUrl.isEmpty()) {
                    // Empty URL, assume default.
                    newOFeedUrl = DEFAULT_OFEED_URL;
                } else if (newOFeedUrl.equals(DEFAULT_OFEED_HOST)) {
                    // Default host.
                    newOFeedUrl = DEFAULT_OFEED_URL;
                } else if (!newOFeedUrl.equals(oldOFeedUrl)) {
                    // New URL. User has updated the server URL.
                    try {
                        String[] parsedNewOFeedUrl = parseUrl(newOFeedUrl);
                        // Protocol, parseUrl() always returns "https".
                        // Host.
                        if (parsedNewOFeedUrl[1].isEmpty()) parsedNewOFeedUrl[1] = DEFAULT_OFEED_HOST;
                        // Path.
                        if (parsedNewOFeedUrl[2].isEmpty()) parsedNewOFeedUrl[2] = DEFAULT_OFEED_PATH;
                        if (parsedNewOFeedUrl[2].endsWith("/")) parsedNewOFeedUrl[2] =
                                parsedNewOFeedUrl[2].substring(0, parsedNewOFeedUrl[2].length() - 1);
                        if (!parsedNewOFeedUrl[2].endsWith("/rest/v1/events")) {
                            showDialog(R.string.server_incorrect_path);
                            return;
                        }
                        newOFeedUrl = parsedNewOFeedUrl[0] + "://" + parsedNewOFeedUrl[1] + parsedNewOFeedUrl[2];
                    } catch (MalformedURLException e) {
                        showDialog(e.getMessage());
                        return;
                    }
                }

                // Event id.
                String newEventId = oFeedEventId.getText().toString().trim();
                if (newEventId.isEmpty()) {
                    showDialog(R.string.event_id_is_missing);
                    return;
                }
                // Event password.
                String newPassword = oFeedEventPassword.getText().toString().trim();
                if (newPassword.isEmpty()) {
                    showDialog(R.string.password_is_missing);
                    return;
                }
                // OFeed settings are ok.
                prefs.oFeedUrl = newOFeedUrl;
                prefs.oFeedEventId = newEventId;
                prefs.oFeedEventPassword = newPassword;
                prefs.uploadTo = UPLOAD_TO_OFEED;

            } else {
                // OResults tab.
                String newApiKey = oResultsApiKey.getText().toString().trim();
                if (newApiKey.isEmpty()) {
                    showDialog(R.string.oresults_api_key_is_missing);
                    return;
                }
                prefs.oResultsApiKey = newApiKey;
                prefs.uploadTo = UPLOAD_TO_ORESULTS;
            }
            prefs.createXmlId = createXmlId.isSelected();
            prefs.siDroidPort = newPort;
            prefs.save();
            dialog.dismiss();
            // Done.
            listener.onSettingsDialogClosed();
        });
    }

    //**********************************************************************************************
    // SI-Droid upload interval.
    //**********************************************************************************************

    /**
     * The user has clicked to adjust the upload interval.
     * {@link Preferences#uploadIntervalSec} is updated.
     *
     * @param button The user interface button to be updated.
     */
    private void onUploadIntervalClicked(Button button) {
        View layout = activity.getLayoutInflater().inflate(R.layout.time_picker_dialog, null);
        TextView tv = layout.findViewById(R.id.time_interval_picker_min_caption);
        float textSizePx = tv.getTextSize();
        // Minimum number of seconds when minute is zero.
        final int secLow = 10;

        int min = prefs.uploadIntervalSec / 60;
        int sec = prefs.uploadIntervalSec - 60 * min;

        NumberPicker minPicker = layout.findViewById(R.id.time_interval_picker_min);
        NumberPicker secPicker = layout.findViewById(R.id.time_interval_picker_sec);
        setupNumberPicker(minPicker, 0, 10, min, textSizePx);
        setupNumberPicker(secPicker, min == 0 ? secLow : 0, 59, sec, textSizePx);

        // Ensure that upload interval is 'secMin' or more, ie, if min = 0 then sec = secMin-59.
        minPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (newVal == 0) {
                // Don't allow values below 'secLow' when minutes = 0.
                int newSec = Math.max(secPicker.getValue(), secLow);
                setupNumberPicker(secPicker, secLow, 59, newSec, textSizePx);
            } else if (secPicker.getMaxValue() != 59) {
                // Restore full range of seconds.
                setupNumberPicker(secPicker, 0, 59, Math.min(secPicker.getValue() + secLow, 59), textSizePx);
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(layout)
                .setTitle(R.string.upload_interval)
                .setPositiveButton(android.R.string.ok, (d, id) -> {
                    int mm = minPicker.getValue();
                    int secCorrection = secPicker.getMaxValue() != 59 ? secLow : 0;
                    int ss = secPicker.getValue() + secCorrection;
                    prefs.uploadIntervalSec = 60 * mm + ss;
                    updateTimeButton(new int[]{mm, ss}, button);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .show();
        setDialogTextSize(dialog);
    }

    /**
     * Update user interface button with new time value.
     *
     * @param time   New time, given in seconds.
     * @param button The button to be updated.
     */
    private void updateTimeButton(int time, Button button) {
        updateTimeButton(splitTimeSec(time), button);
    }

    /**
     * Update user interface button with new time value.
     *
     * @param time   New time, array of {minutes, seconds}.
     * @param button The button to be updated.
     */
    private void updateTimeButton(int[] time, Button button) {
        String s;
        if (time[M] != 0 && time[S] == 0) {
            s = time[M] + " " + activity.getString(R.string.minute);
        } else if (time[M] == 0 && time[S] != 0) {
            s = time[S] + " " + activity.getString(R.string.second);
        } else {
            s = timeFormatter(time[M], time[S]);
        }
        button.setText(s);
    }

    //**********************************************************************************************
    // App link: Scan QR code, paste link.
    //**********************************************************************************************
    private void scanOFeedQrCode() {
        GmsBarcodeScanning.getClient(activity)
                .startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null) appLinkCommon(rawValue);
                })
//                .addOnCanceledListener(() -> errorDialog("Cancelled."))
                .addOnFailureListener(e -> {
                    String message = e.getMessage();
                    if (message == null) message = activity.getString(R.string.qr_code_scan_failed);
                    showDialog(message);
                });
    }

    private void pasteOFeedLink() {
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            showDialog(R.string.clipboard_is_empty);
            return;
        }
        ClipData clipData = cm.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            showDialog(R.string.clipboard_is_empty);
            return;
        }
        CharSequence cs = clipData.getItemAt(0).coerceToText(activity);
        if (cs == null) {
            showDialog(R.string.clipboard_is_empty);
            return;
        }
        String clip = cs.toString().trim();
        String appLink = extractUrl(clip);
        if (appLink == null || appLink.isEmpty()) {
            String s = activity.getString(R.string.ofeed_link_invalid) + "\n\n" + clip;
            showDialog(s);
            return;
        }
        appLinkCommon(appLink);
    }

    private void appLinkCommon(String appLink) {
        String[] credentials = parseOFeedCredentials(activity, appLink);
        if (credentials.length == 3) {
            try {
                String[] parsedUrl = parseUrl(credentials[0]);
                if (isDefaultUrl(parsedUrl)) {
                    oFeedServer.setText((parsedUrl[1]));
                } else {
                    oFeedServer.setText(credentials[0]);
                }
                oFeedEventId.setText(credentials[1]);
                oFeedEventPassword.setText(credentials[2]);
            } catch (MalformedURLException e) {
                String message = e.getMessage() + "\n" + credentials[0] + "\n\n" + appLink;
                showDialog(message);
            }
        } else {
            String message = activity.getString(R.string.qr_code_invalid) + "\n" + credentials[0] + "\n\n" + appLink;
            showDialog(message);
        }
    }

    //**********************************************************************************************
    // OFeed server URL.
    //**********************************************************************************************

    /**
     * Parse URL.
     *
     * @param url URL to be parsed.
     * @return Array of {protocol, host, path}.
     * @throws MalformedURLException if the given URL could not be parsed.
     */
    private @NonNull String[] parseUrl(@NonNull String url) throws MalformedURLException {
        // Ensure URL is absolute.
        String absoluteUrl = url;
        int i = url.indexOf("://");
        if (i == -1) {
            absoluteUrl = "https://" + url;
        } else {
            if (!url.startsWith("https://")) {
                absoluteUrl = "https" + url.substring(i);
            }
        }

        try {
            URL parsedUrl = new URI(absoluteUrl).toURL();
            String protocol = parsedUrl.getProtocol();
            String host = parsedUrl.getAuthority();     // Authority = host + port (or, host if no port in URL).
            String path = parsedUrl.getPath();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return new String[]{protocol, host, path};
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null) {
                // Capitalize first letter.
                if (!message.isEmpty()) message = message.substring(0, 1).toUpperCase() + message.substring(1);
            } else {
                message = activity.getString(R.string.server_incorrect_url);
            }
            throw new MalformedURLException(message);
        }
    }

    /**
     * Determine if the given url equals {@link Preferences#DEFAULT_OFEED_URL}.
     *
     * @param parsedUrl Array of {protocol, host, path}.
     */
    private boolean isDefaultUrl(String[] parsedUrl) {
        return parsedUrl[0].equalsIgnoreCase("https")
                && parsedUrl[1].equalsIgnoreCase(DEFAULT_OFEED_HOST)
                && parsedUrl[2].equalsIgnoreCase(DEFAULT_OFEED_PATH);
    }

    //**********************************************************************************************
    // Utilities.
    //**********************************************************************************************

    /**
     * Convenience method for showing a dialog to the user.
     */
    private void showDialog(int messageResId) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settings)
                .setMessage(messageResId)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        setDialogTextSize(dialog);
    }

    /**
     * Convenience method for showing a dialog to the user.
     */
    private void showDialog(String message) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settings)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        setDialogTextSize(dialog);
    }

    private void setDialogTextSize(AlertDialog dialog) {
        TextView tv = dialog.findViewById(android.R.id.message);
        if (tv != null) tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        tv = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (tv != null) tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
    }

    /**
     * Index into array produced by {@link #splitTimeSec(int)}.
     */
    private static final int M = 0, S = 1;

    /**
     * Split time into minutes and seconds.
     *
     * @param time Time, given in seconds.
     * @return Array of {minutes, seconds}. Use indexes {@link #M} and {@link #S} to access the array.
     */
    private int[] splitTimeSec(int time) {
        int min = time / 60;
        int sec = time - 60 * min;
        return new int[]{min, sec};
    }

    //**********************************************************************************************
    // Animate OFeed/OResults tabs.
    //**********************************************************************************************
    private static final int[] oFeedTabResIds = {R.id.settings_ofeed_selected, R.id.settings_ofeed_server,
            R.id.settings_ofeed_server_help, R.id.settings_ofeed_event_id, R.id.settings_ofeed_event_password,
            R.id.settings_ofeed_password_visibility,
            R.id.settings_ofeed_scan_qr_code, R.id.settings_ofeed_paste_link, R.id.settings_ofeed_login_details_help};
    private static final int[] oResultsTabResIds = {R.id.settings_oresults_selected, R.id.settings_oresults_api_key};
    private static View[] oFeedTab, oResultsTab;

    private void initTabAnimations(View layout) {
        // Get views in OFeed tab.
        oFeedTab = new View[oFeedTabResIds.length];
        for (int i = 0; i < oFeedTabResIds.length; i++) {
            oFeedTab[i] = layout.findViewById(oFeedTabResIds[i]);
        }
        // Get views in OResults tab.
        oResultsTab = new View[oResultsTabResIds.length];
        for (int i = 0; i < oResultsTabResIds.length; i++) {
            oResultsTab[i] = layout.findViewById(oResultsTabResIds[i]);
        }
    }

    private void showActiveTab() {
        int oFeedTabVisibility, oResultsTabVisibility;
        if (prefs.uploadTo == Preferences.UPLOAD_TO_OFEED) {
            oFeedTabVisibility = View.VISIBLE;
            oResultsTabVisibility = View.INVISIBLE;
        } else {
            oFeedTabVisibility = View.INVISIBLE;
            oResultsTabVisibility = View.VISIBLE;
        }
        for (View v : oFeedTab) v.setVisibility(oFeedTabVisibility);
        for (View v : oResultsTab) v.setVisibility(oResultsTabVisibility);
    }

    /**
     * @return True if OFeed is the active tab, else false.
     */
    private boolean isActiveOFeedTab() {
        return oFeedTab[0].getVisibility() == View.VISIBLE;
    }

    /**
     * Animate switching from OResults tab to OFeed tab.
     */
    private void animateOFeedTab() {
        crossFade(oResultsTab, oFeedTab);
    }

    /**
     * Animate switching from OFeed tab to OResults tab.
     */
    private void animateOResultsTab() {
        crossFade(oFeedTab, oResultsTab);
    }

    private void crossFade(View[] hide, View[] show) {
        final int duration = 500;
        for (View v : show) {
            v.setVisibility(View.VISIBLE);
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(duration).start();
        }
        for (View v : hide) {
            v.animate().alpha(0f).setDuration(duration).withEndAction(() -> v.setVisibility(View.INVISIBLE)).start();
        }
    }
}
