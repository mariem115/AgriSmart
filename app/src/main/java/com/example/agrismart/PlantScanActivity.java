package com.example.agrismart;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.agrismart.databinding.ActivityPlantScanBinding;
import com.example.agrismart.databinding.ItemHistoryBinding;
import com.example.agrismart.model.Diagnostic;
import com.example.agrismart.model.Parcel;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic IA d'une feuille :
 * photo (caméra ou galerie) -> image 224 x 224 normalisée -> modèle TFLite sur le téléphone
 * -> maladie + score + traitement bio. Si le score < 60 %, on demande une nouvelle photo.
 * Le résultat est enregistré dans Firestore et la photo dans Firebase Storage.
 */
public class PlantScanActivity extends AppCompatActivity {

    private static final String MODEL_FILE = "plant_model.tflite"; // dans app/src/main/assets/
    private static final String LABELS_FILE = "labels.txt";        // une étiquette par ligne
    private static final float MIN_CONFIDENCE = 0.60f;              // seuil de fiabilité

    // Normalisation des pixels : valeur envoyée au modèle = (pixel - MEAN) / STD
    //  - MEAN 0     / STD 1     -> pixels bruts 0..255 (modèle actuel : il se normalise lui-même)
    //  - MEAN 0     / STD 255   -> valeurs entre 0 et 1
    //  - MEAN 127.5 / STD 127.5 -> valeurs entre -1 et 1 (MobileNet sans couche de prétraitement)
    private static final float PIXEL_MEAN = 0f;
    private static final float PIXEL_STD = 1f;

    private ActivityPlantScanBinding binding;
    private FirebaseFirestore db;

    private Interpreter interpreter;            // null si le modèle est absent
    private final List<String> labels = new ArrayList<>();

    private List<Parcel> parcels = new ArrayList<>();
    private Parcel chosenParcel;

    // Dernier résultat
    private Bitmap photo;
    private String resultLabel;
    private float resultConfidence;
    private int treatmentRes;

