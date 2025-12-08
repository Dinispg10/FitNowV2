package com.example.fitnow;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.List;

public class Post {
    public String id;
    public String ownerUid;
    public String treinoId;
    public String treinoNome;
    public Integer durationMin;
    public Integer xpGanho;
    public Integer likeCount;
    public Integer commentCount;
    public List<String> likedBy;
    public Timestamp createdAt;
    public String visibility;

    public boolean isLikedBy(String uid) {
        return uid != null && likedBy != null && likedBy.contains(uid);
    }

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

        Object likes = d.get("likes");
        if (likes instanceof Number) p.likeCount = ((Number) likes).intValue();

        // Prefer the explicit commentCount field, but accept legacy "comments" counters too
        Object comments = d.get("commentCount");
        if (!(comments instanceof Number)) comments = d.get("comments");
        if (comments instanceof Number) p.commentCount = ((Number) comments).intValue();

        Object likedBy = d.get("likedBy");
        if (likedBy instanceof java.util.List) {
            //noinspection unchecked
            p.likedBy = (java.util.List<String>) likedBy;
        } else {
            p.likedBy = new java.util.ArrayList<>();
        }

        p.createdAt = d.getTimestamp("createdAt");
        String vis = d.getString("visibility");
        p.visibility = (vis == null || vis.isEmpty()) ? "friends" : vis;
        return p;
    }
}
