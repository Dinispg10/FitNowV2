package com.example.fitnow;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class WorkoutMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Novo token FCM: " + token);
// Usa o método que guarda em SharedPreferences e tenta associar ao user
        MessagingTokenRegistrar.saveToken(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Mensagem FCM recebida: " + data);

        if (data == null || data.isEmpty()) {
// Se estiveres a usar "notification messages", o sistema trata delas em background
            return;
        }

        String type = data.get("type");
        if ("friend_workout".equals(type)) {
            showFriendWorkoutNotification(data);
        }
    }

    private void showFriendWorkoutNotification(@NonNull Map<String, String> data) {
        String friendName = valueOrFallback(data.get("friendName"), "Um amigo");
        String treinoNome = valueOrFallback(data.get("treinoNome"), "Treino");

        NotificationManagerCompat.from(this).notify(
                (friendName + treinoNome).hashCode(),
                NotificationHelper.buildFriendWorkoutNotification(
                        this,
                        friendName + " fez um treino",
                        treinoNome,
                        (friendName + treinoNome).hashCode()
                ).build()
        );
    }

    private String valueOrFallback(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}