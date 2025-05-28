package com.orienteerfeed.ofeed_sidroid_connector;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

/**
 * Utility class for setup of Google Play Services and barcode scanner {@link GmsBarcodeScanning}.
 */
public class GooglePlayServicesUtil {

    /**
     * <p>Utility method for setup of Google Play Services and barcode scanner {@link GmsBarcodeScanning}.</p>
     * <ul><li>Check availability of Google Play Services and try to fix any issues.</li>
     * <li>Check availability of barcode scanner and trigger a download if needed.</li></ul>
     */
    public static void checkBarcodeScanner(Activity activity, Preferences prefs) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);

        if (resultCode == ConnectionResult.SUCCESS) {
            // Google Play Services is ok. Check barcode scanner.
            checkAndInstallGmsBarcodeScanning(activity);

        } else if (apiAvailability.isUserResolvableError(resultCode)) {
            // Play Services is unavailable or outdated.
            if (!prefs.manuallyUpdateGooglePlayServices) return;
            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.qr_code_scanner)
                    .setIcon(R.drawable.qr_code)
                    .setMessage(getStatusMessage(resultCode))
                    .setPositiveButton(R.string.update, (dialog, which) -> {
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(activity, "Failed to open Google Play Store.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.do_not_show_again, (dialog, which) -> {
                        prefs.manuallyUpdateGooglePlayServices = false;
                        prefs.save();
                    })
                    .create().show();

        } else {
            if (!prefs.manuallyUpdateGooglePlayServices) return;
            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.qr_code_scanner)
                    .setIcon(R.drawable.qr_code)
                    .setMessage(getStatusMessage(resultCode))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.do_not_show_again, (dialog, which) -> {
                        prefs.manuallyUpdateGooglePlayServices = false;
                        prefs.save();
                    })
                    .create().show();

        }
    }

    /**
     * Trigger a background download and an install of the {@link GmsBarcodeScanning} module
     * from Google Play Services, if not already installed.
     * https://developers.google.com/android/guides/module-install-apis?hl=en#send_an_urgent_module_install_request
     *
     * @noinspection JavadocLinkAsPlainText
     */
    private static void checkAndInstallGmsBarcodeScanning(Activity activity) {
        ModuleInstallClient moduleInstallClient = ModuleInstall.getClient(activity);
        ModuleInstallRequest moduleInstallRequest = ModuleInstallRequest.newBuilder()
                .addApi(GmsBarcodeScanning.getClient(activity))
                .build();
        moduleInstallClient.installModules(moduleInstallRequest)
                .addOnSuccessListener(response -> {
                    if (!response.areModulesAlreadyInstalled()) {
                        Toast.makeText(activity, "Downloading QR code scanner…", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(activity, "Failed to download QR code scanner.", Toast.LENGTH_LONG).show());
    }

    private static String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case ConnectionResult.SUCCESS:
                return "Google Play Services is available.";
            case ConnectionResult.SERVICE_MISSING:                      // User resolvable.
                return "Google Play Services is missing on this device.";
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:      // User resolvable.
                return "Google Play Services needs to be updated.";
            case ConnectionResult.SERVICE_DISABLED:                     // User resolvable.
                return "Google Play Services is disabled.";
            case ConnectionResult.SIGN_IN_REQUIRED:                     // User resolvable (deprecated).
                return "User must sign in to a Google account.";
            case ConnectionResult.INVALID_ACCOUNT:
                return "The specified Google account is invalid.";
            case ConnectionResult.RESOLUTION_REQUIRED:                  // User resolvable.
                return "A resolution is required to continue.";
            case ConnectionResult.NETWORK_ERROR:
                return "Network error while checking Google Play Services.";
            case ConnectionResult.INTERNAL_ERROR:
                return "Internal error in Google Play Services.";
            case ConnectionResult.SERVICE_INVALID:
                return "Google Play Services is invalid or corrupt.";
            case ConnectionResult.SERVICE_UPDATING:
                return "Google Play Services is currently updating.";
            case ConnectionResult.API_UNAVAILABLE:
                return "Requested Google API is unavailable on this device.";
            case ConnectionResult.DEVELOPER_ERROR:
                return "Developer error: check your API configuration.";
            default:
                return "Unknown Google Play Services error: " + statusCode + ".";
        }
    }
}
