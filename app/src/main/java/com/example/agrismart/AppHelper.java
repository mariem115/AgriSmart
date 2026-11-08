package com.example.agrismart;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.example.agrismart.model.Parcel;
import com.example.agrismart.model.User;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Petites fonctions utilisées par plusieurs écrans :
 * session (rôle, ferme), langue, navigation, dates, nombres, parcelles, météo, erreurs.
 */
public class AppHelper {

    private static final String PREFS = "agrismart_session";
    private static final OkHttpClient HTTP = new OkHttpClient();

    // =====================================================================
    // Session (sauvegardée après la connexion)
    // =====================================================================

    public static void saveSession(Context context, User user) {
        prefs(context).edit()
                .putString("uid", user.getUid())
                .putString("name", user.getName())
                .putString("role", user.getRole())
                .putString("farmId", user.getFarmId())
                .apply();
    }

    public static void clearSession(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static String getUid(Context context) {
        return prefs(context).getString("uid", "");
    }

    public static String getUserName(Context context) {
        return prefs(context).getString("name", "");
    }

    public static void setUserName(Context context, String name) {
        prefs(context).edit().putString("name", name).apply();
    }

    public static String getFarmId(Context context) {
        return prefs(context).getString("farmId", null);
    }

    public static boolean isFarmer(Context context) {
        return User.ROLE_FARMER.equals(prefs(context).getString("role", ""));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // =====================================================================
    // Langue (FR / AR)
    // =====================================================================

    /** Change la langue de toute l'application ("fr" ou "ar"). Les écrans sont recréés. */
    public static void setLanguage(String language) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
    }

    /** Langue actuelle : "ar" si l'arabe est choisi, sinon "fr". */
    public static String getCurrentLanguage() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        Locale locale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        return (locale != null && "ar".equals(locale.getLanguage())) ? "ar" : "fr";
    }

    /** Met en vert la langue active dans un sélecteur "FR | عربي". */
    public static void showLanguagePill(Context context, TextView french, TextView arabic) {
        boolean isArabic = "ar".equals(getCurrentLanguage());
        styleLanguage(context, french, !isArabic);
        styleLanguage(context, arabic, isArabic);
        french.setOnClickListener(v -> setLanguage("fr"));
        arabic.setOnClickListener(v -> setLanguage("ar"));
    }

