package com.orienteerfeed.ofeed_sidroid_connector;

import static com.orienteerfeed.ofeed_sidroid_connector.Util.extractUrl;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Handle a shared deep link.
 * This activity extracts the shared link from the intent,
 * launches the main activity and then kills itself.
 */
public final class OFeedShareLinkReceiver extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the shared text from the intent.
        Intent intent = getIntent();
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (cs != null) text = cs.toString();
            if (text == null) {
                Toast.makeText(this, R.string.ofeed_link_invalid, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }

        // Find URL within the text.
        String url = extractUrl(text);

        // Launch main activity.
        if (url != null) {
            Intent i = new Intent(this, MainActivity.class)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else {
            Toast.makeText(this, R.string.ofeed_link_invalid, Toast.LENGTH_LONG).show();
        }

        finish();
    }
}



