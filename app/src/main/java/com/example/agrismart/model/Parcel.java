package com.example.agrismart.model;

/**
 * Parcelle de l'exploitation (collection "parcels").
 * La culture, le type de sol et l'état de santé sont enregistrés sous forme de codes
 * ("olive", "clay", "healthy"...) pour pouvoir les afficher en français ou en arabe.
 */
public class Parcel {

    // État de santé
    public static final String HEALTH_HEALTHY = "healthy"; // Saine
    public static final String HEALTH_WATCH = "watch";     // À surveiller
    public static final String HEALTH_SICK = "sick";       // Maladie détectée

    private String parcelId;
    private String farmId;
    private String name;
    private String crop;          // code culture (voir R.array.crop_codes)
    private double area;          // surface en hectares
    private String soilType;      // code sol (voir R.array.soil_codes)
    private Double latitude;      // null si aucune position
    private Double longitude;
    private String healthStatus;  // healthy / watch / sick

    // Constructeur vide obligatoire pour Firestore
    public Parcel() {
    }

    public String getParcelId() { return parcelId; }
    public void setParcelId(String parcelId) { this.parcelId = parcelId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }

    public double getArea() { return area; }
    public void setArea(double area) { this.area = area; }

    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
}
