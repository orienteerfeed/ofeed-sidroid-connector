package com.orienteerfeed.ofeed_sidroid_connector;

import static android.webkit.URLUtil.isHttpsUrl;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;

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
}
