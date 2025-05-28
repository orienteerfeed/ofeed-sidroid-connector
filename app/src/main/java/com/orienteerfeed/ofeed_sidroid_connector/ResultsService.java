package com.orienteerfeed.ofeed_sidroid_connector;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_AUTHORIZATION;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_EVENT_ID;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_TIMEOUT_CALL_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_TIMEOUT_CONNECT_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_TIMEOUT_READ_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_TIMEOUT_WRITE_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_OFEED_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_SI_DROID_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_UPDATE_INTERVAL_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.KEY_USER_AGENT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Foreground service which gets results from SI-Droid and uploads them to OFeed.
 */
public class ResultsService extends Service {

    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************

    /**
     * Callback for updating the main user interface with the status of the most recent update
     * of results from SI-Droid to OFeed.
     */
    public interface ResultsServiceStatus {
        void onSuccess(String status);

        void onFailure(String status);
    }

    /**
     * Set callback for updating the main user interface with the status of the most recent update.
     */
    public void setResultsServiceStatus(ResultsServiceStatus statusListener) {
        this.statusListener = statusListener;
    }

    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
    private String latestStatus = "";

    private void statusSuccess(String status) {
        String s = LocalTime.now().format(HH_MM_SS) + " " + status;
        latestStatus = "S" + s;  // Prefix for success.
        statusListener.onSuccess(s);
    }

    private void statusFailure(String status) {
        String s = LocalTime.now().format(HH_MM_SS) + " " + status;
        latestStatus = "F" + s;  // Prefix for failure.
        statusListener.onFailure(s);
    }

    /**
     * Get status of the most recent update.
     *
     * @return Status, prefixed with "S" for success and "F" for failure.
     */
    public String getLatestStatus() {
        return latestStatus;
    }

    // *********************************************************************************************
    // Log.
    // *********************************************************************************************

    /**
     * Get the application level log.
     */
    public String getServerLog() {
        return serverLog.toString();
    }

    /**
     * Get the HTTP log produced by OkHttp HTTP client.
     */
    public String getHttpLog() {
        return httpLog.toString();
    }

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    // Flag checked by main activity to see if service is running.
    public static boolean isRunning = false;
    private OkHttpClient httpClient;
    private ResultsServiceStatus statusListener = null;
    private String oFeedUrl, oFeedEventId, oFeedAuthorization, oFeedUserAgent;
    private int updateIntervalMillisec;
    private static final MediaType XML_MEDIA_TYPE = MediaType.parse("text/xml; charset=utf-8");

    private Request siDroidGetRequest;
    private SimpleTimer updateIntervalTimer = null;

    private CircularLog serverLog, httpLog;

    // *********************************************************************************************
    // Binder that is given to the client.
    // *********************************************************************************************
    private final IBinder oFeedResultsBinder = new OFeedResultsBinder();

    public class OFeedResultsBinder extends Binder {
        ResultsService getService() {
            // Return this instance of GnssService so manager can call public methods.
            return ResultsService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return oFeedResultsBinder;
    }

    // *********************************************************************************************
    // Lifecycle of service.
    // *********************************************************************************************
    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = createNotification();
        } else {
            notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, 1, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        serverLog = new CircularLog(25);
        httpLog = new CircularLog(25);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // Get params.
        String siDroidUrl = intent.getStringExtra(KEY_SI_DROID_URL);
        oFeedUrl = intent.getStringExtra(KEY_OFEED_URL);
        oFeedEventId = intent.getStringExtra(KEY_OFEED_EVENT_ID);
        oFeedAuthorization = intent.getStringExtra(KEY_OFEED_AUTHORIZATION);
        oFeedUserAgent = intent.getStringExtra(KEY_USER_AGENT);
        updateIntervalMillisec = intent.getIntExtra(KEY_UPDATE_INTERVAL_SEC, 30) * 1_000;
        int timeoutConnectSec = intent.getIntExtra(KEY_OFEED_TIMEOUT_CONNECT_SEC, -1);    // -1 = Use default timeout.
        int timeoutReadSec = intent.getIntExtra(KEY_OFEED_TIMEOUT_READ_SEC, -1);
        int timeoutWriteSec = intent.getIntExtra(KEY_OFEED_TIMEOUT_WRITE_SEC, -1);
        int timeoutCallSec = intent.getIntExtra(KEY_OFEED_TIMEOUT_CALL_SEC, -1);

        // Create the HTTP client and attach a logger.
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(logItem -> httpLog.add(logItem));
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.addInterceptor(logging);
        if (timeoutConnectSec >= 0) clientBuilder.connectTimeout(timeoutConnectSec, TimeUnit.SECONDS);
        if (timeoutReadSec >= 0) clientBuilder.readTimeout(timeoutReadSec, TimeUnit.SECONDS);
        if (timeoutWriteSec >= 0) clientBuilder.writeTimeout(timeoutWriteSec, TimeUnit.SECONDS);
        if (timeoutCallSec >= 0) clientBuilder.callTimeout(timeoutCallSec, TimeUnit.SECONDS);
        httpClient = clientBuilder.build();

        // Create GET request to pull results out of SI-Droid.
        siDroidGetRequest = new Request.Builder()
                .url(Objects.requireNonNull(siDroidUrl))
                .header("User-Agent", oFeedUserAgent)
                .get().build();

        // Allow some time for the service to start before the first update of results from SI-Droid to OFeed takes place.
        new SimpleTimer(3_000, this::firstUpdateOfResults).startTimer();

        isRunning = true;

        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        stopResultsUpdates();
    }

