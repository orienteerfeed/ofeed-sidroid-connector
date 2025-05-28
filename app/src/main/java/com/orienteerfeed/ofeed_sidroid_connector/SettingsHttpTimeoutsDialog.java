package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_HTTP_CALL_TIMEOUT_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_HTTP_CONNECT_TIMEOUT_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_HTTP_READ_TIMEOUT_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.DEFAULT_HTTP_WRITE_TIMEOUT_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.string2Int;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.view.ContextThemeWrapper;

/**
 * Settings dialog for HTTP timeouts.
 */
class SettingsHttpTimeoutsDialog {
    private final Activity activity;
    private final Preferences prefs;

    /**
     * Settings dialog for HTTP timeouts.
     */
    SettingsHttpTimeoutsDialog(Activity activity, Preferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    void show() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.settings_http_timeouts_dialog, null);
        EditText connectTimeout = layout.findViewById(R.id.settings_http_connect_timeout);
        EditText readTimeout = layout.findViewById(R.id.settings_http_read_timeout);
        EditText writeTimeout = layout.findViewById(R.id.settings_http_write_timeout);
        EditText callTimeout = layout.findViewById(R.id.settings_http_call_timeout);

        connectTimeout.setText(String.valueOf(prefs.httpConnectTimeoutSec));
        readTimeout.setText(String.valueOf(prefs.httpReadTimeoutSec));
        writeTimeout.setText(String.valueOf(prefs.httpWriteTimeoutSec));
        callTimeout.setText(String.valueOf(prefs.httpCallTimeoutSec));
        layout.findViewById(R.id.settings_http_reset_timeouts).setOnClickListener(view -> {
            connectTimeout.setText(String.valueOf(DEFAULT_HTTP_CONNECT_TIMEOUT_SEC));
            readTimeout.setText(String.valueOf(DEFAULT_HTTP_READ_TIMEOUT_SEC));
            writeTimeout.setText(String.valueOf(DEFAULT_HTTP_WRITE_TIMEOUT_SEC));
            callTimeout.setText(String.valueOf(DEFAULT_HTTP_CALL_TIMEOUT_SEC));
        });
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(layout)
                .setIcon(R.drawable.settings)
                .setTitle(R.string.http_timeouts)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    prefs.httpConnectTimeoutSec = string2Int(connectTimeout.getText().toString());
                    prefs.httpReadTimeoutSec = string2Int(readTimeout.getText().toString());
                    prefs.httpWriteTimeoutSec = string2Int(writeTimeout.getText().toString());
                    prefs.httpCallTimeoutSec = string2Int(callTimeout.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .create().show();
    }
}
