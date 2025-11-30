// === TreinoRunnerFragment.java (com safeToast para evitar NPE) ===
package com.example.fitnow;

import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TreinoRunnerFragment extends Fragment {

    private static final String ARG_TREINO_ID = "treinoId";

    // thresholds de nível (L10 = máximo)
    private static final int[] LEVEL_THRESHOLDS = new int[]{
            0, 100, 250, 450, 700, 1000, 1350, 1750, 2200, 2700
    };
    private static final int MAX_LEVEL = 10;
    private static final int MAX_XP_MES = 2700;
    private static final int BASE_XP_POR_EXERCICIO = 10;

    // UI
    private TextView tvExNome, tvExDif, tvProgresso, tvExTimer, tvTotalTimer;
    private Button btnPlayPause, btnNext, btnStop;

    // Dados do treino
    private String treinoId;
    private String treinoNome = "Treino";
    private List<Map<String, Object>> exercicios = new ArrayList<>();
    private int currentIndex = 0;

    // Timers
    private long totalMillis = 0L, totalRestanteMillis = 0L, exercicioRestanteMillis = 0L;
    private CountDownTimer timerTotal, timerExercicio;
    private boolean isRunning = false;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Application context seguro para Toasts após o fragment ser removido
    private Context appCtx;

    public TreinoRunnerFragment() {}

    public static TreinoRunnerFragment newInstance(String treinoId) {
        TreinoRunnerFragment f = new TreinoRunnerFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TREINO_ID, treinoId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appCtx = context.getApplicationContext();
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_treino_runner, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            treinoId = getArguments().getString(ARG_TREINO_ID);
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvExNome = view.findViewById(R.id.tvExNome);
        tvExDif = view.findViewById(R.id.tvExDif);
        tvProgresso = view.findViewById(R.id.tvProgresso);
        tvExTimer = view.findViewById(R.id.tvExTimer);
        tvTotalTimer = view.findViewById(R.id.tvTotalTimer);

        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnNext = view.findViewById(R.id.btnNext);
        btnStop = view.findViewById(R.id.btnStop);

        btnPlayPause.setOnClickListener(v -> onPlayPauseClicked());
        btnNext.setOnClickListener(v -> onNextClicked());
        btnStop.setOnClickListener(v -> onStopClicked());

        if (treinoId == null || treinoId.isEmpty()) {
            safeToast("Treino inválido");
            return;
        }

        carregarTreino();
    }

    @Override public void onPause() {
        super.onPause();
        pararTimers(false);
    }

    // --------- helper de Toast seguro ---------
    private void safeToast(@NonNull String msg) {
        if (appCtx != null) {
            Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show();
        }
    }
    private void safeToastLong(@NonNull String msg) {
        if (appCtx != null) {
            Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show();
        }
    }

    // --------- carregar treino ---------
    private void carregarTreino() {
        db.collection("treinos").document(treinoId).get()
                .addOnSuccessListener(this::processarDocumentoTreino)
                .addOnFailureListener(e -> safeToast("Erro ao carregar treino: " + e.getMessage()));
    }

    private void processarDocumentoTreino(DocumentSnapshot doc) {
        if (!doc.exists()) {
            safeToast("Treino não encontrado");
            return;
        }

        String nome = doc.getString("nome");
        String tipo = doc.getString("tipo");
        Object exObj = doc.get("exercicios");

        if (nome != null) { treinoNome = nome; tvExNome.setText(nome); }
        if (tipo != null) tvExDif.setText(tipo);

        if (exObj instanceof List) {
            //noinspection unchecked
            exercicios = (List<Map<String, Object>>) exObj;
        } else {
            exercicios = new ArrayList<>();
        }

        if (exercicios.isEmpty()) {
            safeToast("Treino sem exercícios");
            return;
        }

        totalMillis = 0L;
        for (Map<String, Object> ex : exercicios) {
            long tempoExSegundos = obterDuracaoExercicioEmSegundos(ex);
            totalMillis += tempoExSegundos * 1000L;
        }
        totalRestanteMillis = totalMillis;

        currentIndex = 0;
        atualizarUIExercicioAtual();
        atualizarTimersText();
    }

    private long obterDuracaoExercicioEmSegundos(Map<String, Object> exercicio) {
        Object tempoObj = exercicio.get("tempo");
        if (tempoObj == null) tempoObj = exercicio.get("duracao");
        if (tempoObj instanceof Number) return ((Number) tempoObj).longValue() * 60L;
        if (tempoObj instanceof String) {
            String s = ((String) tempoObj).toLowerCase().replaceAll("[^0-9]", "");
            try { return Long.parseLong(s) * 60L; } catch (Exception ignore) {}
        }
        return 60L;
    }

    private void atualizarUIExercicioAtual() {
        if (exercicios.isEmpty() || currentIndex < 0 || currentIndex >= exercicios.size()) return;
        Map<String, Object> ex = exercicios.get(currentIndex);
        String nome = ex.get("nome") != null ? String.valueOf(ex.get("nome")) : "Exercício";
        String dif = ex.get("dificuldade") != null ? String.valueOf(ex.get("dificuldade")) : "";

        tvExNome.setText(nome);
        tvExDif.setText(dif);
        tvProgresso.setText((currentIndex + 1) + "/" + exercicios.size());

        long duracaoSeg = obterDuracaoExercicioEmSegundos(ex);
        exercicioRestanteMillis = duracaoSeg * 1000L;

        atualizarTimersText();
    }

    private void atualizarTimersText() {
        tvExTimer.setText(formatarTempo(exercicioRestanteMillis));
        tvTotalTimer.setText(formatarTempo(totalRestanteMillis));
    }

    private String formatarTempo(long millis) {
        long totalSeg = millis / 1000L;
        long min = totalSeg / 60L;
        long seg = totalSeg % 60L;
        return (min < 10 ? "0" : "") + min + ":" + (seg < 10 ? "0" : "") + seg;
    }

    // --------- botões ---------
    private void onPlayPauseClicked() {
        if (exercicios.isEmpty()) return;
        if (isRunning) {
            pararTimers(false);
            isRunning = false;
            btnPlayPause.setText("Continuar");
        } else {
            iniciarTimers();
            isRunning = true;
            btnPlayPause.setText("Pausar");
        }
    }

    private void onNextClicked() {
        if (exercicios.isEmpty()) return;
        pararTimers(false);
        isRunning = false;

        long duracaoAtual = obterDuracaoExercicioEmSegundos(exercicios.get(currentIndex)) * 1000L;
        totalRestanteMillis = Math.max(0, totalRestanteMillis - duracaoAtual);

        currentIndex++;
        if (currentIndex >= exercicios.size()) {
            terminarTreino();
            return;
        }
        atualizarUIExercicioAtual();
        btnPlayPause.setText("Iniciar");
    }

    private void onStopClicked() { terminarTreino(); }

    // --------- timers ---------
    private void iniciarTimers() {
        timerExercicio = new CountDownTimer(exercicioRestanteMillis, 1000L) {
            @Override public void onTick(long ms) { exercicioRestanteMillis = ms; tvExTimer.setText(formatarTempo(ms)); }
            @Override public void onFinish() { exercicioRestanteMillis = 0L; tvExTimer.setText(formatarTempo(0L)); onNextClicked(); }
        }.start();

        timerTotal = new CountDownTimer(totalRestanteMillis, 1000L) {
            @Override public void onTick(long ms) { totalRestanteMillis = ms; tvTotalTimer.setText(formatarTempo(ms)); }
            @Override public void onFinish() { totalRestanteMillis = 0L; tvTotalTimer.setText(formatarTempo(0L)); }
        }.start();
    }

    private void pararTimers(boolean reset) {
        if (timerExercicio != null) { timerExercicio.cancel(); timerExercicio = null; }
        if (timerTotal != null) { timerTotal.cancel(); timerTotal = null; }
        if (reset) {
            totalRestanteMillis = totalMillis;
            if (!exercicios.isEmpty()) {
                Map<String, Object> ex = exercicios.get(0);
                exercicioRestanteMillis = obterDuracaoExercicioEmSegundos(ex) * 1000L;
            }
            atualizarTimersText();
            currentIndex = 0;
            atualizarUIExercicioAtual();
        }
    }

    // --------- terminar / XP / Level + Post ---------
    private void terminarTreino() {
        pararTimers(true);
        isRunning = false;
        btnPlayPause.setText("Iniciar");

        int xpGanho = calcularXpGanhoTreino();

        safeToastLong("Treino terminado! +" + xpGanho + " XP");

        // Atualiza XP e Level (cap) e depois publica o post
        atualizarXpELevel(xpGanho);

        // Publicar post do treino
        int durMin = (int) Math.max(1, Math.round(totalMillis / 60000.0));
        publicarPostTreino(treinoId, treinoNome, xpGanho, durMin);

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private int calcularXpGanhoTreino() {
        double totalXp = 0.0;
        for (Map<String, Object> ex : exercicios) {
            totalXp += BASE_XP_POR_EXERCICIO * getMultiplicadorDificuldade(ex);
        }
        return (int) Math.round(totalXp);
    }

    private double getMultiplicadorDificuldade(Map<String, Object> exercicio) {
        Object difObj = exercicio.get("dificuldade");
        if (difObj == null) return 1.0;
        String dif = String.valueOf(difObj).toLowerCase().trim();
        if (dif.contains("fácil") || dif.contains("facil")) return 1.0;
        if (dif.contains("média") || dif.contains("media") || dif.contains("medio")) return 1.5;
        if (dif.contains("difícil") || dif.contains("dificil")) return 2.0;
        return 1.0;
    }

    private int calcularNivel(int xpTotal) {
        int level = 1;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (xpTotal >= LEVEL_THRESHOLDS[i]) level = i + 1;
            else break;
        }
        return Math.min(level, MAX_LEVEL);
    }

    private String getCurrentXpPeriod() {
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
    }

    // Atualiza XP/Level com cap e grava no levelHistory
    private void atualizarXpELevel(int xpGanho) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DocumentReference docRef = db.collection("users").document(user.getUid());
        String periodAtual = getCurrentXpPeriod();

        docRef.get().addOnSuccessListener(snapshot -> {
            int xpAtual = 0, levelAtual = 1;
            String xpPeriodDoc = null;

            if (snapshot != null && snapshot.exists()) {
                Object xpObj = snapshot.get("xp");
                if (xpObj instanceof Number) xpAtual = ((Number) xpObj).intValue();
                Object lvlObj = snapshot.get("level");
                if (lvlObj instanceof Number) levelAtual = ((Number) lvlObj).intValue();
                xpPeriodDoc = snapshot.getString("xpPeriod");
            }

            Map<String, Object> updates = new HashMap<>();

            // mudou o mês? fecha anterior e reset
            if (xpPeriodDoc == null || !xpPeriodDoc.equals(periodAtual)) {
                if (xpPeriodDoc != null) updates.put("levelHistory." + xpPeriodDoc, levelAtual);
                xpAtual = 0; levelAtual = 1;
            }

            // cap atingido?
            if (xpAtual >= MAX_XP_MES || levelAtual >= MAX_LEVEL) {
                updates.put("xp", MAX_XP_MES);
                updates.put("level", MAX_LEVEL);
                updates.put("xpPeriod", periodAtual);
                updates.put("lastUpdated", FieldValue.serverTimestamp());
                updates.put("levelHistory." + periodAtual, MAX_LEVEL);
                docRef.set(updates, SetOptions.merge());
                safeToast("Nível 10 máximo — XP não aumenta.");
                return;
            }

            int novoXp = Math.min(MAX_XP_MES, xpAtual + xpGanho);
            int novoLevel = Math.min(MAX_LEVEL, calcularNivel(novoXp));

            updates.put("xp", novoXp);
            updates.put("level", novoLevel);
            updates.put("xpPeriod", periodAtual);
            updates.put("lastUpdated", FieldValue.serverTimestamp());

            @SuppressWarnings("unchecked")
            Map<String, Long> hist = (snapshot != null) ? (Map<String, Long>) snapshot.get("levelHistory") : null;
            int existente = 0;
            if (hist != null && hist.get(periodAtual) != null) existente = hist.get(periodAtual).intValue();
            updates.put("levelHistory." + periodAtual, Math.max(existente, novoLevel));

            docRef.set(updates, SetOptions.merge());

            if (novoXp >= MAX_XP_MES || novoLevel >= MAX_LEVEL) {
                safeToastLong("Atingiste o máximo deste mês! 🎉");
            }
        });
    }

    // Publica um post para o feed dos amigos
    private void publicarPostTreino(String treinoId, String treinoNome, int xpGanho, int duracaoMin) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        Map<String, Object> post = new HashMap<>();
        post.put("ownerUid", uid);
        post.put("treinoId", treinoId);
        post.put("treinoNome", treinoNome != null ? treinoNome : "Treino");
        post.put("durationMin", duracaoMin);
        post.put("xpGanho", xpGanho);
        post.put("likes", 0);
        post.put("commentCount", 0);
        post.put("likedBy", new ArrayList<>());
        post.put("createdAt", FieldValue.serverTimestamp());
        post.put("visibility", "friends");

        FirebaseFirestore.getInstance().collection("posts").add(post);
    }
}
