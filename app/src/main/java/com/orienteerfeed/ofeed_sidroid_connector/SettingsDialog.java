package com.orienteerfeed.ofeed_sidroid_connector;

import static android.webkit.URLUtil.isHttpsUrl;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.parseOFeedCredentials;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.setupNumberPicker;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.string2Int;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.timeFormatter;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

class SettingsDialog {
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

    // Editable user interface views.
    private EditText port, server, eventId, eventPassword;

    /**
     * Index into {@link #serverUrl}.
     */
    private static final int URL_PROTOCOL = 0, URL_HOST = 1, URL_PATH = 2;
    /**
     * Default path of OFeed QR code. If present, it will be replaced by {@link #UPLOAD_URL_DEFAULT_PATH}.
     */
    private static final String QR_CODE_URL_DEFAULT_PATH = "/rest/v1/events";
    /**
     * Default path for uploading results to OFeed.
     */
    private static final String UPLOAD_URL_DEFAULT_PATH = "/rest/v1/upload/iof";
    /**
     * Array of {protocol, host, path}.
     */
    private String[] serverUrl = {"", "", ""};

    //**********************************************************************************************
    // Constructors.
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
        ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.settings_dialog, null);
//        View layout = LayoutInflater.from(activity).inflate(R.layout.settings, null);
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

        // SI-Droid port number.
        port = layout.findViewById(R.id.settings_si_droid_port);
        port.setText(String.valueOf(prefs.siDroidPort));
        layout.findViewById(R.id.settings_si_droid_port_help).setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setIcon(R.drawable.settings)
                        .setTitle(R.string.settings)
                        .setMessage(R.string.port_number_help)
                        .setPositiveButton(android.R.string.ok, null)
                        .create().show());

        // OFeed server.
        server = layout.findViewById(R.id.settings_ofeed_server);
        String errorMessage = parseUrl(prefs.oFeedServer);
        if (errorMessage.isEmpty()) {
            server.setText(formatServerUrl());  // Ok.
        } else {
            alertDialog(R.drawable.error_red, errorMessage);
            server.setText(prefs.oFeedServer);
        }
        layout.findViewById(R.id.settings_ofeed_server_help).setOnClickListener(v -> {
            String newServer = server.getText().toString().trim();
            String errorMsg = parseUrl(newServer);
            if (!errorMsg.isEmpty()) {
                alertDialog(R.drawable.error_red, errorMsg);
                return;
            }
            server.setText(formatServerUrl());
            String message = activity.getString(R.string.server) + ":\n\n" + getServerUrl();
            alertDialog(R.drawable.settings, message);
        });

        // Event id.
        eventId = layout.findViewById(R.id.settings_ofeed_event_id);
        eventId.setText(prefs.oFeedEventId);

        // Event password.
        eventPassword = layout.findViewById(R.id.settings_ofeed_event_password);
        eventPassword.setText(prefs.oFeedEventPassword);
        // Toggle password visibility.
        ImageView eventPasswordVisibility = layout.findViewById(R.id.settings_ofeed_password_visibility);
        boolean[] eventPasswordIsVisible = {false};
        eventPasswordVisibility.setOnClickListener(v -> {
            int passwordIconResId;
            if (eventPasswordIsVisible[0]) {
                eventPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordIconResId = R.drawable.visibility_off;
            } else {
                eventPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                passwordIconResId = R.drawable.visibility;
            }
            eventPasswordVisibility.setImageResource(passwordIconResId);
            eventPasswordIsVisible[0] = !eventPasswordIsVisible[0];
        });

        // Scan QR code.
        Button scanQrCode = layout.findViewById(R.id.settings_ofeed_qr_code);
        scanQrCode.setOnClickListener(v -> scanQrCode());

        // HTTP timeouts.
        Button httpTimeouts = layout.findViewById(R.id.settings_ofeed_http_timeouts);
        httpTimeouts.setOnClickListener(v -> new SettingsHttpTimeoutsDialog(activity, prefs).show());

        // Show the settings dialog.
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(layout)
                .setIcon(R.drawable.settings)
                .setTitle(R.string.settings)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // Dummy, will be overridden below.
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // Restore changes.
                    prefs.uploadIntervalSec = oldUploadIntervalSec;
                    prefs.httpConnectTimeoutSec = oldHttpConnectTimeoutSec;
                    prefs.httpReadTimeoutSec = oldHttpReadTimeoutSec;
                    prefs.httpWriteTimeoutSec = oldHttpWriteTimeoutSec;
                    prefs.httpCallTimeoutSec = oldHttpCallTimeoutSec;
                    // Done.
                    listener.onSettingsDialogClosed();
                })
                .setCancelable(false);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Upload interval: Already set.
            // SI-Droid port number.
            int newPort = string2Int(port.getText().toString());
            // OFeed server.
            String newServer = server.getText().toString().trim();
            String errorMsg = parseUrl(newServer);
            if (!errorMsg.isEmpty()) {
                alertDialog(R.drawable.error_red, errorMsg);
                return;
            }
            server.setText(formatServerUrl());
            String newServerUrl = getServerUrl();
            // Event id.
            String newEventId = eventId.getText().toString().trim();
            // Event password.
            String newPassword = eventPassword.getText().toString().trim();
            // Check values.
            String error = null;
            if (newPort < 1025 || newPort > 65535) error = activity.getString(R.string.port_number_error);
            else if (serverUrl[URL_HOST].isEmpty()) error = activity.getString(R.string.server_not_specified);
            else if (!isHttpsUrl(newServerUrl)) error = activity.getString(R.string.server_https_required) + "\n\n" +
                    activity.getString(R.string.server_https_required_reason);
            else if (newEventId.isEmpty()) error = activity.getString(R.string.event_id_is_missing);
            else if (newPassword.isEmpty()) error = activity.getString(R.string.password_is_missing);
            if (error != null) {
                alertDialog(R.drawable.error_red, error);
                return;
            }
            prefs.siDroidPort = newPort;
            prefs.oFeedServer = newServerUrl;
            prefs.oFeedEventId = newEventId;
            prefs.oFeedEventPassword = newPassword;
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
        ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.time_picker_dialog, null);
        TextView tv = layout.findViewById(R.id.time_interval_picker_min_caption);
        float textSizePx = tv.getTextSize();

        int min = prefs.uploadIntervalSec / 60;
        int sec = prefs.uploadIntervalSec - 60 * min;

        NumberPicker minPicker = layout.findViewById(R.id.time_interval_picker_min);
        NumberPicker secPicker = layout.findViewById(R.id.time_interval_picker_sec);
        setupNumberPicker(minPicker, 0, 10, min, textSizePx);
        setupNumberPicker(secPicker, min == 0 ? 5 : 0, 59, sec, textSizePx);

        // Ensure that upload interval is 5 sec or more, ie, if min = 0 then sec = 5-59.
        minPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (newVal == 0) {
                // Don't allow 0-4 sec when minutes = 0.
                int newSec = Math.max(secPicker.getValue(), 5);
                setupNumberPicker(secPicker, 5, 59, newSec, textSizePx);
            } else if (secPicker.getMaxValue() == 54) {
                // Restore full range of seconds.
                setupNumberPicker(secPicker, 0, 59, Math.min(secPicker.getValue() + 5, 59), textSizePx);
            }
        });

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(layout)
                .setTitle(R.string.upload_interval)
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                    int mm = minPicker.getValue();
                    int secCorrection = secPicker.getMaxValue() == 54 ? 5 : 0;
                    int ss = secPicker.getValue() + secCorrection;
                    prefs.uploadIntervalSec = 60 * mm + ss;
                    updateTimeButton(new int[]{mm, ss}, button);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .create().show();
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
    // Scan QR code.
    //**********************************************************************************************
    private void scanQrCode() {
        GmsBarcodeScanning.getClient(activity)
                .startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null) {
                        String[] credentials = parseOFeedCredentials(activity, rawValue);
                        if (credentials.length == 3) {
                            String s = parseUrl(credentials[0]);
                            server.setText(formatServerUrl());
                            if (!s.isEmpty()) alertDialog(R.drawable.error_red, getServerUrl() + "\n" + s);
                            eventId.setText(credentials[1]);
                            eventPassword.setText(credentials[2]);
                        } else {
                            String message = activity.getString(R.string.qr_code_invalid) + "\n" + credentials[0] + "\n\n" + rawValue;
                            alertDialog(R.drawable.error_red, message);
                        }
                    }
                })
