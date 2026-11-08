package com.example.agrismart;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.TransactionAdapter;
import com.example.agrismart.databinding.ActivityFinanceBinding;
import com.example.agrismart.databinding.DialogTransactionBinding;
import com.example.agrismart.model.Parcel;
import com.example.agrismart.model.Transaction;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Finances : dépenses et revenus par parcelle, bénéfice du mois (revenus - dépenses)
 * et graphique des 6 derniers mois (MPAndroidChart).
 */
public class FinanceActivity extends AppCompatActivity {

    private ActivityFinanceBinding binding;
    private FirebaseFirestore db;
    private TransactionAdapter adapter;

    private final List<Transaction> allTransactions = new ArrayList<>();
    private List<Parcel> parcels = new ArrayList<>();
    private String parcelFilter = null; // null = toutes les parcelles

    // Valeurs choisies dans la boîte de dialogue
    private String chosenCategory;
    private Parcel chosenParcel; // null = dépense générale
    private Date chosenDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFinanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        adapter = new TransactionAdapter(this::confirmDelete);
        binding.recyclerTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTransactions.setAdapter(adapter);
        AppHelper.styleBarChart(this, binding.chart);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnExpense.setOnClickListener(v -> showTransactionDialog(false));
        binding.btnIncome.setOnClickListener(v -> showTransactionDialog(true));

