package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Preferences.ORESULTS_GET_EVENT_URL;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Get event name from server for OFeed/OResults.
 */
class GetEventName {

    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************

    /**
     * Callback for event name.
     */
    interface GetEventNameListener {
        /**
         * Callback for OFeed event name.
         */
        void onOFeedEventName(@NonNull String eventName);

        /**
         * Callback for OResults event name.
         */
        void onOResultsEventName(@NonNull String eventName, int eventId);
    }

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    private final String userAgent;
    private final GetEventNameListener listener;
    private final OkHttpClient client;

    private Call currentCall = null;

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Get event name from server for OFeed/OResults.
     */
    GetEventName(String userAgent, @NonNull GetEventNameListener listener) {
        this.userAgent = userAgent;
        this.listener = listener;
        client = new OkHttpClient();
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    /**
     * Get event name from OFeed server.
     */
    void getOFeedEventName(String oFeedUrl) {
        Request oResultsRequest = new Request.Builder()
                .url(oFeedUrl)
                .header("User-Agent", userAgent)
                .get().build();
        currentCall = client.newCall(oResultsRequest);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return; // Ignore cancelled requests.
                listener.onOFeedEventName("");
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
                    listener.onOFeedEventName(name);
                } catch (IOException ignored) {
                }
            }
        });
    }

    /**
     * Get event name from OResults server.
     */
    void getOResultsEventName(String oResultsApiKey) {
        Request oResultsRequest = new Request.Builder()
                .url(ORESULTS_GET_EVENT_URL + oResultsApiKey)
                .header("User-Agent", userAgent)
                .get().build();
        currentCall = client.newCall(oResultsRequest);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return; // Ignore cancelled requests.
                listener.onOResultsEventName("", 0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    String name = "";
                    Integer id = 0;
                    if (r.isSuccessful()) {
                        Gson gson = new Gson();
                        OResultsEvent event = gson.fromJson(r.body().string(), OResultsEvent.class);
                        name = event.name;
                        if (name == null) name = "";
                        name = name.trim();
                        id = event.id;
                        if (id == null) id = 0;
                    }
                    listener.onOResultsEventName(name, id);
                } catch (IOException ignored) {
                }
            }
        });

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
    }

    /**
     * Cancel any ongoing call.
     */
    void cancelOngoingCall() {
        if (currentCall != null) currentCall.cancel();
    }
}
