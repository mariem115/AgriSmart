package com.example.agrismart;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.HarvestAdapter;
import com.example.agrismart.databinding.ActivityHarvestBinding;
import com.example.agrismart.databinding.DialogHarvestBinding;
import com.example.agrismart.model.Harvest;
import com.example.agrismart.model.Parcel;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Récoltes : saisie, historique et prévision de la saison suivante.
 * Prévision (version simple de l'IA) = moyenne pondérée des dernières saisons,
 * les saisons récentes ayant plus de poids.
 */
public class HarvestActivity extends AppCompatActivity {

    private ActivityHarvestBinding binding;
    private FirebaseFirestore db;
    private HarvestAdapter adapter;

    private final List<Harvest> allHarvests = new ArrayList<>();
    private List<Parcel> parcels = new ArrayList<>();
    private String cropFilter = null; // null = toutes les cultures

    // Valeurs choisies dans la boîte de dialogue
    private Parcel chosenParcel;
    private Date chosenDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHarvestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        adapter = new HarvestAdapter(this::confirmDelete);
        binding.recyclerHarvests.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHarvests.setAdapter(adapter);
        AppHelper.styleBarChart(this, binding.chart);
        binding.chart.getLegend().setEnabled(false);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAdd.setOnClickListener(v -> showHarvestDialog());
        setupCropFilter();

        AppHelper.loadParcels(this, loaded -> parcels = loaded);
        listenToHarvests();
    }

    /** "Toutes les cultures" + chaque culture. */
    private void setupCropFilter() {
        String[] codes = getResources().getStringArray(R.array.crop_codes);
        String[] labels = getResources().getStringArray(R.array.crop_labels);
        String[] items = new String[labels.length + 1];
        items[0] = getString(R.string.harvest_all_crops);
        System.arraycopy(labels, 0, items, 1, labels.length);

        binding.dropCrop.setSimpleItems(items);
        binding.dropCrop.setText(items[0], false);
        binding.dropCrop.setOnItemClickListener((p, v, position, id) -> {
            cropFilter = position == 0 ? null : codes[position - 1];
            refresh();
        });
    }

    private void listenToHarvests() {
        db.collection("harvests")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null) {
                        AppHelper.toast(this, AppHelper.errorMessage(this, error));
                        return;
                    }
                    if (snapshots == null) {
                        return;
                    }
                    allHarvests.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        allHarvests.add(doc.toObject(Harvest.class));
                    }
                    allHarvests.sort((a, b) -> Long.compare(time(b.getDate()), time(a.getDate())));
                    refresh();
                });
    }

    private static long time(Date date) {
        return date == null ? 0 : date.getTime();
    }

    /** Recalcule totaux par saison, prévision, graphique et liste. */
    private void refresh() {
        List<Harvest> filtered = new ArrayList<>();
        // TreeMap : saisons triées de la plus ancienne à la plus récente
        Map<Integer, Double> kgBySeason = new TreeMap<>();
        for (Harvest harvest : allHarvests) {
            if (cropFilter != null && !cropFilter.equals(harvest.getCrop())) {
                continue;
            }
            filtered.add(harvest);
            Double total = kgBySeason.get(harvest.getSeason());
            kgBySeason.put(harvest.getSeason(), (total == null ? 0 : total) + harvest.getQuantityKg());
        }

        adapter.setHarvests(filtered.subList(0, Math.min(20, filtered.size())));
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        List<Integer> seasons = new ArrayList<>(kgBySeason.keySet());
        List<Double> totals = new ArrayList<>(kgBySeason.values());
        String cropName = cropFilter == null ? getString(R.string.harvest_all_crops)
                : AppHelper.labelOf(this, R.array.crop_codes, R.array.crop_labels, cropFilter);

        if (totals.isEmpty()) {
            binding.tvForecastTitle.setText(R.string.harvest_forecast_none_title);
            binding.tvForecastValue.setText(R.string.placeholder_dash);
            binding.tvForecastTrend.setText(R.string.harvest_forecast_none);
            binding.tvReliability.setVisibility(View.GONE);
            binding.chart.clear();
            return;
        }

        int nextSeason = seasons.get(seasons.size() - 1) + 1;
        double forecastKg = weightedForecast(totals);
        double lastKg = totals.get(totals.size() - 1);
        double trend = lastKg > 0 ? (forecastKg - lastKg) / lastKg * 100 : 0;

        binding.tvForecastTitle.setText(getString(R.string.harvest_forecast_title, nextSeason));
        binding.tvForecastValue.setText(getString(R.string.harvest_forecast_value,
                AppHelper.formatNumber(round1(forecastKg / 1000))));
        binding.tvForecastTrend.setText(getString(R.string.harvest_forecast_trend, cropName,
                (trend >= 0 ? "+" : "") + AppHelper.formatNumber(round1(trend)),
                seasons.get(seasons.size() - 1)));
        // Peu de saisons = prévision peu fiable (voir cahier des charges, partie 9)
        binding.tvReliability.setVisibility(totals.size() < 3 ? View.VISIBLE : View.GONE);

        showChart(seasons, totals, nextSeason, forecastKg);
    }

    /**
     * Moyenne pondérée des 4 dernières saisons maximum.
     * Poids 1, 2, 3, 4 : la saison la plus récente compte le plus.
     * Ex : 10,9 t, 11,8 t, 13,1 t -> (10,9x1 + 11,8x2 + 13,1x3) / 6 = 12,3 t
     */
    static double weightedForecast(List<Double> totalsOldestFirst) {
        int start = Math.max(0, totalsOldestFirst.size() - 4);
        double weightedSum = 0;
        double weightSum = 0;
        int weight = 1;
        for (int i = start; i < totalsOldestFirst.size(); i++) {
            weightedSum += totalsOldestFirst.get(i) * weight;
            weightSum += weight;
            weight++;
        }
        return weightedSum / weightSum;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Une barre par saison (en tonnes) + la prévision en vert clair. */
    private void showChart(List<Integer> seasons, List<Double> totals, int nextSeason, double forecastKg) {
        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        int start = Math.max(0, seasons.size() - 4); // 4 dernières saisons
        int x = 0;
        for (int i = start; i < seasons.size(); i++) {
            entries.add(new BarEntry(x++, (float) (totals.get(i) / 1000)));
            colors.add(ContextCompat.getColor(this, R.color.green_primary));
            labels.add(String.valueOf(seasons.get(i)));
        }
        entries.add(new BarEntry(x, (float) (forecastKg / 1000)));
        colors.add(ContextCompat.getColor(this, R.color.green_pale));
        labels.add(getString(R.string.harvest_chart_forecast, nextSeason));

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(11f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return AppHelper.formatNumber(round1(value));
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);
        binding.chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chart.getXAxis().setLabelCount(labels.size());
        binding.chart.setData(data);
        binding.chart.invalidate();
    }

    // =====================================================================
    // Saisie / suppression d'une récolte
    // =====================================================================

    private void showHarvestDialog() {
        if (parcels.isEmpty()) {
            AppHelper.toast(this, getString(R.string.need_parcel_first));
            return;
        }
        DialogHarvestBinding form = DialogHarvestBinding.inflate(getLayoutInflater());
        chosenParcel = null;
        chosenDate = new Date();
        form.btnDate.setText(AppHelper.formatDate(chosenDate));

        form.dropParcel.setSimpleItems(AppHelper.parcelNames(parcels));
        form.dropParcel.setOnItemClickListener((p, v, position, id) -> chosenParcel = parcels.get(position));
        form.btnDate.setOnClickListener(v -> AppHelper.pickDate(this, chosenDate, date -> {
            chosenDate = date;
            form.btnDate.setText(AppHelper.formatDate(date));
        }));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.harvest_add)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_save, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Double kg = AppHelper.parseNumber(AppHelper.textOf(form.etQuantity));
            form.tilParcel.setError(null);
            form.tilQuantity.setError(null);
            if (chosenParcel == null) {
                form.tilParcel.setError(getString(R.string.error_choose));
                return;
            }
            if (kg == null || kg <= 0) {
                form.tilQuantity.setError(getString(R.string.error_number));
                return;
            }

            Calendar cal = Calendar.getInstance();
            cal.setTime(chosenDate);

            DocumentReference ref = db.collection("harvests").document();
            Harvest harvest = new Harvest();
            harvest.setHarvestId(ref.getId());
            harvest.setFarmId(AppHelper.getFarmId(this));
            harvest.setParcelId(chosenParcel.getParcelId());
            harvest.setParcelName(chosenParcel.getName());
            harvest.setCrop(chosenParcel.getCrop()); // la culture vient de la parcelle
            harvest.setQuantityKg(kg);
            harvest.setDate(chosenDate);
            harvest.setSeason(cal.get(Calendar.YEAR)); // saison = année de la récolte
            ref.set(harvest).addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
            AppHelper.toast(this, getString(R.string.harvest_saved));
            dialog.dismiss();
        });
    }

    private void confirmDelete(Harvest harvest) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.harvest_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, w) ->
                        db.collection("harvests").document(harvest.getHarvestId()).delete()
                                .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e))))
                .show();
    }
}
