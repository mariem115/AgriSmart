package com.example.agrismart;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.agrismart.adapter.ChatAdapter;
import com.example.agrismart.databinding.ActivityAssistantBinding;
import com.example.agrismart.model.ChatMessage;
import com.example.agrismart.model.Diagnostic;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Assistant agricole (version simple, sans API d'IA) :
 * réponses guidées par mots-clés, en français ou en arabe, à l'écrit ou à la voix.
 * Chaque échange est enregistré dans la collection "chatMessages".
 * L'avatar en haut à droite ouvre le profil (nom, langue, déconnexion).
 */
public class AssistantActivity extends AppCompatActivity {

    private ActivityAssistantBinding binding;
    private FirebaseFirestore db;
    private ChatAdapter adapter;
    private SpeechRecognizer recognizer;

    private Diagnostic lastDiagnostic; // contexte : dernier diagnostic de la ferme

    // Autorisation du micro
    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListening();
                } else {
                    AppHelper.toast(this, getString(R.string.assistant_mic_denied));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        adapter = new ChatAdapter();
        binding.recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChat.setAdapter(adapter);

        AppHelper.showLanguagePill(this, binding.tvLangFr, binding.tvLangAr);
        binding.tvAvatar.setText(AppHelper.initials(AppHelper.getUserName(this)));
        binding.tvAvatar.setOnClickListener(v -> ProfileBottomSheet.show(this));

        binding.btnSend.setOnClickListener(v -> send(AppHelper.textOf(binding.etMessage)));
        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send(AppHelper.textOf(binding.etMessage));
                return true;
            }
            return false;
        });
        binding.btnMic.setOnClickListener(v -> startVoice());

        addSuggestion(R.string.assistant_suggest_water);
        addSuggestion(R.string.assistant_suggest_disease);
        addSuggestion(R.string.assistant_suggest_harvest);

        loadContext();
        loadHistory();
        AppHelper.setupBottomNav(this, binding.bottomNav, R.id.nav_assistant);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.destroy();
        }
    }

    /** Question rapide : un appui l'envoie directement. */
    private void addSuggestion(int textRes) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_suggestion_chip, binding.chipSuggestions, false);
        chip.setText(textRes);
        chip.setOnClickListener(v -> send(getString(textRes)));
        binding.chipSuggestions.addView(chip);
    }

    // =====================================================================
    // Contexte et historique
    // =====================================================================

    /** Dernier diagnostic de la ferme, utilisé dans les réponses. */
    private void loadContext() {
        binding.tvContext.setText(R.string.assistant_context_empty);
        db.collection("diagnostics").whereEqualTo("farmId", AppHelper.getFarmId(this)).get()
                .addOnSuccessListener(snapshots -> {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Diagnostic d = doc.toObject(Diagnostic.class);
                        if (lastDiagnostic == null || time(d.getDate()) > time(lastDiagnostic.getDate())) {
                            lastDiagnostic = d;
                        }
                    }
                    if (lastDiagnostic != null) {
                        binding.tvContext.setText(getString(R.string.assistant_context,
                                String.valueOf(lastDiagnostic.getParcelName()), lastDiagnostic.getDisease(),
                                AppHelper.formatDate(lastDiagnostic.getDate())));
                    }
                });
    }

    /** Anciennes questions / réponses de l'utilisateur. */
    private void loadHistory() {
        db.collection("chatMessages").whereEqualTo("uid", AppHelper.getUid(this)).get()
                .addOnSuccessListener(snapshots -> {
                    List<ChatMessage> messages = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        messages.add(doc.toObject(ChatMessage.class));
                    }
                    messages.sort((a, b) -> Long.compare(time(a.getDate()), time(b.getDate())));

                    List<ChatAdapter.Line> lines = new ArrayList<>();
                    // Message d'accueil
                    lines.add(new ChatAdapter.Line(getString(R.string.assistant_welcome), false));
                    // 15 derniers échanges
                    for (int i = Math.max(0, messages.size() - 15); i < messages.size(); i++) {
                        lines.add(new ChatAdapter.Line(messages.get(i).getQuestion(), true));
                        lines.add(new ChatAdapter.Line(messages.get(i).getAnswer(), false));
                    }
                    adapter.setLines(lines);
                    scrollToBottom();
                });
    }

    private static long time(Date date) {
        return date == null ? 0 : date.getTime();
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            binding.recyclerChat.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // =====================================================================
    // Envoi d'une question
    // =====================================================================

    private void send(String question) {
        if (question.isEmpty()) {
            return;
        }
        binding.etMessage.setText("");
        String answer = answer(question);

        adapter.addLine(new ChatAdapter.Line(question, true));
        adapter.addLine(new ChatAdapter.Line(answer, false));
        scrollToBottom();

        // Sauvegarde dans Firestore
        DocumentReference ref = db.collection("chatMessages").document();
        ChatMessage message = new ChatMessage();
        message.setMsgId(ref.getId());
        message.setUid(AppHelper.getUid(this));
        message.setQuestion(question);
        message.setAnswer(answer);
        message.setDate(new Date());
        ref.set(message);
    }

    /**
     * Réponse par mots-clés. Si la question contient des lettres arabes,
     * on répond en arabe, sinon en français.
     */
    String answer(String question) {
        // Espaces autour : permet de chercher des mots entiers, ex : " eau " (et pas "beaucoup")
        String q = " " + question.toLowerCase(Locale.ROOT).replaceAll("[?!.,;:]", " ") + " ";
        boolean arabic = question.matches(".*[\\u0600-\\u06FF].*");

        if (has(q, "bonjour", "salut", "salam", "مرحبا", "السلام", "أهلا", "اهلا")) {
            return text(arabic, R.string.answer_hello);
        }
        if (has(q, "merci", "شكرا")) {
            return text(arabic, R.string.answer_thanks);
        }
        if (has(q, "maladie", "mildiou", "traite", "traitement", "malade", "feuille",
                "مرض", "علاج", "دواء", "أوراق", "اوراق", "ورقة")) {
            if (lastDiagnostic != null && lastDiagnostic.getDisease() != null) {
                String treatment = text(arabic, PlantScanActivity.treatmentFor(lastDiagnostic.getDisease()));
                return text(arabic, R.string.answer_disease_context, lastDiagnostic.getDisease(),
                        String.valueOf(lastDiagnostic.getParcelName()), treatment);
            }
            return text(arabic, R.string.answer_disease);
        }
        if (has(q, "arros", "irrig", " eau ", "l'eau", "d'eau", "سقي", "الري", "ماء")) {
            return text(arabic, R.string.answer_irrigation);
        }
        if (has(q, "météo", "meteo", "pluie", "temps", "chaleur", " vent ", "طقس", "مطر", "حرارة", "رياح")) {
            return text(arabic, R.string.answer_weather);
        }
        if (has(q, "récolte", "recolte", "prévision", "prevision", "rendement", "production",
                "صابة", "محصول", "جني", "إنتاج", "انتاج")) {
            return text(arabic, R.string.answer_harvest);
        }
        if (has(q, "stock", "engrais", "intrant", "pesticide", "semence", "سماد", "مخزون", "أسمدة", "بذور")) {
            return text(arabic, R.string.answer_stock);
        }
        if (has(q, "tâche", "ouvrier", "travail", "مهمة", "مهام", "عامل", "عمال")) {
            return text(arabic, R.string.answer_tasks);
        }
        if (has(q, "bénéfice", "benefice", "argent", "dépense", "depense", "finance", "prix",
                "ربح", "مصاريف", "مال", "مداخيل")) {
            return text(arabic, R.string.answer_finance);
        }
        return text(arabic, R.string.answer_unknown);
    }

    private static boolean has(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** Texte en arabe ou en français, quelle que soit la langue de l'application. */
    private String text(boolean arabic, int resId, Object... args) {
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(new Locale(arabic ? "ar" : "fr"));
        return createConfigurationContext(config).getString(resId, args);
    }

    // =====================================================================
    // Saisie vocale (SpeechRecognizer d'Android)
    // =====================================================================

    private void startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AppHelper.toast(this, getString(R.string.assistant_voice_unavailable));
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startListening() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    setListening(false);
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        send(matches.get(0)); // la phrase reconnue est envoyée comme une question
                    }
                }

                @Override
                public void onError(int error) {
                    setListening(false);
                    AppHelper.toast(AssistantActivity.this, getString(R.string.assistant_voice_error));
                }

                // Méthodes obligatoires de l'interface, inutiles ici
                @Override
                public void onReadyForSpeech(Bundle params) { }

                @Override
                public void onBeginningOfSpeech() { }

                @Override
                public void onRmsChanged(float rmsdB) { }

                @Override
                public void onBufferReceived(byte[] buffer) { }

                @Override
                public void onEndOfSpeech() { }

                @Override
                public void onPartialResults(Bundle partialResults) { }

                @Override
                public void onEvent(int eventType, Bundle params) { }
            });
        }
        // Langue de la dictée = langue de l'application
        String language = "ar".equals(AppHelper.getCurrentLanguage()) ? "ar-TN" : "fr-FR";
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        recognizer.startListening(intent);
        setListening(true);
        AppHelper.toast(this, getString(R.string.assistant_listening));
    }

    /** Micro rouge pendant l'écoute. */
    private void setListening(boolean listening) {
        binding.btnMic.setBackgroundTintList(ContextCompat.getColorStateList(this,
                listening ? R.color.red_soft : R.color.green_soft));
        binding.btnMic.setImageTintList(ContextCompat.getColorStateList(this,
                listening ? R.color.red : R.color.green_primary));
    }
}
