package com.example.agrismart.model;

import java.util.Date;

/**
 * Arrosage d'une parcelle (collection "irrigations").
 */
public class Irrigation {
    private String irrigId;
    private String farmId;
    private String parcelId;
    private String parcelName;
    private Date date;
    private double quantity; // litres par m²
    private String advice; // conseil affiché au moment de l'arrosage
    private boolean done;

    // Constructeur vide obligatoire pour Firestore
    public Irrigation() {
    }

    public String getIrrigId() { return irrigId; }
    public void setIrrigId(String irrigId) { this.irrigId = irrigId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getParcelId() { return parcelId; }
    public void setParcelId(String parcelId) { this.parcelId = parcelId; }

    public String getParcelName() { return parcelName; }
    public void setParcelName(String parcelName) { this.parcelName = parcelName; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }

    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
