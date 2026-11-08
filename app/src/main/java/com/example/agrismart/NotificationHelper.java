package com.example.agrismart;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.agrismart.model.StockItem;
import com.example.agrismart.model.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Set;

/**
 * Notifications LOCALES (sans serveur FCM) :
 * - l'ouvrier écoute ses tâches dans Firestore et reçoit une notification à chaque nouvelle tâche ;
 * - l'agriculteur est prévenu quand un intrant passe sous son seuil minimum.
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "agrismart_alerts";
    private static final String PREFS = "agrismart_notifications";

    // Écouteur des tâches de l'ouvrier (gardé tant que l'application est ouverte)
    private static ListenerRegistration taskListener;

    /** Crée le canal de notifications (obligatoire depuis Android 8). */
    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    /** Affiche une notification ; un appui ouvre l'écran "target". */
    @SuppressLint("MissingPermission") // l'autorisation est vérifiée juste en dessous
    public static void show(Context context, int id, String title, String text, Class<?> target) {
        // Android 13+ : il faut l'autorisation POST_NOTIFICATIONS (demandée sur le tableau de bord)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannel(context);

        Intent intent = new Intent(context, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    // =====================================================================
    // Ouvrier : nouvelle tâche
    // =====================================================================

    /**
     * Écoute en temps réel les tâches assignées à l'ouvrier connecté.
     * Les tâches déjà connues sont mémorisées pour ne notifier que les nouvelles.
     */
    public static void startTaskListener(Context context) {
        if (taskListener != null) {
            return; // déjà en écoute
        }
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        taskListener = FirebaseFirestore.getInstance().collection("tasks")
                .whereEqualTo("farmId", AppHelper.getFarmId(app))
                .whereEqualTo("assignedTo", AppHelper.getUid(app))
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    // Première fois sur ce téléphone : on mémorise sans notifier
                    boolean firstSync = !prefs.contains("known_tasks");
                    Set<String> known = new HashSet<>(prefs.getStringSet("known_tasks", new HashSet<>()));

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() != DocumentChange.Type.ADDED) {
                            continue;
                        }
                        Task task = change.getDocument().toObject(Task.class);
                        if (known.add(task.getTaskId()) && !firstSync
                                && Task.STATUS_TODO.equals(task.getStatus())) {
                            show(app, task.getTaskId().hashCode(),
                                    app.getString(R.string.notif_new_task_title),
                                    app.getString(R.string.notif_new_task_text, task.getTitle(),
                                            String.valueOf(task.getParcelName())),
                                    TasksActivity.class);
                        }
                    }
                    prefs.edit().putStringSet("known_tasks", known).apply();
                });
    }

    /** À la déconnexion : on arrête d'écouter. */
    public static void stopTaskListener() {
        if (taskListener != null) {
            taskListener.remove();
            taskListener = null;
        }
    }

    // =====================================================================
    // Agriculteur : stock bas
    // =====================================================================

    /** Notifie un stock bas (au plus une fois par jour et par article). */
    public static void notifyLowStock(Context context, StockItem item) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "stock_" + item.getItemId() + "_" + AppHelper.dayKey(new java.util.Date());
        if (prefs.getBoolean(key, false)) {
            return;
        }
        prefs.edit().putBoolean(key, true).apply();
        show(context, item.getItemId().hashCode(),
                context.getString(R.string.notif_low_stock_title),
                context.getString(R.string.notif_low_stock_text, item.getName(),
                        AppHelper.formatNumber(item.getQuantity()), item.getUnit()),
                StockActivity.class);
    }
}
