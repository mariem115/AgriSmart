package com.example.agrismart;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.StockAdapter;
import com.example.agrismart.databinding.ActivityStockBinding;
import com.example.agrismart.databinding.DialogStockItemBinding;
import com.example.agrismart.databinding.DialogStockMoveBinding;
import com.example.agrismart.model.StockItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Stock d'intrants (engrais, pesticides, semences) : entrées, sorties,
 * seuil minimum et alerte de rupture (bandeau + notification locale).
 */
public class StockActivity extends AppCompatActivity {

    private ActivityStockBinding binding;
    private FirebaseFirestore db;
    private StockAdapter adapter;

    private final List<StockItem> allItems = new ArrayList<>();
    private String typeFilter = StockItem.TYPE_FERTILIZER; // onglet choisi

    // Type choisi dans la boîte de dialogue d'un article
    private String chosenType;
    // Article choisi dans la boîte de dialogue entrée / sortie
    private StockItem chosenItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        adapter = new StockAdapter(this::showItemDialog);
        binding.recyclerStock.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStock.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v -> showItemDialog(null));
        binding.btnIn.setOnClickListener(v -> showMoveDialog(true));
        binding.btnOut.setOnClickListener(v -> showMoveDialog(false));

        setupTabs();
        listenToStock();
    }

    private void setupTabs() {
        String[] codes = getResources().getStringArray(R.array.stock_type_codes);
        String[] labels = getResources().getStringArray(R.array.stock_type_labels);
        for (int i = 0; i < codes.length; i++) {
            binding.tabs.addTab(binding.tabs.newTab().setText(labels[i]).setTag(codes[i]));
        }
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                typeFilter = (String) tab.getTag();
                showItems();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void listenToStock() {
        db.collection("stock")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null) {
                        AppHelper.toast(this, AppHelper.errorMessage(this, error));
                        return;
                    }
                    if (snapshots == null) {
                        return;
                    }
                    allItems.clear();
                    int lowCount = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        StockItem item = doc.toObject(StockItem.class);
                        allItems.add(item);
                        if (StockAdapter.isLow(item)) {
                            lowCount++;
                            NotificationHelper.notifyLowStock(this, item); // 1 fois par jour max
                        }
                    }
                    allItems.sort((a, b) -> String.valueOf(a.getName())
                            .compareToIgnoreCase(String.valueOf(b.getName())));

                    // Bandeau rouge "Rupture proche"
                    binding.layoutAlert.setVisibility(lowCount > 0 ? View.VISIBLE : View.GONE);
                    binding.tvAlert.setText(getString(R.string.stock_alert_count, lowCount));
                    showItems();
                });
    }

    /** Affiche les articles de l'onglet choisi. */
    private void showItems() {
        List<StockItem> visible = new ArrayList<>();
        for (StockItem item : allItems) {
            if (typeFilter.equals(item.getType())) {
                visible.add(item);
            }
        }
        adapter.setItems(visible);
        binding.tvEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // Ajout / modification d'un article
    // =====================================================================

    private void showItemDialog(StockItem item) {
        DialogStockItemBinding form = DialogStockItemBinding.inflate(getLayoutInflater());
        form.dropType.setSimpleItems(R.array.stock_type_labels);
        form.dropType.setOnItemClickListener((p, v, position, id) ->
                chosenType = getResources().getStringArray(R.array.stock_type_codes)[position]);

        if (item == null) {
            chosenType = typeFilter; // par défaut : l'onglet affiché
            form.etUnit.setText("kg");
        } else {
            chosenType = item.getType();
            form.etName.setText(item.getName());
            form.etUnit.setText(item.getUnit());
            form.etThreshold.setText(AppHelper.formatNumber(item.getMinThreshold()));
            // En modification, la quantité change seulement par Entrée / Sortie
            form.layoutQuantity.setVisibility(View.GONE);
        }
        form.dropType.setText(AppHelper.labelOf(this, R.array.stock_type_codes,
                R.array.stock_type_labels, chosenType), false);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(item == null ? R.string.stock_add : R.string.stock_edit)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null);
        if (item != null) {
            builder.setNeutralButton(R.string.btn_delete, (d, w) ->
                    db.collection("stock").document(item.getItemId()).delete()
                            .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e))));
        }
        AlertDialog dialog = builder.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = AppHelper.textOf(form.etName);
            String unit = AppHelper.textOf(form.etUnit);
            Double quantity = AppHelper.parseNumber(AppHelper.textOf(form.etQuantity));
            Double threshold = AppHelper.parseNumber(AppHelper.textOf(form.etThreshold));

            form.tilName.setError(null);
            form.tilUnit.setError(null);
            form.tilQuantity.setError(null);
            form.tilThreshold.setError(null);
            boolean ok = true;
            if (name.isEmpty()) {
                form.tilName.setError(getString(R.string.error_required));
                ok = false;
            }
            if (unit.isEmpty()) {
                form.tilUnit.setError(getString(R.string.error_required));
                ok = false;
            }
            if (item == null && (quantity == null || quantity < 0)) {
                form.tilQuantity.setError(getString(R.string.error_number));
                ok = false;
            }
            if (threshold == null || threshold < 0) {
                form.tilThreshold.setError(getString(R.string.error_number));
                ok = false;
            }
            if (!ok) {
                return;
            }

            if (item == null) {
                DocumentReference ref = db.collection("stock").document();
                StockItem newItem = new StockItem();
                newItem.setItemId(ref.getId());
                newItem.setFarmId(AppHelper.getFarmId(this));
                newItem.setName(name);
                newItem.setType(chosenType);
                newItem.setQuantity(quantity);
                newItem.setMinThreshold(threshold);
                newItem.setUnit(unit);
                ref.set(newItem).addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            } else {
                db.collection("stock").document(item.getItemId())
                        .update("name", name, "type", chosenType, "unit", unit, "minThreshold", threshold)
                        .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            }
            dialog.dismiss();
        });
    }

    // =====================================================================
    // Entrée (+) ou sortie (-) de stock
    // =====================================================================

    private void showMoveDialog(boolean isEntry) {
        if (allItems.isEmpty()) {
            AppHelper.toast(this, getString(R.string.stock_empty));
            return;
        }
        DialogStockMoveBinding form = DialogStockMoveBinding.inflate(getLayoutInflater());
        chosenItem = null;
        String[] names = new String[allItems.size()];
        for (int i = 0; i < allItems.size(); i++) {
            names[i] = allItems.get(i).getName();
        }
        form.dropItem.setSimpleItems(names);
        form.dropItem.setOnItemClickListener((p, v, position, id) -> chosenItem = allItems.get(position));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isEntry ? R.string.stock_in : R.string.stock_out)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Double quantity = AppHelper.parseNumber(AppHelper.textOf(form.etQuantity));
            form.tilItem.setError(null);
            form.tilQuantity.setError(null);
            if (chosenItem == null) {
                form.tilItem.setError(getString(R.string.error_choose));
                return;
            }
            if (quantity == null || quantity <= 0) {
                form.tilQuantity.setError(getString(R.string.error_number));
                return;
            }
            if (!isEntry && quantity > chosenItem.getQuantity()) {
                form.tilQuantity.setError(getString(R.string.stock_not_enough,
                        AppHelper.formatNumber(chosenItem.getQuantity()), chosenItem.getUnit()));
                return;
            }
            // increment() : Firestore ajoute / retire la quantité sans écraser le reste
            double change = isEntry ? quantity : -quantity;
            db.collection("stock").document(chosenItem.getItemId())
                    .update("quantity", FieldValue.increment(change))
                    .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            dialog.dismiss();
        });
    }
}
