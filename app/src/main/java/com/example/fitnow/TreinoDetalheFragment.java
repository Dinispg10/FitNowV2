package com.example.fitnow;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detalhe de Treino
 * Aceita argumentos:
 *  - "ownerUid"   (String) -> dono do treino (para contexto; leitura via regras)
 *  - "treinoId"   (String) -> ID do documento em /treinos
 *  - "readOnly"   (boolean)-> true se vindo do feed de amigo
 *
 * Mantém compat com a versão antiga usando ARG_ID ("treinoId").
 */
public class TreinoDetalheFragment extends Fragment {

    private static final String TAG = "TreinoDetalheFragment";

    // Novos argumentos (vêm do HomeFragment/MainActivity)
    private static final String ARG_OWNER_UID = "ownerUid";
    private static final String ARG_TREINO_ID = "treinoId";
    private static final String ARG_READ_ONLY = "readOnly";

    // Compat anterior
    private static final String ARG_ID_LEGACY = "treinoId";

    public static TreinoDetalheFragment newInstance(String treinoId) {
        Bundle b = new Bundle();
        b.putString(ARG_TREINO_ID, treinoId);
        TreinoDetalheFragment f = new TreinoDetalheFragment();
        f.setArguments(b);
        return f;
    }

    private TextView tvNome, tvTipo, tvDuracao, tvExerciciosVazio;
    private RecyclerView recyclerExercicios;
    private ExercicioAdapter exercicioAdapter;
    private Button btnIniciar;
    private Button btnGuardar; // novo

