package com.example.agrismart;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Écran de démarrage : affiche le logo puis ouvre
 * le tableau de bord (déjà connecté) ou l'écran de connexion.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::openNextScreen, SPLASH_DELAY_MS);
    }

    private void openNextScreen() {
        // Connecté ET session enregistrée -> tableau de bord, sinon -> connexion
        boolean loggedIn = FirebaseAuth.getInstance().getCurrentUser() != null
                && AppHelper.getFarmId(this) != null;

        Class<?> next = loggedIn ? DashboardActivity.class : LoginActivity.class;
        startActivity(new Intent(this, next));
        finish();
    }
}
