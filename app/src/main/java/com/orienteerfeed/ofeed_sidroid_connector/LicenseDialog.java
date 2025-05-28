package com.orienteerfeed.ofeed_sidroid_connector;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Dialog for OkHttp and Apache 2.0 licenses.
 */
class LicenseDialog {
    private final Activity activity;

    /**
     * Dialog for OkHttp and Apache 2.0 licenses.
     */
    LicenseDialog(Activity activity) {
        this.activity = activity;
    }

    void show() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.license_dialog, null);
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(layout)
                .setIcon(R.drawable.copyright)
                .setTitle(R.string.license)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.license_apache, (dialog, which) -> showApacheLicense())
                .create().show();
    }

    private void showApacheLicense() {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.Theme_ofeed_sidroid_connector);
        View layout = LayoutInflater.from(themedContext).inflate(R.layout.license_apache_dialog, null);
        TextView licenseApache = layout.findViewById(R.id.license_apache_text);
        licenseApache.setText(readTextFromAssets("licenses/apache-2.0.txt"));
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(layout)
                .setIcon(R.drawable.copyright)
                .setTitle(R.string.license_apache)
                .setPositiveButton(android.R.string.ok, null)
                .create().show();

    }

    /**
     * @noinspection SameParameterValue
     */
    private String readTextFromAssets(String filePath) {
        StringBuilder builder = new StringBuilder(11_560);  // Length of apache-2.0.txt.
        try (InputStream is = activity.getAssets().open(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line.trim()).append("\n");
            }
        } catch (IOException e) {
            String message = e.getMessage();
            if (message == null) message = activity.getString(R.string.io_exception);
            builder.append("Failed to load license.\n").append(message);
        }
        return builder.toString();
    }
}
