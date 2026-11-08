package com.example.agrismart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.AppHelper;
import com.example.agrismart.R;
import com.example.agrismart.databinding.ItemWorkerBinding;
import com.example.agrismart.model.Worker;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Adaptateur de la liste des ouvriers (présence du jour + salaire).
 */
public class WorkerAdapter extends RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder> {

    public interface Listener {
        void onWorkerClick(Worker worker);       // modifier
        void onPresenceClick(Worker worker);     // présent / absent aujourd'hui
        void onPhoneClick(Worker worker);        // appeler
    }

    private final List<Worker> workers = new ArrayList<>();
    private final Listener listener;

    public WorkerAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setWorkers(List<Worker> newWorkers) {
        workers.clear();
        workers.addAll(newWorkers);
        notifyDataSetChanged();
    }

    /** Nombre de jours de présence ce mois-ci (les clés commencent par "2026-11"). */
    public static int daysThisMonth(Worker worker) {
        String monthPrefix = AppHelper.dayKey(new Date()).substring(0, 7);
        int days = 0;
        if (worker.getPresences() != null) {
            for (String day : worker.getPresences()) {
                if (day.startsWith(monthPrefix)) {
                    days++;
                }
            }
        }
        return days;
    }

    /** Vrai si l'ouvrier est marqué présent aujourd'hui. */
    public static boolean isPresentToday(Worker worker) {
        return worker.getPresences() != null && worker.getPresences().contains(AppHelper.dayKey(new Date()));
    }

    @NonNull
    @Override
    public WorkerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new WorkerViewHolder(ItemWorkerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WorkerViewHolder holder, int position) {
        Worker worker = workers.get(position);
        Context context = holder.itemView.getContext();

        holder.binding.tvInitials.setText(AppHelper.initials(worker.getName()));
        holder.binding.tvName.setText(worker.getName());
        boolean hasPhone = worker.getPhone() != null && !worker.getPhone().isEmpty();
        holder.binding.tvPhone.setText(hasPhone ? worker.getPhone() : context.getString(R.string.workers_no_phone));
        holder.binding.tvSalary.setText(context.getString(R.string.workers_salary_line,
                AppHelper.formatNumber(worker.getDailySalary()), daysThisMonth(worker)));

        boolean present = isPresentToday(worker);
        holder.binding.tvPresence.setText(present ? R.string.workers_present : R.string.workers_absent);
        AppHelper.setBadgeColors(context, holder.binding.tvPresence,
                present ? R.color.green_primary : R.color.red,
                present ? R.color.green_soft : R.color.red_soft);

        holder.itemView.setOnClickListener(v -> listener.onWorkerClick(worker));
        holder.binding.tvPresence.setOnClickListener(v -> listener.onPresenceClick(worker));
        holder.binding.tvPhone.setOnClickListener(v -> listener.onPhoneClick(worker));
    }

    @Override
    public int getItemCount() {
        return workers.size();
    }

    static class WorkerViewHolder extends RecyclerView.ViewHolder {
        final ItemWorkerBinding binding;

        WorkerViewHolder(ItemWorkerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
