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
    private final String oFeedUploadUrl, eventId, authorization, siDroidUrl, oResultsApiKey, userAgent;
    private final int updateIntervalSec;
    private final int[] timeoutsSec;
    private final boolean createXmlId;
    private final ResultsService.ResultsServiceUpdateStatus statusListener;

    private ResultsService resultsService;
    private Intent resultsServiceIntent;
    private boolean resultsServiceIsBound = false;

    /**
     * Package name of this application.
     */
    private static final String pn = "com.orienteerfeed.ofeed_sidroid_connector.";
    /**
     * Key for value passed as intent extras to {@link ResultsService}.
     */
    static final String RESULTS_SERVICE_KEY_SI_DROID_URL = pn + "siDroidUrl", RESULTS_SERVICE_KEY_UPLOAD_TO = pn + "uploadTo",
            RESULTS_SERVICE_KEY_OFEED_UPLOAD_URL = pn + "uploadUrl", RESULTS_SERVICE_KEY_OFEED_EVENT_ID = pn + "eventId",
            RESULTS_SERVICE_KEY_OFEED_AUTHORIZATION = pn + "authorization", RESULTS_SERVICE_KEY_USER_AGENT = pn + "userAgent",
            RESULTS_SERVICE_KEY_UPDATE_INTERVAL_SEC = pn + "updateIntervalSec", RESULTS_SERVICE_KEY_ORESULTS_API_KEY = pn + "apiKey",
            RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CONNECT_SEC = pn + "timeoutConnectSec", RESULTS_SERVICE_KEY_HTTP_TIMEOUT_READ_SEC = pn + "timeoutReadSec",
            RESULTS_SERVICE_KEY_HTTP_TIMEOUT_WRITE_SEC = pn + "timeoutWriteSec", RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CALL_SEC = pn + "timeoutCallSec",
            RESULTS_SERVICE_KEY_CREATE_XML_ID = pn + "createXmlId";

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Manager for {@link ResultsService} when uploading to OFeed/OResults.
     *
     * @param activity          Reference to activity.
     * @param uploadTo          One of {@link Preferences#UPLOAD_TO_OFEED}, {@link Preferences#UPLOAD_TO_ORESULTS}.
     * @param siDroidUrl        URL of SI-Droid Event results service, eg, "http://localhost:8080/reports/ResultsIof30Xml".
     * @param oFeedUploadUrl    Base URL of O Feed event, eg, https://api.orienteerfeed.com/rest/v1/upload/iof.
     * @param eventId           O Feed event id, eg, cm1tqvqkh0006qk3mjig95qw1.
     * @param eventPassword     O Feed event password.
     * @param oResultsApiKey    OResults api key.
     * @param userAgent         SI Droid OFeed Connector user agent. Included in the HTTP request header to O Feed.
     * @param updateIntervalSec Time between uploads from SI Droid Event to OFeed/OResults (sec). Must be greater than zero.
     * @param timeoutsSec       Timeouts in seconds for OkHttpClient, as array {connect, read, write, call}.
     *                          A value of -1 means default timeout.
     * @param createXmlId       Create and insert an id tag into the XML results list before uploading.
     * @param statusListener    Status of most recent update by {@link ResultsService} is received through this listener.
     * @noinspection JavadocLinkAsPlainText
     */
    ResultsServiceManager(Activity activity, int uploadTo, String siDroidUrl,
                          String oFeedUploadUrl, String eventId, String eventPassword,
                          String oResultsApiKey, String userAgent, int updateIntervalSec, int[] timeoutsSec,
                          boolean createXmlId,
                          @NonNull ResultsService.ResultsServiceUpdateStatus statusListener) {
        if (updateIntervalSec <= 0) throw new IllegalArgumentException("updateIntervalSec must be > 0");
        this.activity = activity;
        this.uploadTo = uploadTo;
        this.siDroidUrl = siDroidUrl;
        this.oFeedUploadUrl = oFeedUploadUrl;
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
    void startResultsService() {
        resultsServiceIntent = new Intent(activity, ResultsService.class);

        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_UPLOAD_TO, uploadTo);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_SI_DROID_URL, siDroidUrl);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_OFEED_UPLOAD_URL, oFeedUploadUrl);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_OFEED_EVENT_ID, eventId);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_OFEED_AUTHORIZATION, authorization);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_USER_AGENT, userAgent);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_UPDATE_INTERVAL_SEC, updateIntervalSec);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_ORESULTS_API_KEY, oResultsApiKey);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CONNECT_SEC, timeoutsSec[0]);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_READ_SEC, timeoutsSec[1]);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_WRITE_SEC, timeoutsSec[2]);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CALL_SEC, timeoutsSec[3]);
        resultsServiceIntent.putExtra(RESULTS_SERVICE_KEY_CREATE_XML_ID, createXmlId);

        ContextCompat.startForegroundService(activity, resultsServiceIntent);
    }

    void stopResultsService() {
        activity.stopService(resultsServiceIntent);
    }

    void bindResultsService() {
        activity.bindService(resultsServiceIntent, resultsServiceConnection, Context.BIND_AUTO_CREATE);
    }

    void unbindResultsService() {
        if (resultsServiceIsBound) {
            activity.unbindService(resultsServiceConnection);
            resultsService.setResultsServiceStatus(null);
            resultsServiceIsBound = false;
        }
    }

    /**
     * Get the application level log.
     */
    String getLog() {
        if (resultsServiceIsBound) {
            return resultsService.getServerLog();
        } else {
            return "";
        }
    }

    /**
     * Get the HTTP log produced by OkHttp HTTP client.
     */
    String getHttpLog() {
        if (resultsServiceIsBound) {
            return resultsService.getHttpLog();
        } else {
            return "";
        }
    }

    private final ServiceConnection resultsServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Get service instance.
            ResultsService.resultsServiceBinder binder = (ResultsService.resultsServiceBinder) service;
            resultsService = binder.getService();
            resultsService.setResultsServiceStatus(statusListener);
            resultsServiceIsBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            resultsServiceIsBound = false;
        }
    };

}
