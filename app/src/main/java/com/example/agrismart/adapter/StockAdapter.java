package com.example.agrismart.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.AppHelper;
import com.example.agrismart.R;
import com.example.agrismart.databinding.ItemStockBinding;
import com.example.agrismart.model.StockItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur de la liste du stock : quantité, seuil minimum et état (OK / à surveiller / stock bas).
 */
public class StockAdapter extends RecyclerView.Adapter<StockAdapter.StockViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(StockItem item);
    }

    private final List<StockItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public StockAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<StockItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Règle simple de l'alerte de rupture : quantité sous le seuil minimum fixe. */
    public static boolean isLow(StockItem item) {
        return item.getQuantity() < item.getMinThreshold();
    }

    @NonNull
    @Override
    public StockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new StockViewHolder(ItemStockBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull StockViewHolder holder, int position) {
        StockItem item = items.get(position);
        Context context = holder.itemView.getContext();

        holder.binding.tvName.setText(item.getName());
        holder.binding.tvThreshold.setText(context.getString(R.string.stock_threshold_line,
                AppHelper.formatNumber(item.getMinThreshold()), item.getUnit()));
        holder.binding.tvQuantity.setText(context.getString(R.string.quantity_unit,
                AppHelper.formatNumber(item.getQuantity()), item.getUnit()));

        // Couleur selon le niveau : rouge = sous le seuil, orange = proche (< 1,5 x seuil), vert = OK
        int strong;
        int soft;
        int label;
        if (isLow(item)) {
            strong = R.color.red;
            soft = R.color.red_soft;
            label = R.string.stock_low;
        } else if (item.getQuantity() < item.getMinThreshold() * 1.5) {
            strong = R.color.orange;
            soft = R.color.yellow_soft;
            label = R.string.stock_watch;
        } else {
            strong = R.color.green_primary;
            soft = R.color.green_soft;
            label = R.string.stock_ok;
        }
        int strongColor = ContextCompat.getColor(context, strong);
        int softColor = ContextCompat.getColor(context, soft);

        holder.binding.tvBadge.setText(label);
        AppHelper.setBadgeColors(context, holder.binding.tvBadge, strong, soft);
        holder.binding.tvQuantity.setTextColor(strongColor);
        holder.binding.ivIcon.setBackgroundTintList(ColorStateList.valueOf(softColor));
        holder.binding.ivIcon.setImageTintList(ColorStateList.valueOf(strongColor));

        // Barre : 100 % = 3 fois le seuil minimum
        double full = Math.max(item.getMinThreshold() * 3, 1);
        int percent = (int) Math.min(100, item.getQuantity() / full * 100);
        holder.binding.progress.setProgressCompat(percent, false);
        holder.binding.progress.setIndicatorColor(strongColor);
        holder.binding.progress.setTrackColor(ContextCompat.getColor(context, R.color.divider));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StockViewHolder extends RecyclerView.ViewHolder {
        final ItemStockBinding binding;

        StockViewHolder(ItemStockBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
