package com.orienteerfeed.ofeed_sidroid_connector;

import android.os.Handler;
import android.os.Looper;

class SimpleTimer {

    //**********************************************************************************************
    // Interface.
    //**********************************************************************************************
    interface TimerListener {
        /** Callback on timeout. */
        void onTimeout();
    }

    //**********************************************************************************************
    // Member fields.
    //**********************************************************************************************
    private final int timerDelayMs;
    private final Handler handler;
    private final Runnable runnable;

    //**********************************************************************************************
    // Constructor.
    //**********************************************************************************************
    /**
     * Create a timer with given delay.
     * @param timerDelayMs  Timer delay (ms).
     * @param timerListener Callback on timeout.
     */
    SimpleTimer(@SuppressWarnings("SameParameterValue") int timerDelayMs, final TimerListener timerListener) {
        this.timerDelayMs = timerDelayMs;
        handler = new Handler(Looper.getMainLooper());
        runnable = timerListener :: onTimeout;
    }

    //**********************************************************************************************
    // Methods.
    //**********************************************************************************************

    /**
     * Start the timer, using timer delay set in the constructor.
     */
    void startTimer() {
        handler.postDelayed(runnable, timerDelayMs);
    }

    /**
     * Stop the timer.
     */
    void stopTimer() {
        handler.removeCallbacks(runnable);
    }
}
