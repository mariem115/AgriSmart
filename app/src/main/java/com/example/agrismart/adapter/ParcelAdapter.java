package com.example.agrismart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.AppHelper;
import com.example.agrismart.R;
import com.example.agrismart.databinding.ItemParcelBinding;
import com.example.agrismart.model.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur de la liste des parcelles (une carte par parcelle).
 */
public class ParcelAdapter extends RecyclerView.Adapter<ParcelAdapter.ParcelViewHolder> {

    /** Action quand on touche une parcelle. */
    public interface OnParcelClickListener {
        void onParcelClick(Parcel parcel);
    }

    private final List<Parcel> parcels = new ArrayList<>();
    private final OnParcelClickListener listener;

    public ParcelAdapter(OnParcelClickListener listener) {
        this.listener = listener;
    }

    /** Remplace la liste affichée. */
    public void setParcels(List<Parcel> newParcels) {
        parcels.clear();
        parcels.addAll(newParcels);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ParcelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemParcelBinding binding = ItemParcelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ParcelViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ParcelViewHolder holder, int position) {
        Parcel parcel = parcels.get(position);
        Context context = holder.itemView.getContext();

        String crop = AppHelper.labelOf(context, R.array.crop_codes, R.array.crop_labels, parcel.getCrop());
        String soil = AppHelper.labelOf(context, R.array.soil_codes, R.array.soil_labels, parcel.getSoilType());

        holder.binding.tvName.setText(parcel.getName());
        holder.binding.tvSubtitle.setText(context.getString(R.string.parcel_subtitle,
                crop, AppHelper.formatNumber(parcel.getArea()), soil));
        AppHelper.showHealth(context, parcel.getHealthStatus(),
                holder.binding.tvHealth, holder.binding.ivIcon);

        holder.itemView.setOnClickListener(v -> listener.onParcelClick(parcel));
    }

    @Override
    public int getItemCount() {
        return parcels.size();
    }

    static class ParcelViewHolder extends RecyclerView.ViewHolder {
        final ItemParcelBinding binding;

        ParcelViewHolder(ItemParcelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
