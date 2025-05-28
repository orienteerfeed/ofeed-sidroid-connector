package com.orienteerfeed.ofeed_sidroid_connector;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/**
 * This class holds the app's settings and handle their storage in shared preferences.
 */
public class Preferences {

    /**
     * Format string for URL of SI-Droid Event.
     * To allow HTTP in Android, see network security configuration file in xml folder.
     */
    static final String SI_DROID_URL = "http://localhost:%d/reports/ResultsIof30Xml";

    /**
     * Format string for checking if the Result Service of SI-Droid Event is running.
     */
    static final String SI_DROID_PING_URL = "http://localhost:%d";

    /**
     * HTTP user agent of this app.
     */
    static String USER_AGENT;

    //**********************************************************************************************
    // Settings.
    //**********************************************************************************************

    /**
     * Key to get {@link #PREFERENCES_VERSION}.
     *
     * @noinspection unused
     */
    private static final String KEY_PREFERENCES_VERSION = "PREFERENCES_VERSION";

    /**
     * Version of this preferences storage.
     * Not retrieved unless needed, eg, to detect if a conversion from a previous version is required.
     *
     * @noinspection unused
     */
    private static final int PREFERENCES_VERSION = 1;

    /**
     * Controls whether the news screen is displayed when launching the app.
     */
    boolean showNews;
    private static final String KEY_VERSION_CODE = "DO_NOT_SHOW_AGAIN";
    private static final int DEFAULT_VERSION_CODE = 0;

    /**
     * Postpone in-app update until this date has been passed.
     */
    int inAppUpdatePostponedUntilYear, inAppUpdatePostponedUntilMonth, inAppUpdatePostponedUntilDay;
    private static final String KEY_IN_APP_UPDATE_POSTPONED_UNTIL_YEAR = "IN_APP_UPDATE_POSTPONED_UNTIL_YEAR";
    private static final String KEY_IN_APP_UPDATE_POSTPONED_UNTIL_MONTH = "IN_APP_UPDATE_POSTPONED_UNTIL_MONTH";
    private static final String KEY_IN_APP_UPDATE_POSTPONED_UNTIL_DAY = "IN_APP_UPDATE_POSTPONED_UNTIL_DAY";
    private static final int DEFAULT_IN_APP_UPDATE_POSTPONED_UNTIL_YEAR = 2025;
    private static final int DEFAULT_IN_APP_UPDATE_POSTPONED_UNTIL_MONTH = 1;
    private static final int DEFAULT_IN_APP_UPDATE_POSTPONED_UNTIL_DAY = 1;

    /**
     * Timeout (seconds) used by OkHttpClient.
     * The connect timeout is applied when connecting a TCP socket to the target host.
     */
    int httpConnectTimeoutSec;
    private static final String KEY_HTTP_CONNECT_TIMEOUT_SEC = "UPLOAD_POST_CONNECT_TIMEOUT_SEC";
    static final int DEFAULT_HTTP_CONNECT_TIMEOUT_SEC = 10;

    /**
     * Timeout (seconds) used by OkHttpClient.
     * The read timeout is applied to both the TCP socket and for individual read IO
     * operations including on Source of the Response.
     */
    int httpReadTimeoutSec;
    private static final String KEY_HTTP_READ_TIMEOUT_SEC = "UPLOAD_POST_READ_TIMEOUT_SEC";
    static final int DEFAULT_HTTP_READ_TIMEOUT_SEC = 10;

    /**
     * Timeout (seconds) used by OkHttpClient.
     * The write timeout is applied for individual write IO operations.
     */
    int httpWriteTimeoutSec;
    private static final String KEY_HTTP_WRITE_TIMEOUT_SEC = "UPLOAD_POST_WRITE_TIMEOUT_SEC";
    static final int DEFAULT_HTTP_WRITE_TIMEOUT_SEC = 10;

    /**
     * Timeout (seconds) used by OkHttpClient.
     * Timeout for complete calls. The call timeout spans the entire call: resolving DNS, connecting, writing the
     * request body, server processing, and reading the response body. If the call requires redirects or retries
     * all must complete within one timeout period.
     */
    int httpCallTimeoutSec;
    private static final String KEY_HTTP_CALL_TIMEOUT_SEC = "UPLOAD_POST_CALL_TIMEOUT_SEC";
    static final int DEFAULT_HTTP_CALL_TIMEOUT_SEC = 0;

