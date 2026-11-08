import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Les clés secrètes (OpenWeather...) sont lues dans local.properties, jamais commité sur Git
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    namespace = "com.example.agrismart"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.agrismart"
        minSdk = 26
        targetSdk = 35 // 35 : on garde l'affichage classique (pas d'edge-to-edge forcé)
        versionCode = 1
        versionName = "1.0"

        // Accessible en Java via BuildConfig.OPENWEATHER_API_KEY (utilisée en phase 5)
        buildConfigField(
            "String",
            "OPENWEATHER_API_KEY",
            "\"${localProps.getProperty("OPENWEATHER_API_KEY", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)

    // Firebase (versions gérées par la BoM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Localisation : bouton "Utiliser ma position actuelle"
    implementation(libs.play.services.location)

    // Météo (OkHttp), graphiques (MPAndroidChart), diagnostic IA (TensorFlow Lite)
    implementation(libs.okhttp)
    implementation(libs.mpandroidchart)
    implementation(libs.tensorflow.lite)
}
