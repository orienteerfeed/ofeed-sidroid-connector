package com.orienteerfeed.ofeed_sidroid_connector;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.ORESULTS_GET_EVENT_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.ORESULTS_RESULTS_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.UPLOAD_TO_OFEED;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_CREATE_XML_ID;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CALL_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CONNECT_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_HTTP_TIMEOUT_READ_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_HTTP_TIMEOUT_WRITE_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_OFEED_AUTHORIZATION;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_OFEED_EVENT_ID;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_OFEED_UPLOAD_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_ORESULTS_API_KEY;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_SI_DROID_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_UPDATE_INTERVAL_SEC;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_UPLOAD_TO;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsServiceManager.RESULTS_SERVICE_KEY_USER_AGENT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
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
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Foreground service which retrieves results from SI-Droid Event and uploads them to OFeed/OResults.
 */
public class ResultsService extends Service {

    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************

    /**
     * Callback for updating the main user interface with the status of the update
     * of results from SI-Droid Event to OFeed/OResults.
     */
    public interface ResultsServiceUpdateStatus {
        /**
         * Update started by requesting results from SI-Droid Event.
         *
         * @param timeMs Timestamp (ms).
         */
        void onUpdateStart(long timeMs);

        /**
         * Update successfully completed.
         *
         * @param timeMs Timestamp (ms).
         * @param status Status message.
         */
        void onUpdateSuccess(long timeMs, String status);

        /**
         * Update failed.
         *
         * @param timeMs Timestamp (ms).
         * @param status Status message.
         */
        void onUpdateFailure(long timeMs, String status);

        /**
         * A featured imgage has been downloaded for this event.
         */
        void onOFeedFeaturedImage(Bitmap image);

        /**
         * Event info have been downloaded.
         *
         * @param oResultsId Only valid for OResults. OFeed will use -1.
         */
        void onEvent(String name, int oResultsId);
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    /**
     * Set callback for updating the main user interface with the status of the update.
     */
    public void setResultsServiceStatus(ResultsServiceUpdateStatus statusListener) {
        this.statusListener = statusListener;
    }

    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);

    /**
     * Convenience method for callback {@link ResultsServiceUpdateStatus#onUpdateSuccess(long, String)}.
     */
    private void statusSuccess(String status) {
        String s = LocalTime.now().format(HH_MM_SS) + " " + status;
        if (statusListener != null) statusListener.onUpdateSuccess(System.currentTimeMillis(), s);
    }

