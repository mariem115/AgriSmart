# AgriSmart

Application Android de gestion d'exploitation agricole (projet universitaire, 3ème année mobile).
Parcelles, ouvriers et tâches, stock, finances, récoltes, irrigation, diagnostic des plantes par IA (TFLite)
et assistant agricole FR / AR. Données dans Firebase (Auth, Firestore, Storage).

- Java + XML, Gradle Kotlin DSL, minSdk 26
- Firebase Authentication, Cloud Firestore, Firebase Storage
- OpenWeather (OkHttp), MPAndroidChart, TensorFlow Lite, FusedLocationProvider, SpeechRecognizer

## Installation

1. Ouvrir le dossier dans Android Studio (File > Open).
2. Firebase : créer une application Android `com.example.agrismart` dans la console Firebase,
   télécharger `google-services.json` et le placer dans `app/` (fichier non versionné).
3. Activer **Authentication > Email/Password**, créer **Firestore** et **Storage**,
   puis publier `firestore.rules` et `storage.rules` dans les onglets *Rules*.
4. Météo : ajouter dans `local.properties` (non versionné) :
   ```
   OPENWEATHER_API_KEY=votre_cle
   ```
5. Diagnostic IA : le modèle `plant_model.tflite` + `labels.txt` (PlantVillage, 38 classes) est déjà dans
   `app/src/main/assets/` — repris du projet [Cropora](https://github.com/M-F-Tushar/Cropora) (licence MIT).
6. Lancer l'application. En debug, le profil (avatar) de l'agriculteur propose
   « Remplir avec des données de démo ».

## Rôles

- **Agriculteur** : accès complet. Son code de ferme (tableau de bord) permet aux ouvriers de s'inscrire.
- **Ouvrier** : tableau de bord, ses tâches, parcelles en lecture, diagnostic, assistant.
