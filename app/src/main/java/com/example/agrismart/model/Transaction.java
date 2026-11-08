package com.example.agrismart.model;

import java.util.Date;

/**
 * Dépense ou revenu, lié à une parcelle (collection "transactions").
 */
public class Transaction {
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";

    private String txId;
    private String farmId;
    private String parcelId; // peut être vide (dépense générale)
    private String parcelName;
    private String type; // expense / income
    private double amount; // montant en DT
    private String category;
    private String label; // libellé libre
    private Date date;

    // Constructeur vide obligatoire pour Firestore
    public Transaction() {
    }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getParcelId() { return parcelId; }
    public void setParcelId(String parcelId) { this.parcelId = parcelId; }

    public String getParcelName() { return parcelName; }
    public void setParcelName(String parcelName) { this.parcelName = parcelName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
}
