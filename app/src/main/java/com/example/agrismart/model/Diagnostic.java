package com.example.agrismart.model;

import java.util.Date;

/**
 * Résultat d'un diagnostic de plante (collection "diagnostics").
 */
public class Diagnostic {
    private String diagId;
    private String farmId;
    private String parcelId;
    private String parcelName;
    private String photoUrl; // URL Firebase Storage (vide si l'envoi a échoué)
    private String disease; // étiquette du modèle
    private double confidence; // score entre 0 et 1
    private String treatment;
    private Date date;
    private String uid; // auteur du scan

    // Constructeur vide obligatoire pour Firestore
    public Diagnostic() {
    }

    public String getDiagId() { return diagId; }
    public void setDiagId(String diagId) { this.diagId = diagId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getParcelId() { return parcelId; }
    public void setParcelId(String parcelId) { this.parcelId = parcelId; }

    public String getParcelName() { return parcelName; }
    public void setParcelName(String parcelName) { this.parcelName = parcelName; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getDisease() { return disease; }
    public void setDisease(String disease) { this.disease = disease; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getTreatment() { return treatment; }
    public void setTreatment(String treatment) { this.treatment = treatment; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
}