    private void stopResultsUpdates() {
        updateIntervalTimer.stopTimer();
    }

    // *********************************************************************************************
    // Get results from SI-Droid.
    // *********************************************************************************************

    private void firstUpdateOfResults() {
        // First update of results from SI-Droid to OFeed.
        updateResults();

        // Recurring updates.
        updateIntervalTimer = new SimpleTimer(updateIntervalMillisec, () -> {
            updateResults();
            updateIntervalTimer.startTimer();   // Restart timer.
        });
        updateIntervalTimer.startTimer();
    }

    /**
     * Get results from SI-Droid.
     */
    private void updateResults() {
        serverLog.add(getString(R.string.si_droid_get_request));
        httpClient.newCall(siDroidGetRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String message = e.getMessage();
                if (message == null) message = getString(R.string.io_exception);
                statusFailure(message);
                serverLog.add(message);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful()) {
                        if (responseBody != null) {
                            String responseBodyAsString = responseBody.string();
                            if(responseBodyAsString.contains("<PersonResult>")) {
                                // Results available.
                                serverLog.add(getString(R.string.si_droid_results_retrieved));
                                uploadResults(responseBodyAsString);
                            } else {
                                String message = getString(R.string.si_droid_no_results);
                                statusSuccess(message);
                                serverLog.add(message);
                            }
                        } else {
                            String message = getString(R.string.null_response);
                            statusFailure(message);
                            serverLog.add(message);
                        }
                    } else {
                        // Unsuccessful response.
                        String message = HttpStatusCodes.getMeaning(response.code());
                        statusFailure(message);
                        serverLog.add(message);
                    }
                } catch (IOException e) {
                    String message = e.getMessage();
                    if (message == null) message = getString(R.string.io_exception);
                    statusFailure(message);
                    serverLog.add(message);
                }
            }
        });
    }

    /**
     * Upload results to OFeed.
     */
    private void uploadResults(String xmlContent) {
        serverLog.add(getString(R.string.ofeed_post_request));

        // Insert external id.
        String xmlContentWithExternalId;
        try {
            xmlContentWithExternalId = XmlModifier.updateOrInsertIds(xmlContent);
        } catch (Exception e) {
            String message = getString(R.string.external_id_error);
            statusFailure(message);
            if (e.getMessage() != null) message += " " + e.getMessage();
            serverLog.add(message);
            return;
        }

        RequestBody xmlRequestBody = RequestBody.create(xmlContentWithExternalId, XML_MEDIA_TYPE);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("eventId", oFeedEventId)
                .addFormDataPart("file", "result-list-iof-3.0.xml", xmlRequestBody)
                .build();

        Request request = new Request.Builder()
                .url(oFeedUrl)
                .addHeader("User-Agent", oFeedUserAgent)
                .addHeader("Authorization", oFeedAuthorization)
                .addHeader("Content-Type", "text; charset=utf-8")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String message = e.getMessage();
                if (message == null) message = getString(R.string.io_exception);
                statusFailure(message);
                serverLog.add(message);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful()) {
                        if (responseBody != null) {
                            String message = getString(R.string.ofeed_upload_ok);
                            statusSuccess(message);
                            serverLog.add(message);
                        } else {
                            String message = getString(R.string.null_response);
                            statusFailure(message);
                            serverLog.add(message);
                        }
                    } else {
                        // Unsuccessful response.
                        String message = HttpStatusCodes.getMeaning(response.code());
                        statusFailure(message);
                        serverLog.add(message);
                    }
                }
            }
        });
    }

    // *********************************************************************************************
    // Notification to tell user that this service is active in the foreground.
    // *********************************************************************************************
    private static final String NOTIFICATION_CHANNEL_ID = "OFeedResultsServiceNotificationChannelId";

    @RequiresApi(Build.VERSION_CODES.O)
    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                getString(R.string.ofeed_results_service_notification_title), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.ofeed_results_service_notification_text));

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle(getString(R.string.ofeed_results_service_notification_title))
                .setContentText(getString(R.string.ofeed_results_service_notification_text))
                .setSmallIcon(R.drawable.ofeed_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
