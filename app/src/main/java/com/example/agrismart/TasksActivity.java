package com.example.agrismart;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupMenu;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.TaskAdapter;
import com.example.agrismart.databinding.ActivityTasksBinding;
import com.example.agrismart.databinding.DialogTaskBinding;
import com.example.agrismart.databinding.ItemDayBinding;
import com.example.agrismart.model.Parcel;
import com.example.agrismart.model.Task;
import com.example.agrismart.model.Worker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tâches.
 * - Agriculteur : voit toutes les tâches, en crée et les assigne, change leur statut.
 * - Ouvrier : voit SES tâches, les commence / termine, signale un problème.
 */
public class TasksActivity extends AppCompatActivity {

    // Identifiants des choix du menu de l'agriculteur
    private static final int MENU_TODO = 1;
    private static final int MENU_IN_PROGRESS = 2;
    private static final int MENU_DONE = 3;
    private static final int MENU_DELETE = 4;

    private ActivityTasksBinding binding;
    private FirebaseFirestore db;
    private TaskAdapter adapter;
    private boolean farmer;

    private final List<Task> allTasks = new ArrayList<>();
    private String statusFilter = Task.STATUS_TODO; // onglet choisi
    private Date dayFilter = null;                  // jour choisi dans la semaine (null = tous)

    private final List<Date> weekDays = new ArrayList<>();
    private final List<ItemDayBinding> dayViews = new ArrayList<>();

    // Valeurs choisies dans la boîte de dialogue "nouvelle tâche"
    private Parcel chosenParcel;
    private Worker chosenWorker;
    private Calendar chosenDue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTasksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();
        farmer = AppHelper.isFarmer(this);

        binding.tvTitle.setText(farmer ? R.string.tasks_title : R.string.tasks_my_title);
        binding.tvWorkerBadge.setVisibility(farmer ? View.GONE : View.VISIBLE);
        binding.fabAdd.setVisibility(farmer ? View.VISIBLE : View.GONE);
        binding.fabAdd.setOnClickListener(v -> prepareNewTask());

        String month = new SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(new Date());
        binding.tvMonth.setText(month.substring(0, 1).toUpperCase(Locale.getDefault()) + month.substring(1));

        buildWeek();
        setupTabs();

        adapter = new TaskAdapter(!farmer, new TaskAdapter.Listener() {
            @Override
            public void onTaskClick(Task task, View anchor) {
                if (farmer) {
                    showFarmerMenu(task, anchor);
                }
            }

            @Override
            public void onPrimaryClick(Task task) {
                // À faire -> En cours -> Terminée
                String next = Task.STATUS_TODO.equals(task.getStatus())
                        ? Task.STATUS_IN_PROGRESS : Task.STATUS_DONE;
                updateStatus(task, next);
            }

            @Override
            public void onReportClick(Task task) {
                showReportDialog(task);
            }
        });
        binding.recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTasks.setAdapter(adapter);

