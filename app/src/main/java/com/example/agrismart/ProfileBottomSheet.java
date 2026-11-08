package com.example.agrismart;

import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.agrismart.databinding.BottomSheetProfileBinding;
import com.example.agrismart.model.Farm;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Profil de l'utilisateur, affiché en "feuille du bas" (pas un écran à part) :
 * nom modifiable, rôle, ferme, choix de la langue FR / AR, données de démo, déconnexion.
 * Ouvert depuis l'avatar du tableau de bord et de l'assistant.
 */
public class ProfileBottomSheet {

    public static void show(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        BottomSheetProfileBinding sheet = BottomSheetProfileBinding.inflate(activity.getLayoutInflater());
        dialog.setContentView(sheet.getRoot());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = AppHelper.getUid(activity);
        boolean farmer = AppHelper.isFarmer(activity);
        String name = AppHelper.getUserName(activity);

        // ---- Informations ----
        sheet.tvInitials.setText(AppHelper.initials(name));
        sheet.tvEmail.setText(user != null ? user.getEmail() : "");
        sheet.tvRole.setText(farmer ? R.string.role_farmer : R.string.role_worker);
        sheet.etName.setText(name);
        db.collection("farms").document(AppHelper.getFarmId(activity)).get()
                .addOnSuccessListener(doc -> {
                    Farm farm = doc.toObject(Farm.class);
                    if (farm != null) {
                        sheet.tvFarm.setText(activity.getString(R.string.profile_farm_line,
                                farm.getName(), farm.getFarmId()));
                    }
                });

        // ---- Modifier le nom ----
        sheet.btnSaveName.setOnClickListener(v -> {
            String newName = AppHelper.textOf(sheet.etName);
            sheet.tilName.setError(null);
            if (newName.isEmpty()) {
                sheet.tilName.setError(activity.getString(R.string.error_required));
                return;
            }
            db.collection("users").document(uid).update("name", newName)
                    .addOnFailureListener(e -> AppHelper.toast(activity, AppHelper.errorMessage(activity, e)));
            AppHelper.setUserName(activity, newName);
            sheet.tvInitials.setText(AppHelper.initials(newName));
            AppHelper.toast(activity, activity.getString(R.string.profile_saved));
        });

        // ---- Langue : les écrans sont recréés dans la nouvelle langue ----
        boolean arabic = "ar".equals(AppHelper.getCurrentLanguage());
        sheet.btnFrench.setChecked(!arabic);
        sheet.btnArabic.setChecked(arabic);
        sheet.btnFrench.setOnClickListener(v -> changeLanguage(dialog, uid, "fr"));
        sheet.btnArabic.setOnClickListener(v -> changeLanguage(dialog, uid, "ar"));

        // ---- Données de démonstration : seulement en debug et pour l'agriculteur ----
        sheet.btnSeed.setVisibility(BuildConfig.DEBUG && farmer ? View.VISIBLE : View.GONE);
        sheet.btnSeed.setOnClickListener(v -> {
            sheet.btnSeed.setEnabled(false);
            DemoDataSeeder.seed(activity, () -> {
                AppHelper.toast(activity, activity.getString(R.string.seed_done));
                dialog.dismiss();
            });
        });

        // ---- Déconnexion ----
        sheet.btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            NotificationHelper.stopTaskListener();
            FirebaseAuth.getInstance().signOut();
            AppHelper.clearSession(activity);
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
        });

        dialog.show();
    }

    private static void changeLanguage(BottomSheetDialog dialog, String uid, String language) {
        FirebaseFirestore.getInstance().collection("users").document(uid).update("language", language);
        dialog.dismiss();
        AppHelper.setLanguage(language);
    }
}
