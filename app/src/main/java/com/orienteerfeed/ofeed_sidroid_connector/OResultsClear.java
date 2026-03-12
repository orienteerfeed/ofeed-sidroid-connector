package com.orienteerfeed.ofeed_sidroid_connector;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Delete all competitors of this event in OResults.
 */
class OResultsClear {
    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************

    /**
     * Callback for deletion of competitors in OResults.
     */
    interface OResultsClearListener {
        /**
         * Callback for deletion of competitors in OResults.
         *
         * @param isCleared True if deleted successfully, false if deletion failed.
         * @param message   Empty string if deleted successfully, an error message if deletion failed.
         */
        void onResponse(boolean isCleared, @NonNull String message);
    }

    // *********************************************************************************************
    // Member fields.
    // *********************************************************************************************
    private final OResultsClearListener listener;
    private final OkHttpClient client;
    private final Request deleteRequest;

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Delete all competitors of this event in OResults.
     */
    OResultsClear(String deleteEndpoint, String userAgent,
               @NonNull OResultsClearListener listener) {
        this.listener = listener;
        client = new OkHttpClient();
        deleteRequest = new Request.Builder()
                .url(deleteEndpoint)
                .header("User-Agent", userAgent)
                .delete()
                .build();
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    /**
     * Delete all competitors of this event in OResults.
     */
    void delete() {
        client.newCall(deleteRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String message = e.getMessage() != null ? e.getMessage() : "I/O exception.";
                listener.onResponse(false, message);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                boolean success = response.isSuccessful();
                String message = "";
                if (!success) {
                    message = HttpStatusCodes.getMeaning(response.code());
                }
                listener.onResponse(success, message);
                response.close();
            }
        });
    }
}