        AppHelper.loadParcels(this, loaded -> {
            parcels = loaded;
            setupParcelFilter();
        });
        listenToTransactions();
    }

    /** Liste déroulante "Toutes les parcelles" + chaque parcelle. */
    private void setupParcelFilter() {
        String[] items = new String[parcels.size() + 1];
        items[0] = getString(R.string.finance_all_parcels);
        for (int i = 0; i < parcels.size(); i++) {
            items[i + 1] = parcels.get(i).getName();
        }
        binding.dropParcelFilter.setSimpleItems(items);
        binding.dropParcelFilter.setText(items[0], false);
        binding.dropParcelFilter.setOnItemClickListener((p, v, position, id) -> {
            parcelFilter = position == 0 ? null : parcels.get(position - 1).getParcelId();
            refresh();
        });
    }

    private void listenToTransactions() {
        db.collection("transactions")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null) {
                        AppHelper.toast(this, AppHelper.errorMessage(this, error));
                        return;
                    }
                    if (snapshots == null) {
                        return;
                    }
                    allTransactions.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        allTransactions.add(doc.toObject(Transaction.class));
                    }
                    // Plus récente en premier
                    allTransactions.sort((a, b) -> Long.compare(time(b.getDate()), time(a.getDate())));
                    refresh();
                });
    }

    private static long time(Date date) {
        return date == null ? 0 : date.getTime();
    }

    /** Recalcule les totaux, le graphique et la liste (avec le filtre parcelle). */
    private void refresh() {
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction tx : allTransactions) {
            if (parcelFilter == null || parcelFilter.equals(tx.getParcelId())) {
                filtered.add(tx);
            }
        }

        // ---- Totaux du mois en cours ----
        Date monthStart = AppHelper.startOfMonth();
        double income = 0;
        double expenses = 0;
        for (Transaction tx : filtered) {
            if (tx.getDate() == null || tx.getDate().before(monthStart)) {
                continue;
            }
            if (Transaction.TYPE_INCOME.equals(tx.getType())) {
                income += tx.getAmount();
            } else {
                expenses += tx.getAmount();
            }
        }
        double profit = income - expenses; // bénéfice = revenus - dépenses
        String month = new SimpleDateFormat("LLLL", Locale.getDefault()).format(new Date());

        binding.tvIncome.setText(getString(R.string.money_value, AppHelper.formatMoney(income)));
        binding.tvExpenses.setText(getString(R.string.money_value, AppHelper.formatMoney(expenses)));
        binding.tvProfitLabel.setText(getString(R.string.finance_profit_month, month));
        binding.tvProfit.setText(getString(R.string.money_value, AppHelper.formatMoney(profit)));
        binding.tvProfit.setTextColor(ContextCompat.getColor(this, profit < 0 ? R.color.red : R.color.green_primary));

        showChart(filtered);

        // ---- 20 dernières opérations ----
        List<Transaction> latest = filtered.subList(0, Math.min(20, filtered.size()));
        adapter.setTransactions(new ArrayList<>(latest));
        binding.tvEmpty.setVisibility(latest.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Barres groupées revenus / dépenses pour les 6 derniers mois. */
    private void showChart(List<Transaction> transactions) {
        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM", Locale.US);
        SimpleDateFormat labelFormat = new SimpleDateFormat("MMM", Locale.getDefault());

        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();
        String[] labels = new String[6];

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MONTH, -5);
        for (int i = 0; i < 6; i++) {
            String monthKey = keyFormat.format(cal.getTime());
            labels[i] = labelFormat.format(cal.getTime());
            float income = 0;
            float expense = 0;
            for (Transaction tx : transactions) {
                if (tx.getDate() != null && monthKey.equals(keyFormat.format(tx.getDate()))) {
                    if (Transaction.TYPE_INCOME.equals(tx.getType())) {
                        income += (float) tx.getAmount();
                    } else {
                        expense += (float) tx.getAmount();
                    }
                }
            }
            incomeEntries.add(new BarEntry(i, income));
            expenseEntries.add(new BarEntry(i, expense));
            cal.add(Calendar.MONTH, 1);
        }

        BarDataSet incomeSet = new BarDataSet(incomeEntries, getString(R.string.finance_income));
        incomeSet.setColor(ContextCompat.getColor(this, R.color.green_primary));
        incomeSet.setDrawValues(false);
        BarDataSet expenseSet = new BarDataSet(expenseEntries, getString(R.string.finance_expenses));
        expenseSet.setColor(ContextCompat.getColor(this, R.color.orange));
        expenseSet.setDrawValues(false);

        // 2 barres par mois : (0,3 + 0,05) x 2 + 0,3 = 1 unité par groupe
        float barWidth = 0.3f;
        float barSpace = 0.05f;
        float groupSpace = 0.3f;
        BarData data = new BarData(incomeSet, expenseSet);
        data.setBarWidth(barWidth);

        XAxis xAxis = binding.chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setCenterAxisLabels(true);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(6f);

        binding.chart.setData(data);
        binding.chart.groupBars(0f, groupSpace, barSpace);
        binding.chart.invalidate();
    }

    // =====================================================================
    // Nouvelle dépense / nouveau revenu
    // =====================================================================

    private void showTransactionDialog(boolean income) {
        DialogTransactionBinding form = DialogTransactionBinding.inflate(getLayoutInflater());
        chosenCategory = null;
        chosenParcel = null;
        chosenDate = new Date();
        form.btnDate.setText(AppHelper.formatDate(chosenDate));

        form.dropCategory.setSimpleItems(R.array.finance_category_labels);
        form.dropCategory.setOnItemClickListener((p, v, position, id) ->
                chosenCategory = getResources().getStringArray(R.array.finance_category_codes)[position]);

        // Parcelle : "Général" en premier, puis les parcelles
        String[] parcelItems = new String[parcels.size() + 1];
        parcelItems[0] = getString(R.string.finance_general);
        for (int i = 0; i < parcels.size(); i++) {
            parcelItems[i + 1] = parcels.get(i).getName();
        }
        form.dropParcel.setSimpleItems(parcelItems);
        form.dropParcel.setText(parcelItems[0], false);
        form.dropParcel.setOnItemClickListener((p, v, position, id) ->
                chosenParcel = position == 0 ? null : parcels.get(position - 1));

        form.btnDate.setOnClickListener(v -> AppHelper.pickDate(this, chosenDate, date -> {
            chosenDate = date;
            form.btnDate.setText(AppHelper.formatDate(date));
        }));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(income ? R.string.finance_add_income : R.string.finance_add_expense)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String label = AppHelper.textOf(form.etLabel);
            Double amount = AppHelper.parseNumber(AppHelper.textOf(form.etAmount));
            form.tilLabel.setError(null);
            form.tilAmount.setError(null);
            form.tilCategory.setError(null);
            boolean ok = true;
            if (label.isEmpty()) {
                form.tilLabel.setError(getString(R.string.error_required));
                ok = false;
            }
            if (amount == null || amount <= 0) {
                form.tilAmount.setError(getString(R.string.error_amount));
                ok = false;
            }
            if (chosenCategory == null) {
                form.tilCategory.setError(getString(R.string.error_choose));
                ok = false;
            }
            if (!ok) {
                return;
            }

            DocumentReference ref = db.collection("transactions").document();
            Transaction tx = new Transaction();
            tx.setTxId(ref.getId());
            tx.setFarmId(AppHelper.getFarmId(this));
            tx.setParcelId(chosenParcel == null ? "" : chosenParcel.getParcelId());
            tx.setParcelName(chosenParcel == null ? "" : chosenParcel.getName());
            tx.setType(income ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE);
            tx.setAmount(amount);
            tx.setCategory(chosenCategory);
            tx.setLabel(label);
            tx.setDate(chosenDate);
            ref.set(tx).addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            AppHelper.toast(this, getString(R.string.finance_saved));
            dialog.dismiss();
        });
    }

    private void confirmDelete(Transaction tx) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.finance_delete_confirm, tx.getLabel()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, w) ->
                        db.collection("transactions").document(tx.getTxId()).delete()
                                .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e))))
                .show();
    }
}
