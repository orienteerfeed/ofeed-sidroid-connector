package com.orienteerfeed.ofeed_sidroid_connector;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

/**
 * Detect when the user stops typing or leaves the given view by attaching
 * a TextChangedListener and an OnFocusChangeListener to the view.
 */
final class TextChangeWatcher implements TextWatcher, View.OnFocusChangeListener {

    // *********************************************************************************************
    // Interface.
    // *********************************************************************************************
    interface TextChangeWatcherListener {
        void onTextChanged(@NonNull String text);
    }

    // *********************************************************************************************
    // Fields.
    // *********************************************************************************************
    private static final long TIMEOUT_MS = 800;
    private final EditText editText;
    private final TextChangeWatcherListener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fireRunnable = new Runnable() {
        @Override
        public void run() {
            listener.onTextChanged(editText.getText().toString());
        }
    };

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * Detect when the user stops typing or leaves the given view by attaching
     * a TextChangedListener and an OnFocusChangeListener to the view.
     * Call {@link #detach()} when done.
     *
     * @param editText  EditText to observe.
     * @param listener  Callback, fires when timeout expires or when focus is lost.
     */
    TextChangeWatcher(@NonNull EditText editText, @NonNull TextChangeWatcherListener listener) {
        this.editText = editText;
        this.listener = listener;
        editText.addTextChangedListener(this);
        editText.setOnFocusChangeListener(this);
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************
    void detach() {
        editText.removeTextChangedListener(this);
        if (editText.getOnFocusChangeListener() == this) editText.setOnFocusChangeListener(null);
        handler.removeCallbacks(fireRunnable);
    }

    // *********************************************************************************************
    // Text watcher.
    // *********************************************************************************************
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        handler.removeCallbacks(fireRunnable);          // Reset timer on every keystroke/change.
        handler.postDelayed(fireRunnable, TIMEOUT_MS);
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    // *********************************************************************************************
    // On focus changed listener.
    // *********************************************************************************************
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            handler.removeCallbacks(fireRunnable);
            listener.onTextChanged(editText.getText().toString());
        }
    }
}