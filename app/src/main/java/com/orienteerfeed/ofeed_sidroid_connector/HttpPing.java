package com.orienteerfeed.ofeed_sidroid_connector;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Simple HTTP ping utility.
 */
class HttpPing {
    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************

    /**
     * Callback for ping result.
     */
    interface HttpPingListener {
        void onResponse(boolean isReachable);
    }

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    private final HttpPingListener listener;
    private final OkHttpClient httpClient;
    private final Request pingRequest;

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Simple HTTP ping utility.
     */
    HttpPing(String url, String userAgent, @NonNull HttpPingListener listener) {
        this.listener = listener;
        httpClient = new OkHttpClient();
        pingRequest = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build();
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    /**
     * Send a ping to given URL.
     */
    void ping() {
        httpClient.newCall(pingRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                listener.onResponse(false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                listener.onResponse(response.isSuccessful());
                response.close();
            }
        });
    }
}
