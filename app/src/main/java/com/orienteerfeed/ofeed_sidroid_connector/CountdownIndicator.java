package com.orienteerfeed.ofeed_sidroid_connector;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.MainThread;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

/**
 * A circular progress count down indicator with an associated text.
 */
final class CountdownIndicator {

    // ********************************************************************************************
    // Fields.
    // ********************************************************************************************
    private final CircularProgressIndicator indicator;
    private final TextView indicatorText;

    private ValueAnimator animator;
    private long endUptimeMs;
    private long lastShownSec = -1;

    // ********************************************************************************************
    // Constructor.
    // ********************************************************************************************

    /**
     * A circular progress count down indicator with an associated text.
     */
    CountdownIndicator(CircularProgressIndicator indicator, TextView indicatorText) {
        this.indicator = indicator;
        this.indicatorText = indicatorText;
        indicator.setIndeterminate(false);
    }

    // ********************************************************************************************
    // Methods.
    // ********************************************************************************************

    /**
     * Start countdown and show countdown text.
     */
    @MainThread
    void start(int durationSec) {
        int durationMs = 1000 * durationSec;

        this.endUptimeMs = SystemClock.uptimeMillis() + durationMs;
        this.lastShownSec = -1;

        indicator.setMax(durationMs);
        indicator.setProgressCompat(durationMs, false);

        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(a -> {
            int remaining = Math.max(0, (int) (endUptimeMs - SystemClock.uptimeMillis()));
            updateUi(remaining);
        });

        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                updateUi(0);
            }
        });

        animator.start();
    }

    /**
     * Stop countdown and hide countdown text.
     */
    @MainThread
    void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    /**
     * Returns whether this countdown animation is running.
     */
    @MainThread
    boolean isRunning() {
        if (animator == null) return false;
        return animator.isRunning();
    }

    private void updateUi(int remainingMs) {
        indicator.setProgressCompat(remainingMs, false);

        // Text updates once per second.
        int sec = (remainingMs + 999) / 1000; // Round up.
        if (sec != lastShownSec) {
            lastShownSec = sec;
            indicatorText.setText(formatSeconds(sec));
            indicator.setContentDescription("Time remaining " + (sec == 1 ? "1 second" : sec + " seconds"));
        }
    }

    private static String formatSeconds(int sec) {
        if (sec >= 60) {
            int m = sec / 60;
            int s = sec - 60 * m;
            return String.format(Locale.US, "%d:%02d", m, s);
        }
        return String.valueOf(sec);
    }
}
