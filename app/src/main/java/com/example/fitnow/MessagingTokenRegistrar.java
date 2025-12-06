package com.example.fitnow;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MessagingTokenRegistrar {

    private static final String TAG = "FCM_TOKEN";
    private static final String PREFS_NAME = "fcm_prefs";
    private static final String KEY_PENDING_TOKEN = "pending_fcm_token";

    private MessagingTokenRegistrar() {
// util class
    }

    /**
     * Pede o token FCM e guarda-o localmente.
     * Chama isto em sítios com Context (por ex. HomeFragment, MainActivity).
     */
    public static void sync(Context context) {
        Context appCtx = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> saveToken(appCtx, token))
                .addOnFailureListener(e -> Log.e(TAG, "Erro a obter token FCM", e));
    }

    /**
     * Chamar isto logo a seguir ao login bem-sucedido.
     * Vai buscar o token pendente nas prefs e associa-o ao user atual.
     */
    public static void onLogin(Context context) {
        Context appCtx = context.getApplicationContext();
        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_PENDING_TOKEN, null);

        if (token != null && !token.isEmpty()) {
            Log.d(TAG, "onLogin: a associar token pendente ao user: " + token);
            uploadTokenForCurrentUser(token);
        } else {
            Log.d(TAG, "onLogin: nenhum token pendente encontrado.");
        }
    }

    /**
     * Guarda o token nas SharedPreferences e tenta associá-lo ao user atual (se existir).
     */
    public static void saveToken(Context context, @Nullable String token) {
        if (token == null || token.isEmpty()) return;

        Context appCtx = context.getApplicationContext();

// 1) Guardar sempre localmente, mesmo sem utilizador autenticado
        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PENDING_TOKEN, token).apply();
        Log.d(TAG, "Token FCM guardado em prefs: " + token);

// 2) Se já houver user autenticado, enviar logo para o Firestore
        uploadTokenForCurrentUser(token);
    }

    /**
     * Associa o token ao utilizador autenticado no Firestore.
     */
    private static void uploadTokenForCurrentUser(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Log.d(TAG, "uploadTokenForCurrentUser: ainda não há utilizador logado, token fica pendente.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> payload = new HashMap<>();
        payload.put("fcmTokens", FieldValue.arrayUnion(token));

        db.collection("users")
                .document(uid)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Token FCM associado ao user " + uid))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Falha a guardar token FCM no user " + uid, e);
                    db.collection("users")
                            .document(uid)
                            .set(Collections.singletonMap("fcmTokens",
                                            Collections.singletonList(token)),
                                    SetOptions.merge());
                });
    }
}