    /**
     * Endpoint URL of OFeed, eg, "https://api.orienteerfeed.com/rest/v1/events".
     * Default value {@link #DEFAULT_OFEED_SERVER}.
     *
     * @noinspection JavadocLinkAsPlainText
     */
    String oFeedServer;
    private static final String KEY_OFEED_SERVER = "O_FEED_SERVER";
    private static final String DEFAULT_OFEED_SERVER = "https://api.orienteerfeed.com/rest/v1/upload/iof";

    /**
     * OFeed event id.
     * Default value {@link #DEFAULT_OFEED_EVENT_ID}.
     */
    String oFeedEventId;
    private static final String KEY_OFEED_EVENT_ID = "O_FEED_EVENT_ID";
    private static final String DEFAULT_OFEED_EVENT_ID = "";

    /**
     * OFeed event password.
     * Default value {@link #DEFAULT_OFEED_EVENT_PASSWORD}.
     */
    String oFeedEventPassword;
    private static final String KEY_OFEED_EVENT_PASSWORD = "O_FEED_EVENT_PASSWORD";
    private static final String DEFAULT_OFEED_EVENT_PASSWORD = "";

    /**
     * SI-Droid port number for GET request of results.
     * Default value {@link #DEFAULT_SI_DROID_PORT}.
     */
    int siDroidPort;
    private static final String KEY_SI_DROID_PORT = "SI_DROID_PORT";
    private static final int DEFAULT_SI_DROID_PORT = 8080;

    /**
     * Interval (seconds) between updates from SI-Droid to OFeed.
     * Default value {@link #DEFAULT_UPLOAD_INTERVAL_SEC}.
     */
    int uploadIntervalSec;
    private static final String KEY_UPLOAD_INTERVAL_SEC = "UPLOAD_INTERVAL_SEC";
    private static final int DEFAULT_UPLOAD_INTERVAL_SEC = 30;

    /**
     * Android battery restrictions.
     */
    boolean checkBatteryRestriction;
    private static final String KEY_BATTERY_RESTRICTION = "BATTERY_RESTRICTION";
    private static final boolean DEFAULT_BATTERY_RESTRICTION = true;

    /**
     * Suggest manual update of Google Play Services. Play Services is used by the barcode scanner.
     */
    boolean manuallyUpdateGooglePlayServices;
    private static final String KEY_MANUALLY_UPDATE_GOOGLE_PLAY_SERVICES = "MANUALLY_UPDATE_GOOGLE_PLAY_SERVICES";
    private static final boolean DEFAULT_MANUALLY_UPDATE_GOOGLE_PLAY_SERVICES = true;


    //**********************************************************************************************
    // Other member fields.
    //**********************************************************************************************
    private final Context context;
    final int packageVersionCode;

    //**********************************************************************************************
    // Constructor.
    //**********************************************************************************************

    /**
     * This class holds the app's settings and handle their storage in shared preferences.
     */
    Preferences(Context context) {
        this.context = context;
        packageVersionCode = getPackageVersionCode();
        USER_AGENT = context.getString(R.string.app_name) + "/" + BuildConfig.VERSION_NAME;
    }

    //**********************************************************************************************
    // Methods.
    //**********************************************************************************************

    /**
     * Get settings from shared preferences.
     */
    void get() {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);

        showNews = prefs.getInt(KEY_VERSION_CODE, DEFAULT_VERSION_CODE) != packageVersionCode;

        // Postpone in-app update until this date has been passed.
        inAppUpdatePostponedUntilYear = prefs.getInt(KEY_IN_APP_UPDATE_POSTPONED_UNTIL_YEAR, DEFAULT_IN_APP_UPDATE_POSTPONED_UNTIL_YEAR);
        inAppUpdatePostponedUntilMonth = prefs.getInt(KEY_IN_APP_UPDATE_POSTPONED_UNTIL_MONTH, DEFAULT_IN_APP_UPDATE_POSTPONED_UNTIL_MONTH);
        inAppUpdatePostponedUntilDay = prefs.getInt(KEY_IN_APP_UPDATE_POSTPONED_UNTIL_DAY, DEFAULT_IN_APP_UPDATE_POSTPONED_UNTIL_DAY);

