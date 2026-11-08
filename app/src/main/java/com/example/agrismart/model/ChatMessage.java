package com.example.agrismart.model;

import java.util.Date;

/**
 * Question / réponse de l'assistant (collection "chatMessages").
 */
public class ChatMessage {
    private String msgId;
    private String uid;
    private String question;
    private String answer;
    private Date date;

    // Constructeur vide obligatoire pour Firestore
    public ChatMessage() {
    }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
}
