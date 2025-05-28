package com.orienteerfeed.ofeed_sidroid_connector;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * A log implemented as a circular buffer. Threadsafe.
 */
public class CircularLog {

    // *********************************************************************************************
    // Helper class, which stores one log item.
    // *********************************************************************************************
    static class LogItem {
        /**
         * The logged item.
         */
        final String text;
        /**
         * Local time hh:mm:ss when this log item was created.
         */
        final String time;

        private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);

        /**
         * Helper class, which stores one log item.
         *
         * @param text    Item to be logged.
         */
        private LogItem(String text) {
            this.text = text;
            time = LocalTime.now().format(HH_MM_SS);
        }
    }
    // *********************************************************************************************
    // Fields.
    // *********************************************************************************************

    private final LogItem[] log;
    private int logIndex, logCount;
    private boolean wrapAround;

    private final Object[] lockBuffer = {};

    // *********************************************************************************************
    // Constructor.
    // *********************************************************************************************

    /**
     * A log implemented as a circular buffer. Threadsafe.
     *
     * @param capacity Max number of items the log can hold.
     * @noinspection SameParameterValue
     */
    CircularLog(int capacity) {
        log = new LogItem[capacity];
        clear();
    }

    // *********************************************************************************************
    // Methods.
    // *********************************************************************************************

    @NonNull
    @Override
    public String toString() {
        ArrayList<LogItem> log = get();
        if (log == null) return "";
        StringBuilder sb = new StringBuilder();
        for (LogItem item : log) {
            sb.append(item.time).append(" ").append(item.text).append("\n");
        }
        return sb.delete(sb.length() - 1, sb.length()).toString();
    }

    /**
     * Add item to log. The item will be timestamped.
     * If the logs capacity has been reached, this item will replace the oldest item of the log.
     * @param item    Item to be logged.
     */
    void add(String item) {
        synchronized (lockBuffer) {
            logCount++;
            if (++logIndex >= log.length) {
                logIndex = 0;
                wrapAround = true;
            }
            log[logIndex] = new LogItem(item);
        }
    }

    /**
     * Clear log. All items of the log will be removed.
     */
    void clear() {
        synchronized (lockBuffer) {
            Arrays.fill(log, null);
            logIndex = -1;
            logCount = 0;
            wrapAround = false;
        }
    }

    /**
     * Get all items from log.
     *
     * @return The logged items. Oldest item first (index 0), last added item last.
     * Return null if log is empty.
     */
    @Nullable ArrayList<LogItem> get() {
        synchronized (lockBuffer) {
            if (logIndex == -1) return null;
            ArrayList<LogItem> logItems = new ArrayList<>(wrapAround ? log.length + 1 : logCount);
            // Retrieve items.
            if (!wrapAround) {
                logItems.addAll(Arrays.asList(log).subList(0, logIndex + 1));
            } else if (logIndex == log.length - 1) {
                Collections.addAll(logItems, log);
            } else {
                logItems.addAll(Arrays.asList(log).subList(logIndex + 1, log.length));
                logItems.addAll(Arrays.asList(log).subList(0, logIndex + 1));
            }
            // Add info that log items have been skipped.
            if (wrapAround) {
                logItems.add(0, new LogItem("...")); // Add first, order will be reversed below.
            }
            Collections.reverse(logItems);
            return logItems;
        }
    }
}
