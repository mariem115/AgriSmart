package com.example.agrismart;

import android.content.Context;

import com.example.agrismart.model.Diagnostic;
import com.example.agrismart.model.Harvest;
import com.example.agrismart.model.Irrigation;
import com.example.agrismart.model.Parcel;
import com.example.agrismart.model.StockItem;
import com.example.agrismart.model.Task;
import com.example.agrismart.model.Transaction;
import com.example.agrismart.model.Worker;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Remplit Firestore avec des données de démonstration tunisiennes réalistes
 * (oliviers, agrumes, palmiers dattiers, blé...). Bouton visible seulement
 * dans la version DEBUG, pour l'agriculteur (profil). Ne pas utiliser en production.
 */
public class DemoDataSeeder {

    private static FirebaseFirestore db;
    private static WriteBatch batch;
    private static String farmId;

    public static void seed(Context context, Runnable onDone) {
        db = FirebaseFirestore.getInstance();
        farmId = AppHelper.getFarmId(context);

        // On lit d'abord les ouvriers qui ont déjà un compte, pour leur assigner une tâche
        db.collection("workers").whereEqualTo("farmId", farmId).get()
                .addOnSuccessListener(snapshots -> {
                    List<Worker> accountWorkers = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        accountWorkers.add(doc.toObject(Worker.class));
                    }
                    writeAll(context, accountWorkers, onDone);
                })
                .addOnFailureListener(e -> AppHelper.toast(context, AppHelper.errorMessage(context, e)));
    }

    private static void writeAll(Context context, List<Worker> accountWorkers, Runnable onDone) {
        batch = db.batch();

        // ---------- Parcelles ----------
        Parcel olive = parcel("Parcelle 1 · Oliveraie Nord", "olive", 3.5, "clay", 34.7406, 10.7603, Parcel.HEALTH_HEALTHY);
        Parcel citrus = parcel("Parcelle 2 · Agrumes", "citrus", 1.8, "loam", 34.7398, 10.7600, Parcel.HEALTH_SICK);
        Parcel palm = parcel("Parcelle 3 · Palmeraie", "date_palm", 2.2, "sandy", 33.9197, 8.1335, Parcel.HEALTH_WATCH);
        Parcel wheat = parcel("Parcelle 4 · Blé dur", "cereal", 5.0, "limestone", 36.7256, 9.1817, Parcel.HEALTH_HEALTHY);

        // ---------- Ouvriers (sans compte) ----------
        Worker ali = worker("Ali Mansour", "+216 98 123 456", 45, 14, true);
        Worker salah = worker("Salah Kacem", "+216 97 456 789", 45, 12, true);
        Worker fatma = worker("Fatma Bouzid", "+216 22 987 654", 50, 15, true);
        Worker hamdi = worker("Hamdi Trabelsi", "+216 55 321 098", 40, 9, false);
        // Si un ouvrier a un compte, c'est lui qui reçoit la tâche principale (démo de notification)
        Worker main = accountWorkers.isEmpty() ? ali : accountWorkers.get(0);

        // ---------- Tâches ----------
        task("Traiter la parcelle 2 (mildiou)", citrus, main, daysFromNow(0, 17), Task.STATUS_TODO);
        task("Désherber l'oliveraie", olive, salah, daysFromNow(1, 8), Task.STATUS_TODO);
        task("Tailler les palmiers", palm, fatma, daysFromNow(3, 9), Task.STATUS_TODO);
        task("Irriguer le blé", wheat, hamdi, daysFromNow(0, 7), Task.STATUS_IN_PROGRESS);
        task("Récolter les oranges", citrus, ali, daysFromNow(-2, 10), Task.STATUS_DONE);

        // ---------- Stock (quelques articles sous le seuil) ----------
        stock("Engrais NPK 15-15-15", StockItem.TYPE_FERTILIZER, 8, 20, "kg");
        stock("Urée 46 %", StockItem.TYPE_FERTILIZER, 35, 30, "kg");
        stock("Compost organique", StockItem.TYPE_FERTILIZER, 120, 50, "kg");
        stock("Bouillie bordelaise", StockItem.TYPE_PESTICIDE, 3, 5, "kg");
        stock("Soufre mouillable", StockItem.TYPE_PESTICIDE, 12, 5, "kg");
        stock("Savon noir", StockItem.TYPE_PESTICIDE, 6, 4, "L");
        stock("Semences de blé dur", StockItem.TYPE_SEED, 150, 100, "kg");
        stock("Plants de tomate", StockItem.TYPE_SEED, 40, 50, "plants");

        // ---------- Finances : 6 derniers mois ----------
        double[] olivesSales = {0, 0, 0, 0, 1800, 6800};
        double[] citrusSales = {900, 1100, 600, 0, 0, 0};
        double[] dateSales = {0, 0, 0, 1500, 900, 0};
        double[] treatments = {120, 150, 90, 110, 160, 180};
        double[] fertilizers = {300, 0, 250, 0, 280, 420};
        double[] labor = {700, 650, 900, 800, 950, 1950};
        for (int m = 0; m < 6; m++) {
            int monthsAgo = 5 - m;
            income("Vente d'olives", olive, olivesSales[m], monthsAgo);
            income("Vente d'oranges", citrus, citrusSales[m], monthsAgo);
            income("Vente de dattes Deglet Nour", palm, dateSales[m], monthsAgo);
            expense("Traitement bio", citrus, "treatment", treatments[m], monthsAgo);
            expense("Engrais", olive, "fertilizer", fertilizers[m], monthsAgo);
            expense("Main-d'œuvre", null, "labor", labor[m], monthsAgo);
        }

        // ---------- Récoltes : 3 saisons ----------
        int year = Calendar.getInstance().get(Calendar.YEAR);
        harvest(olive, 10900, year - 2);
        harvest(olive, 11800, year - 1);
        harvest(olive, 13100, year);
        harvest(citrus, 280, year - 2);
        harvest(citrus, 300, year - 1);
        harvest(citrus, 320, year);
        harvest(palm, 1800, year - 1);
        harvest(palm, 2100, year);

        // ---------- Irrigations ----------
        irrigation(olive, 40, -3);
        irrigation(olive, 35, -7);
        irrigation(palm, 45, -1);

        // ---------- Diagnostic ----------
        DocumentReference diagRef = db.collection("diagnostics").document();
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setDiagId(diagRef.getId());
        diagnostic.setFarmId(farmId);
        diagnostic.setParcelId(citrus.getParcelId());
        diagnostic.setParcelName(citrus.getName());
        diagnostic.setPhotoUrl("");
        diagnostic.setDisease("Mildiou");
        diagnostic.setConfidence(0.87);
        diagnostic.setTreatment(context.getString(R.string.treat_mildew));
        diagnostic.setDate(daysFromNow(-2, 9));
        diagnostic.setUid(AppHelper.getUid(context));
        batch.set(diagRef, diagnostic);

        batch.commit()
                .addOnSuccessListener(v -> onDone.run())
                .addOnFailureListener(e -> AppHelper.toast(context, AppHelper.errorMessage(context, e)));
    }

    // =====================================================================
    // Petites fonctions de création
    // =====================================================================

    private static Parcel parcel(String name, String crop, double area, String soil,
                                 double lat, double lon, String health) {
        DocumentReference ref = db.collection("parcels").document();
        Parcel p = new Parcel();
        p.setParcelId(ref.getId());
        p.setFarmId(farmId);
        p.setName(name);
        p.setCrop(crop);
        p.setArea(area);
        p.setSoilType(soil);
        p.setLatitude(lat);
        p.setLongitude(lon);
        p.setHealthStatus(health);
        batch.set(ref, p);
        return p;
    }

    /** Ouvrier présent "daysPresent" jours ce mois-ci (+ aujourd'hui si presentToday). */
    private static Worker worker(String name, String phone, double salary, int daysPresent, boolean presentToday) {
        DocumentReference ref = db.collection("workers").document();
        Worker w = new Worker(ref.getId(), farmId, name, phone, salary);
        List<String> presences = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        if (presentToday) {
            presences.add(AppHelper.dayKey(cal.getTime()));
        }
        for (int day = today - 1; day >= 1 && presences.size() < daysPresent; day--) {
            cal.set(Calendar.DAY_OF_MONTH, day);
            presences.add(AppHelper.dayKey(cal.getTime()));
        }
        w.setPresences(presences);
        batch.set(ref, w);
        return w;
    }

    private static void task(String title, Parcel parcel, Worker worker, Date due, String status) {
        DocumentReference ref = db.collection("tasks").document();
        Task t = new Task();
        t.setTaskId(ref.getId());
        t.setFarmId(farmId);
        t.setParcelId(parcel.getParcelId());
        t.setParcelName(parcel.getName());
        t.setTitle(title);
        t.setAssignedTo(worker.getWorkerId());
        t.setAssignedName(worker.getName());
        t.setDueDate(due);
        t.setStatus(status);
        t.setProblemNote("");
        t.setCreatedAt(new Date());
        batch.set(ref, t);
    }

    private static void stock(String name, String type, double quantity, double threshold, String unit) {
        DocumentReference ref = db.collection("stock").document();
        StockItem item = new StockItem();
        item.setItemId(ref.getId());
        item.setFarmId(farmId);
        item.setName(name);
        item.setType(type);
        item.setQuantity(quantity);
        item.setMinThreshold(threshold);
        item.setUnit(unit);
        batch.set(ref, item);
    }

    private static void income(String label, Parcel parcel, double amount, int monthsAgo) {
        if (amount > 0) {
            transaction(label, parcel, Transaction.TYPE_INCOME, "sale", amount, monthsAgo);
        }
    }

    private static void expense(String label, Parcel parcel, String category, double amount, int monthsAgo) {
        if (amount > 0) {
            transaction(label, parcel, Transaction.TYPE_EXPENSE, category, amount, monthsAgo);
        }
    }

    private static void transaction(String label, Parcel parcel, String type, String category,
                                    double amount, int monthsAgo) {
        DocumentReference ref = db.collection("transactions").document();
        Transaction tx = new Transaction();
        tx.setTxId(ref.getId());
        tx.setFarmId(farmId);
        tx.setParcelId(parcel == null ? "" : parcel.getParcelId());
        tx.setParcelName(parcel == null ? "" : parcel.getName());
        tx.setType(type);
        tx.setCategory(category);
        tx.setLabel(label);
        tx.setAmount(amount);
        tx.setDate(monthsAgo(monthsAgo));
        batch.set(ref, tx);
    }

    private static void harvest(Parcel parcel, double kg, int season) {
        DocumentReference ref = db.collection("harvests").document();
        Harvest h = new Harvest();
        h.setHarvestId(ref.getId());
        h.setFarmId(farmId);
        h.setParcelId(parcel.getParcelId());
        h.setParcelName(parcel.getName());
        h.setCrop(parcel.getCrop());
        h.setQuantityKg(kg);
        h.setSeason(season);
        // Récolte en octobre de la saison (sans dépasser aujourd'hui)
        Calendar cal = Calendar.getInstance();
        cal.set(season, Calendar.OCTOBER, 12, 10, 0);
        Date date = cal.getTime().after(new Date()) ? new Date() : cal.getTime();
        h.setDate(date);
        batch.set(ref, h);
    }

    private static void irrigation(Parcel parcel, double quantity, int daysAgo) {
        DocumentReference ref = db.collection("irrigations").document();
        Irrigation i = new Irrigation();
        i.setIrrigId(ref.getId());
        i.setFarmId(farmId);
        i.setParcelId(parcel.getParcelId());
        i.setParcelName(parcel.getName());
        i.setDate(daysFromNow(daysAgo, 6));
        i.setQuantity(quantity);
        i.setAdvice("");
        i.setDone(true);
        batch.set(ref, i);
    }

    /** Date dans "days" jours (négatif = passé), à l'heure donnée. */
    private static Date daysFromNow(int days, int hour) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, days);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        return cal.getTime();
    }

    /** Le 10 du mois, il y a "months" mois (pour le mois en cours : pas après aujourd'hui). */
    private static Date monthsAgo(int months) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -months);
        int day = months == 0 ? Math.min(10, cal.get(Calendar.DAY_OF_MONTH)) : 10;
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        return cal.getTime();
    }
}
