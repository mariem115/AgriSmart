package com.example.agrismart;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.agrismart.adapter.StockAdapter;
import com.example.agrismart.adapter.TaskAdapter;
import com.example.agrismart.databinding.ActivityDashboardBinding;
import com.example.agrismart.databinding.ItemAlertBinding;
import com.example.agrismart.databinding.ItemDashboardTaskBinding;
import com.example.agrismart.model.Farm;
import com.example.agrismart.model.Parcel;
import com.example.agrismart.model.StockItem;
import com.example.agrismart.model.Task;
import com.example.agrismart.model.Transaction;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Tableau de bord : météo, alertes, tâches du jour et bénéfice du mois.
 * L'ouvrier voit seulement la météo, ses tâches du jour et les alertes des parcelles.
 */
public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;
    private FirebaseFirestore db;
    private boolean farmer;
    private Farm farm;

    // Les alertes viennent de plusieurs sources : on les garde séparées puis on les affiche ensemble
    private final List<Alert> stockAlerts = new ArrayList<>();
    private final List<Alert> parcelAlerts = new ArrayList<>();
    private final List<Alert> weatherAlerts = new ArrayList<>();

    /** Une ligne d'alerte. */
    private static class Alert {
        final String title;
        final String subtitle;
        final int iconRes;
        final int colorRes;
        final int softColorRes;
        final Class<?> target;

        Alert(String title, String subtitle, int iconRes, int colorRes, int softColorRes, Class<?> target) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.colorRes = colorRes;
            this.softColorRes = softColorRes;
            this.target = target;
        }
    }

    // Android 13+ : autorisation d'afficher des notifications
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();
        farmer = AppHelper.isFarmer(this);

        binding.tvGreeting.setText(getString(R.string.dashboard_greeting, AppHelper.getUserName(this)));
        binding.tvAvatar.setText(AppHelper.initials(AppHelper.getUserName(this)));
        showWeatherPlaceholder();

        // L'ouvrier ne voit ni le bénéfice, ni les raccourcis de gestion, ni le code de la ferme
        int farmerOnly = farmer ? View.VISIBLE : View.GONE;
        binding.cardProfit.setVisibility(farmerOnly);
        binding.layoutQuickActions.setVisibility(farmerOnly);
        binding.cardFarmCode.setVisibility(farmerOnly);

        setupClicks();
        askNotificationPermission();
        NotificationHelper.createChannel(this);
        if (!farmer) {
            // Notification locale à chaque nouvelle tâche assignée à cet ouvrier
            NotificationHelper.startTaskListener(this);
        }

        loadFarmAndWeather();
        listenToTodayTasks();
        listenToParcelAlerts();
        if (farmer) {
            listenToStockAlerts();
            loadMonthProfit();
        }
        AppHelper.setupBottomNav(this, binding.bottomNav, R.id.nav_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Le nom a pu changer dans le profil
        binding.tvGreeting.setText(getString(R.string.dashboard_greeting, AppHelper.getUserName(this)));
        binding.tvAvatar.setText(AppHelper.initials(AppHelper.getUserName(this)));
    }

    private void setupClicks() {
        binding.actionIrrigation.setOnClickListener(v -> open(IrrigationActivity.class));
        binding.actionWorkers.setOnClickListener(v -> open(WorkersActivity.class));
        binding.actionStock.setOnClickListener(v -> open(StockActivity.class));
        binding.actionFinance.setOnClickListener(v -> open(FinanceActivity.class));
        binding.actionHarvest.setOnClickListener(v -> open(HarvestActivity.class));
        binding.cardProfit.setOnClickListener(v -> open(FinanceActivity.class));
        binding.cardTasks.setOnClickListener(v -> open(TasksActivity.class));
        binding.tvSeeAllTasks.setOnClickListener(v -> open(TasksActivity.class));
        binding.tvAvatar.setOnClickListener(v -> ProfileBottomSheet.show(this));
        // La cloche fait défiler jusqu'aux alertes
        binding.btnNotifications.setOnClickListener(v ->
                binding.scrollView.smoothScrollTo(0, binding.layoutAlerts.getTop()));

        // Appui sur le code de la ferme -> copié dans le presse-papiers
        binding.cardFarmCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String code = binding.tvFarmCode.getText().toString();
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.farm_code_title), code));
            AppHelper.toast(this, getString(R.string.farm_code_copied));
        });
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // =====================================================================
    // Ferme + météo actuelle (OpenWeather, par nom de région)
    // =====================================================================

    private void loadFarmAndWeather() {
        String farmId = AppHelper.getFarmId(this);
        if (farmId == null) {
            return;
        }
        db.collection("farms").document(farmId).get()
                .addOnSuccessListener(doc -> {
                    farm = doc.toObject(Farm.class);
                    if (farm == null) {
                        return;
                    }
                    binding.tvFarm.setText(getString(R.string.dashboard_farm_line, farm.getName(), farm.getRegion()));
                    binding.tvWeatherPlace.setText(getString(R.string.weather_today, farm.getRegion()));
                    binding.tvFarmCode.setText(farm.getFarmId());
                    loadWeather(farm.getRegion());
                })
                .addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));
    }

    private void showWeatherPlaceholder() {
        String dash = getString(R.string.placeholder_dash);
        binding.tvWeatherTemp.setText(R.string.weather_placeholder_temp);
        binding.tvHumidity.setText(getString(R.string.weather_humidity, dash));
        binding.tvWind.setText(getString(R.string.weather_wind, dash));
        binding.tvRain.setText(getString(R.string.weather_rain, dash));
    }

    private void loadWeather(String region) {
        if (!AppHelper.hasWeatherKey()) {
            binding.tvWeatherDesc.setText(R.string.weather_no_key);
            return;
        }
        // "q=Sfax,TN" : la ville de la région, en Tunisie
        AppHelper.getWeather("weather?q=" + Uri.encode(region + ",TN"), json -> {
            if (json == null) {
                binding.tvWeatherDesc.setText(R.string.weather_unavailable);
                return;
            }
            JSONObject main = json.optJSONObject("main");
            JSONObject wind = json.optJSONObject("wind");
            JSONObject rain = json.optJSONObject("rain");
            JSONArray weather = json.optJSONArray("weather");

            double temp = main != null ? main.optDouble("temp", 0) : 0;
            int humidity = main != null ? main.optInt("humidity", 0) : 0;
            double windKmh = wind != null ? wind.optDouble("speed", 0) * 3.6 : 0; // m/s -> km/h
            double rainMm = rain != null ? rain.optDouble("1h", 0) : 0;
            String description = "";
            String mainType = "";
            if (weather != null && weather.optJSONObject(0) != null) {
                description = weather.optJSONObject(0).optString("description");
                mainType = weather.optJSONObject(0).optString("main");
            }

            binding.tvWeatherTemp.setText(getString(R.string.temperature_value, Math.round(temp)));
            binding.tvWeatherDesc.setText(description.isEmpty() ? ""
                    : description.substring(0, 1).toUpperCase() + description.substring(1));
            binding.tvHumidity.setText(getString(R.string.weather_humidity, humidity + " %"));
            binding.tvWind.setText(getString(R.string.weather_wind, Math.round(windKmh) + " km/h"));
            binding.tvRain.setText(getString(R.string.weather_rain, AppHelper.formatNumber(rainMm) + " mm"));
            boolean rainy = mainType.equals("Rain") || mainType.equals("Drizzle") || mainType.equals("Thunderstorm");
            binding.ivWeather.setImageResource(rainy ? R.drawable.ic_umbrella
                    : (mainType.equals("Clouds") ? R.drawable.ic_cloud : R.drawable.ic_sun));

            // Alertes météo simples : vent fort (pulvérisation déconseillée) ou pluie
            weatherAlerts.clear();
            if (windKmh >= 40) {
                weatherAlerts.add(new Alert(getString(R.string.alert_wind_title, Math.round(windKmh)),
                        getString(R.string.alert_wind_text), R.drawable.ic_air,
                        R.color.orange, R.color.yellow_soft, null));
            }
            if (rainy) {
                weatherAlerts.add(new Alert(getString(R.string.alert_rain_title),
                        getString(R.string.alert_rain_text), R.drawable.ic_umbrella,
                        R.color.blue, R.color.blue_soft, farmer ? IrrigationActivity.class : null));
            }
            showAlerts();
        });
    }

    // =====================================================================
    // Alertes : stock bas (agriculteur) + parcelles malades
    // =====================================================================

    private void listenToStockAlerts() {
        db.collection("stock").whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    stockAlerts.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        StockItem item = doc.toObject(StockItem.class);
                        if (StockAdapter.isLow(item)) {
                            stockAlerts.add(new Alert(getString(R.string.alert_stock_title, item.getName()),
                                    getString(R.string.alert_stock_text,
                                            AppHelper.formatNumber(item.getQuantity()), item.getUnit(),
                                            AppHelper.formatNumber(item.getMinThreshold()), item.getUnit()),
                                    R.drawable.ic_inventory, R.color.red, R.color.red_soft, StockActivity.class));
                            NotificationHelper.notifyLowStock(this, item); // 1 fois par jour max
                        }
                    }
                    showAlerts();
                });
    }

    private void listenToParcelAlerts() {
        db.collection("parcels").whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    parcelAlerts.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Parcel parcel = doc.toObject(Parcel.class);
                        if (Parcel.HEALTH_SICK.equals(parcel.getHealthStatus())) {
                            parcelAlerts.add(new Alert(getString(R.string.alert_sick_title, parcel.getName()),
                                    getString(R.string.alert_sick_text), R.drawable.ic_warning,
                                    R.color.red, R.color.red_soft, PlantScanActivity.class));
                        } else if (Parcel.HEALTH_WATCH.equals(parcel.getHealthStatus())) {
                            parcelAlerts.add(new Alert(getString(R.string.alert_watch_title, parcel.getName()),
                                    getString(R.string.alert_watch_text), R.drawable.ic_leaf,
                                    R.color.orange, R.color.yellow_soft, ParcelListActivity.class));
                        }
                    }
                    showAlerts();
                });
    }

    /** Affiche toutes les alertes (5 au maximum). */
    private void showAlerts() {
        List<Alert> all = new ArrayList<>();
        all.addAll(stockAlerts);
        all.addAll(parcelAlerts);
        all.addAll(weatherAlerts);

        binding.layoutAlerts.removeAllViews();
        for (int i = 0; i < Math.min(5, all.size()); i++) {
            Alert alert = all.get(i);
            ItemAlertBinding row = ItemAlertBinding.inflate(getLayoutInflater(), binding.layoutAlerts, false);
            row.ivIcon.setImageResource(alert.iconRes);
            row.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, alert.colorRes)));
            row.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, alert.softColorRes)));
            row.tvTitle.setText(alert.title);
            row.tvSubtitle.setText(alert.subtitle);
            if (alert.target != null) {
                row.getRoot().setOnClickListener(v -> open(alert.target));
            }
            binding.layoutAlerts.addView(row.getRoot());
        }
        binding.tvAlertsEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // Tâches du jour
    // =====================================================================

    private void listenToTodayTasks() {
        Query query = db.collection("tasks").whereEqualTo("farmId", AppHelper.getFarmId(this));
        if (!farmer) {
            query = query.whereEqualTo("assignedTo", AppHelper.getUid(this));
        }
        query.addSnapshotListener(this, (snapshots, error) -> {
            if (error != null || snapshots == null) {
                return;
            }
            Date today = new Date();
            List<Task> todayTasks = new ArrayList<>();
            int toDo = 0;
            int done = 0;
            for (QueryDocumentSnapshot doc : snapshots) {
                Task task = doc.toObject(Task.class);
                if (!AppHelper.sameDay(task.getDueDate(), today)) {
                    continue;
                }
                todayTasks.add(task);
                if (Task.STATUS_DONE.equals(task.getStatus())) {
                    done++;
                } else {
                    toDo++;
                }
            }
            binding.tvTasksCount.setText(getString(R.string.dashboard_tasks_todo, toDo));
            binding.tvTasksDone.setText(getString(R.string.dashboard_tasks_done, done));
            showTodayTasks(todayTasks);
        });
    }

    private void showTodayTasks(List<Task> tasks) {
        binding.layoutTodayTasks.removeAllViews();
        for (int i = 0; i < Math.min(4, tasks.size()); i++) {
            Task task = tasks.get(i);
            ItemDashboardTaskBinding row = ItemDashboardTaskBinding.inflate(getLayoutInflater(),
                    binding.layoutTodayTasks, false);
            row.tvTitle.setText(task.getTitle());
            String due = TaskAdapter.dueLabel(this, task.getDueDate());
            row.tvSubtitle.setText(farmer
                    ? getString(R.string.tasks_assignee_due, String.valueOf(task.getAssignedName()), due)
                    : getString(R.string.tasks_assignee_due, String.valueOf(task.getParcelName()), due));
            TaskAdapter.showStatus(this, task.getStatus(), row.tvStatus);
            row.getRoot().setOnClickListener(v -> open(TasksActivity.class));
            binding.layoutTodayTasks.addView(row.getRoot());
        }
        binding.tvTasksEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // Bénéfice du mois (agriculteur)
    // =====================================================================

    private void loadMonthProfit() {
        db.collection("transactions").whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    Date monthStart = AppHelper.startOfMonth();
                    double income = 0;
                    double expenses = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx.getDate() == null || tx.getDate().before(monthStart)) {
                            continue;
                        }
                        if (Transaction.TYPE_INCOME.equals(tx.getType())) {
                            income += tx.getAmount();
                        } else {
                            expenses += tx.getAmount();
                        }
                    }
                    double profit = income - expenses;
                    binding.tvProfit.setText(getString(R.string.money_value, AppHelper.formatMoney(profit)));
                    binding.tvProfit.setTextColor(ContextCompat.getColor(this,
                            profit < 0 ? R.color.red : R.color.green_primary));
                    binding.tvProfitDetail.setText(getString(R.string.dashboard_profit_detail,
                            AppHelper.formatMoney(income), AppHelper.formatMoney(expenses)));
                });
    }
}