    /**
     * Convenience method for callback {@link ResultsServiceUpdateStatus#onUpdateFailure(long, String)}.
     */
    private void statusFailure(String status) {
        String s = LocalTime.now().format(HH_MM_SS) + " " + status;
        if (statusListener != null) statusListener.onUpdateFailure(System.currentTimeMillis(), s);
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
    //
    /**
     * Flag between {@link ResultsService} and {@link MainActivity} which
     * signals if the results service is running.
     * Set to true by {@link ResultsService#onStartCommand(Intent, int, int)} when
     * the service starts, set to false by {@link ResultsService#onDestroy()} when
     * the service stops.
     */
    public static boolean resultServiceIsRunning = false;
    private OkHttpClient httpClient;
    private ResultsServiceUpdateStatus statusListener = null;
    private int uploadTo;
    private String oFeedUploadUrl, oFeedEventId, oFeedAuthorization, oResultsApiKey, UserAgent;
    private int updateIntervalMillisec;
    private boolean createXmlId;
    private static final MediaType XML_MEDIA_TYPE = MediaType.parse("text/xml; charset=utf-8");

    private Request siDroidGetRequest;
    private SimpleTimer updateIntervalTimer = null;

    private CircularLog serverLog, httpLog;

    private XmlIds xmlIds;
    public static final String XML_IDS_FILENAME = "xml_ids.dat";

    /**
     * Allow some time for the service to start before the first update of results
     * from SI-Droid Event to OFeed/OResults takes place.
     */
    public static final int TIME_TO_FIRST_UPDATE_SEC = 3;

    private static final String IO_EXCEPTION = "I/O exception.";

    // *********************************************************************************************
    // Binder that is given to the client.
    // *********************************************************************************************
    private final IBinder resultsServiceBinder = new resultsServiceBinder();

    public class resultsServiceBinder extends Binder {
        ResultsService getService() {
            // Return this instance of ResultsService so manager can call public methods.
            return ResultsService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return resultsServiceBinder;
    }

    // *********************************************************************************************
    // Lifecycle of service.
    // *********************************************************************************************
    @Override
    public void onCreate() {
        super.onCreate();
        xmlIds = SerializableManager.load(this, XML_IDS_FILENAME);
        if (xmlIds == null) {
            xmlIds = new XmlIds();
        }

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
        uploadTo = intent.getIntExtra(RESULTS_SERVICE_KEY_UPLOAD_TO, 0);
        String siDroidUrl = intent.getStringExtra(RESULTS_SERVICE_KEY_SI_DROID_URL);
        oFeedUploadUrl = intent.getStringExtra(RESULTS_SERVICE_KEY_OFEED_UPLOAD_URL);
        oFeedEventId = intent.getStringExtra(RESULTS_SERVICE_KEY_OFEED_EVENT_ID);
        oFeedAuthorization = intent.getStringExtra(RESULTS_SERVICE_KEY_OFEED_AUTHORIZATION);
        UserAgent = intent.getStringExtra(RESULTS_SERVICE_KEY_USER_AGENT);
        updateIntervalMillisec = intent.getIntExtra(RESULTS_SERVICE_KEY_UPDATE_INTERVAL_SEC, 30) * 1_000;
        oResultsApiKey = intent.getStringExtra(RESULTS_SERVICE_KEY_ORESULTS_API_KEY);
        int timeoutConnectSec = intent.getIntExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CONNECT_SEC, -1);    // -1 = Use default timeout.
        int timeoutReadSec = intent.getIntExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_READ_SEC, -1);
        int timeoutWriteSec = intent.getIntExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_WRITE_SEC, -1);
        int timeoutCallSec = intent.getIntExtra(RESULTS_SERVICE_KEY_HTTP_TIMEOUT_CALL_SEC, -1);
        createXmlId = intent.getBooleanExtra(RESULTS_SERVICE_KEY_CREATE_XML_ID, true);

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

        // Create GET request to pull results out of SI-Droid Event.
        siDroidGetRequest = new Request.Builder()
                .url(Objects.requireNonNull(siDroidUrl))
                .header("User-Agent", UserAgent)
                .get().build();

        getEventName();
        if (uploadTo == UPLOAD_TO_OFEED) getOFeedFeaturedImage();

        // Allow some time for the service to start before the first update of results from SI-Droid Event to OFeed/OResults takes place.
        new SimpleTimer(1_000 * TIME_TO_FIRST_UPDATE_SEC, this::firstUpdateOfResults).startTimer();