    private String ownerUidArg;
    private String treinoIdArg;
    private boolean readOnlyArg = false;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_treino_detalhe, container, false);

        // Cabeçalho
        tvNome = v.findViewById(R.id.tvNomeTreinoDetalhe);
        tvTipo = v.findViewById(R.id.tvTipoTreinoDetalhe);
        tvDuracao = v.findViewById(R.id.tvDuracaoTreinoDetalhe);

        // Lista
        recyclerExercicios = v.findViewById(R.id.recyclerExerciciosDetalhe);
        tvExerciciosVazio = v.findViewById(R.id.tvExerciciosVazio);

        if (recyclerExercicios != null) {
            recyclerExercicios.setLayoutManager(new GridLayoutManager(getContext(), 2));
            recyclerExercicios.setHasFixedSize(true);
            exercicioAdapter = new ExercicioAdapter(new ArrayList<>(), sel -> {
                // Detalhe em modo leitura: não seleciona
            });
            recyclerExercicios.setAdapter(exercicioAdapter);
        }

        // Botões
        btnIniciar = v.findViewById(R.id.btnIniciarTreino);
        btnGuardar = v.findViewById(R.id.btnGuardarTreino); // adiciona este botão no XML se ainda não existir

        if (btnIniciar == null) {
            Log.w(TAG, "btnIniciarTreino não encontrado no layout.");
        }
        if (btnGuardar == null) {
            Log.w(TAG, "btnGuardarTreino não encontrado no layout.");
        }

        // Ler argumentos (novos + legacy)
        Bundle b = getArguments();
        if (b != null) {
            ownerUidArg = b.getString(ARG_OWNER_UID, null);
            treinoIdArg = b.getString(ARG_TREINO_ID, b.getString(ARG_ID_LEGACY, null));
            readOnlyArg = b.getBoolean(ARG_READ_ONLY, false);
        }

        if (TextUtils.isEmpty(treinoIdArg)) {
            Toast.makeText(getContext(), "Treino inválido.", Toast.LENGTH_SHORT).show();
            requireActivity().onBackPressed();
            return v;
        }

        // Carregar dados do treino
        db.collection("treinos")
                .document(treinoIdArg)
                .get()
                .addOnSuccessListener(this::preencherUI)
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Erro ao carregar detalhes.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Falha a carregar treino: " + e.getMessage(), e);
                });

        return v;
    }

    private void preencherUI(DocumentSnapshot doc) {
        Treino t = doc.toObject(Treino.class);
        if (t == null) {
            Toast.makeText(getContext(), "Treino não encontrado.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Cabeçalho
        tvNome.setText(!TextUtils.isEmpty(t.getNome()) ? t.getNome() : "(Sem nome)");
        tvTipo.setText(!TextUtils.isEmpty(t.getTipo()) ? t.getTipo() : "(Sem tipo)");
        tvDuracao.setText(t.getDuracao() > 0 ? (t.getDuracao() + " min") : "(Sem duração)");

        // Exercícios
        List<Exercicio> lista = toExerciciosList(t.getExercicios());
        if (exercicioAdapter != null) {
            if (lista.isEmpty()) {
                recyclerExercicios.setVisibility(View.GONE);
                if (tvExerciciosVazio != null) tvExerciciosVazio.setVisibility(View.VISIBLE);
            } else {
                recyclerExercicios.setVisibility(View.VISIBLE);
                if (tvExerciciosVazio != null) tvExerciciosVazio.setVisibility(View.GONE);
                exercicioAdapter.atualizarDados(lista);
            }
        }

        // Iniciar treino (se o botão existir, deixa sempre iniciar – mesmo readOnly, é só execução)
        if (btnIniciar != null) {
            btnIniciar.setOnClickListener(view -> {
                TreinoRunnerFragment fragment = TreinoRunnerFragment.newInstance(doc.getId());
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
            });
        }

        // Botão GUARDAR (apenas se o treino não for meu OU se vier em readOnly)
        if (btnGuardar != null) {
            String myUid = FirebaseAuth.getInstance().getUid();
            boolean treinoEhMeu = myUid != null && myUid.equals(t.getUserId());
            btnGuardar.setVisibility((readOnlyArg || !treinoEhMeu) ? View.VISIBLE : View.GONE);

            btnGuardar.setOnClickListener(v -> guardarTreinoParaMim(t));
        }
    }

    /** Duplica o treino do amigo para /treinos com o meu userId (Opção A). */
    private void guardarTreinoParaMim(@NonNull Treino tOrig) {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) {
            Toast.makeText(getContext(), "Sessão inválida.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> novo = new HashMap<>();
        novo.put("userId", myUid);
        novo.put("nome", safeStr(tOrig.getNome(), "(Sem nome)"));
        novo.put("tipo", safeStr(tOrig.getTipo(), ""));
        novo.put("duracao", tOrig.getDuracao());
        // Mantém a lista de exercícios tal como está (Firestore converte Map/List)
        if (tOrig.getExercicios() != null) novo.put("exercicios", tOrig.getExercicios());

        // Visibilidade do treino que estás a guardar — geralmente "private"
        novo.put("visibility", "private");

        // Datas
        novo.put("createdAt", FieldValue.serverTimestamp());
        // (opcional) se guardas updatedAt
        novo.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("treinos")
                .add(novo)
                .addOnSuccessListener(r -> Toast.makeText(getContext(), "Treino guardado nos teus.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Falha ao guardar treino: " + e.getMessage(), e);
                    Toast.makeText(getContext(), "Erro ao guardar treino.", Toast.LENGTH_SHORT).show();
                });
    }

    // ---------- Utils ----------

    private List<Exercicio> toExerciciosList(List<?> src) {
        List<Exercicio> lista = new ArrayList<>();
        if (src == null) return lista;

        for (Object obj : src) {
            if (obj instanceof Exercicio) {
                lista.add((Exercicio) obj);
            } else if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) obj;
                String nome = toStr(m.get("nome"), "(sem nome)");
                String dificuldade = toStr(m.get("dificuldade"), "");
                String tempo = mapTempoToString(m.get("tempo"), m.get("duracao")); // tenta ambos
                lista.add(new Exercicio(nome, tempo, dificuldade));
            }
        }
        return lista;
    }

    private static String safeStr(String s, String def) {
        return (s == null) ? def : s;
    }

    private static String toStr(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static String mapTempoToString(Object... campos) {
        for (Object tempoField : campos) {
            if (tempoField == null) continue;
            if (tempoField instanceof Number) {
                return ((Number) tempoField).intValue() + " min";
            }
            return String.valueOf(tempoField);
        }
        return "";
    }
}
