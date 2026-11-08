package com.example.agrismart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.AppHelper;
import com.example.agrismart.R;
import com.example.agrismart.databinding.ItemHarvestBinding;
import com.example.agrismart.model.Harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur de la liste des dernières récoltes.
 */
public class HarvestAdapter extends RecyclerView.Adapter<HarvestAdapter.HarvestViewHolder> {

    public interface OnHarvestClickListener {
        void onHarvestClick(Harvest harvest);
    }

    private final List<Harvest> harvests = new ArrayList<>();
    private final OnHarvestClickListener listener;

    public HarvestAdapter(OnHarvestClickListener listener) {
        this.listener = listener;
    }

    public void setHarvests(List<Harvest> newHarvests) {
        harvests.clear();
        harvests.addAll(newHarvests);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HarvestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HarvestViewHolder(ItemHarvestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HarvestViewHolder holder, int position) {
        Harvest harvest = harvests.get(position);
        Context context = holder.itemView.getContext();

        holder.binding.tvCrop.setText(AppHelper.labelOf(context, R.array.crop_codes,
                R.array.crop_labels, harvest.getCrop()));
        holder.binding.tvDetails.setText(context.getString(R.string.harvest_details,
                String.valueOf(harvest.getParcelName()), AppHelper.formatDate(harvest.getDate()),
                harvest.getSeason()));
        holder.binding.tvQuantity.setText(context.getString(R.string.quantity_unit,
                AppHelper.formatMoney(harvest.getQuantityKg()), "kg"));
        holder.itemView.setOnClickListener(v -> listener.onHarvestClick(harvest));
    }

    @Override
    public int getItemCount() {
        return harvests.size();
    }

    static class HarvestViewHolder extends RecyclerView.ViewHolder {
        final ItemHarvestBinding binding;

        HarvestViewHolder(ItemHarvestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
