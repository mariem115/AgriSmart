package com.example.agrismart;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.agrismart.databinding.ActivityRegisterBinding;
import com.example.agrismart.model.Farm;
import com.example.agrismart.model.User;
import com.example.agrismart.model.Worker;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.Locale;

/**
 * Création de compte.
 * - Agriculteur : crée son compte + sa ferme (documents users et farms).
 * - Ouvrier : rejoint une ferme existante grâce au code donné par l'agriculteur
 *   (documents users et workers).
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean farmerSelected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        selectRole(true);
        binding.btnFarmer.setOnClickListener(v -> selectRole(true));
        binding.btnWorker.setOnClickListener(v -> selectRole(false));

        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvGoLogin.setOnClickListener(v -> finish());
        binding.btnRegister.setOnClickListener(v -> register());
    }

    /** Affiche les champs propres au rôle choisi. */
    private void selectRole(boolean farmer) {
        farmerSelected = farmer;
        binding.btnFarmer.setChecked(farmer);
        binding.btnWorker.setChecked(!farmer);
        binding.layoutFarmerFields.setVisibility(farmer ? View.VISIBLE : View.GONE);
        binding.layoutWorkerFields.setVisibility(farmer ? View.GONE : View.VISIBLE);
    }

    private void register() {
        String name = AppHelper.textOf(binding.etName);
        String email = AppHelper.textOf(binding.etEmail);
        String password = AppHelper.textOf(binding.etPassword);
        String farmName = AppHelper.textOf(binding.etFarmName);
        String region = AppHelper.textOf(binding.etRegion);
        String farmCode = AppHelper.textOf(binding.etFarmCode).toUpperCase(Locale.ROOT);

        if (!validate(name, email, password, farmName, region, farmCode)) {
            return;
        }

        setLoading(true);
        // 1) Création du compte Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
                    // 2) Création des documents Firestore
                    if (farmerSelected) {
                        createFarmerAccount(uid, name, email, farmName, region);
                    } else {
                        joinFarm(uid, name, email, farmCode);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showMessage(AppHelper.errorMessage(this, e));
                });
    }

    /** Vérifie les champs et affiche l'erreur sous le champ concerné. */
    private boolean validate(String name, String email, String password,
                             String farmName, String region, String farmCode) {
        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilFarmName.setError(null);
        binding.tilRegion.setError(null);
        binding.tilFarmCode.setError(null);

        boolean ok = true;
        if (name.isEmpty()) {
            binding.tilName.setError(getString(R.string.error_required));
            ok = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email));
            ok = false;
        }
        if (password.length() < 8) {
            binding.tilPassword.setError(getString(R.string.error_password_short));
            ok = false;
        }
        if (farmerSelected) {
            if (farmName.isEmpty()) {
                binding.tilFarmName.setError(getString(R.string.error_required));
                ok = false;
            }
            if (region.isEmpty()) {
                binding.tilRegion.setError(getString(R.string.error_required));
                ok = false;
            }
        } else if (farmCode.length() != 6) {
            binding.tilFarmCode.setError(getString(R.string.error_farm_code_format));
            ok = false;
        }
        return ok;
    }

    /** Agriculteur : on crée la ferme et le profil en une seule écriture (batch). */
    private void createFarmerAccount(String uid, String name, String email,
                                     String farmName, String region) {
        String farmId = AppHelper.generateFarmCode();
        Farm farm = new Farm(farmId, farmName, uid, region);
        User user = new User(uid, name, email, User.ROLE_FARMER, farmId,
                AppHelper.getCurrentLanguage());

        WriteBatch batch = db.batch();
        batch.set(db.collection("farms").document(farmId), farm);
        batch.set(db.collection("users").document(uid), user);
        batch.commit()
                .addOnSuccessListener(v -> openDashboard(user))
                .addOnFailureListener(this::onFirestoreError);
    }

    /** Ouvrier : on vérifie que le code de ferme existe, puis on crée le profil. */
    private void joinFarm(String uid, String name, String email, String farmCode) {
        db.collection("farms").document(farmCode).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        deleteCreatedAccount();
                        setLoading(false);
                        binding.tilFarmCode.setError(getString(R.string.error_farm_code_unknown));
                        return;
                    }
                    User user = new User(uid, name, email, User.ROLE_WORKER, farmCode,
                            AppHelper.getCurrentLanguage());
                    // Fiche ouvrier (salaire à 0 : c'est l'agriculteur qui le fixera)
                    Worker worker = new Worker(uid, farmCode, name, "", 0);

                    WriteBatch batch = db.batch();
                    batch.set(db.collection("users").document(uid), user);
                    batch.set(db.collection("workers").document(uid), worker);
                    batch.commit()
                            .addOnSuccessListener(v -> openDashboard(user))
                            .addOnFailureListener(this::onFirestoreError);
                })
                .addOnFailureListener(this::onFirestoreError);
    }

    /** En cas d'échec Firestore, on supprime le compte Auth pour pouvoir réessayer. */
    private void onFirestoreError(Exception e) {
        deleteCreatedAccount();
        setLoading(false);
        showMessage(AppHelper.errorMessage(this, e));
    }

    private void deleteCreatedAccount() {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            current.delete();
        }
    }

    private void openDashboard(User user) {
        AppHelper.saveSession(this, user);
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
