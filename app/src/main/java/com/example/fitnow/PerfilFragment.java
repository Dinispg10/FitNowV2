package com.example.fitnow;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerfilFragment extends Fragment {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("^(\\d{4}-\\d{2})");

    // UI
    private ImageView ivFoto;
    private EditText etNome;
    private Spinner spSexo;
    private EditText etDataNascimento; // yyyy-MM-dd
    private EditText etAlturaCm;       // cm
    private EditText etPesoKg;         // kg
    private TextView tvImc;
    private SwitchCompat switchFeedVisibilidade;  // ✅ ADICIONADO
    private Button btnEscolherFoto, btnGuardar, btnAbrirDefinicoes;

    // Barra de progresso entre níveis
    private TextView tvLevelLeft, tvLevelRight, tvXpMini, tvAmigos;
    private ProgressBar progressXp;

    // Gráfico histórico
    private LineChart lineChart;

    // Estado ✅ VISIBILIDADE
    private Uri fotoSelecionadaUri = null;
    private String fotoBase64Atual = null;
    private String postVisibilityPreference = "friends";  // ✅ ADICIONADO
    private boolean visibilityChangedByUser = false;     // ✅ ADICIONADO
    private boolean isApplyingVisibilityFromDb = false;  // ✅ ADICIONADO

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    fotoSelecionadaUri = uri;
                    ivFoto.setImageURI(uri);
                    fotoBase64Atual = encodeImageToBase64(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_perfil, container, false);

        ivFoto = v.findViewById(R.id.ivFotoPerfil);
        etNome = v.findViewById(R.id.etNomePerfil);
        spSexo = v.findViewById(R.id.spSexoPerfil);
        etDataNascimento = v.findViewById(R.id.etDataNascimento);
        etAlturaCm = v.findViewById(R.id.etAlturaCm);
        etPesoKg = v.findViewById(R.id.etPesoKg);
        tvImc = v.findViewById(R.id.tvImcValor);
        switchFeedVisibilidade = v.findViewById(R.id.switchFeedVisibilidade);  // ✅ ADICIONADO
        btnEscolherFoto = v.findViewById(R.id.btnEscolherFoto);
        btnGuardar = v.findViewById(R.id.btnGuardarPerfil);
        btnAbrirDefinicoes = v.findViewById(R.id.btnAbrirDefinicoes);

        // Views da barra de progresso e amigos
        tvLevelLeft  = v.findViewById(R.id.tvLevelLeft);
        tvLevelRight = v.findViewById(R.id.tvLevelRight);
        progressXp   = v.findViewById(R.id.progressXp);
        tvXpMini     = v.findViewById(R.id.tvXpMini);
        tvAmigos     = v.findViewById(R.id.tvAmigos);

        // Gráfico
        lineChart = v.findViewById(R.id.lineChartHistorico);
        if (lineChart != null) {
            lineChart.getDescription().setEnabled(false);
            lineChart.getLegend().setEnabled(false);
            lineChart.getAxisRight().setEnabled(false);
            lineChart.getAxisLeft().setAxisMinimum(0f);
            lineChart.setNoDataText("Sem histórico dos últimos 6 meses");
            lineChart.setExtraOffsets(8f, 8f, 8f, 12f);

            XAxis x = lineChart.getXAxis();
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
            x.setGranularity(1f);
            x.setDrawGridLines(false);
            x.setTextSize(11f);
            x.setLabelRotationAngle(-10f);
        }

        String[] opcoesSexo = new String[]{"", "Masculino", "Feminino", "Outro"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, opcoesSexo);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSexo.setAdapter(adapter);

        etDataNascimento.setFocusable(false);
        etDataNascimento.setOnClickListener(v1 -> abrirDatePicker());

        View.OnFocusChangeListener recalc = (view, hasFocus) -> {
            if (!hasFocus) calcularImcUI();
        };
        etAlturaCm.setOnFocusChangeListener(recalc);
        etPesoKg.setOnFocusChangeListener(recalc);

        btnEscolherFoto.setOnClickListener(v12 -> pickImageLauncher.launch("image/*"));
        btnGuardar.setOnClickListener(v13 -> guardarPerfil());

        // ✅ SWITCH COMPLETO - Toggle ligado = friends, desligado = private
        if (switchFeedVisibilidade != null) {
            switchFeedVisibilidade.setEnabled(false); // Desativa até carregar do DB
            switchFeedVisibilidade.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isApplyingVisibilityFromDb) return; // Ignora mudanças automáticas do DB
                postVisibilityPreference = isChecked ? "friends" : "private";
                visibilityChangedByUser = true; // Marca para salvar
            });
        }

        if (btnAbrirDefinicoes != null) {
            btnAbrirDefinicoes.setOnClickListener(view -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchFragment(new ConfiguracoesFragment());
                }
            });
        }

        carregarPerfil();
        return v;
    }

    private void abrirDatePicker() {
        final Calendar cal = Calendar.getInstance();
        String atual = etDataNascimento.getText().toString().trim();
        if (!TextUtils.isEmpty(atual)) {
            try { cal.setTime(dateFormat.parse(atual)); } catch (ParseException ignore) {}
        }
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dlg = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar c2 = Calendar.getInstance();
                    c2.set(year, month, dayOfMonth);
                    etDataNascimento.setText(dateFormat.format(c2.getTime()));
                    calcularImcUI();
                },
                y, m, d
        );
        dlg.show();
    }

    private void calcularImcUI() {
        Double imc = calcularImc();
        if (imc == null) {
            tvImcValorSafe("(—)");
        } else {
            tvImcValorSafe(String.format(Locale.getDefault(), "%.1f", imc));
        }
    }

    private void tvImcValorSafe(String text) {
        if (isAdded()) tvImc.setText(text);
    }

    @Nullable
    private Double calcularImc() {
        try {
            String sAlt = etAlturaCm.getText().toString().trim();
            String sPeso = etPesoKg.getText().toString().trim();
            if (TextUtils.isEmpty(sAlt) || TextUtils.isEmpty(sPeso)) return null;
            int alturaCm = Integer.parseInt(sAlt);
            double pesoKg = Double.parseDouble(sPeso);
            if (alturaCm <= 0 || pesoKg <= 0) return null;
            double alturaM = alturaCm / 100.0;
            return pesoKg / (alturaM * alturaM);
        } catch (Exception e) {
            return null;
        }
    }

    private void carregarPerfil() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    int xp = 0;
                    int level = 1;
                    String xpPeriodDoc = null;

                    if (doc != null && doc.exists()) {
                        try {
                            Object xpObj = doc.get("xp");
                            if (xpObj instanceof Number) xp = ((Number) xpObj).intValue();

                            Object lvlObj = doc.get("level");
                            if (lvlObj instanceof Number) level = ((Number) lvlObj).intValue();

                            xpPeriodDoc = normalizePeriod(doc.getString("xpPeriod"));
                        } catch (Exception ignored) {}
                    }

                    String periodAtual = getCurrentXpPeriod();
                    boolean precisaRollover = (xpPeriodDoc == null || !xpPeriodDoc.equals(periodAtual));

                    if (precisaRollover) {
                        String uid2 = FirebaseAuth.getInstance().getUid();
                        if (uid2 == null) return;

                        Map<String, Object> resetData = new HashMap<>();
                        if (xpPeriodDoc != null) {
                            resetData.put("levelHistory." + xpPeriodDoc, level);
                        }
                        resetData.put("xp", 0);
                        resetData.put("level", 1);
                        resetData.put("xpPeriod", periodAtual);
                        resetData.put("lastUpdated", Timestamp.now());

                        db.collection("users")
                                .document(uid2)
                                .set(resetData, SetOptions.merge())
                                .addOnSuccessListener(v -> {
                                    db.collection("users")
                                            .document(uid2)
                                            .get()
                                            .addOnSuccessListener(updatedDoc -> {
                                                preencherUI(updatedDoc);
                                                carregarGraficoHistorico(uid2, updatedDoc);
                                                carregarNumeroAmigos(uid2);
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(getContext(),
                                                        "Erro ao recarregar perfil após rollover.",
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(getContext(),
                                            "Erro ao atualizar período de XP.",
                                            Toast.LENGTH_SHORT).show();
                                    preencherUI(doc);
                                    carregarGraficoHistorico(uid, doc);
                                    carregarNumeroAmigos(uid);
                                });

                    } else {
                        preencherUI(doc);
                        carregarGraficoHistorico(uid, doc);
                        carregarNumeroAmigos(uid);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Falha ao carregar perfil.", Toast.LENGTH_SHORT).show());
    }

    private void preencherUI(DocumentSnapshot doc) {
        if (doc != null && doc.exists()) {
            String nome = doc.getString("displayName");
            String sexo = doc.getString("sexo");
            String birthDate = doc.getString("birthDate");
            Long heightCm = doc.getLong("heightCm");
            Double weightKg = null;
            try {
                Object w = doc.get("weightKg");
                if (w instanceof Number) weightKg = ((Number) w).doubleValue();
            } catch (Exception ignored) {}

            int xp = 0;
            int level = 1;
            try {
                Object xpObj = doc.get("xp");
                if (xpObj instanceof Number) xp = ((Number) xpObj).intValue();
                Object lvlObj = doc.get("level");
                if (lvlObj instanceof Number) level = ((Number) lvlObj).intValue();
            } catch (Exception ignored) {}

            // ✅ CARREGA VISIBILIDADE DO FIRESTORE
            String postVisibility = doc.getString("postVisibility");
            postVisibilityPreference = TextUtils.isEmpty(postVisibility) ? "friends" : postVisibility;
            visibilityChangedByUser = false;

            if (switchFeedVisibilidade != null) {
                isApplyingVisibilityFromDb = true;  // Evita loop
                switchFeedVisibilidade.setChecked(!"private".equals(postVisibilityPreference)); // ligado=friends
                switchFeedVisibilidade.setEnabled(true);
                isApplyingVisibilityFromDb = false;
            }

            fotoBase64Atual = doc.getString("photoBase64");

            if (!TextUtils.isEmpty(nome)) etNome.setText(nome);

            if (!TextUtils.isEmpty(sexo)) {
                for (int i = 0; i < spSexo.getCount(); i++) {
                    if (sexo.equals(spSexo.getItemAtPosition(i))) {
                        spSexo.setSelection(i);
                        break;
                    }
                }
            }

            if (!TextUtils.isEmpty(birthDate)) etDataNascimento.setText(birthDate);
            if (heightCm != null && heightCm > 0) etAlturaCm.setText(String.valueOf(heightCm));
            if (weightKg != null && weightKg > 0) etPesoKg.setText(String.valueOf(weightKg));

            if (!TextUtils.isEmpty(fotoBase64Atual) && isAdded()) {
                Bitmap bmp = decodeBase64ToBitmap(fotoBase64Atual);
                if (bmp != null) ivFoto.setImageBitmap(bmp);
            }

            atualizarBarraNivel(level, xp);
            calcularImcUI();
        } else {
            tvImcValorSafe("(—)");
            // Default para switch se não há dados
            if (switchFeedVisibilidade != null) {
                isApplyingVisibilityFromDb = true;
                switchFeedVisibilidade.setChecked(true); // friends por default
                switchFeedVisibilidade.setEnabled(true);
                isApplyingVisibilityFromDb = false;
            }
        }
    }

    private void carregarGraficoHistorico(@NonNull String uid, @Nullable DocumentSnapshot doc) {
        if (lineChart == null) return;

        if (doc == null || !doc.exists()) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .get()
                    .addOnSuccessListener(this::plotFromDoc)
                    .addOnFailureListener(e -> {
                        lineChart.clear();
                        lineChart.setNoDataText("Erro ao carregar histórico");
                        lineChart.invalidate();
                    });
        } else {
            plotFromDoc(doc);
        }
    }

    private void plotFromDoc(@NonNull DocumentSnapshot doc) {
        if (lineChart == null) return;

        Locale locale = Locale.getDefault();
        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM", locale);
        SimpleDateFormat labelFormat = new SimpleDateFormat("MMM", locale);

        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        Calendar base = Calendar.getInstance();
        base.set(Calendar.DAY_OF_MONTH, 1);
        for (int i = 5; i >= 0; i--) {
            Calendar cursor = (Calendar) base.clone();
            cursor.add(Calendar.MONTH, -i);
            keys.add(keyFormat.format(cursor.getTime()));
            labels.add(formatLabel(labelFormat.format(cursor.getTime())));
        }

        Map<String, Object> histRaw = null;
        Map<String, Integer> histConsolidado = new HashMap<>();
        String xpPeriodDoc = normalizePeriod(doc.getString("xpPeriod"));
        String periodAtual = getCurrentXpPeriod();
        int levelAtual = 0;
        Object lvlDoc = doc.get("level");
        if (lvlDoc instanceof Number) levelAtual = ((Number) lvlDoc).intValue();
        try {
            Object h = doc.get("levelHistory");
            if (h instanceof Map) histRaw = (Map<String, Object>) h;
        } catch (Exception ignored) {}

        if (histRaw != null) {
            for (Map.Entry<String, Object> entry : histRaw.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    String key = normalizePeriod(entry.getKey());
                    if (key != null) {
                        histConsolidado.put(key, ((Number) entry.getValue()).intValue());
                    }
                }
            }
        }

        boolean mudouMes = xpPeriodDoc != null && !xpPeriodDoc.equals(periodAtual);
        if (mudouMes && !histConsolidado.containsKey(xpPeriodDoc)) {
            histConsolidado.put(xpPeriodDoc, levelAtual);
        }

        List<Entry> entries = new ArrayList<>();
        int maxLevel = 0;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int lvl = 0;
            if (histConsolidado.containsKey(key)) {
                lvl = histConsolidado.get(key);
            } else if (!mudouMes && key.equals(xpPeriodDoc)) {
                lvl = levelAtual;
            }
            entries.add(new Entry(i, (float) lvl));
            maxLevel = Math.max(maxLevel, lvl);
        }

        LineDataSet dataSet = new LineDataSet(entries, "Nível por mês");
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(true);
        dataSet.setLineWidth(2.4f);

        int accent = ContextCompat.getColor(requireContext(), R.color.colorAccent);
        int accentAlt = ContextCompat.getColor(requireContext(), R.color.colorAccentAlt);
        int surface = ContextCompat.getColor(requireContext(), R.color.colorSurface);

        dataSet.setColor(accent);
        dataSet.setCircleColor(accent);
        dataSet.setCircleHoleColor(surface);
        dataSet.setCircleRadius(4.5f);
        dataSet.setHighLightColor(accentAlt);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ColorUtils.setAlphaComponent(accent, 80));

        lineChart.setData(new LineData(dataSet));
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        float paddedMax = Math.max(5f, maxLevel + 1f);
        lineChart.getAxisLeft().setAxisMaximum(paddedMax);
        lineChart.getAxisLeft().setTextSize(11f);
        lineChart.getAxisLeft().setGranularity(1f);
        lineChart.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.colorOutline));
        lineChart.getXAxis().setGridColor(ContextCompat.getColor(requireContext(), R.color.colorOutline));
        lineChart.animateY(600);
        lineChart.invalidate();
    }

    private String formatLabel(String raw) {
        if (TextUtils.isEmpty(raw)) return raw;
        String lower = raw.toLowerCase(Locale.getDefault());
        return lower.substring(0, 1).toUpperCase(Locale.getDefault()) + lower.substring(1);
    }

    @Nullable
    private String normalizePeriod(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        Matcher matcher = PERIOD_PATTERN.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void guardarPerfil() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(getContext(), "Sessão expirada. Faz login.", Toast.LENGTH_SHORT).show();
            return;
        }

        String nome = etNome.getText().toString().trim();
        String sexo = spSexo.getSelectedItem() != null ? spSexo.getSelectedItem().toString() : "";
        String birthDate = etDataNascimento.getText().toString().trim();

        Integer alturaCm = null;
        Double pesoKg = null;
        try {
            String sAlt = etAlturaCm.getText().toString().trim();
            if (!TextUtils.isEmpty(sAlt)) alturaCm = Integer.parseInt(sAlt);
        } catch (Exception ignored) {}
        try {
            String sPeso = etPesoKg.getText().toString().trim();
            if (!TextUtils.isEmpty(sPeso)) pesoKg = Double.parseDouble(sPeso);
        } catch (Exception ignored) {}

        if (!TextUtils.isEmpty(birthDate)) {
            try { dateFormat.parse(birthDate); } catch (ParseException e) {
                Toast.makeText(getContext(), "Data inválida. Usa o seletor de data.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (alturaCm != null && alturaCm <= 0) {
            Toast.makeText(getContext(), "Altura inválida.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pesoKg != null && pesoKg <= 0) {
            Toast.makeText(getContext(), "Peso inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        salvarNoFirestore(uid, nome, sexo, birthDate, alturaCm, pesoKg, fotoBase64Atual);
    }

    private void salvarNoFirestore(String uid,
                                   String nome,
                                   String sexo,
                                   String birthDate,
                                   @Nullable Integer alturaCm,
                                   @Nullable Double pesoKg,
                                   @Nullable String photoBase64Novo) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get()
                .continueWithTask(task -> {
                    String photoBase64Final = photoBase64Novo;
                    DocumentSnapshot doc = task.getResult();
                    if (photoBase64Final == null && doc != null && doc.exists()) {
                        String existente = doc.getString("photoBase64");
                        if (!TextUtils.isEmpty(existente)) photoBase64Final = existente;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("displayName", TextUtils.isEmpty(nome) ? null : nome);
                    data.put("sexo", TextUtils.isEmpty(sexo) ? null : sexo);
                    data.put("birthDate", TextUtils.isEmpty(birthDate) ? null : birthDate);
                    if (alturaCm != null) data.put("heightCm", alturaCm); else data.put("heightCm", null);
                    if (pesoKg != null) data.put("weightKg", pesoKg); else data.put("weightKg", null);
                    if (!TextUtils.isEmpty(photoBase64Final)) data.put("photoBase64", photoBase64Final);

                    // ✅ VISIBILIDADE SALVA PERFEITAMENTE
                    String visibilityToSave;
                    if (switchFeedVisibilidade != null && visibilityChangedByUser) {
                        // Toggle ligado = friends, desligado = private
                        visibilityToSave = switchFeedVisibilidade.isChecked() ? "friends" : "private";
                    } else if (doc != null && doc.exists()) {
                        String existing = doc.getString("postVisibility");
                        visibilityToSave = TextUtils.isEmpty(existing) ? "friends" : existing;
                    } else {
                        visibilityToSave = "friends"; // default
                    }
                    data.put("postVisibility", visibilityToSave);
                    postVisibilityPreference = visibilityToSave;
                    visibilityChangedByUser = false;

                    // email
                    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                    if (u != null && !TextUtils.isEmpty(u.getEmail())) {
                        data.put("email", u.getEmail());
                    }

                    WriteBatch batch = db.batch();
                    DocumentReference userRef = db.collection("users").document(uid);
                    batch.set(userRef, data, SetOptions.merge());

                    String email = u != null ? u.getEmail() : null;
                    if (!TextUtils.isEmpty(email)) {
                        String emailLower = email.toLowerCase(Locale.ROOT);
                        DocumentReference luRef = db.collection("user_lookup").document(emailLower);
                        Map<String, Object> lookup = new HashMap<>();
                        lookup.put("uid", uid);
                        batch.set(luRef, lookup, SetOptions.merge());
                    }

                    return batch.commit();
                })
                .addOnSuccessListener(aVoid -> {
                    calcularImcUI();
                    Toast.makeText(getContext(), "Perfil guardado!", Toast.LENGTH_SHORT).show();
                    String uid2 = FirebaseAuth.getInstance().getUid();
                    if (uid2 != null) {
                        FirebaseFirestore.getInstance().collection("users").document(uid2)
                                .get().addOnSuccessListener(this::plotFromDoc);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Falha ao guardar: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ---------- Barra de nível / XP ----------
    private static final int[] LEVEL_THRESHOLDS = new int[]{
            0, 100, 250, 450, 700, 1000, 1350, 1750, 2200, 2700
    };

    private String getCurrentXpPeriod() {
        return new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(new Date());
    }

    private void atualizarBarraNivel(int level, int xp) {
        if (progressXp != null) progressXp.setMax(100);

        if (level < 1) level = 1;
        if (level > 10) level = 10;

        int minXp = LEVEL_THRESHOLDS[level - 1];
        int nextLevel = Math.min(level + 1, 10);
        int maxXp = LEVEL_THRESHOLDS[nextLevel - 1];

        if (level == 10) {
            if (tvLevelLeft != null)  tvLevelLeft.setText("Nível 10");
            if (tvLevelRight != null) tvLevelRight.setText("Máximo");
        } else {
            if (tvLevelLeft != null)  tvLevelLeft.setText("Nível " + level);
            if (tvLevelRight != null) tvLevelRight.setText("Nível " + nextLevel);
        }

        int progress;
        String mini;
        if (level == 10) {
            progress = 100;
            mini = xp + " / " + maxXp + " XP";
        } else {
            int span = Math.max(1, maxXp - minXp);
            int earnedInThisLevel = Math.max(0, xp - minXp);
            progress = Math.min(100, (int) Math.round(earnedInThisLevel * 100.0 / span));
            mini = earnedInThisLevel + " / " + span + " XP";
        }

        if (progressXp != null) progressXp.setProgress(progress);
        if (tvXpMini != null) tvXpMini.setText(mini);
    }

    private void carregarNumeroAmigos(@NonNull String uid) {
        if (tvAmigos == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("friends")
                .get()
                .addOnSuccessListener(snap -> {
                    int numeroAmigos = snap.size();
                    tvAmigos.setText("Amigos: " + numeroAmigos);
                })
                .addOnFailureListener(e -> {
                    tvAmigos.setText("Amigos: 0");
                });
    }

    @Nullable
    private String encodeImageToBase64(@NonNull Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(is);
            if (original == null) return null;

            int maxSize = 256;
            int w = original.getWidth();
            int h = original.getHeight();
            float scale = Math.min((float) maxSize / w, (float) maxSize / h);
            Bitmap scaled = Bitmap.createScaledBitmap(
                    original,
                    Math.round(w * scale),
                    Math.round(h * scale),
                    true
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] bytes = baos.toByteArray();

            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private Bitmap decodeBase64ToBitmap(@NonNull String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
