package com.example.agrismart;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.agrismart.databinding.ActivityLoginBinding;
import com.example.agrismart.model.User;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Écran de connexion : e-mail + mot de passe, choix du rôle,
 * mot de passe oublié et choix de la langue (FR / AR).
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean farmerSelected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Choix du rôle
        selectRole(true);
        binding.btnFarmer.setOnClickListener(v -> selectRole(true));
        binding.btnWorker.setOnClickListener(v -> selectRole(false));

        binding.btnLogin.setOnClickListener(v -> login());
        binding.tvForgotPassword.setOnClickListener(v -> resetPassword());
        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        // Choix de la langue
        AppHelper.showLanguagePill(this, binding.tvLangFr, binding.tvLangAr);
    }

    private void selectRole(boolean farmer) {
        farmerSelected = farmer;
        binding.btnFarmer.setChecked(farmer);
        binding.btnWorker.setChecked(!farmer);
    }

    private void login() {
        String email = AppHelper.textOf(binding.etEmail);
        String password = AppHelper.textOf(binding.etPassword);

        // Validation des champs
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email));
            return;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_required));
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadProfile(result.getUser().getUid()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showMessage(AppHelper.errorMessage(this, e));
                });
    }

    /** Lit le document users/{uid} pour connaître le rôle et la ferme. */
    private void loadProfile(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    User user = doc.toObject(User.class);
                    if (user == null) {
                        auth.signOut();
                        setLoading(false);
                        showMessage(getString(R.string.error_profile_missing));
                        return;
                    }
                    // Le rôle choisi doit correspondre au rôle du compte
                    if (user.isFarmer() != farmerSelected) {
                        auth.signOut();
                        setLoading(false);
                        showMessage(getString(user.isFarmer()
                                ? R.string.error_role_farmer : R.string.error_role_worker));
                        return;
                    }
                    AppHelper.saveSession(this, user);
                    openDashboard();
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    setLoading(false);
                    showMessage(AppHelper.errorMessage(this, e));
                });
    }

    private void resetPassword() {
        String email = AppHelper.textOf(binding.etEmail);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.reset_enter_email));
            return;
        }
        binding.tilEmail.setError(null);
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> showMessage(getString(R.string.reset_email_sent, email)))
                .addOnFailureListener(e -> showMessage(AppHelper.errorMessage(this, e)));
    }

    private void openDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