    private static void styleLanguage(Context context, TextView view, boolean selected) {
        view.setBackgroundTintList(ContextCompat.getColorStateList(context,
                selected ? R.color.green_primary : R.color.white));
        view.setTextColor(ContextCompat.getColor(context, selected ? R.color.white : R.color.text_primary));
        view.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    // =====================================================================
    // Barre de navigation du bas
    // =====================================================================

    /**
     * Branche la barre du bas : chaque onglet ouvre son écran.
     * La pile reste toujours "Tableau de bord -> écran", donc Retour ramène au tableau de bord.
     */
    public static void setupBottomNav(Activity activity, BottomNavigationView nav, int currentItemId) {
        nav.setSelectedItemId(currentItemId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentItemId) {
                return true;
            }

            Class<?> target;
            if (id == R.id.nav_parcels) {
                target = ParcelListActivity.class;
            } else if (id == R.id.nav_scan) {
                target = PlantScanActivity.class;
            } else if (id == R.id.nav_tasks) {
                target = TasksActivity.class;
            } else if (id == R.id.nav_assistant) {
                target = AssistantActivity.class;
            } else {
                target = DashboardActivity.class;
            }

            Intent intent = new Intent(activity, target);
            if (target == DashboardActivity.class) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            activity.startActivity(intent);
            if (!(activity instanceof DashboardActivity)) {
                activity.finish();
            }
            return false; // l'onglet sélectionné reste celui de l'écran courant
        });
    }

    // =====================================================================
    // Codes <-> libellés traduits
    // =====================================================================

    /**
     * Libellé traduit d'un code, ex : "olive" -> "Olivier" (ou "زيتون").
     * codesRes et labelsRes sont deux tableaux dans le même ordre (arrays.xml / strings.xml).
     */
    public static String labelOf(Context context, int codesRes, int labelsRes, String code) {
        String[] codes = context.getResources().getStringArray(codesRes);
        String[] labels = context.getResources().getStringArray(labelsRes);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(code)) {
                return labels[i];
            }
        }
        return code == null ? "" : code;
    }

    /** Colore la pastille d'état et l'icône d'une parcelle (vert / orange / rouge). */
    public static void showHealth(Context context, String health, TextView badge, ImageView icon) {
        int strong = R.color.green_primary;
        int soft = R.color.green_soft;
        if (Parcel.HEALTH_WATCH.equals(health)) {
            strong = R.color.orange;
            soft = R.color.yellow_soft;
        } else if (Parcel.HEALTH_SICK.equals(health)) {
            strong = R.color.red;
            soft = R.color.red_soft;
        }
        badge.setText(labelOf(context, R.array.health_codes, R.array.health_labels, health));
        setBadgeColors(context, badge, strong, soft);
        if (icon != null) {
            icon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, soft)));
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, strong)));
        }
    }

    /** Couleur d'une pastille (texte foncé sur fond clair). */
    public static void setBadgeColors(Context context, TextView badge, int textColorRes, int bgColorRes) {
        badge.setTextColor(ContextCompat.getColor(context, textColorRes));
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, bgColorRes)));
    }

    // =====================================================================
    // Nombres et textes
    // =====================================================================

    /** Texte d'un champ, sans espaces autour. */
    public static String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    /** Lit un nombre saisi ("3,5" ou "3.5"). Renvoie null si le texte n'est pas un nombre. */
    public static Double parseNumber(String text) {
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Affiche un nombre sans zéros inutiles : 3.50 -> "3,5". */
    public static String formatNumber(double value) {
        return new DecimalFormat("0.##").format(value);
    }

    /** Montant avec séparateur de milliers : 4250 -> "4 250". */
    public static String formatMoney(double value) {
        return new DecimalFormat("#,##0").format(value);
    }

    /** Initiales d'un nom : "Ali Mansour" -> "AM". */
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        StringBuilder result = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (result.length() < 2) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return result.toString();
    }

    /** Code de ferme de 6 caractères faciles à lire (sans O/0, I/1). */
    public static String generateFarmCode() {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(letters.charAt(random.nextInt(letters.length())));
        }
        return code.toString();
    }

    // =====================================================================
    // Dates
    // =====================================================================

    /** Date lisible dans la langue de l'appareil, ex : "8 nov. 2026". */
    public static String formatDate(Date date) {
        return date == null ? "" : DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
    }

    /** Clé du jour, ex : "2026-11-08" (utilisée pour les présences). */
    public static String dayKey(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    /** Vrai si les deux dates tombent le même jour. */
    public static boolean sameDay(Date a, Date b) {
        return a != null && b != null && dayKey(a).equals(dayKey(b));
    }

    /** Début du mois courant (1er jour à 00:00). */
    public static Date startOfMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /** Callback simple pour le choix d'une date. */
    public interface DateCallback {
        void onDate(Date date);
    }

    /**
     * Ouvre le calendrier Material et renvoie la date choisie (à midi, heure locale).
     * Le calendrier travaille en UTC : on convertit vers le fuseau du téléphone.
     */
    public static void pickDate(AppCompatActivity activity, Date initial, DateCallback callback) {
        Calendar local = Calendar.getInstance();
        local.setTime(initial == null ? new Date() : initial);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(utc.getTimeInMillis())
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar chosenUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            chosenUtc.setTimeInMillis(selection);
            Calendar chosen = Calendar.getInstance();
            chosen.clear();
            chosen.set(chosenUtc.get(Calendar.YEAR), chosenUtc.get(Calendar.MONTH),
                    chosenUtc.get(Calendar.DAY_OF_MONTH), 12, 0);
            callback.onDate(chosen.getTime());
        });
        picker.show(activity.getSupportFragmentManager(), "datePicker");
    }

    // =====================================================================
    // Parcelles (utilisées dans beaucoup de listes déroulantes)
    // =====================================================================

    public interface ParcelsCallback {
        void onParcels(List<Parcel> parcels);
    }

    /** Charge toutes les parcelles de la ferme, triées par nom. */
    public static void loadParcels(Context context, ParcelsCallback callback) {
        FirebaseFirestore.getInstance().collection("parcels")
                .whereEqualTo("farmId", getFarmId(context))
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Parcel> parcels = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        parcels.add(doc.toObject(Parcel.class));
                    }
                    parcels.sort((a, b) -> String.valueOf(a.getName())
                            .compareToIgnoreCase(String.valueOf(b.getName())));
                    callback.onParcels(parcels);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, errorMessage(context, e), Toast.LENGTH_LONG).show();
                    callback.onParcels(new ArrayList<>());
                });
    }

    /** Noms des parcelles, pour remplir une liste déroulante. */
    public static String[] parcelNames(List<Parcel> parcels) {
        String[] names = new String[parcels.size()];
        for (int i = 0; i < parcels.size(); i++) {
            names[i] = parcels.get(i).getName();
        }
        return names;
    }

    // =====================================================================
    // Météo OpenWeather (requête HTTP avec OkHttp)
    // =====================================================================

    public interface JsonCallback {
        /** json = null en cas d'erreur (pas de réseau, mauvaise clé...). */
        void onResult(JSONObject json);
    }

    /** Clé OpenWeather lue dans local.properties (via BuildConfig). */
    public static boolean hasWeatherKey() {
        return !BuildConfig.OPENWEATHER_API_KEY.isEmpty();
    }

    /**
     * Appelle une URL OpenWeather, ex : "forecast?lat=34.7&lon=10.7".
     * La réponse est renvoyée sur le thread principal (on peut toucher à l'écran).
     */
    public static void getWeather(String pathAndQuery, JsonCallback callback) {
        Handler mainThread = new Handler(Looper.getMainLooper());
        String url = "https://api.openweathermap.org/data/2.5/" + pathAndQuery
                + "&units=metric&lang=" + getCurrentLanguage()
                + "&appid=" + BuildConfig.OPENWEATHER_API_KEY;

        HTTP.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainThread.post(() -> callback.onResult(null));
            }

            @Override
            public void onResponse(Call call, Response response) {
                JSONObject json = null;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        json = new JSONObject(response.body().string());
                    }
                } catch (Exception ignored) {
                    // json reste null -> message d'erreur à l'écran
                } finally {
                    response.close();
                }
                JSONObject result = json;
                mainThread.post(() -> callback.onResult(result));
            }
        });
    }

    // =====================================================================
    // Graphiques (MPAndroidChart)
    // =====================================================================

    /** Style commun des graphiques en barres : sobre, sans grille, légende en haut. */
    public static void styleBarChart(Context context, BarChart chart) {
        int textColor = ContextCompat.getColor(context, R.color.text_secondary);
        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setTextColor(textColor);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setTextColor(textColor);
        chart.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        chart.getLegend().setTextColor(textColor);
        chart.setTouchEnabled(false);
        chart.setNoDataText(context.getString(R.string.chart_no_data));
        chart.setNoDataTextColor(textColor);
    }

    // =====================================================================
    // Messages d'erreur
    // =====================================================================

    /** Transforme une erreur Firebase en message clair pour l'utilisateur. */
    public static String errorMessage(Context context, Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return context.getString(R.string.error_network);
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            return context.getString(R.string.error_weak_password);
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return context.getString(R.string.error_email_used);
        } else if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException) {
            return context.getString(R.string.error_wrong_credentials);
        }
        return context.getString(R.string.error_unknown, e.getLocalizedMessage());
    }

    /** Petit message en bas de l'écran. */
    public static void toast(Context context, String message) {
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}
