package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Util.base64EncodeToString;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

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
    private final int uploadTo;
    private final String oFeedUrl, eventId, authorization, siDroidUrl, oResultsApiKey, userAgent;
    private final int updateIntervalSec;
    private final int[] timeoutsSec;
    private final boolean createXmlId;
    private final ResultsService.ResultsServiceUpdateStatus statusListener;

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
    static final String RESULTS_SERVICE_KEY_SI_DROID_URL = pn + "siDroidUrl", RESULTS_SERVICE_KEY_UPLOAD_TO = pn + "uploadTo",
            RESULTS_SERVICE_KEY_OFEED_URL = pn + "url", RESULTS_SERVICE_KEY_OFEED_EVENT_ID = pn + "eventId",
            RESULTS_SERVICE_KEY_OFEED_AUTHORIZATION = pn + "authorization", RESULTS_SERVICE_KEY_USER_AGENT = pn + "userAgent",
            RESULTS_SERVICE_KEY_UPDATE_INTERVAL_SEC = pn + "updateIntervalSec", RESULTS_SERVICE_KEY_ORESULTS_API_KEY = pn + "apiKey",
            RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CONNECT_SEC = pn + "timeoutConnectSec", RESULTS_SERVICE_KEY_HTTP_TIMEOUT_READ_SEC = pn + "timeoutReadSec",
            RESULTS_SERVICE_KEY_HTTP_TIMEOUT_WRITE_SEC = pn + "timeoutWriteSec", RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CALL_SEC = pn + "timeoutCallSec",
            RESULTS_SERVICE_KEY_CREATE_XML_ID = pn + "createXmlId";

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Manager for {@link ResultsService} when uploading to OFeed.
     *
     * @param activity          Reference to activity.
     * @param uploadTo          One of {@link Preferences#UPLOAD_TO_OFEED}, {@link Preferences#UPLOAD_TO_ORESULTS}.
     * @param siDroidUrl        URL of SI-Droid Event results service, eg, "http://localhost:8080/reports/ResultsIof30Xml".
     * @param oFeedUrl          Base URL of O Feed event, eg, https://api.orienteerfeed.com/rest/v1/upload/iof.
     * @param eventId           O Feed event id, eg, cm1tqvqkh0006qk3mjig95qw1.
     * @param eventPassword     O Feed event password.
     * @param oResultsApiKey    OResults api key.
     * @param userAgent         SI Droid OFeed Connector user agent. Included in the HTTP request header to O Feed.
     * @param updateIntervalSec Time between uploads from SI Droid Event to OFeed (sec). Must be greater than zero.
     * @param timeoutsSec       Timeouts in seconds for OkHttpClient, as array {connect, read, write, call}.
     *                          A value of -1 means default timeout.
     * @param createXmlId       Create and insert an id tag into the XML results list before uploading.
     * @param statusListener    Status of most recent update by {@link ResultsService} is received through this listener.
     * @noinspection JavadocLinkAsPlainText
     */
    ResultsServiceManager(Activity activity, int uploadTo, String siDroidUrl,
                          String oFeedUrl, String eventId, String eventPassword,
                          String oResultsApiKey, String userAgent, int updateIntervalSec, int[] timeoutsSec,
                          boolean createXmlId,
                          @NonNull ResultsService.ResultsServiceUpdateStatus statusListener) {
        if (updateIntervalSec <= 0) throw new IllegalArgumentException("updateIntervalSec must be > 0");
        this.activity = activity;
        this.uploadTo = uploadTo;
        this.siDroidUrl = siDroidUrl;
        this.oFeedUrl = oFeedUrl;
        this.eventId = eventId;
        authorization = "Basic " + base64EncodeToString(eventId + ":" + eventPassword);
        this.oResultsApiKey = oResultsApiKey;
        this.userAgent = userAgent;
        this.updateIntervalSec = updateIntervalSec;
        this.timeoutsSec = timeoutsSec;
        this.createXmlId = createXmlId;
        this.statusListener = statusListener;
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************
    void startOFeedResultsService() {
        oFeedResultsServiceIntent = new Intent(activity, ResultsService.class);

        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_UPLOAD_TO, uploadTo);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_SI_DROID_URL, siDroidUrl);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_OFEED_URL, oFeedUrl);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_OFEED_EVENT_ID, eventId);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_OFEED_AUTHORIZATION, authorization);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_USER_AGENT, userAgent);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_UPDATE_INTERVAL_SEC, updateIntervalSec);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_ORESULTS_API_KEY, oResultsApiKey);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CONNECT_SEC, timeoutsSec[0]);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_READ_SEC, timeoutsSec[1]);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_WRITE_SEC, timeoutsSec[2]);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CALL_SEC, timeoutsSec[3]);
        oFeedResultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_CREATE_XML_ID, createXmlId);

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

}
