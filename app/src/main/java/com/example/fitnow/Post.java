package com.example.fitnow;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class Post {
    public String id;
    public String ownerUid;
    public String treinoId;
    public String treinoNome;
    public Integer durationMin;
    public Integer xpGanho;
    public Timestamp createdAt;

    public static Post from(DocumentSnapshot d) {
        if (d == null || !d.exists()) return null;
        Post p = new Post();
        p.id = d.getId();
        p.ownerUid = d.getString("ownerUid");
        p.treinoId = d.getString("treinoId");
        p.treinoNome = d.getString("treinoNome");

        Object dur = d.get("durationMin");
        if (dur instanceof Number) p.durationMin = ((Number) dur).intValue();

        Object xp = d.get("xpGanho");
        if (xp instanceof Number) p.xpGanho = ((Number) xp).intValue();

        p.createdAt = d.getTimestamp("createdAt");
        return p;
    }
}