//                .addOnCanceledListener(() -> errorDialog("Cancelled."))
                .addOnFailureListener(e -> {
                    String message = e.getMessage();
                    if (message == null) message = activity.getString(R.string.qr_code_scan_failed);
                    alertDialog(R.drawable.error_red, message);
                });
    }

    //**********************************************************************************************
    // OFeed server URL.
    //**********************************************************************************************

    /**
     * Parse URL and split it into array of {protocol, host, path}.
     * If path equals {@link #QR_CODE_URL_DEFAULT_PATH}, it will be replaced by {@link #QR_CODE_URL_DEFAULT_PATH}.
     * For example, "https://api.orienteerfeed.com/rest/v1/events/" will update {@link #serverUrl}
     * to contain {"https", "api.orienteerfeed.com", "/rest/v1/upload/iof"}.
     * Values are unchanged if the given URL could not be parsed.
     * If protocol is missing from the given URL, then https will be assumed.
     * If path is missing from the given URL, then {@link #UPLOAD_URL_DEFAULT_PATH} will be assumed.
     *
     * @param url URL to be parsed.
     * @return Empty string if the given URL could be parsed, else an error message.
     * @noinspection JavadocLinkAsPlainText
     */
    private @NonNull String parseUrl(@NonNull String url) {
        if (url.isEmpty()) {
            serverUrl = new String[]{"", "", ""};
            return "";
        }
        // Check protocol. Assume https if missing.
        String s = url.contains("://") ? url : "https://" + url;
        // Parse url.
        try {
            URL parsedUrl = new URI(s).toURL();
            String protocol = parsedUrl.getProtocol();
            String host = parsedUrl.getAuthority();     // Authority = host + port (or, = host if no port in URL).
            String path = parsedUrl.getPath();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.isEmpty() || path.equals(QR_CODE_URL_DEFAULT_PATH)) path = UPLOAD_URL_DEFAULT_PATH;
            serverUrl = new String[]{protocol, host, path};
            return "";
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null) {
                // Capitalize first letter.
                exceptionMessage = exceptionMessage.substring(0, 1).toUpperCase() + exceptionMessage.substring(1);
                exceptionMessage = "\n\n" + exceptionMessage;
            } else {
                exceptionMessage = "";
            }
            return activity.getString(R.string.server_incorrect_url) + "\n" + url + exceptionMessage;
        }
    }

    /**
     * Format the server URL which is displayed to the user. Protocol and path are hidden if they have default values.
     */
    private String formatServerUrl() {
        if (serverUrl[URL_HOST].isEmpty()) return "";
        String protocol = serverUrl[URL_PROTOCOL].equals("https") ? "" : serverUrl[URL_PROTOCOL] + "://";
        String path = serverUrl[URL_PATH].equals(UPLOAD_URL_DEFAULT_PATH) ? "" : serverUrl[URL_PATH];
        return protocol + serverUrl[URL_HOST] + path;
    }

    /**
     * Get the complete server URL (protocol://host/path).
     */
    private String getServerUrl() {
        if (serverUrl[URL_HOST].isEmpty()) return "";
        return serverUrl[URL_PROTOCOL] + "://" + serverUrl[URL_HOST] + serverUrl[URL_PATH];
    }

    //**********************************************************************************************
    // Utilities.
    //**********************************************************************************************

    /**
     * Convenience method for showing a dialog to the user.
     */
    private void alertDialog(int iconResId, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setIcon(iconResId)
                .setTitle(R.string.settings)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create().show();
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
}
