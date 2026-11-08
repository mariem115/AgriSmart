package com.example.agrismart.model;

import java.util.Date;

/**
 * Récolte d'une parcelle (collection "harvests").
 */
public class Harvest {
    private String harvestId;
    private String farmId;
    private String parcelId;
    private String parcelName;
    private String crop; // code culture
    private double quantityKg;
    private Date date;
    private int season; // année de la saison, ex : 2026

    // Constructeur vide obligatoire pour Firestore
    public Harvest() {
    }

    public String getHarvestId() { return harvestId; }
    public void setHarvestId(String harvestId) { this.harvestId = harvestId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getParcelId() { return parcelId; }
    public void setParcelId(String parcelId) { this.parcelId = parcelId; }

    public String getParcelName() { return parcelName; }
    public void setParcelName(String parcelName) { this.parcelName = parcelName; }

    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }

    public double getQuantityKg() { return quantityKg; }
    public void setQuantityKg(double quantityKg) { this.quantityKg = quantityKg; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public int getSeason() { return season; }
    public void setSeason(int season) { this.season = season; }
}
