package com.orienteerfeed.ofeed_sidroid_connector;

import static android.webkit.URLUtil.isHttpsUrl;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class Util {

    /**
     * Set up a picker for numbers, eg, hour, minutes or seconds.
     *
     * @param picker     The picker to be set up.
     * @param minValue   Min value (inclusive).
     * @param maxValue   Max value (inclusive).
     * @param value      The initial value of the picker.
     * @param textSizePx Text size (px) of picker (API 29+, only).
     */
    static void setupNumberPicker(NumberPicker picker, int minValue, int maxValue, int value, float textSizePx) {
        String[] values = new String[maxValue - minValue + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = String.format(Locale.US, "%02d", minValue + i);
        }
        picker.setDisplayedValues(null);    // Must reset, otherwise index out of bounds in setMaxValue().
        picker.setMinValue(0);
        picker.setMaxValue(maxValue - minValue);
        picker.setDisplayedValues(values);
        picker.setValue(value - minValue);
        picker.setWrapSelectorWheel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.setTextSize(textSizePx);
    }

    /**
     * Format time. Does not check validity of the input parameters.
     *
     * @return Time formatted as "mm:ss".
     */
    public static String timeFormatter(int min, int sec) {
        StringBuilder sb = new StringBuilder(5);
        // Minutes.
        sb.append(min).append(":");
        // Seconds.
        if (sec <= 9) sb.append("0");
        sb.append(sec);

        return sb.toString();
    }

    /**
     * Parse OFeed credentials, typically retrieved from a QR code formatted as an app link.
     * Note: Parameter auth=basic is ignored.
     *
     * @param credentials OFeed credentials, formatted as https://stigning.se/ofeed?url=xxx&auth=basic&id=yyy&pwd=zzz
     * @return Array of {serverUrl, eventId, password}, or, array of {errorMessage} if the credentials could not be parsed.
     * @noinspection JavadocLinkAsPlainText
     */
    static @NonNull String[] parseOFeedCredentials(Context context, @NonNull String credentials) {
        final String urlStart = "https://stigning.se/ofeed";
        if (!credentials.startsWith(urlStart)) return new String[]{context.getString(R.string.qr_code_start, urlStart)};
        try {
            Uri uri = Uri.parse(credentials);
            // Server URL.
            String serverUrl = uri.getQueryParameter("url");
            if (serverUrl == null || serverUrl.isEmpty()) return new String[]{context.getString(R.string.server_not_specified)};
            if (!isHttpsUrl(serverUrl)) return new String[]{context.getString(R.string.server_https_required)};
            // Event id.
            String eventId = uri.getQueryParameter("id");
            if (eventId == null || eventId.isEmpty()) return new String[]{context.getString(R.string.event_id_is_missing)};
            // Password
            String password = uri.getQueryParameter("pwd");
            if (password == null || password.isEmpty()) return new String[]{context.getString(R.string.password_is_missing)};
            // Done.
            return new String[]{serverUrl, eventId, password};
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) message = "Error parsing OFeed credentials.";
            return new String[]{message};
        }
    }

    /**
     * Encode data as a Base64-encoded string. Note that this method uses android.util.Base64,
     * which is different from java.util.Base64 (requires API 26+).
     *
     * @param data The data to encode.
     * @return The data encoded as a Base64-encoded string.
     */
    static String base64EncodeToString(@NonNull String data) {
        return android.util.Base64.encodeToString(data.getBytes(), Base64.NO_WRAP);
    }

    /**
     * This version of Integer.parseInt() returns 0 if not a number.
     *
     * @param s String representation of an integer value.
     * @return The value represented by s, or zero if not a number.
     */
    static int string2Int(String s) {
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Read text file in assets folder.
     *
     * @param filePath Path within the assets folder.
     */
    @SuppressWarnings("SameParameterValue")
    static String readTextFromAssets(Context context, String filePath) throws IOException {
        StringBuilder builder = new StringBuilder(11_560);  // Length of apache-2.0.txt.
        try (InputStream is = context.getAssets().open(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line.trim()).append("\n");
            }
        }
        return builder.toString();
    }

//    /**
//     * Read text file from local storage.
//     */
//    static String readTextFileOld(Context context, Uri uri) throws IOException {
//        InputStream is = context.getContentResolver().openInputStream(uri);
//        if (is == null) throw new FileNotFoundException("Unable to open XML file.");
//
//        try (is; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
//            byte[] data = new byte[4096];
//            int n;
//            while ((n = is.read(data)) != -1) {
//                buffer.write(data, 0, n);
//            }
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                return buffer.toString(StandardCharsets.UTF_8);
//            } else {
//                //noinspection StringOperationCanBeSimplified
//                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
//            }
//        }
//    }

    /**
     * Read text file from local storage.
     */
    static String readTextFile(Context context, Uri uri) throws IOException {
        InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) throw new FileNotFoundException("Unable to open XML file.");

        StringBuilder sb = new StringBuilder(10_000);
        try (is;
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader)) {

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
