package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Util.readTextFromAssets;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

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
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.license)
                .setMessage(R.string.license_ok_http)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.license_apache, (d, which) -> showApacheLicense())
                .show();
    }

    private void showApacheLicense() {
        String licenseText;
        try {
            licenseText = readTextFromAssets(activity, "licenses/apache-2.0.txt");
        } catch (IOException e) {
            String message = e.getMessage();
            if (message == null) message = "I/O exception.";
            licenseText = "Failed to load license.\n\n" + message;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.license_apache)
                .setMessage(licenseText)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
