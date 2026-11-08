package com.example.agrismart.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ouvrier de l'exploitation (collection "workers").
 * Si l'ouvrier a un compte dans l'application, workerId = son uid Firebase :
 * c'est cet id qui est utilisé pour lui assigner des tâches.
 */
public class Worker {

    private String workerId;
    private String farmId;
    private String name;
    private String phone;
    private double dailySalary;                          // salaire journalier en DT
    private List<String> presences = new ArrayList<>();  // dates de présence "yyyy-MM-dd"

    // Constructeur vide obligatoire pour Firestore
    public Worker() {
    }

    public Worker(String workerId, String farmId, String name, String phone, double dailySalary) {
        this.workerId = workerId;
        this.farmId = farmId;
        this.name = name;
        this.phone = phone;
        this.dailySalary = dailySalary;
    }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public double getDailySalary() { return dailySalary; }
    public void setDailySalary(double dailySalary) { this.dailySalary = dailySalary; }

    public List<String> getPresences() { return presences; }
    public void setPresences(List<String> presences) { this.presences = presences; }
}
