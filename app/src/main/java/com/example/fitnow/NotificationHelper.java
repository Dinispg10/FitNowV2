package com.example.fitnow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

public final class NotificationHelper {

    public static final String FRIEND_WORKOUT_CHANNEL_ID = "friend_workout_notifications";

    private NotificationHelper() {
    }

    public static void ensureNotificationChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager mgr = context.getSystemService(NotificationManager.class);
        if (mgr == null) return;

        NotificationChannel friendChannel = new NotificationChannel(
                FRIEND_WORKOUT_CHANNEL_ID,
                "Treinos dos amigos",
                NotificationManager.IMPORTANCE_HIGH
        );
        friendChannel.setDescription("Alertas sempre que um amigo termina um treino.");
        mgr.createNotificationChannel(friendChannel);
    }

    public static PendingIntent buildMainActivityPendingIntent(
            @NonNull Context context,
            int requestCode
    ) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    public static NotificationCompat.Builder buildFriendWorkoutNotification(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String content,
            int requestCode
    ) {
        ensureNotificationChannels(context);

        return new NotificationCompat.Builder(context, FRIEND_WORKOUT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(buildMainActivityPendingIntent(context, requestCode));
    }
}