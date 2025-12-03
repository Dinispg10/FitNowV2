package com.example.fitnow;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CriarTreinoFragment extends Fragment {

    private Spinner spinnerParteCorpo;
    private Spinner spinnerCategoria;
    private RecyclerView recyclerExercicios;
    private EditText editNomeTreino;
    private EditText editDuracao;
    private Button btnSalvarTreino;

    private String categoriaSelecionada = "";
    private String parteSelecionada = "";

    private ExercicioAdapter adapterExercicios;
    private final List<Exercicio> exerciciosSelecionados = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // partes para "Força" (tem de bater com o campo 'parte' no Firestore)
    private final String[] partesCorpo = {"Peito", "Pernas", "Braços", "Ombros", "Costas"};

    // categorias vindas de exercise_types
    private final List<String> categorias = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_criar_treino, container, false);

        spinnerParteCorpo = view.findViewById(R.id.spinnerParteCorpo);
        spinnerCategoria = view.findViewById(R.id.spinnerCategoria);
        recyclerExercicios = view.findViewById(R.id.recyclerExercicios);
        editNomeTreino = view.findViewById(R.id.editNomeTreino);
        editDuracao = view.findViewById(R.id.editDuracao);
        btnSalvarTreino = view.findViewById(R.id.btnSalvarTreino);

        // Duração é calculada automaticamente → tornar não editável
        editDuracao.setFocusable(false);
        editDuracao.setClickable(false);
        editDuracao.setLongClickable(false);
        editDuracao.setKeyListener(null);
        editDuracao.setText("0 min");

        recyclerExercicios.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerExercicios.setHasFixedSize(true);
        recyclerExercicios.setClipToPadding(false);

        // Spinner de Partes (Força)
        ArrayAdapter<String> adapterParte = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, partesCorpo);
        adapterParte.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerParteCorpo.setAdapter(adapterParte);
        spinnerParteCorpo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                parteSelecionada = parent.getItemAtPosition(position).toString();
                if ("Força".equals(categoriaSelecionada)) {
                    carregarExercicios(categoriaSelecionada, parteSelecionada);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinnerParteCorpo.setVisibility(View.GONE);

        // Carregar categorias dinamicamente do Firestore
        carregarCategorias();

        btnSalvarTreino.setOnClickListener(v -> salvarTreino());

        return view;
    }

    private void carregarCategorias() {
        db.collection("exercise_types")
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    categorias.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String n = d.getString("name");
                        if (!TextUtils.isEmpty(n)) categorias.add(n);
                    }
                    if (categorias.isEmpty()) {
                        Toast.makeText(getContext(), "Sem tipos configurados (exercise_types).", Toast.LENGTH_LONG).show();
                        return;
                    }

                    ArrayAdapter<String> adapterCategoria = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_spinner_item,
                            categorias
                    );
                    adapterCategoria.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerCategoria.setAdapter(adapterCategoria);

                    spinnerCategoria.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View view12, int position, long id) {
                            categoriaSelecionada = parent.getItemAtPosition(position).toString();
                            atualizarCategoriaSelecionada();
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) { }
                    });

                    spinnerCategoria.setSelection(0);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Erro a carregar tipos: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void atualizarCategoriaSelecionada() {
        if ("Força".equals(categoriaSelecionada)) {
            spinnerParteCorpo.setVisibility(View.VISIBLE);
            if (spinnerParteCorpo.getSelectedItem() == null) spinnerParteCorpo.setSelection(0);
            parteSelecionada = spinnerParteCorpo.getSelectedItem().toString();
            carregarExercicios(categoriaSelecionada, parteSelecionada);
        } else {
            spinnerParteCorpo.setVisibility(View.GONE);
            carregarExercicios(categoriaSelecionada, null);
        }
    }

    private void carregarExercicios(String tipo, @Nullable String parte) {
        Query base = db.collection("exercises").whereEqualTo("tipo", tipo);
        Query q = base;

        boolean tentarFiltrarParte = "Força".equals(tipo) && parte != null && !parte.trim().isEmpty();
        if (tentarFiltrarParte) {
            q = q.whereEqualTo("parte", parte);
        }

        q.get().addOnSuccessListener(snap -> {
            // Se filtrámos por parte e veio vazio (ainda sem 'parte' nos docs), fallback para todos de Força
            if (tentarFiltrarParte && (snap == null || snap.isEmpty())) {
                base.get().addOnSuccessListener(snapAll -> mostrarExercicios(mapDocsParaExercicios(snapAll)))
                        .addOnFailureListener(e -> Toast.makeText(getContext(),
                                "Erro a carregar exercícios: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            mostrarExercicios(mapDocsParaExercicios(snap));
        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Erro a carregar exercícios: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private List<Exercicio> mapDocsParaExercicios(QuerySnapshot snap) {
        List<Exercicio> lista = new ArrayList<>();
        if (snap == null) return lista;
        for (DocumentSnapshot d : snap.getDocuments()) {
            String nome = d.getString("nome");
            String dificuldade = d.getString("dificuldade");
            String imagemResName = d.contains("imagemResName")
                    ? d.getString("imagemResName")
                    : d.getString("imagem");


            Object tempoObj = d.get("tempo");
            String tempo = null;
            if (tempoObj instanceof Number) {
                tempo = ((Number) tempoObj).intValue() + " min";
            } else if (tempoObj != null) {
                tempo = tempoObj.toString();
            }

            if (!TextUtils.isEmpty(nome) && !TextUtils.isEmpty(tempo) && !TextUtils.isEmpty(dificuldade)) {
                lista.add(new Exercicio(nome, tempo, dificuldade, imagemResName));
            }
        }
        return lista;
    }

    private void mostrarExercicios(List<Exercicio> exercicios) {
        if (adapterExercicios == null) {
            adapterExercicios = new ExercicioAdapter(exerciciosSelecionados, selecionados -> {
                atualizarDuracaoTotal();
            });
            recyclerExercicios.setAdapter(adapterExercicios);
        }
        adapterExercicios.atualizarDados(exercicios);
        recyclerExercicios.setVisibility(exercicios == null || exercicios.isEmpty() ? View.GONE : View.VISIBLE);
        atualizarDuracaoTotal();
    }

    private void salvarTreino() {
        String nome = editNomeTreino.getText().toString().trim();

        if (nome.isEmpty()) {
            Toast.makeText(getContext(), "Preencha o nome do treino", Toast.LENGTH_SHORT).show();
            return;
        }
        if (exerciciosSelecionados.isEmpty()) {
            Toast.makeText(getContext(), "Selecione pelo menos um exercício", Toast.LENGTH_SHORT).show();
            return;
        }

        int duracao = getDuracaoTotalSelecionados();
        if (duracao <= 0) {
            Toast.makeText(getContext(), "A duração total tem de ser > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Usuário não autenticado", Toast.LENGTH_SHORT).show();
            return;
        }

        // >>> Salvar via Map para garantir visibility + id <<<
        Map<String, Object> data = new HashMap<>();
        data.put("nome", nome);
        data.put("tipo", "Personalizado");
        data.put("duracao", duracao);
        data.put("userId", user.getUid());
        data.put("exercicios", mapearExerciciosSelecionados());
        data.put("visibility", "friends"); // <— IMPORTANTE

        FirebaseFirestore.getInstance()
                .collection("treinos")
                .add(data)
                .addOnSuccessListener(ref -> {
                    // guardar o id do documento dentro do próprio doc
                    ref.update("id", ref.getId());
                    Toast.makeText(getContext(), "Treino salvo com sucesso!", Toast.LENGTH_SHORT).show();
                    limparCampos();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Erro ao salvar treino.", Toast.LENGTH_SHORT).show()
                );
    }

    private List<Map<String, Object>> mapearExerciciosSelecionados() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Exercicio exercicio : exerciciosSelecionados) {
            Map<String, Object> map = new HashMap<>();
            map.put("nome", exercicio.getNome());
            map.put("tempo", exercicio.getTempo());           // gravar String (ex.: "15 min")
            map.put("dificuldade", exercicio.getDificuldade());
            map.put("imagemResName", exercicio.getImagemResName());
            lista.add(map);
        }
        return lista;
    }

    private void limparCampos() {
        editNomeTreino.setText("");
        editDuracao.setText("0 min");
        exerciciosSelecionados.clear();
        if (adapterExercicios != null) {
            adapterExercicios.atualizarDados(new ArrayList<>());
        }
        recyclerExercicios.setVisibility(View.GONE);
        spinnerParteCorpo.setVisibility(View.GONE);
        if (spinnerCategoria.getAdapter() != null) spinnerCategoria.setSelection(0);
        if (spinnerParteCorpo.getAdapter() != null) spinnerParteCorpo.setSelection(0);
    }

    /* ==== Cálculo automático de duração ==== */

    private int parseMinutos(String tempo) {
        if (tempo == null) return 0;
        int len = tempo.length();
        for (int i = 0; i < len; i++) {
            char c = tempo.charAt(i);
            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < len && Character.isDigit(tempo.charAt(j))) j++;
                try { return Integer.parseInt(tempo.substring(i, j)); } catch (Exception ignore) { }
                break;
            }
        }
        return 0;
    }

    private int getDuracaoTotalSelecionados() {
        int total = 0;
        for (Exercicio e : exerciciosSelecionados) {
            total += parseMinutos(e.getTempo());
        }
        return total;
    }

    private void atualizarDuracaoTotal() {
        int total = getDuracaoTotalSelecionados();
        editDuracao.setText(total + " min");
    }


}
