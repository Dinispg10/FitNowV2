package com.example.fitnow;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class Comment {
    public String id;
    public String authorUid;
    public String authorName;
    public String text;
    public Timestamp createdAt;

    public static Comment from(DocumentSnapshot d) {
        if (d == null || !d.exists()) return null;
        Comment c = new Comment();
        c.id = d.getId();
        c.authorUid = d.getString("authorUid");
        c.authorName = d.getString("authorName");
        c.text = d.getString("text");
        c.createdAt = d.getTimestamp("createdAt");
        return c;
    }
}