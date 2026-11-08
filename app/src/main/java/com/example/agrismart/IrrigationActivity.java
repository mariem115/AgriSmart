package com.example.agrismart;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.agrismart.databinding.ActivityIrrigationBinding;
import com.example.agrismart.databinding.ItemForecastDayBinding;
import com.example.agrismart.databinding.ItemHistoryBinding;
import com.example.agrismart.model.Irrigation;
import com.example.agrismart.model.Parcel;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Irrigation : météo 3 jours de la parcelle (API OpenWeather) + conseil d'arrosage.
 * Règle simple : PAS de pluie prévue dans les 24 h ET sol sec -> arroser.
 * "Sol sec" = pas d'arrosage enregistré depuis 48 h (pas de capteur d'humidité).
 */
public class IrrigationActivity extends AppCompatActivity {

    private static final long SOIL_DRY_AFTER_MS = 48L * 60 * 60 * 1000; // 48 heures

    private ActivityIrrigationBinding binding;
    private FirebaseFirestore db;

    private List<Parcel> parcels = new ArrayList<>();
    private Parcel parcel;                                  // parcelle choisie
    private final List<Irrigation> history = new ArrayList<>();

    // Résultat du dernier calcul
    private double suggestedQuantity;
    private String adviceText = "";

    /** Météo résumée d'une journée. */
    private static class DayForecast {
        Date date;
        double maxTemp = -100;
        double maxRainChance = 0; // 0 à 1
        boolean rain;
        boolean clouds;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIrrigationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnDone.setOnClickListener(v -> markAsDone());

        AppHelper.loadParcels(this, loaded -> {
            parcels = loaded;
            if (parcels.isEmpty()) {
                showStatus(getString(R.string.need_parcel_first));
                return;
            }
            binding.dropParcel.setSimpleItems(AppHelper.parcelNames(parcels));
            binding.dropParcel.setOnItemClickListener((p, v, position, id) -> selectParcel(parcels.get(position)));
            // Par défaut : la première parcelle
            binding.dropParcel.setText(parcels.get(0).getName(), false);
            selectParcel(parcels.get(0));
        });
    }

    private void selectParcel(Parcel selected) {
        parcel = selected;
        binding.layoutWeather.setVisibility(View.GONE);
        binding.cardAdvice.setVisibility(View.GONE);
        binding.btnDone.setEnabled(false);
        loadHistory();
    }

    private void showStatus(String message) {
        binding.tvStatus.setVisibility(message == null ? View.GONE : View.VISIBLE);
        binding.tvStatus.setText(message);
    }

    // =====================================================================
    // 1) Historique des arrosages de la parcelle
    // =====================================================================

