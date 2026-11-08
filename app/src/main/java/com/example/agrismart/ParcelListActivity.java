package com.example.agrismart;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.ParcelAdapter;
import com.example.agrismart.databinding.ActivityParcelListBinding;
import com.example.agrismart.model.Parcel;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liste des parcelles de la ferme, avec recherche et filtre par culture.
 * L'agriculteur peut ajouter une parcelle ; l'ouvrier consulte seulement.
 */
public class ParcelListActivity extends AppCompatActivity {

    public static final String EXTRA_PARCEL_ID = "parcelId";

    private ActivityParcelListBinding binding;
    private ParcelAdapter adapter;
    private final List<Parcel> allParcels = new ArrayList<>(); // tout ce qui vient de Firestore
    private String cropFilter = null;                          // null = "Toutes"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParcelListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Liste
        adapter = new ParcelAdapter(parcel -> {
            Intent intent = new Intent(this, ParcelDetailActivity.class);
            intent.putExtra(EXTRA_PARCEL_ID, parcel.getParcelId());
            startActivity(intent);
        });
        binding.recyclerParcels.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerParcels.setAdapter(adapter);

        // Bouton "+" réservé à l'agriculteur
        binding.fabAdd.setVisibility(AppHelper.isFarmer(this) ? View.VISIBLE : View.GONE);
        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, ParcelDetailActivity.class)));

        // Recherche : on filtre à chaque lettre tapée
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        createFilterChips();
        listenToParcels();
        AppHelper.setupBottomNav(this, binding.bottomNav, R.id.nav_parcels);
    }

    /** Puces "Toutes" + une puce par culture. */
    private void createFilterChips() {
        String[] codes = getResources().getStringArray(R.array.crop_codes);
        String[] labels = getResources().getStringArray(R.array.crop_labels);

        addChip(getString(R.string.parcels_filter_all), null, true);
        for (int i = 0; i < codes.length; i++) {
            addChip(labels[i], codes[i], false);
        }
    }

    private void addChip(String label, String cropCode, boolean checked) {
        // La puce est créée à partir du petit layout item_filter_chip.xml (style vert)
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, binding.chipGroup, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnClickListener(v -> {
            cropFilter = cropCode;
            applyFilters();
        });
        binding.chipGroup.addView(chip);
    }

    /**
     * Écoute en temps réel les parcelles de la ferme.
     * Firestore garde aussi une copie locale : la liste s'affiche même hors ligne.
     */
    private void listenToParcels() {
        FirebaseFirestore.getInstance().collection("parcels")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, AppHelper.errorMessage(this, error), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshots == null) {
                        return;
                    }
                    allParcels.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        allParcels.add(doc.toObject(Parcel.class));
                    }
                    // Tri alphabétique par nom
                    allParcels.sort((a, b) -> String.valueOf(a.getName())
                            .compareToIgnoreCase(String.valueOf(b.getName())));
                    applyFilters();
                });
    }

    /** Applique la recherche texte + le filtre culture, puis met à jour la liste. */
    private void applyFilters() {
        String search = AppHelper.textOf(binding.etSearch).toLowerCase(Locale.ROOT);
        List<Parcel> visible = new ArrayList<>();
        for (Parcel parcel : allParcels) {
            boolean matchCrop = cropFilter == null || cropFilter.equals(parcel.getCrop());
            boolean matchText = search.isEmpty()
                    || String.valueOf(parcel.getName()).toLowerCase(Locale.ROOT).contains(search);
            if (matchCrop && matchText) {
                visible.add(parcel);
            }
        }
        adapter.setParcels(visible);
        binding.tvEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
