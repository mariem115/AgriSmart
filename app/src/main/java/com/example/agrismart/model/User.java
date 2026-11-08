package com.example.agrismart.model;

import com.google.firebase.firestore.Exclude;

/**
 * Utilisateur de l'application (collection "users", id du document = uid Firebase).
 */
public class User {

    public static final String ROLE_FARMER = "farmer";
    public static final String ROLE_WORKER = "worker";

    private String uid;
    private String name;
    private String email;
    private String role;      // "farmer" ou "worker"
    private String farmId;    // exploitation à laquelle appartient l'utilisateur
    private String language;  // "fr" ou "ar"

    // Constructeur vide obligatoire pour Firestore
    public User() {
    }

    public User(String uid, String name, String email, String role, String farmId, String language) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.farmId = farmId;
        this.language = language;
    }

    // @Exclude : méthode utilitaire, pas un champ à enregistrer dans Firestore
    @Exclude
    public boolean isFarmer() {
        return ROLE_FARMER.equals(role);
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
