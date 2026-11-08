package com.example.agrismart;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.agrismart.databinding.ActivityParcelDetailBinding;
import com.example.agrismart.databinding.ItemHistoryBinding;
import com.example.agrismart.model.Parcel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Détail d'une parcelle + ajout + modification + suppression.
 * - Sans "parcelId" dans l'Intent : formulaire vide (nouvelle parcelle).
 * - Avec "parcelId" : consultation ; l'agriculteur peut modifier ou supprimer.
 */
public class ParcelDetailActivity extends AppCompatActivity {

    private ActivityParcelDetailBinding binding;
    private FirebaseFirestore db;
    private FusedLocationProviderClient locationClient;

    private Parcel parcel; // null tant que la parcelle n'existe pas (mode ajout)

    // Codes choisis dans les listes déroulantes
    private String selectedCrop;
    private String selectedSoil;
    private String selectedHealth = Parcel.HEALTH_HEALTHY;

    // Lignes de l'historique (diagnostics, irrigations, récoltes)
    private final List<HistoryRow> historyRows = new ArrayList<>();

    // Demande de l'autorisation de localisation, puis lecture de la position
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    fetchLocation();
                } else {
                    toast(getString(R.string.location_permission_denied));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParcelDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        setupDropdowns();
        setupClicks();

        // Ouvrier : lecture seule (pas de modification, pas d'historique)
        boolean farmer = AppHelper.isFarmer(this);
        binding.layoutActions.setVisibility(farmer ? View.VISIBLE : View.GONE);
        binding.layoutHistorySection.setVisibility(farmer ? View.VISIBLE : View.GONE);
        binding.tvLastDiagnostic.setVisibility(farmer ? View.VISIBLE : View.GONE);

        String parcelId = getIntent().getStringExtra(ParcelListActivity.EXTRA_PARCEL_ID);
        if (parcelId == null) {
            showForm();
        } else {
            loadParcel(parcelId);
        }
    }

    private void setupClicks() {
        binding.btnBack.setOnClickListener(v -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        binding.btnEditTop.setOnClickListener(v -> showForm());
        binding.btnEdit.setOnClickListener(v -> showForm());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
        binding.btnOpenMaps.setOnClickListener(v -> openInMaps());
        binding.btnMyLocation.setOnClickListener(v -> askLocation());
        binding.btnSave.setOnClickListener(v -> save());
    }

    /** Retour : en modification on revient au détail, sinon on quitte l'écran. */
    private void goBack() {
        if (binding.layoutForm.getVisibility() == View.VISIBLE && parcel != null) {
            showDetails();
        } else {
            finish();
        }
    }

    /** Remplit les 3 listes déroulantes et mémorise le code choisi. */
    private void setupDropdowns() {
        binding.dropCrop.setSimpleItems(R.array.crop_labels);
        binding.dropCrop.setOnItemClickListener((parent, view, position, id) ->
                selectedCrop = getResources().getStringArray(R.array.crop_codes)[position]);

        binding.dropSoil.setSimpleItems(R.array.soil_labels);
        binding.dropSoil.setOnItemClickListener((parent, view, position, id) ->
                selectedSoil = getResources().getStringArray(R.array.soil_codes)[position]);

        binding.dropHealth.setSimpleItems(R.array.health_labels);
        binding.dropHealth.setOnItemClickListener((parent, view, position, id) ->
                selectedHealth = getResources().getStringArray(R.array.health_codes)[position]);
    }

    // =====================================================================
    // Lecture et affichage
    // =====================================================================

    private void loadParcel(String parcelId) {
        binding.layoutView.setVisibility(View.GONE);
        binding.progress.setVisibility(View.VISIBLE);
        db.collection("parcels").document(parcelId).get()
                .addOnSuccessListener(doc -> {
                    binding.progress.setVisibility(View.GONE);
                    parcel = doc.toObject(Parcel.class);
                    if (parcel == null) {
                        toast(getString(R.string.parcel_not_found));
                        finish();
                        return;
                    }
                    showDetails();
                    if (AppHelper.isFarmer(this)) {
                        loadHistory();
                    }
                })
                .addOnFailureListener(e -> {
                    toast(AppHelper.errorMessage(this, e));
                    finish();
                });
    }

    /** Mode consultation : affiche les informations de la parcelle. */
    private void showDetails() {
        binding.layoutForm.setVisibility(View.GONE);
        binding.layoutView.setVisibility(View.VISIBLE);
        binding.btnEditTop.setVisibility(AppHelper.isFarmer(this) ? View.VISIBLE : View.GONE);

        binding.tvTitle.setText(parcel.getName());
        AppHelper.showHealth(this, parcel.getHealthStatus(), binding.tvHealth, null);
        binding.tvCrop.setText(AppHelper.labelOf(this, R.array.crop_codes, R.array.crop_labels, parcel.getCrop()));
        binding.tvSoil.setText(AppHelper.labelOf(this, R.array.soil_codes, R.array.soil_labels, parcel.getSoilType()));
        binding.tvArea.setText(getString(R.string.parcel_area_value, AppHelper.formatNumber(parcel.getArea())));

        if (hasPosition()) {
            binding.tvGps.setText(getString(R.string.parcel_gps_value,
                    String.format(Locale.US, "%.4f", parcel.getLatitude()),
                    String.format(Locale.US, "%.4f", parcel.getLongitude())));
            binding.btnOpenMaps.setEnabled(true);
        } else {
            binding.tvGps.setText(R.string.parcel_no_position);
            binding.btnOpenMaps.setEnabled(false);
        }
        renderHistory();
    }

    private boolean hasPosition() {
        return parcel != null && parcel.getLatitude() != null && parcel.getLongitude() != null;
    }

    // =====================================================================
    // Historique : diagnostics, irrigations et récoltes de cette parcelle
    // (ces données seront créées par les écrans des phases 5 et 6)
    // =====================================================================

    private void loadHistory() {
        historyRows.clear();
        String farmId = AppHelper.getFarmId(this);
        String parcelId = parcel.getParcelId();

        // Diagnostics : champs "disease", "confidence" (0 à 1), "date"
        db.collection("diagnostics").whereEqualTo("farmId", farmId).whereEqualTo("parcelId", parcelId)
                .get().addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        Double confidence = doc.getDouble("confidence");
                        int percent = confidence == null ? 0 : (int) Math.round(confidence * 100);
                        String text = getString(R.string.history_diagnostic, doc.getString("disease"), percent);
                        historyRows.add(new HistoryRow(doc.getDate("date"), text,
                                R.drawable.ic_warning, R.color.red, R.color.red_soft, true));
                    }
                    renderHistory();
                });

        // Irrigations : champs "quantity" (L/m²), "date"
        db.collection("irrigations").whereEqualTo("farmId", farmId).whereEqualTo("parcelId", parcelId)
                .get().addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        Double quantity = doc.getDouble("quantity");
                        String text = getString(R.string.history_irrigation,
                                AppHelper.formatNumber(quantity == null ? 0 : quantity));
                        historyRows.add(new HistoryRow(doc.getDate("date"), text,
                                R.drawable.ic_water, R.color.blue, R.color.blue_soft, false));
                    }
                    renderHistory();
                });

        // Récoltes : champs "quantityKg", "date"
        db.collection("harvests").whereEqualTo("farmId", farmId).whereEqualTo("parcelId", parcelId)
                .get().addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        Double kg = doc.getDouble("quantityKg");
                        String text = getString(R.string.history_harvest,
                                AppHelper.formatNumber(kg == null ? 0 : kg));
                        historyRows.add(new HistoryRow(doc.getDate("date"), text,
                                R.drawable.ic_agriculture, R.color.green_primary, R.color.green_soft, false));
                    }
                    renderHistory();
                });
    }

    /** Affiche les 10 événements les plus récents + la date du dernier diagnostic. */
    private void renderHistory() {
        // Du plus récent au plus ancien
        historyRows.sort((a, b) -> Long.compare(b.time(), a.time()));

        binding.layoutHistory.removeAllViews();
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
        Date lastDiagnostic = null;

        for (int i = 0; i < historyRows.size(); i++) {
            HistoryRow row = historyRows.get(i);
            if (row.isDiagnostic && lastDiagnostic == null) {
                lastDiagnostic = row.date;
            }
            if (i >= 10) {
                continue;
            }
            ItemHistoryBinding item = ItemHistoryBinding.inflate(getLayoutInflater(), binding.layoutHistory, false);
            item.ivIcon.setImageResource(row.iconRes);
            item.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, row.colorRes)));
            item.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, row.softColorRes)));
            item.tvText.setText(row.text);
            item.tvDate.setText(row.date == null ? "" : dateFormat.format(row.date));
            binding.layoutHistory.addView(item.getRoot());
        }
        binding.tvHistoryEmpty.setVisibility(historyRows.isEmpty() ? View.VISIBLE : View.GONE);

        if (lastDiagnostic != null) {
            // Ex : "Dernier diagnostic : il y a 2 jours"
            CharSequence ago = DateUtils.getRelativeTimeSpanString(lastDiagnostic.getTime());
            binding.tvLastDiagnostic.setText(getString(R.string.parcel_last_diagnostic, ago));
        } else {
            binding.tvLastDiagnostic.setText(R.string.parcel_no_diagnostic);
        }
    }

    /** Une ligne d'historique (classe interne très simple). */
    private static class HistoryRow {
        final Date date;
        final String text;
        final int iconRes;
        final int colorRes;
        final int softColorRes;
        final boolean isDiagnostic;

        HistoryRow(Date date, String text, int iconRes, int colorRes, int softColorRes, boolean isDiagnostic) {
            this.date = date;
            this.text = text;
            this.iconRes = iconRes;
            this.colorRes = colorRes;
            this.softColorRes = softColorRes;
            this.isDiagnostic = isDiagnostic;
        }

        long time() {
            return date == null ? 0 : date.getTime();
        }
    }

    // =====================================================================
    // Formulaire : ajout / modification
    // =====================================================================

    /** Mode saisie : vide pour une nouvelle parcelle, pré-rempli pour une modification. */
    private void showForm() {
        binding.layoutView.setVisibility(View.GONE);
        binding.layoutForm.setVisibility(View.VISIBLE);
        binding.btnEditTop.setVisibility(View.GONE);

        if (parcel == null) {
            binding.tvTitle.setText(R.string.parcel_new_title);
        } else {
            binding.tvTitle.setText(R.string.parcel_edit_title);
            binding.etName.setText(parcel.getName());
            binding.etArea.setText(AppHelper.formatNumber(parcel.getArea()));
            selectedCrop = parcel.getCrop();
            selectedSoil = parcel.getSoilType();
            selectedHealth = parcel.getHealthStatus();
            if (hasPosition()) {
                binding.etLatitude.setText(String.valueOf(parcel.getLatitude()));
                binding.etLongitude.setText(String.valueOf(parcel.getLongitude()));
            }
        }
        // "false" = ne pas filtrer la liste déroulante
        if (selectedCrop != null) {
            binding.dropCrop.setText(AppHelper.labelOf(this, R.array.crop_codes, R.array.crop_labels, selectedCrop), false);
        }
        if (selectedSoil != null) {
            binding.dropSoil.setText(AppHelper.labelOf(this, R.array.soil_codes, R.array.soil_labels, selectedSoil), false);
        }
        binding.dropHealth.setText(AppHelper.labelOf(this, R.array.health_codes, R.array.health_labels, selectedHealth), false);
    }

    private void save() {
        String name = AppHelper.textOf(binding.etName);
        String areaText = AppHelper.textOf(binding.etArea);
        String latText = AppHelper.textOf(binding.etLatitude);
        String lonText = AppHelper.textOf(binding.etLongitude);

        // ---- Validation ----
        binding.tilName.setError(null);
        binding.tilCrop.setError(null);
        binding.tilArea.setError(null);
        binding.tilSoil.setError(null);
        binding.tilLatitude.setError(null);
        binding.tilLongitude.setError(null);
        boolean ok = true;

        if (name.isEmpty()) {
            binding.tilName.setError(getString(R.string.error_required));
            ok = false;
        }
        if (selectedCrop == null) {
            binding.tilCrop.setError(getString(R.string.error_choose));
            ok = false;
        }
        Double area = AppHelper.parseNumber(areaText);
        if (area == null) {
            binding.tilArea.setError(getString(R.string.error_number));
            ok = false;
        } else if (area <= 0) {
            binding.tilArea.setError(getString(R.string.error_area));
            ok = false;
        }
        if (selectedSoil == null) {
            binding.tilSoil.setError(getString(R.string.error_choose));
            ok = false;
        }

        // Position : facultative, mais latitude ET longitude ensemble
        Double latitude = null;
        Double longitude = null;
        if (!latText.isEmpty() || !lonText.isEmpty()) {
            latitude = AppHelper.parseNumber(latText);
            longitude = AppHelper.parseNumber(lonText);
            if (latitude == null || longitude == null) {
                binding.tilLatitude.setError(getString(R.string.error_gps_both));
                ok = false;
            } else {
                if (latitude < -90 || latitude > 90) {
                    binding.tilLatitude.setError(getString(R.string.error_latitude));
                    ok = false;
                }
                if (longitude < -180 || longitude > 180) {
                    binding.tilLongitude.setError(getString(R.string.error_longitude));
                    ok = false;
                }
            }
        }
        if (!ok) {
            return;
        }

        // ---- Enregistrement ----
        boolean isNew = (parcel == null);
        DocumentReference ref;
        if (isNew) {
            ref = db.collection("parcels").document(); // nouvel id automatique
            parcel = new Parcel();
            parcel.setParcelId(ref.getId());
            parcel.setFarmId(AppHelper.getFarmId(this));
        } else {
            ref = db.collection("parcels").document(parcel.getParcelId());
        }
        parcel.setName(name);
        parcel.setCrop(selectedCrop);
        parcel.setArea(area);
        parcel.setSoilType(selectedSoil);
        parcel.setHealthStatus(selectedHealth);
        parcel.setLatitude(latitude);
        parcel.setLongitude(longitude);

        // On n'attend pas le serveur : Firestore enregistre d'abord sur le téléphone
        // puis synchronise dès que le réseau est là (fonctionne hors ligne).
        ref.set(parcel).addOnFailureListener(e -> toast(AppHelper.errorMessage(this, e)));
        toast(getString(R.string.parcel_saved));

        if (isNew) {
            finish();
        } else {
            showDetails();
        }
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.parcel_delete_confirm, parcel.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    db.collection("parcels").document(parcel.getParcelId()).delete()
                            .addOnFailureListener(e -> toast(AppHelper.errorMessage(this, e)));
                    toast(getString(R.string.parcel_deleted));
                    finish();
                })
                .show();
    }

    // =====================================================================
    // Position GPS et Google Maps
    // =====================================================================

    private void askLocation() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fine || coarse) {
            fetchLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    /** Lit la position actuelle (permission déjà vérifiée dans askLocation). */
    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        toast(getString(R.string.location_searching));
        binding.btnMyLocation.setEnabled(false);
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    binding.btnMyLocation.setEnabled(true);
                    if (location == null) {
                        toast(getString(R.string.location_unavailable));
                        return;
                    }
                    binding.etLatitude.setText(String.format(Locale.US, "%.6f", location.getLatitude()));
                    binding.etLongitude.setText(String.format(Locale.US, "%.6f", location.getLongitude()));
                    toast(getString(R.string.location_found));
                })
                .addOnFailureListener(e -> {
                    binding.btnMyLocation.setEnabled(true);
                    toast(getString(R.string.location_unavailable));
                });
    }

    /** Ouvre la position dans l'application Google Maps (ou le navigateur). */
    private void openInMaps() {
        if (!hasPosition()) {
            return;
        }
        String latLon = parcel.getLatitude() + "," + parcel.getLongitude();
        Uri geoUri = Uri.parse("geo:" + latLon + "?q=" + latLon + "(" + Uri.encode(parcel.getName()) + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, geoUri));
        } catch (ActivityNotFoundException e) {
            // Pas d'application de cartes : on essaie le site Google Maps
            Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + latLon);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, webUri));
            } catch (ActivityNotFoundException e2) {
                toast(getString(R.string.maps_unavailable));
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}