        listenToTasks();
        AppHelper.setupBottomNav(this, binding.bottomNav, R.id.nav_tasks);
    }

    // =====================================================================
    // Semaine (3 jours avant -> 3 jours après aujourd'hui)
    // =====================================================================

    private void buildWeek() {
        SimpleDateFormat dayName = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat dayNumber = new SimpleDateFormat("d", Locale.getDefault());

        for (int offset = -3; offset <= 3; offset++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, offset);
            Date day = cal.getTime();
            weekDays.add(day);

            ItemDayBinding dayView = ItemDayBinding.inflate(getLayoutInflater(), binding.layoutWeek, false);
            dayView.tvDayName.setText(dayName.format(day));
            dayView.tvDayNumber.setText(dayNumber.format(day));
            dayView.getRoot().setOnClickListener(v -> {
                // Un appui filtre sur ce jour, un second appui enlève le filtre
                dayFilter = AppHelper.sameDay(dayFilter, day) ? null : day;
                refreshWeek();
                applyFilters();
            });
            binding.layoutWeek.addView(dayView.getRoot());
            dayViews.add(dayView);
        }
        refreshWeek();
    }

    /** Jour sélectionné en vert, aujourd'hui en texte vert. */
    private void refreshWeek() {
        Date today = new Date();
        for (int i = 0; i < weekDays.size(); i++) {
            Date day = weekDays.get(i);
            ItemDayBinding dayView = dayViews.get(i);
            boolean selected = AppHelper.sameDay(day, dayFilter);
            boolean isToday = AppHelper.sameDay(day, today);

            int bg = selected ? R.color.green_primary : R.color.white;
            int text = selected ? R.color.white : (isToday ? R.color.green_primary : R.color.text_primary);
            dayView.getRoot().setBackgroundTintList(ContextCompat.getColorStateList(this, bg));
            dayView.tvDayName.setTextColor(ContextCompat.getColor(this, text));
            dayView.tvDayNumber.setTextColor(ContextCompat.getColor(this, text));
        }
    }

    // =====================================================================
    // Onglets À faire / En cours / Terminées
    // =====================================================================

    private void setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setTag(Task.STATUS_TODO));
        binding.tabs.addTab(binding.tabs.newTab().setTag(Task.STATUS_IN_PROGRESS));
        binding.tabs.addTab(binding.tabs.newTab().setTag(Task.STATUS_DONE));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                statusFilter = (String) tab.getTag();
                applyFilters();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
        updateTabTitles(0, 0, 0);
    }

    private void updateTabTitles(int todo, int inProgress, int done) {
        setTabText(0, getString(R.string.tasks_tab_todo, todo));
        setTabText(1, getString(R.string.tasks_tab_in_progress, inProgress));
        setTabText(2, getString(R.string.tasks_tab_done, done));
    }

    private void setTabText(int index, String text) {
        TabLayout.Tab tab = binding.tabs.getTabAt(index);
        if (tab != null) {
            tab.setText(text);
        }
    }

    // =====================================================================
    // Lecture des tâches
    // =====================================================================

    private void listenToTasks() {
        Query query = db.collection("tasks").whereEqualTo("farmId", AppHelper.getFarmId(this));
        if (!farmer) {
            // L'ouvrier ne lit que ses tâches (obligatoire avec les règles de sécurité)
            query = query.whereEqualTo("assignedTo", AppHelper.getUid(this));
        }
        query.addSnapshotListener(this, (snapshots, error) -> {
            if (error != null) {
                AppHelper.toast(this, AppHelper.errorMessage(this, error));
                return;
            }
            if (snapshots == null) {
                return;
            }
            allTasks.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                allTasks.add(doc.toObject(Task.class));
            }
            // Échéance la plus proche en premier
            allTasks.sort((a, b) -> Long.compare(time(a.getDueDate()), time(b.getDueDate())));
            applyFilters();
        });
    }

    private static long time(Date date) {
        return date == null ? Long.MAX_VALUE : date.getTime();
    }

    /** Filtre jour + onglet, puis met à jour la liste et les compteurs. */
    private void applyFilters() {
        int todo = 0;
        int inProgress = 0;
        int done = 0;
        List<Task> visible = new ArrayList<>();

        for (Task task : allTasks) {
            if (dayFilter != null && !AppHelper.sameDay(task.getDueDate(), dayFilter)) {
                continue;
            }
            if (Task.STATUS_DONE.equals(task.getStatus())) {
                done++;
            } else if (Task.STATUS_IN_PROGRESS.equals(task.getStatus())) {
                inProgress++;
            } else {
                todo++;
            }
            if (statusFilter.equals(task.getStatus())) {
                visible.add(task);
            }
        }
        updateTabTitles(todo, inProgress, done);
        adapter.setTasks(visible);
        binding.tvEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // Actions sur une tâche
    // =====================================================================

    private void updateStatus(Task task, String status) {
        db.collection("tasks").document(task.getTaskId()).update("status", status)
                .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
        if (Task.STATUS_DONE.equals(status)) {
            AppHelper.toast(this, getString(R.string.tasks_done_message));
        }
    }

    /** Agriculteur : menu pour changer le statut ou supprimer. */
    private void showFarmerMenu(Task task, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_TODO, 0, R.string.task_status_todo);
        menu.getMenu().add(0, MENU_IN_PROGRESS, 1, R.string.task_status_in_progress);
        menu.getMenu().add(0, MENU_DONE, 2, R.string.task_status_done);
        menu.getMenu().add(0, MENU_DELETE, 3, R.string.btn_delete);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_TODO) {
                updateStatus(task, Task.STATUS_TODO);
            } else if (id == MENU_IN_PROGRESS) {
                updateStatus(task, Task.STATUS_IN_PROGRESS);
            } else if (id == MENU_DONE) {
                updateStatus(task, Task.STATUS_DONE);
            } else if (id == MENU_DELETE) {
                db.collection("tasks").document(task.getTaskId()).delete()
                        .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            }
            return true;
        });
        menu.show();
    }

    /** Ouvrier : signaler un problème (texte libre). */
    private void showReportDialog(Task task) {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint(R.string.tasks_report_hint);
        input.setText(task.getProblemNote());
        FrameLayout container = new FrameLayout(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tasks_report)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_send, (d, w) -> {
                    String note = AppHelper.textOf(input);
                    db.collection("tasks").document(task.getTaskId()).update("problemNote", note)
                            .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
                    AppHelper.toast(this, getString(R.string.tasks_report_sent));
                })
                .show();
    }

    // =====================================================================
    // Agriculteur : nouvelle tâche
    // =====================================================================

    /** Charge parcelles et ouvriers, puis ouvre le formulaire. */
    private void prepareNewTask() {
        AppHelper.loadParcels(this, parcels -> {
            if (parcels.isEmpty()) {
                AppHelper.toast(this, getString(R.string.need_parcel_first));
                return;
            }
            db.collection("workers").whereEqualTo("farmId", AppHelper.getFarmId(this)).get()
                    .addOnSuccessListener(snapshots -> {
                        List<Worker> workers = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            workers.add(doc.toObject(Worker.class));
                        }
                        if (workers.isEmpty()) {
                            AppHelper.toast(this, getString(R.string.need_worker_first));
                            return;
                        }
                        showNewTaskDialog(parcels, workers);
                    })
                    .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
        });
    }

    private void showNewTaskDialog(List<Parcel> parcels, List<Worker> workers) {
        DialogTaskBinding form = DialogTaskBinding.inflate(getLayoutInflater());
        chosenParcel = null;
        chosenWorker = null;

        // Échéance par défaut : aujourd'hui à 17:00
        chosenDue = Calendar.getInstance();
        chosenDue.set(Calendar.HOUR_OF_DAY, 17);
        chosenDue.set(Calendar.MINUTE, 0);
        showDue(form);

        form.dropParcel.setSimpleItems(AppHelper.parcelNames(parcels));
        form.dropParcel.setOnItemClickListener((p, v, position, id) -> chosenParcel = parcels.get(position));

        String[] workerNames = new String[workers.size()];
        for (int i = 0; i < workers.size(); i++) {
            workerNames[i] = workers.get(i).getName();
        }
        form.dropWorker.setSimpleItems(workerNames);
        form.dropWorker.setOnItemClickListener((p, v, position, id) -> chosenWorker = workers.get(position));

        form.btnDate.setOnClickListener(v -> AppHelper.pickDate(this, chosenDue.getTime(), date -> {
            Calendar picked = Calendar.getInstance();
            picked.setTime(date);
            chosenDue.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH));
            showDue(form);
        }));
        form.btnTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(chosenDue.get(Calendar.HOUR_OF_DAY))
                    .setMinute(chosenDue.get(Calendar.MINUTE))
                    .build();
            picker.addOnPositiveButtonClickListener(x -> {
                chosenDue.set(Calendar.HOUR_OF_DAY, picker.getHour());
                chosenDue.set(Calendar.MINUTE, picker.getMinute());
                showDue(form);
            });
            picker.show(getSupportFragmentManager(), "timePicker");
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tasks_add)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null)
                .show();

        // Validation avant de fermer la boîte de dialogue
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = AppHelper.textOf(form.etTitle);
            form.tilTitle.setError(null);
            form.tilParcel.setError(null);
            form.tilWorker.setError(null);
            boolean ok = true;
            if (title.isEmpty()) {
                form.tilTitle.setError(getString(R.string.error_required));
                ok = false;
            }
            if (chosenParcel == null) {
                form.tilParcel.setError(getString(R.string.error_choose));
                ok = false;
            }
            if (chosenWorker == null) {
                form.tilWorker.setError(getString(R.string.error_choose));
                ok = false;
            }
            if (!ok) {
                return;
            }
            saveTask(title);
            dialog.dismiss();
        });
    }

    private void showDue(DialogTaskBinding form) {
        form.btnDate.setText(AppHelper.formatDate(chosenDue.getTime()));
        form.btnTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(chosenDue.getTime()));
    }

    private void saveTask(String title) {
        DocumentReference ref = db.collection("tasks").document();
        Task task = new Task();
        task.setTaskId(ref.getId());
        task.setFarmId(AppHelper.getFarmId(this));
        task.setParcelId(chosenParcel.getParcelId());
        task.setParcelName(chosenParcel.getName());
        task.setTitle(title);
        task.setAssignedTo(chosenWorker.getWorkerId());
        task.setAssignedName(chosenWorker.getName());
        task.setDueDate(chosenDue.getTime());
        task.setStatus(Task.STATUS_TODO);
        task.setProblemNote("");
        task.setCreatedAt(new Date());

        // L'ouvrier sera notifié par son téléphone (écouteur Firestore, voir NotificationHelper)
        ref.set(task).addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
        AppHelper.toast(this, getString(R.string.tasks_created));
    }
}