    private void loadHistory() {
        db.collection("irrigations")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .whereEqualTo("parcelId", parcel.getParcelId())
                .get()
                .addOnSuccessListener(snapshots -> {
                    history.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        history.add(doc.toObject(Irrigation.class));
                    }
                    history.sort((a, b) -> Long.compare(time(b.getDate()), time(a.getDate())));
                    showHistory();
                    loadWeather();
                })
                .addOnFailureListener(e -> showStatus(AppHelper.errorMessage(this, e)));
    }

    private static long time(Date date) {
        return date == null ? 0 : date.getTime();
    }

    private void showHistory() {
        binding.layoutHistory.removeAllViews();
        SimpleDateFormat format = new SimpleDateFormat("d MMM · HH:mm", Locale.getDefault());
        for (int i = 0; i < Math.min(5, history.size()); i++) {
            Irrigation irrigation = history.get(i);
            ItemHistoryBinding row = ItemHistoryBinding.inflate(getLayoutInflater(), binding.layoutHistory, false);
            row.ivIcon.setImageResource(R.drawable.ic_water);
            row.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue)));
            row.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_soft)));
            row.tvText.setText(irrigation.getDate() == null ? "" : format.format(irrigation.getDate()));
            row.tvDate.setText(getString(R.string.irrigation_quantity_value,
                    AppHelper.formatNumber(irrigation.getQuantity())));
            binding.layoutHistory.addView(row.getRoot());
        }
        binding.tvHistoryEmpty.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // 2) Météo OpenWeather (prévisions toutes les 3 heures sur 5 jours)
    // =====================================================================

    private void loadWeather() {
        if (!AppHelper.hasWeatherKey()) {
            showStatus(getString(R.string.weather_no_key));
            return;
        }
        if (parcel.getLatitude() == null || parcel.getLongitude() == null) {
            showStatus(getString(R.string.irrigation_no_gps));
            return;
        }
        showStatus(getString(R.string.loading));
        Parcel requested = parcel;
        AppHelper.getWeather("forecast?lat=" + parcel.getLatitude() + "&lon=" + parcel.getLongitude(), json -> {
            if (requested != parcel) {
                return; // l'utilisateur a changé de parcelle entre-temps
            }
            if (json == null) {
                showStatus(getString(R.string.weather_unavailable));
                return;
            }
            showStatus(null);
            analyzeWeather(json);
        });
    }

    /** Lit la réponse JSON, affiche 3 jours et calcule le conseil. */
    private void analyzeWeather(JSONObject json) {
        JSONArray list = json.optJSONArray("list");
        if (list == null || list.length() == 0) {
            showStatus(getString(R.string.weather_unavailable));
            return;
        }

        double rainNext24h = 0;       // millimètres prévus dans les 24 h
        double rainChanceNext24h = 0; // probabilité max de pluie dans les 24 h
        Map<String, DayForecast> days = new LinkedHashMap<>();

        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            Date date = new Date(entry.optLong("dt") * 1000);
            double temp = entry.optJSONObject("main") != null ? entry.optJSONObject("main").optDouble("temp", 0) : 0;
            double rainChance = entry.optDouble("pop", 0);
            JSONObject rainObject = entry.optJSONObject("rain");
            double rainMm = rainObject != null ? rainObject.optDouble("3h", 0) : 0;
            JSONArray weather = entry.optJSONArray("weather");
            String main = (weather != null && weather.optJSONObject(0) != null)
                    ? weather.optJSONObject(0).optString("main") : "";

            // Les 8 premières prévisions = les 24 prochaines heures (8 x 3 h)
            if (i < 8) {
                rainNext24h += rainMm;
                rainChanceNext24h = Math.max(rainChanceNext24h, rainChance);
            }

            String key = AppHelper.dayKey(date);
            DayForecast day = days.get(key);
            if (day == null) {
                if (days.size() == 3) {
                    continue; // on garde seulement 3 jours
                }
                day = new DayForecast();
                day.date = date;
                days.put(key, day);
            }
            day.maxTemp = Math.max(day.maxTemp, temp);
            day.maxRainChance = Math.max(day.maxRainChance, rainChance);
            day.rain = day.rain || main.equals("Rain") || main.equals("Drizzle") || main.equals("Thunderstorm");
            day.clouds = day.clouds || main.equals("Clouds");
        }

        showForecast(new ArrayList<>(days.values()));

        double todayMaxTemp = days.isEmpty() ? 0 : days.values().iterator().next().maxTemp;
        boolean rainExpected = rainNext24h >= 1.0 || rainChanceNext24h >= 0.6;
        computeAdvice(rainExpected, todayMaxTemp);
    }

    private void showForecast(List<DayForecast> days) {
        binding.layoutDays.removeAllViews();
        SimpleDateFormat dayName = new SimpleDateFormat("EEEE", Locale.getDefault());
        for (int i = 0; i < days.size(); i++) {
            DayForecast day = days.get(i);
            ItemForecastDayBinding column = ItemForecastDayBinding.inflate(getLayoutInflater(), binding.layoutDays, false);
            column.tvDay.setText(i == 0 ? getString(R.string.today) : dayName.format(day.date));
            column.ivWeather.setImageResource(day.rain ? R.drawable.ic_umbrella
                    : (day.clouds ? R.drawable.ic_cloud : R.drawable.ic_sun));
            column.tvTemp.setText(getString(R.string.temperature_value, Math.round(day.maxTemp)));
            column.tvRain.setText(getString(R.string.weather_rain_chance, Math.round(day.maxRainChance * 100)));
            binding.layoutDays.addView(column.getRoot());
        }
        binding.layoutWeather.setVisibility(View.VISIBLE);
    }

    // =====================================================================
    // 3) Conseil d'arrosage (règle simple)
    // =====================================================================

    private void computeAdvice(boolean rainExpected, double todayMaxTemp) {
        Date lastWatering = history.isEmpty() ? null : history.get(0).getDate();
        boolean soilDry = lastWatering == null
                || new Date().getTime() - lastWatering.getTime() > SOIL_DRY_AFTER_MS;

        boolean shouldWater = !rainExpected && soilDry;

        // Quantité : valeur de base selon la culture, +20 % s'il fait plus de 30 °C
        suggestedQuantity = baseQuantity(parcel.getCrop());
        if (todayMaxTemp > 30) {
            suggestedQuantity = Math.round(suggestedQuantity * 1.2);
        }

        int ruleText;
        if (shouldWater) {
            ruleText = R.string.irrigation_rule_water;
        } else if (rainExpected) {
            ruleText = R.string.irrigation_rule_rain;
        } else {
            ruleText = R.string.irrigation_rule_wet;
        }
        adviceText = getString(shouldWater ? R.string.irrigation_water_today : R.string.irrigation_no_water);

        binding.tvAdvice.setText(adviceText);
        binding.tvAdvice.setTextColor(ContextCompat.getColor(this, shouldWater ? R.color.blue : R.color.green_primary));
        binding.tvQuantity.setText(shouldWater
                ? getString(R.string.irrigation_quantity_value, AppHelper.formatNumber(suggestedQuantity))
                : getString(R.string.placeholder_dash));
        binding.tvSoil.setText(soilDry ? R.string.irrigation_soil_dry : R.string.irrigation_soil_wet);
        binding.tvRule.setText(ruleText);
        binding.cardAdvice.setVisibility(View.VISIBLE);
        binding.btnDone.setEnabled(true);
    }

    /** Besoin en eau indicatif (litres / m²) selon la culture. */
    private static double baseQuantity(String crop) {
        if ("olive".equals(crop)) {
            return 30;
        } else if ("citrus".equals(crop)) {
            return 35;
        } else if ("date_palm".equals(crop)) {
            return 40;
        } else if ("cereal".equals(crop)) {
            return 20;
        }
        return 25;
    }

    // =====================================================================
    // 4) "Valider : arrosage effectué" -> enregistré dans Firestore
    // =====================================================================

    private void markAsDone() {
        if (parcel == null) {
            return;
        }
        DocumentReference ref = db.collection("irrigations").document();
        Irrigation irrigation = new Irrigation();
        irrigation.setIrrigId(ref.getId());
        irrigation.setFarmId(AppHelper.getFarmId(this));
        irrigation.setParcelId(parcel.getParcelId());
        irrigation.setParcelName(parcel.getName());
        irrigation.setDate(new Date());
        irrigation.setQuantity(suggestedQuantity);
        irrigation.setAdvice(adviceText);
        irrigation.setDone(true);
        ref.set(irrigation).addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));

        AppHelper.toast(this, getString(R.string.irrigation_saved));
        // Mise à jour immédiate de l'écran (le sol devient "humide")
        history.add(0, irrigation);
        showHistory();
        loadWeather();
    }
}
