package com.orienteerfeed.ofeed_sidroid_connector;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

class AboutDialog {

    private final Activity activity;
    private TextSwitcher textSwitcher;
    private String[] contributors;
    private int contributorsIndex = 0;
    private final Handler contributorsHandler;
    private static final int TIMER_DELAY_MS = 2_000;

    AboutDialog(Activity activity) {
        this.activity = activity;

        contributorsHandler = new Handler(Looper.getMainLooper());
        contributorsHandler.postDelayed(updateContributor, TIMER_DELAY_MS);
    }

    void show() {
        View layout = activity.getLayoutInflater().inflate(R.layout.about_dialog, null);
        String version = activity.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME;
        ((TextView) layout.findViewById(R.id.about_version)).setText(version);

        // Animate contributors.
        contributors = activity.getResources().getStringArray(R.array.about_contributors);
        textSwitcher = layout.findViewById(R.id.about_contributors);
        textSwitcher.setFactory(() -> {
            TextView tvContributor = new TextView(activity);
            tvContributor.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            tvContributor.setTextAppearance(android.R.style.TextAppearance_Medium);
            return tvContributor;
        });
        Animation animIn = AnimationUtils.loadAnimation(activity, android.R.anim.slide_in_left);
        Animation animOut = AnimationUtils.loadAnimation(activity, android.R.anim.slide_out_right);
        animIn.setDuration(500L);
        animOut.setDuration(500L);
        textSwitcher.setInAnimation(animIn);
        textSwitcher.setOutAnimation(animOut);
        textSwitcher.setText(contributors[contributorsIndex]);

        new MaterialAlertDialogBuilder(activity)
                .setView(layout)
                .setTitle(activity.getString(R.string.about))
                .setPositiveButton(android.R.string.ok, (d, which)
                        -> contributorsHandler.removeCallbacks(updateContributor))
                .setCancelable(false)
                .create().show();
    }

    private final Runnable updateContributor = new Runnable() {
        @Override
        public void run() {
            if (++contributorsIndex == contributors.length) contributorsIndex = 0;
            textSwitcher.setText(contributors[contributorsIndex]);
            contributorsHandler.postDelayed(updateContributor, TIMER_DELAY_MS);
        }
    };
}
