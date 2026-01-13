package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.ORESULTS_URL;
import static com.orienteerfeed.ofeed_sidroid_connector.ResultsService.XML_IDS_FILENAME;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.base64EncodeToString;
import static com.orienteerfeed.ofeed_sidroid_connector.Util.readTextFile;

import android.app.Activity;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 * Upload XML results list from local file storage.
 */
class LocalXmlFileUploader {
    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************

    /**
     * Callback for upload of XML results list from local file storage.
     */
    interface LocalXmlFileUploaderListener {
        /**
         * Callback for upload of XML results list from local file storage.
         *
         * @param isUploaded True if the upload was successful, else false.
         * @param message    Null if uploaded successfully, an error message if upload failed.
         */
        void onResponse(boolean isUploaded, String message);
    }

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    private final Activity activity;
    private final String userAgent;
    private final LocalXmlFileUploaderListener listener;

    // OFeed parameters.
    private String OFeedUrl, eventId, authorization;
    // OResults parameters.
    private String apiKey;

    private XmlIds xmlIds;
    private final OkHttpClient client;
    private static final MediaType XML_MEDIA_TYPE = MediaType.parse("text/xml; charset=utf-8");

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Upload XML results list from local file storage to OFeed.
     */
    LocalXmlFileUploader(Activity activity, String userAgent, @NonNull LocalXmlFileUploaderListener listener) {
        this.activity = activity;
        this.userAgent = userAgent;
        this.listener = listener;

        client = new OkHttpClient();

        xmlIds = SerializableManager.load(activity, XML_IDS_FILENAME);
        if (xmlIds == null) {
            xmlIds = new XmlIds();
        }
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    /**
     * Set parameters for uploading to OFeed.
     */
    void setOFeedParams(String url, String eventId, String password) {
        this.OFeedUrl = url;
        this.eventId = eventId;
        authorization = "Basic " + base64EncodeToString(eventId + ":" + password);
    }

    /**
     * Set parameters for uploading to OResults.
     */
    void setOResultsParams(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Upload XML results list from local storage to OFeed.
     */
    void uploadToOFeed(Uri localXmlFile, boolean createXmlIds) {
        new Thread(() -> {
            String xmlUpload;
            try {
                xmlUpload = readTextFile(activity, localXmlFile);
                if (createXmlIds) {
                    xmlUpload = XmlModifier.updateOrInsertXmlId(xmlUpload, xmlIds);
                }
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null) message = "Failed to create upload file";
                listener.onResponse(false, message);
                return;
            }

            RequestBody xmlRequestBody = RequestBody.create(xmlUpload, XML_MEDIA_TYPE);
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("eventId", eventId)
                    .addFormDataPart("file", "result-list-iof-3.0.xml", xmlRequestBody)
                    .build();

            Request request = new Request.Builder()
                    .url(OFeedUrl)
                    .addHeader("User-Agent", userAgent)
                    .addHeader("Authorization", authorization)
                    .addHeader("Content-Type", "text; charset=utf-8")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    String message = e.getMessage();
                    if (message == null) message = "I/O exception";
                    listener.onResponse(false, message);
                    saveXmlIds();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.isSuccessful()) {
                        listener.onResponse(true, null);
                    } else {
                        // Unsuccessful response.
                        String message = HttpStatusCodes.getMeaning(response.code());
                        listener.onResponse(false, message);
                    }
                    saveXmlIds();
                }
            });
        }).start();
    }

    /**
     * Upload XML results list from local storage to OResults.
     */
    void uploadToOResults(Uri localXmlFile, boolean createXmlIds) {
        new Thread(() -> {
            String xmlUpload;
            try {
                xmlUpload = readTextFile(activity, localXmlFile);
                if (createXmlIds) {
                    xmlUpload = XmlModifier.updateOrInsertXmlId(xmlUpload, xmlIds);
                }
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null) message = "Failed to create upload file";
                listener.onResponse(false, message);
                return;
            }

            RequestBody xmlRequestBody = RequestBody.create(xmlUpload, XML_MEDIA_TYPE);
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("apiKey", apiKey)
                    .addFormDataPart("file", "result-list-iof-3.0.xml", xmlRequestBody)
                    .build();

            Request request = new Request.Builder()
                    .url(ORESULTS_URL)
                    .addHeader("User-Agent", userAgent)
//                    .addHeader("Content-Type", "text; charset=utf-8")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    String message = e.getMessage();
                    if (message == null) message = "I/O exception";
                    listener.onResponse(false, message);
                    saveXmlIds();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.isSuccessful()) {
                        listener.onResponse(true, null);
                    } else {
                        // Unsuccessful response.
                        String message = HttpStatusCodes.getMeaning(response.code());
                        listener.onResponse(false, message);
                    }
                    saveXmlIds();
                }
            });
        }).start();
    }

    private void saveXmlIds() {
        if (!xmlIds.isEmpty()) {
            SerializableManager.save(activity, xmlIds, XML_IDS_FILENAME);
        } else {
            SerializableManager.delete(activity, XML_IDS_FILENAME);
        }
    }
}