        resultServiceIsRunning = true;
        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        resultServiceIsRunning = false;
        stopResultsUpdates();
        if (!xmlIds.isEmpty()) {
            SerializableManager.save(this, xmlIds, XML_IDS_FILENAME);
        } else {
            SerializableManager.delete(this, XML_IDS_FILENAME);
        }
    }

    private void stopResultsUpdates() {
        if (updateIntervalTimer != null) updateIntervalTimer.stopTimer();
    }

    // *********************************************************************************************
    // Get results from SI-Droid Event and upload them to OFeed/OResults.
    // *********************************************************************************************

    private void firstUpdateOfResults() {
        // First update of results from SI-Droid Event to OFeed/OResults.
        updateResults();

        // Recurring updates.
        updateIntervalTimer = new SimpleTimer(updateIntervalMillisec, () -> {
            updateResults();
            updateIntervalTimer.startTimer();   // Restart timer.
        });
        updateIntervalTimer.startTimer();
    }

    /**
     * Get results from SI-Droid Event.
     */
    private void updateResults() {
        if (statusListener != null) statusListener.onUpdateStart(System.currentTimeMillis());
        serverLog.add(getString(R.string.si_droid_get_request));
        httpClient.newCall(siDroidGetRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String message = e.getMessage() != null ? e.getMessage() : IO_EXCEPTION;
                statusFailure(message);
                serverLog.add(message);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        String responseBodyAsString = r.body().string();
                        if (responseBodyAsString.contains("<PersonResult>")) {
                            // Results available.
                            serverLog.add(getString(R.string.si_droid_results_retrieved));
                            uploadResults(responseBodyAsString);
                        } else {
                            String message = getString(R.string.si_droid_no_results);
                            statusSuccess(message);
                            serverLog.add(message);
                        }
                    } else {
                        // Unsuccessful response.
                        String message = HttpStatusCodes.getMeaning(r.code());
                        statusFailure(message);
                        serverLog.add(message);
                    }
                } catch (IOException e) {
                    String message = e.getMessage() != null ? e.getMessage() : IO_EXCEPTION;
                    statusFailure(message);
                    serverLog.add(message);
                }
            }
        });
    }

    /**
     * Upload results to OFeed/OResults.
     */
    private void uploadResults(String xmlContent) {
        // Insert XML id.
        String xmlUpload;
        if (createXmlId) {
            try {
                xmlUpload = XmlModifier.updateOrInsertXmlId(xmlContent, xmlIds);
                if (xmlUpload == null) {
                    String message = getString(R.string.no_results_to_upload);
                    statusSuccess(message);
                    serverLog.add(message);
                    return;
                }
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null) message = getString(R.string.upload_failed);
                statusFailure(message);
                serverLog.add(message);
                return;
            }
        } else {
            xmlUpload = xmlContent;
        }

        Request request;
        if (uploadTo == UPLOAD_TO_OFEED) {
            serverLog.add(getString(R.string.ofeed_post_request));
            RequestBody xmlRequestBody = RequestBody.create(xmlUpload, XML_MEDIA_TYPE);
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("eventId", oFeedEventId)
                    .addFormDataPart("file", "result-list-iof-3.0.xml", xmlRequestBody)
                    .build();

            request = new Request.Builder()
                    .url(oFeedUploadUrl)
                    .addHeader("User-Agent", UserAgent)
                    .addHeader("Authorization", oFeedAuthorization)
                    .addHeader("Content-Type", "text; charset=utf-8")
                    .post(requestBody)
                    .build();

        } else {
            // Upload to OResults.
            serverLog.add(getString(R.string.oresults_post_request));
            RequestBody xmlRequestBody = RequestBody.create(xmlUpload, XML_MEDIA_TYPE);
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("apiKey", oResultsApiKey)
                    .addFormDataPart("file", "result-list-iof-3.0.xml", xmlRequestBody)
                    .build();
            request = new Request.Builder()
                    .url(ORESULTS_RESULTS_URL)
                    .addHeader("User-Agent", UserAgent)
//                    .addHeader("Content-Type", "text; charset=utf-8")
                    .post(requestBody)
                    .build();
        }

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String message = e.getMessage() != null ? e.getMessage() : IO_EXCEPTION;
                statusFailure(message);
                serverLog.add(message);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        String message = getString(R.string.uploaded_ok);
                        statusSuccess(message);
                        serverLog.add(message);
                    } else {
                        // Unsuccessful response.
                        String message = HttpStatusCodes.getMeaning(r.code());
                        statusFailure(message);
                        serverLog.add(message);
                    }
                }
            }
        });
    }

    // *********************************************************************************************
    // Get info about the event from OFeed/OResults.
    // *********************************************************************************************

    /**
     * Get featured image from OFeed. This method fails silently.
     */
    private void getOFeedFeaturedImage() {
        String oFeedEventUrl = getOFeedEventUrl();
        if (oFeedEventUrl == null) return;
        String oFeedFeaturedImageEndpoint = oFeedEventUrl + "/image";
        Request oFeedGetRequest = new Request.Builder()
                .url(Objects.requireNonNull(oFeedFeaturedImageEndpoint))
                .header("User-Agent", UserAgent)
                .get().build();

        httpClient.newCall(oFeedGetRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                Bitmap image = null;
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        InputStream inputStream = r.body().byteStream();
                        image = BitmapFactory.decodeStream(inputStream);
                    }
                    if (statusListener != null) statusListener.onOFeedFeaturedImage(image);
                }
            }
        });
    }

    /**
     * Get event name. This method fails silently.
     */
    private void getEventName() {
        if (uploadTo == UPLOAD_TO_OFEED) {
            String oFeedEventEndpoint = getOFeedEventUrl();
            if (oFeedEventEndpoint == null) return;
            Request oFeedGetRequest = new Request.Builder()
                    .url(Objects.requireNonNull(oFeedEventEndpoint))
                    .header("User-Agent", UserAgent)
                    .get().build();

            httpClient.newCall(oFeedGetRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response r = response) {
                        String name = "";
                        if (r.isSuccessful()) {
                            Gson gson = new Gson();
                            OFeedEvent event = gson.fromJson(r.body().string(), OFeedEvent.class);
                            name = event.results.data.name;
                            if (name == null) name = "";
                            name = name.trim();
                        }
                        if (statusListener != null) statusListener.onEvent(name, -1);
                    } catch (IOException ignored) {
                    }
                }
            });

        } else {
            // OResults.
            String oResultsEventEndpoint = ORESULTS_GET_EVENT_URL + oResultsApiKey;
            Request oFeedGetRequest = new Request.Builder()
                    .url(Objects.requireNonNull(oResultsEventEndpoint))
                    .header("User-Agent", UserAgent)
                    .get().build();

            httpClient.newCall(oFeedGetRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response r = response) {
                        String name = "";
                        int id = -1;
                        if (r.isSuccessful()) {
                            Gson gson = new Gson();
                            OResultsEvent event = gson.fromJson(r.body().string(), OResultsEvent.class);
                            id = event.id;
                            name = event.name;
                            if (name == null) name = "";
                            name = name.trim();
                        }
                        if (statusListener != null) statusListener.onEvent(name, id);
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }

    // *********************************************************************************************
    // Classes used by Gson when parsing Json. Also see proguard-rulese.pro.
    // *********************************************************************************************

    @SuppressWarnings("unused")
    private static class OFeedEvent {
//        String message;
//        Boolean error;
//        Integer code;
        OFeedResults results;
    }

    @SuppressWarnings("unused")
    private static class OFeedResults {
        OFeedData data;
    }

    @SuppressWarnings("unused")
    private static class OFeedData {
        String name;
//        String organizer;
    }

    @SuppressWarnings("unused")
    private static class OResultsEvent {
        Integer id;
        String name;
//        String organizer;
    }

    /**
     * Convert upload endpoint {@link #oFeedUploadUrl} to get endpoint .
     *
     * @return https://api.orienteerfeed.com/rest/v1/events/eventId, where eventId = {@link #oFeedEventId}.
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    private @Nullable String getOFeedEventUrl() {
        if (!oFeedUploadUrl.endsWith("/upload/iof")) return null;
        int i = oFeedUploadUrl.lastIndexOf("/upload/iof");
        return oFeedUploadUrl.substring(0, i) + "/events/" + oFeedEventId;
    }

    // *********************************************************************************************
    // Notification to tell user that this service is active in the foreground.
    // *********************************************************************************************
    private static final String NOTIFICATION_CHANNEL_ID = "ResultsServiceNotificationChannelId";

    /**
     * Requires android:launchMode="singleTop" in the manifest.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_title), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_text));

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1001, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.results_service_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
