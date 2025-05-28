package com.orienteerfeed.ofeed_sidroid_connector;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Manager for {@link ResultsService}.
 */
public class ResultsServiceManager {

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    private final Activity activity;
    private final String oFeedUrl, siDroidUrl, eventId, authorization, userAgent;
    private final int updateIntervalSec;
    private final int[] timeoutsSec;
    private final ResultsService.ResultsServiceStatus statusListener;

    private ResultsService resultsService;
    private Intent oFeedResultsServiceIntent;
    private boolean oFeedResultsServiceIsBound = false;

    /**
     * Package name of this application.
     */
    private static final String pn = "com.orienteerfeed.ofeed_sidroid_connector.";
    /**
     * Key for value passed as intent extras to {@link ResultsService}.
     */
    static final String KEY_SI_DROID_URL = pn + "siDroidUrl", KEY_OFEED_URL = pn + "url",
            KEY_OFEED_EVENT_ID = pn + "eventId", KEY_OFEED_AUTHORIZATION = pn + "authorization",
            KEY_USER_AGENT = pn + "userAgent", KEY_UPDATE_INTERVAL_SEC = pn + "updateIntervalSec",
            KEY_OFEED_TIMEOUT_CONNECT_SEC = pn + "timeoutConnectSec", KEY_OFEED_TIMEOUT_READ_SEC = pn + "timeoutReadSec",
            KEY_OFEED_TIMEOUT_WRITE_SEC = pn + "timeoutWriteSec", KEY_OFEED_TIMEOUT_CALL_SEC = pn + "timeoutCallSec";

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Manager for {@link ResultsService}.
     *
     * @param activity          Reference to activity.
     * @param oFeedUrl          Base URL of O Feed event, eg, https://api.orienteerfeed.com/rest/v1/upload/iof.
     * @param eventId           O Feed event id, eg, cm1tqvqkh0006qk3mjig95qw1.
     * @param eventPassword     O Feed event password.
     * @param userAgent         SI Droid OFeed Connector user agent. Included in the HTTP request header to O Feed.
     * @param updateIntervalSec Time between uploads from SI Droid Event to OFeed (sec). Must be greater than zero.
     * @param timeoutsSec       Timeouts in seconds for OkHttpClient, as array {connect, read, write, call}.
     *                          A value of -1 means default timeout.
     * @param statusListener    Status of most recent update by {@link ResultsService} is received through this listener.
     * @noinspection JavadocLinkAsPlainText
     */
    ResultsServiceManager(Activity activity, String siDroidUrl, String oFeedUrl, String eventId, String eventPassword,
                          String userAgent, int updateIntervalSec, int[] timeoutsSec,
                          @NonNull ResultsService.ResultsServiceStatus statusListener) {
        if (updateIntervalSec <= 0) throw new IllegalArgumentException("updateIntervalSec must be > 0");
        this.activity = activity;
        this.siDroidUrl = siDroidUrl;
        this.oFeedUrl = oFeedUrl;
        this.eventId = eventId;
        authorization = "Basic " + base64EncodeToString(eventId + ":" + eventPassword);
        this.userAgent = userAgent;
        this.updateIntervalSec = updateIntervalSec;
        this.timeoutsSec = timeoutsSec;
        this.statusListener = statusListener;
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************
    void startOFeedResultsService() {
        oFeedResultsServiceIntent = new Intent(activity, ResultsService.class);

        oFeedResultsServiceIntent.putExtra(KEY_SI_DROID_URL, siDroidUrl);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_URL, oFeedUrl);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_EVENT_ID, eventId);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_AUTHORIZATION, authorization);
        oFeedResultsServiceIntent.putExtra(KEY_USER_AGENT, userAgent);
        oFeedResultsServiceIntent.putExtra(KEY_UPDATE_INTERVAL_SEC, updateIntervalSec);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_TIMEOUT_CONNECT_SEC, timeoutsSec[0]);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_TIMEOUT_READ_SEC, timeoutsSec[1]);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_TIMEOUT_WRITE_SEC, timeoutsSec[2]);
        oFeedResultsServiceIntent.putExtra(KEY_OFEED_TIMEOUT_CALL_SEC, timeoutsSec[3]);

        ContextCompat.startForegroundService(activity, oFeedResultsServiceIntent);
    }

    void stopOFeedResultsService() {
        activity.stopService(oFeedResultsServiceIntent);
    }

    void bindOFeedResultsService() {
        activity.bindService(oFeedResultsServiceIntent, oFeedResultsServiceConnection, Context.BIND_AUTO_CREATE);
    }

    void unbindOFeedResultsService() {
        if (oFeedResultsServiceIsBound) {
            activity.unbindService(oFeedResultsServiceConnection);
            resultsService.setResultsServiceStatus(null);
            oFeedResultsServiceIsBound = false;
        }
    }

    /**
     * Get the application level log.
     */
    String getLog() {
        if (oFeedResultsServiceIsBound) {
            return resultsService.getServerLog();
        } else {
            return "";
        }
    }

    /**
     * Get the HTTP log produced by OkHttp HTTP client.
     */
    String getHttpLog() {
        if (oFeedResultsServiceIsBound) {
            return resultsService.getHttpLog();
        } else {
            return "";
        }
    }

    /**
     * Get status of the most recent update.
     *
     * @return Status, prefixed with "S" for success and "F" for failure.
     */
    String getLatestStatus() {
        if (oFeedResultsServiceIsBound) {
            return resultsService.getLatestStatus();
        } else {
            return "";
        }
    }

    private final ServiceConnection oFeedResultsServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Get service instance.
            ResultsService.OFeedResultsBinder binder = (ResultsService.OFeedResultsBinder) service;
            resultsService = binder.getService();
            resultsService.setResultsServiceStatus(statusListener);
            oFeedResultsServiceIsBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            oFeedResultsServiceIsBound = false;
        }
    };

    // ********************************************************************************************
    // Utility methods.
    // ********************************************************************************************

    /**
     * Encode data as a Base64-encoded string. Note that this method uses android.util.Base64,
     * which is different from java.util.Base64 (requires API 26+).
     *
     * @param data The data to encode.
     * @return The data encoded as a Base64-encoded string.
     */
    private static String base64EncodeToString(@NonNull String data) {
        return android.util.Base64.encodeToString(data.getBytes(), Base64.NO_WRAP);
    }
}
