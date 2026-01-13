package com.orienteerfeed.ofeed_sidroid_connector;

/**
 * An item on the status list.
 */
class StatusListItem {
    private final String status;
    private final int iconResId;

    /**
     * An item on the status list.
     *
     * @param status    Status of upload.
     * @param iconResId Status icon resource id.
     */
    StatusListItem(String status, int iconResId) {
        this.status = status;
        this.iconResId = iconResId;
    }

    String getStatus() {
        return status;
    }

    int getIconResId() {
        return iconResId;
    }
}