        // OFeed.
        httpConnectTimeoutSec = prefs.getInt(KEY_HTTP_CONNECT_TIMEOUT_SEC, DEFAULT_HTTP_CONNECT_TIMEOUT_SEC);
        httpReadTimeoutSec = prefs.getInt(KEY_HTTP_READ_TIMEOUT_SEC, DEFAULT_HTTP_READ_TIMEOUT_SEC);
        httpWriteTimeoutSec = prefs.getInt(KEY_HTTP_WRITE_TIMEOUT_SEC, DEFAULT_HTTP_WRITE_TIMEOUT_SEC);
        httpCallTimeoutSec = prefs.getInt(KEY_HTTP_CALL_TIMEOUT_SEC, DEFAULT_HTTP_CALL_TIMEOUT_SEC);

        oFeedServer = prefs.getString(KEY_OFEED_SERVER, DEFAULT_OFEED_SERVER);
        oFeedEventId = prefs.getString(KEY_OFEED_EVENT_ID, DEFAULT_OFEED_EVENT_ID);
        oFeedEventPassword = prefs.getString(KEY_OFEED_EVENT_PASSWORD, DEFAULT_OFEED_EVENT_PASSWORD);

        // SI-Droid.
        siDroidPort = prefs.getInt(KEY_SI_DROID_PORT, DEFAULT_SI_DROID_PORT);

        // Upload interval.
        uploadIntervalSec = prefs.getInt(KEY_UPLOAD_INTERVAL_SEC, DEFAULT_UPLOAD_INTERVAL_SEC);

        // Battery restriction
        checkBatteryRestriction = prefs.getBoolean(KEY_BATTERY_RESTRICTION, DEFAULT_BATTERY_RESTRICTION);

        // Google Play Services.
        manuallyUpdateGooglePlayServices = prefs.getBoolean(KEY_MANUALLY_UPDATE_GOOGLE_PLAY_SERVICES, DEFAULT_MANUALLY_UPDATE_GOOGLE_PLAY_SERVICES);
    }

    /**
     * Save settings to shared preferences.
     */
    void save() {
        SharedPreferences.Editor editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit();

        if (!showNews) editor.putInt(KEY_VERSION_CODE, packageVersionCode);

        // Postpone in-app update until this date has been passed.
        editor.putInt(KEY_IN_APP_UPDATE_POSTPONED_UNTIL_YEAR, inAppUpdatePostponedUntilYear);
        editor.putInt(KEY_IN_APP_UPDATE_POSTPONED_UNTIL_MONTH, inAppUpdatePostponedUntilMonth);
        editor.putInt(KEY_IN_APP_UPDATE_POSTPONED_UNTIL_DAY, inAppUpdatePostponedUntilDay);

        // OFeed.
        editor.putInt(KEY_HTTP_CONNECT_TIMEOUT_SEC, httpConnectTimeoutSec);
        editor.putInt(KEY_HTTP_READ_TIMEOUT_SEC, httpReadTimeoutSec);
        editor.putInt(KEY_HTTP_WRITE_TIMEOUT_SEC, httpWriteTimeoutSec);
        editor.putInt(KEY_HTTP_CALL_TIMEOUT_SEC, httpCallTimeoutSec);

        editor.putString(KEY_OFEED_SERVER, oFeedServer);
        editor.putString(KEY_OFEED_EVENT_ID, oFeedEventId);
        editor.putString(KEY_OFEED_EVENT_PASSWORD, oFeedEventPassword);

        // SI-Droid.
        editor.putInt(KEY_SI_DROID_PORT, siDroidPort);

        // Upload interval.
        editor.putInt(KEY_UPLOAD_INTERVAL_SEC, uploadIntervalSec);

        // Battery restriction
        editor.putBoolean(KEY_BATTERY_RESTRICTION, checkBatteryRestriction);

        // Google Play Services.
        editor.putBoolean(KEY_MANUALLY_UPDATE_GOOGLE_PLAY_SERVICES, manuallyUpdateGooglePlayServices);

        editor.apply();
    }

    private int getPackageVersionCode() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }
}
