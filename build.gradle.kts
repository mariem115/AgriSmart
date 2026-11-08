// Fichier de build racine : on déclare seulement les plugins (appliqués dans app/)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}
