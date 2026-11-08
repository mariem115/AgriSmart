package com.example.agrismart.model;

/**
 * Exploitation agricole (collection "farms").
 * L'id du document est un code court de 6 caractères (ex : "AB12CD")
 * que l'agriculteur donne à ses ouvriers pour qu'ils rejoignent la ferme.
 */
public class Farm {

    private String farmId;
    private String name;
    private String ownerUid;  // uid de l'agriculteur propriétaire
    private String region;

    // Constructeur vide obligatoire pour Firestore
    public Farm() {
    }

    public Farm(String farmId, String name, String ownerUid, String region) {
        this.farmId = farmId;
        this.name = name;
        this.ownerUid = ownerUid;
        this.region = region;
    }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
