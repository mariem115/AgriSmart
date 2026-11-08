package com.example.agrismart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.AppHelper;
import com.example.agrismart.R;
import com.example.agrismart.databinding.ItemTaskBinding;
import com.example.agrismart.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptateur de la liste des tâches.
 * En mode ouvrier, chaque tâche a ses boutons (Commencer / Terminée et Signaler).
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface Listener {
        void onTaskClick(Task task, View anchor); // agriculteur : changer le statut / supprimer
        void onPrimaryClick(Task task);           // ouvrier : commencer ou terminer
        void onReportClick(Task task);            // ouvrier : signaler un problème
    }

    private final List<Task> tasks = new ArrayList<>();
    private final boolean workerMode;
    private final Listener listener;

    public TaskAdapter(boolean workerMode, Listener listener) {
        this.workerMode = workerMode;
        this.listener = listener;
    }

    public void setTasks(List<Task> newTasks) {
        tasks.clear();
        tasks.addAll(newTasks);
        notifyDataSetChanged();
    }

    /** Libellé + couleurs de la pastille de statut. */
    public static void showStatus(Context context, String status, android.widget.TextView badge) {
        if (Task.STATUS_DONE.equals(status)) {
            badge.setText(R.string.task_status_done);
            AppHelper.setBadgeColors(context, badge, R.color.green_primary, R.color.green_soft);
        } else if (Task.STATUS_IN_PROGRESS.equals(status)) {
            badge.setText(R.string.task_status_in_progress);
            AppHelper.setBadgeColors(context, badge, R.color.blue, R.color.blue_soft);
        } else {
            badge.setText(R.string.task_status_todo);
            AppHelper.setBadgeColors(context, badge, R.color.orange, R.color.yellow_soft);
        }
    }

    /** Échéance lisible : "17:00" aujourd'hui, "Demain", sinon "mer. 11". */
    public static String dueLabel(Context context, Date due) {
        if (due == null) {
            return "";
        }
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (AppHelper.sameDay(due, new Date())) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(due);
        } else if (AppHelper.sameDay(due, tomorrow.getTime())) {
            return context.getString(R.string.tomorrow);
        }
        return new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(due);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TaskViewHolder(ItemTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        Context context = holder.itemView.getContext();

        holder.binding.tvTitle.setText(task.getTitle());
        holder.binding.tvParcel.setText(task.getParcelName());
        showStatus(context, task.getStatus(), holder.binding.tvStatus);

        // L'agriculteur voit à qui est la tâche ; l'ouvrier voit seulement l'échéance
        String due = dueLabel(context, task.getDueDate());
        if (workerMode) {
            holder.binding.tvInitials.setVisibility(View.GONE);
            holder.binding.tvAssigneeDue.setText(due);
        } else {
            holder.binding.tvInitials.setVisibility(View.VISIBLE);
            holder.binding.tvInitials.setText(AppHelper.initials(task.getAssignedName()));
            holder.binding.tvAssigneeDue.setText(context.getString(R.string.tasks_assignee_due,
                    String.valueOf(task.getAssignedName()), due));
        }

        boolean hasProblem = task.getProblemNote() != null && !task.getProblemNote().isEmpty();
        holder.binding.tvProblem.setVisibility(hasProblem ? View.VISIBLE : View.GONE);
        holder.binding.tvProblem.setText(task.getProblemNote());

        // Boutons de l'ouvrier (pas pour une tâche terminée)
        boolean showActions = workerMode && !Task.STATUS_DONE.equals(task.getStatus());
        holder.binding.layoutWorkerActions.setVisibility(showActions ? View.VISIBLE : View.GONE);
        if (Task.STATUS_TODO.equals(task.getStatus())) {
            holder.binding.btnPrimary.setText(R.string.tasks_start);
            holder.binding.btnPrimary.setIconResource(R.drawable.ic_play);
        } else {
            holder.binding.btnPrimary.setText(R.string.tasks_finish);
            holder.binding.btnPrimary.setIconResource(R.drawable.ic_check);
        }

        holder.binding.btnPrimary.setOnClickListener(v -> listener.onPrimaryClick(task));
        holder.binding.btnReport.setOnClickListener(v -> listener.onReportClick(task));
        holder.itemView.setOnClickListener(v -> listener.onTaskClick(task, v));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final ItemTaskBinding binding;

        TaskViewHolder(ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
