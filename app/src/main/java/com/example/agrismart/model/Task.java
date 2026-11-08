package com.example.agrismart.model;

import java.util.Date;

/**
 * Tâche assignée à un ouvrier (collection "tasks").
 */
public class Task {
    public static final String STATUS_TODO = "todo";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_DONE = "done";

    private String taskId;
    private String farmId;
    private String parcelId;
    private String parcelName; // nom copié pour l'affichage
    private String title;
    private String assignedTo; // workerId (= uid de l'ouvrier s'il a un compte)
    private String assignedName;
    private Date dueDate; // échéance
    private String status; // todo / in_progress / done
    private String problemNote; // problème signalé par l'ouvrier
    private Date createdAt;

    // Constructeur vide obligatoire pour Firestore
    public Task() {
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getFarmId() { return farmId; }
    public void setFarmId(String farmId) { this.farmId = farmId; }

    public String getParcelId() { return parcelId; }
    public void setParcelId(String parcelId) { this.parcelId = parcelId; }

    public String getParcelName() { return parcelName; }
    public void setParcelName(String parcelName) { this.parcelName = parcelName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignedName() { return assignedName; }
    public void setAssignedName(String assignedName) { this.assignedName = assignedName; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProblemNote() { return problemNote; }
    public void setProblemNote(String problemNote) { this.problemNote = problemNote; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
