package com.example.fitnow;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista de treinos do utilizador + criação de novos treinos (com post).
 *
 * - Carrega os meus treinos em /treinos (where userId == meu uid)
 * - Abre detalhe ao tocar
 * - Elimina treino
 * - Botão "Novo Treino" (opcional no layout com id btnNovoTreino) abre um diálogo
 *   para criar rapidamente (nome/tipo/duração). Sem XML extra.
 * - Ao guardar: gera docId, define "id", "visibility" ("friends" por omissão),
 *   "createdAt" e cria um documento em /posts com o treinoId.
 */
public class FragmentTreino extends Fragment {

    private RecyclerView recyclerView;
    private TreinoAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_treino, container, false);

        recyclerView = view.findViewById(R.id.recyclerTreinos);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TreinoAdapter();
        recyclerView.setAdapter(adapter);

        // Click -> abrir detalhe
        adapter.setOnTreinoClickListener(treino -> {
            if (treino.getId() == null || treino.getId().isEmpty()) {
                Toast.makeText(getContext(), "Treino sem ID. Atualiza a lista e tenta novamente.", Toast.LENGTH_SHORT).show();
                return;
            }
            TreinoDetalheFragment f = TreinoDetalheFragment.newInstance(treino.getId());
            ((MainActivity) requireActivity()).switchFragment(f);
        });

        // Long/btn delete -> eliminar
        adapter.setOnTreinoDeleteListener(treino -> {
            if (treino.getId() == null || treino.getId().isEmpty()) {
                Toast.makeText(getContext(), "Treino sem ID válido.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar treino")
                    .setMessage("Tens a certeza que queres eliminar \"" + (treino.getNome() != null ? treino.getNome() : "este treino") + "\"?")
                    .setPositiveButton("Eliminar", (d, w) -> eliminarTreino(treino.getId()))
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        carregarTreinos();
        return view;
    }

    // ====================== CRIAR / GUARDAR TREINO ======================

    /**
     * Mostra um diálogo simples com campos Nome, Tipo e Duração (min) e chama guardarTreino().
     * Não requer XML novo.
     */
    private void mostrarDialogoNovoTreino() {
        final LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        final EditText etNome = new EditText(requireContext());
        etNome.setHint("Nome do treino");
        container.addView(etNome);

        final EditText etTipo = new EditText(requireContext());
        etTipo.setHint("Tipo (ex.: Força, Cardio)");
        container.addView(etTipo);

        final EditText etDuracao = new EditText(requireContext());
        etDuracao.setHint("Duração (min)");
        etDuracao.setInputType(InputType.TYPE_CLASS_NUMBER);
        container.addView(etDuracao);

        new AlertDialog.Builder(requireContext())
                .setTitle("Novo Treino")
                .setView(container)
                .setPositiveButton("Guardar", (d, w) -> {
                    String nome = safeStr(etNome.getText());
                    String tipo = safeStr(etTipo.getText());
                    int duracao = parseIntSafe(safeStr(etDuracao.getText()));

                    if (nome.isEmpty()) {
                        Toast.makeText(getContext(), "Indica um nome para o treino.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (duracao <= 0) {
                        Toast.makeText(getContext(), "Indica uma duração válida (minutos).", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Podes substituir por exercícios reais do teu editor, se tiveres.
                    List<Map<String, Object>> exercicios = new ArrayList<>();
                    // Exemplo (opcional):
                    // Map<String, Object> ex = new HashMap<>();
                    // ex.put("nome", "Flexões");
                    // ex.put("tempo", "30 seg");
                    // ex.put("dificuldade", "Média");
                    // exercicios.add(ex);

                    guardarTreino(nome, tipo, duracao, exercicios);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Guarda o treino em /treinos com docId gerado e cria um post em /posts.
     * - Garante que o campo "id" do documento é igual ao docId (para os detalhes funcionarem)
     * - Define visibility = "friends" por omissão (alinha com as rules para amigos)
     * - Define createdAt (server timestamp) para ordenação no feed
     * - Cria também um /posts com treinoId para aparecer no feed dos amigos
     */
    private void guardarTreino(@NonNull String nome,
                               @NonNull String tipo,
                               int duracaoMin,
                               @NonNull List<Map<String, Object>> exercicios) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Sessão expirada. Faz login novamente.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1) Gera o docRef e usa o seu ID como "id" do treino
        DocumentReference treinoRef = db.collection("treinos").document();
        String treinoId = treinoRef.getId();

        Map<String, Object> treinoData = new HashMap<>();
        treinoData.put("id", treinoId);
        treinoData.put("userId", user.getUid());
        treinoData.put("nome", nome);
        treinoData.put("tipo", tipo);
        treinoData.put("duracao", duracaoMin);
        treinoData.put("exercicios", exercicios);              // lista de maps
        treinoData.put("visibility", "friends");               // ou "public"/"private" conforme quiseres
        treinoData.put("createdAt", FieldValue.serverTimestamp());

        // 2) Grava o treino, depois cria o post
        treinoRef.set(treinoData)
                .addOnSuccessListener(aVoid -> {
                    // 2.a) Cria um post para o feed
                    Map<String, Object> post = new HashMap<>();
                    post.put("ownerUid", user.getUid());
                    post.put("treinoId", treinoId);
                    post.put("treinoNome", nome);
                    post.put("durationMin", duracaoMin);
                    post.put("xpGanho", 0); // ajusta se tiveres cálculo de XP
                    post.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("posts").add(post)
                            .addOnSuccessListener(r -> {
                                Toast.makeText(getContext(), "Treino guardado e publicado no feed!", Toast.LENGTH_SHORT).show();
                                carregarTreinos(); // refresh lista
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Treino guardado, mas falhou criar post: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                carregarTreinos();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Falha ao guardar treino: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    // ====================== LISTAR / APAGAR ======================

    private void carregarTreinos() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Sessão expirada. Faz login novamente.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("treinos")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Treino> listaTreinos = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Treino t = doc.toObject(Treino.class);
                        if (t != null) {
                            // Garante ID do Firestore e também alinha se o campo "id" do doc estiver vazio
                            String id = doc.getId();
                            t.setId(id);
                            // Se por algum motivo o documento não tiver o campo "id", corrige-o em background
                            Object storedId = doc.get("id");
                            if (storedId == null || String.valueOf(storedId).isEmpty()) {
                                doc.getReference().update("id", id);
                            }
                            listaTreinos.add(t);
                        }
                    }
                    adapter.setTreinos(listaTreinos);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Erro ao carregar treinos: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void eliminarTreino(String treinoId) {
        FirebaseFirestore.getInstance()
                .collection("treinos")
                .document(treinoId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    adapter.removeTreinoById(treinoId);
                    Toast.makeText(getContext(), "Treino eliminado.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage();
                    if (msg != null && msg.toLowerCase().contains("permission")) {
                        Toast.makeText(getContext(),
                                "Falha ao eliminar treino (permissões do Firestore). Verifica as regras.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(),
                                "Falha ao eliminar treino: " + (msg != null ? msg : "erro desconhecido"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ====================== HELPERS ======================

    private static String safeStr(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
