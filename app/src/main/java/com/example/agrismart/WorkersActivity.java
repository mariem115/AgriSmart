package com.example.agrismart;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.WorkerAdapter;
import com.example.agrismart.databinding.ActivityWorkersBinding;
import com.example.agrismart.databinding.DialogWorkerBinding;
import com.example.agrismart.model.Worker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Gestion des ouvriers (agriculteur uniquement) :
 * liste, présence du jour, salaire journalier et total des salaires du mois.
 */
public class WorkersActivity extends AppCompatActivity {

    private ActivityWorkersBinding binding;
    private FirebaseFirestore db;
    private WorkerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWorkersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        adapter = new WorkerAdapter(new WorkerAdapter.Listener() {
            @Override
            public void onWorkerClick(Worker worker) {
                showWorkerDialog(worker);
            }

            @Override
            public void onPresenceClick(Worker worker) {
                togglePresence(worker);
            }

            @Override
            public void onPhoneClick(Worker worker) {
                if (worker.getPhone() != null && !worker.getPhone().isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + worker.getPhone())));
                }
            }
        });
        binding.recyclerWorkers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWorkers.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v -> showWorkerDialog(null));
        listenToWorkers();
    }

    /** Liste en temps réel des ouvriers de la ferme. */
    private void listenToWorkers() {
        db.collection("workers")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null) {
                        AppHelper.toast(this, AppHelper.errorMessage(this, error));
                        return;
                    }
                    if (snapshots == null) {
                        return;
                    }
                    List<Worker> workers = new ArrayList<>();
                    int present = 0;
                    double salaries = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Worker worker = doc.toObject(Worker.class);
                        workers.add(worker);
                        if (WorkerAdapter.isPresentToday(worker)) {
                            present++;
                        }
                        // Salaire du mois = salaire journalier x jours de présence ce mois
                        salaries += worker.getDailySalary() * WorkerAdapter.daysThisMonth(worker);
                    }
                    workers.sort((a, b) -> String.valueOf(a.getName())
                            .compareToIgnoreCase(String.valueOf(b.getName())));

                    adapter.setWorkers(workers);
                    binding.tvEmpty.setVisibility(workers.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.tvPresent.setText(getString(R.string.workers_present_count, present, workers.size()));
                    binding.tvSalaries.setText(getString(R.string.money_value, AppHelper.formatMoney(salaries)));
                });
    }

    /** Présent <-> absent pour aujourd'hui (ajoute / retire la date du jour). */
    private void togglePresence(Worker worker) {
        String today = AppHelper.dayKey(new Date());
        Object change = WorkerAdapter.isPresentToday(worker)
                ? FieldValue.arrayRemove(today)
                : FieldValue.arrayUnion(today);
        db.collection("workers").document(worker.getWorkerId()).update("presences", change)
                .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
    }

    /** Boîte de dialogue d'ajout (worker == null) ou de modification d'un ouvrier. */
    private void showWorkerDialog(Worker worker) {
        DialogWorkerBinding form = DialogWorkerBinding.inflate(getLayoutInflater());
        if (worker != null) {
            form.etName.setText(worker.getName());
            form.etPhone.setText(worker.getPhone());
            form.etSalary.setText(AppHelper.formatNumber(worker.getDailySalary()));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(worker == null ? R.string.workers_add : R.string.workers_edit)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null); // géré plus bas pour valider avant de fermer
        if (worker != null) {
            builder.setNeutralButton(R.string.btn_delete, (d, w) -> confirmDelete(worker));
        }
        AlertDialog dialog = builder.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = AppHelper.textOf(form.etName);
            String phone = AppHelper.textOf(form.etPhone);
            Double salary = AppHelper.parseNumber(AppHelper.textOf(form.etSalary));

            form.tilName.setError(null);
            form.tilSalary.setError(null);
            if (name.isEmpty()) {
                form.tilName.setError(getString(R.string.error_required));
                return;
            }
            if (salary == null || salary < 0) {
                form.tilSalary.setError(getString(R.string.error_number));
                return;
            }

            if (worker == null) {
                DocumentReference ref = db.collection("workers").document();
                Worker newWorker = new Worker(ref.getId(), AppHelper.getFarmId(this), name, phone, salary);
                ref.set(newWorker)
                        .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            } else {
                // update() garde la liste des présences intacte
                db.collection("workers").document(worker.getWorkerId())
                        .update("name", name, "phone", phone, "dailySalary", salary)
                        .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            }
            dialog.dismiss();
        });
    }

    private void confirmDelete(Worker worker) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.workers_delete_confirm, worker.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, w) ->
                        db.collection("workers").document(worker.getWorkerId()).delete()
                                .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e))))
                .show();
    }
}