    // Photo avec l'appareil (miniature suffisante pour un modèle en 224 x 224)
    private final ActivityResultLauncher<Void> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    analyze(bitmap);
                }
            });

    // Image depuis la galerie
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Bitmap bitmap = loadBitmap(uri);
                    if (bitmap != null) {
                        analyze(bitmap);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlantScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();
        // Hors ligne, on n'attend pas 10 minutes l'envoi de la photo
        FirebaseStorage.getInstance().setMaxUploadRetryTimeMillis(15000);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCamera.setOnClickListener(v -> takePicture.launch(null));
        binding.btnGallery.setOnClickListener(v -> pickImage.launch("image/*"));
        binding.btnNewPhoto.setOnClickListener(v -> showCapture());
        binding.btnRetake.setOnClickListener(v -> {
            showCapture();
            takePicture.launch(null);
        });
        binding.btnSave.setOnClickListener(v -> saveDiagnostic());

        loadModel();
        loadParcels();
        listenToHistory();
        showCapture();
        AppHelper.setupBottomNav(this, binding.bottomNav, R.id.nav_scan);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (interpreter != null) {
            interpreter.close();
        }
    }

    private void loadParcels() {
        AppHelper.loadParcels(this, loaded -> {
            parcels = loaded;
            if (parcels.isEmpty()) {
                return;
            }
            binding.dropParcel.setSimpleItems(AppHelper.parcelNames(parcels));
            binding.dropParcel.setOnItemClickListener((p, v, position, id) -> chosenParcel = parcels.get(position));
            chosenParcel = parcels.get(0);
            binding.dropParcel.setText(chosenParcel.getName(), false);
        });
    }

    // =====================================================================
    // Les 3 états de l'écran
    // =====================================================================

    private void showCapture() {
        binding.layoutCapture.setVisibility(View.VISIBLE);
        binding.layoutResult.setVisibility(View.GONE);
        binding.layoutLowScore.setVisibility(View.GONE);
        binding.ivPhoto.setVisibility(View.GONE);
        binding.layoutPlaceholder.setVisibility(View.VISIBLE);
        binding.tvPlaceholder.setVisibility(View.VISIBLE);
        binding.tvTitle.setText(R.string.scan_title);
        binding.btnSave.setEnabled(true);
        binding.progress.setVisibility(View.GONE);
    }

    private void showPhoto(Bitmap bitmap) {
        binding.ivPhoto.setImageBitmap(bitmap);
        binding.ivPhoto.setVisibility(View.VISIBLE);
        binding.layoutPlaceholder.setVisibility(View.GONE);
        binding.tvPlaceholder.setVisibility(View.GONE);
    }

    // =====================================================================
    // Modèle TFLite
    // =====================================================================

    /** Charge le modèle et les étiquettes depuis assets/. S'ils manquent : message clair. */
    private void loadModel() {
        try {
            interpreter = new Interpreter(readModel());
            labels.clear();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open(LABELS_FILE)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        labels.add(line.trim());
                    }
                }
            }
            binding.tvModelMissing.setVisibility(View.GONE);
        } catch (Exception e) {
            // Fichier absent ou invalide : l'application continue de fonctionner
            interpreter = null;
            binding.tvModelMissing.setVisibility(View.VISIBLE);
        }
    }

    /** Lit le fichier .tflite dans un ByteBuffer (fonctionne même si l'asset est compressé). */
    private ByteBuffer readModel() throws IOException {
        try (InputStream in = getAssets().open(MODEL_FILE)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            byte[] bytes = out.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes);
            buffer.rewind();
            return buffer;
        }
    }

    /**
     * Fait tourner le modèle sur l'image et renvoie un score (0 à 1) par classe.
     * Étapes : redimensionnement (224 x 224 en général) -> normalisation -> inférence.
     */
    private float[] classify(Bitmap bitmap) {
        Tensor input = interpreter.getInputTensor(0);
        int height = input.shape()[1];
        int width = input.shape()[2];
        boolean quantized = input.dataType() == DataType.UINT8;

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        int[] pixels = new int[width * height];
        scaled.getPixels(pixels, 0, width, 0, 0, width, height);

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3 * (quantized ? 1 : 4))
                .order(ByteOrder.nativeOrder());
        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            if (quantized) {
                buffer.put((byte) r).put((byte) g).put((byte) b);
            } else {
                buffer.putFloat(normalize(r)).putFloat(normalize(g)).putFloat(normalize(b));
            }
        }
        buffer.rewind();

        Tensor output = interpreter.getOutputTensor(0);
        int classes = output.shape()[1];
        float[] scores = new float[classes];
        if (output.dataType() == DataType.UINT8) {
            byte[][] result = new byte[1][classes];
            interpreter.run(buffer, result);
            for (int i = 0; i < classes; i++) {
                scores[i] = (result[0][i] & 0xFF) / 255f;
            }
        } else {
            float[][] result = new float[1][classes];
            interpreter.run(buffer, result);
            scores = result[0];
        }
        return toProbabilities(scores);
    }

    private static float normalize(int value) {
        return (value - PIXEL_MEAN) / PIXEL_STD;
    }

    /** Si le modèle renvoie des "logits" (pas des probabilités), on applique softmax. */
    private static float[] toProbabilities(float[] scores) {
        float sum = 0;
        boolean outOfRange = false;
        for (float s : scores) {
            sum += s;
            if (s < 0 || s > 1) {
                outOfRange = true;
            }
        }
        if (!outOfRange && Math.abs(sum - 1) < 0.1f) {
            return scores;
        }
        float max = Float.NEGATIVE_INFINITY;
        for (float s : scores) {
            max = Math.max(max, s);
        }
        float total = 0;
        float[] result = new float[scores.length];
        for (int i = 0; i < scores.length; i++) {
            result[i] = (float) Math.exp(scores[i] - max);
            total += result[i];
        }
        for (int i = 0; i < result.length; i++) {
            result[i] /= total;
        }
        return result;
    }

    // =====================================================================
    // Analyse et affichage du résultat
    // =====================================================================

    private void analyze(Bitmap bitmap) {
        photo = bitmap;
        showPhoto(bitmap);
        if (interpreter == null) {
            AppHelper.toast(this, getString(R.string.scan_model_missing));
            return;
        }

        float[] scores = classify(bitmap);
        int[] top = topIndexes(scores, 3);
        resultLabel = labelAt(top[0]);
        resultConfidence = scores[top[0]];
        treatmentRes = treatmentFor(resultLabel);

        int percent = Math.round(resultConfidence * 100);
        binding.layoutCapture.setVisibility(View.GONE);
        binding.tvTitle.setText(R.string.scan_result_title);

        if (resultConfidence < MIN_CONFIDENCE) {
            // Score trop faible : pas de diagnostic douteux, on demande une autre photo
            binding.layoutLowScore.setVisibility(View.VISIBLE);
            binding.tvLowScore.setText(getString(R.string.scan_low_score, percent));
            binding.progressLow.setProgressCompat(percent, false);
            return;
        }

        binding.layoutResult.setVisibility(View.VISIBLE);
        binding.tvDisease.setText(prettyLabel(resultLabel));
        binding.tvConfidence.setText(getString(R.string.percent_value, percent));
        binding.progressConfidence.setProgressCompat(percent, false);
        binding.tvTreatment.setText(treatmentRes);

        // Autres hypothèses (2e et 3e classes)
        StringBuilder others = new StringBuilder();
        for (int i = 1; i < top.length; i++) {
            if (top[i] < 0) {
                continue;
            }
            if (others.length() > 0) {
                others.append(" · ");
            }
            others.append(prettyLabel(labelAt(top[i]))).append(' ')
                    .append(Math.round(scores[top[i]] * 100)).append(" %");
        }
        binding.tvOthers.setText(others.length() == 0 ? ""
                : getString(R.string.scan_other_hypotheses, others.toString()));
    }

    /** Indices des n meilleurs scores (du meilleur au moins bon), -1 si pas assez de classes. */
    private static int[] topIndexes(float[] scores, int n) {
        int[] top = new int[n];
        boolean[] used = new boolean[scores.length];
        for (int k = 0; k < n; k++) {
            top[k] = -1;
            for (int i = 0; i < scores.length; i++) {
                if (!used[i] && (top[k] == -1 || scores[i] > scores[top[k]])) {
                    top[k] = i;
                }
            }
            if (top[k] >= 0) {
                used[top[k]] = true;
            }
        }
        return top;
    }

    private String labelAt(int index) {
        return index >= 0 && index < labels.size() ? labels.get(index) : "Classe " + index;
    }

    /** "Tomato___Late_blight" -> "Tomato · Late blight". */
    static String prettyLabel(String label) {
        return label.replace("___", " · ").replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * Petite table locale "étiquette -> traitement bio", par mots-clés.
     * L'ordre compte : les cas les plus précis d'abord.
     */
    static int treatmentFor(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.contains("healthy") || l.contains("sain")) {
            return R.string.treat_healthy;
        } else if (l.contains("virus") || l.contains("greening") || l.contains("haunglongbing")) {
            return R.string.treat_virus;
        } else if (l.contains("powdery") || l.contains("oidium")) {
            return R.string.treat_powdery;
        } else if (l.contains("late_blight") || l.contains("late blight") || l.contains("mildew") || l.contains("mildiou")) {
            return R.string.treat_mildew;
        } else if (l.contains("rust") || l.contains("rouille")) {
            return R.string.treat_rust;
        } else if (l.contains("bacterial")) {
            return R.string.treat_bacterial;
        } else if (l.contains("mite")) {
            return R.string.treat_mites;
        } else if (l.contains("scab") || l.contains("tavelure")) {
            return R.string.treat_scab;
        } else if (l.contains("rot") || l.contains("esca")) {
            return R.string.treat_rot;
        } else if (l.contains("blight") || l.contains("spot") || l.contains("mold") || l.contains("scorch")) {
            return R.string.treat_leaf_spot;
        }
        return R.string.treat_generic;
    }

    private static boolean isHealthy(String label) {
        return label != null && treatmentFor(label) == R.string.treat_healthy;
    }

    // =====================================================================
    // Enregistrement : photo dans Storage + résultat dans Firestore
    // =====================================================================

    private void saveDiagnostic() {
        if (photo == null || resultLabel == null) {
            return;
        }
        binding.btnSave.setEnabled(false);
        String farmId = AppHelper.getFarmId(this);

        DocumentReference ref = db.collection("diagnostics").document();
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setDiagId(ref.getId());
        diagnostic.setFarmId(farmId);
        diagnostic.setParcelId(chosenParcel == null ? "" : chosenParcel.getParcelId());
        diagnostic.setParcelName(chosenParcel == null ? "" : chosenParcel.getName());
        diagnostic.setPhotoUrl("");
        diagnostic.setDisease(prettyLabel(resultLabel));
        diagnostic.setConfidence(resultConfidence);
        diagnostic.setTreatment(getString(treatmentRes));
        diagnostic.setDate(new Date());
        diagnostic.setUid(AppHelper.getUid(this));

        // Photo compressée en JPEG, rangée dans le dossier de la ferme
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 85, jpeg);
        StorageReference photoRef = FirebaseStorage.getInstance()
                .getReference("farms/" + farmId + "/diagnostics/" + ref.getId() + ".jpg");

        binding.progress.setVisibility(View.VISIBLE);
        photoRef.putBytes(jpeg.toByteArray())
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return photoRef.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    diagnostic.setPhotoUrl(uri.toString());
                    writeDiagnostic(ref, diagnostic);
                })
                .addOnFailureListener(e -> {
                    // Photo non envoyée (hors ligne, Storage non activé...) : on garde le résultat
                    AppHelper.toast(this, getString(R.string.scan_photo_not_uploaded));
                    writeDiagnostic(ref, diagnostic);
                });
    }

    private void writeDiagnostic(DocumentReference ref, Diagnostic diagnostic) {
        ref.set(diagnostic).addOnFailureListener(e -> AppHelper.toast(this, AppHelper.errorMessage(this, e)));

        // L'agriculteur : l'état de santé de la parcelle suit le diagnostic
        if (AppHelper.isFarmer(this) && chosenParcel != null) {
            String health = isHealthy(resultLabel) ? Parcel.HEALTH_HEALTHY : Parcel.HEALTH_SICK;
            db.collection("parcels").document(chosenParcel.getParcelId()).update("healthStatus", health);
        }
        AppHelper.toast(this, getString(R.string.scan_saved));
        showCapture();
    }

    // =====================================================================
    // Historique des diagnostics de la ferme
    // =====================================================================

    private void listenToHistory() {
        db.collection("diagnostics")
                .whereEqualTo("farmId", AppHelper.getFarmId(this))
                .addSnapshotListener(this, (snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    List<Diagnostic> diagnostics = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        diagnostics.add(doc.toObject(Diagnostic.class));
                    }
                    diagnostics.sort((a, b) -> Long.compare(time(b.getDate()), time(a.getDate())));
                    showHistory(diagnostics);
                });
    }

    private static long time(Date date) {
        return date == null ? 0 : date.getTime();
    }

    private void showHistory(List<Diagnostic> diagnostics) {
        binding.layoutHistory.removeAllViews();
        for (int i = 0; i < Math.min(5, diagnostics.size()); i++) {
            Diagnostic diagnostic = diagnostics.get(i);
            boolean healthy = isHealthy(diagnostic.getDisease());
            int strong = ContextCompat.getColor(this, healthy ? R.color.green_primary : R.color.red);
            int soft = ContextCompat.getColor(this, healthy ? R.color.green_soft : R.color.red_soft);

            ItemHistoryBinding row = ItemHistoryBinding.inflate(getLayoutInflater(), binding.layoutHistory, false);
            row.ivIcon.setImageResource(healthy ? R.drawable.ic_check : R.drawable.ic_warning);
            row.ivIcon.setImageTintList(ColorStateList.valueOf(strong));
            row.ivIcon.setBackgroundTintList(ColorStateList.valueOf(soft));
            row.tvText.setText(getString(R.string.scan_history_line, diagnostic.getDisease(),
                    Math.round(diagnostic.getConfidence() * 100), String.valueOf(diagnostic.getParcelName())));
            row.tvDate.setText(AppHelper.formatDate(diagnostic.getDate()));
            binding.layoutHistory.addView(row.getRoot());
        }
        binding.tvHistoryEmpty.setVisibility(diagnostics.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // Lecture d'une image de la galerie (réduite pour économiser la mémoire)
    // =====================================================================

    private Bitmap loadBitmap(Uri uri) {
        try {
            // 1) Lire seulement la taille
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, options);
            }
            // 2) Lire l'image réduite (environ 1000 px max)
            int sample = 1;
            while (options.outWidth / sample > 1000 || options.outHeight / sample > 1000) {
                sample *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, decode);
            }
        } catch (IOException e) {
            AppHelper.toast(this, getString(R.string.scan_image_error));
            return null;
        }
    }
}
