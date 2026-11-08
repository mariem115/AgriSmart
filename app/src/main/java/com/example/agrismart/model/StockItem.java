package com.example.agrismart.model;

/**
 * Intrant en stock : engrais, pesticide ou semence (collection "stock").
 */
public class StockItem {
    public static final String TYPE_FERTILIZER = "fertilizer";
    public static final String TYPE_PESTICIDE = "pesticide";
    public static final String TYPE_SEED = "seed";

    private String itemId;
    private String farmId;
    private String name;
    private String type; // fertilizer / pesticide / seed
    private double quantity;
    private double minThreshold; // seuil minimum (alerte de rupture)
    private String unit; // kg, L, sac...

    // Constructeur vide obligatoire pour Firestore
    public StockItem() {
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getMinThreshold() { return minThreshold; }
    public void setMinThreshold(double minThreshold) { this.minThreshold = minThreshold; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
