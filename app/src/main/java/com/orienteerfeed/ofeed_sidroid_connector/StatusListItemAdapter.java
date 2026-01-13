package com.orienteerfeed.ofeed_sidroid_connector;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for status list.
 */
public class StatusListItemAdapter extends RecyclerView.Adapter<StatusListItemAdapter.ViewHolder> {

    private final List<StatusListItem> items;

    /**
     * Adapter for status list.
     *
     * @param items Items to be displayed.
     */
    StatusListItemAdapter(List<StatusListItem> items) {
        this.items = items;
    }

    /**
     * Add a new item at position 0 of the status list.
     *
     * @param item Item to be displayed at first position.
     */
    void addListItemAtTop(StatusListItem item) {
        items.add(0, item);
        notifyItemInserted(0);
        if (items.size() > 1) notifyItemChanged(1); // Previous first item is not bold any longer.
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.status_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StatusListItem item = items.get(position);
        holder.status.setText(item.getStatus());
        if (position == 0) holder.status.setTypeface(null, Typeface.BOLD);
        if (position >= 1) holder.status.setTypeface(null, Typeface.NORMAL);
        holder.status.setCompoundDrawablesWithIntrinsicBounds(item.getIconResId(), 0, 0, 0);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView status;

        ViewHolder(View itemView) {
            super(itemView);
            status = itemView.findViewById(R.id.status_list_item);
        }
    }
}
