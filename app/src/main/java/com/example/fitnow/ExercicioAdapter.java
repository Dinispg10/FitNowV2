package com.example.fitnow;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExercicioAdapter extends RecyclerView.Adapter<ExercicioAdapter.ViewHolder> {

    private final List<Exercicio> exercicios = new ArrayList<>();
    private final List<Exercicio> exerciciosSelecionados;
    private final OnSelectionChangeListener selectionChangeListener;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(List<Exercicio> selecionados);
    }

    public ExercicioAdapter(List<Exercicio> selecionados, OnSelectionChangeListener listener) {
        this.exerciciosSelecionados = selecionados != null ? selecionados : new ArrayList<>();
        this.selectionChangeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exercicio, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Exercicio exercicio = exercicios.get(position);
        holder.tvNome.setText(exercicio.getNome());
        holder.tvTempo.setText(exercicio.getTempo());
        holder.tvDificuldade.setText(exercicio.getDificuldade());
        bindImagem(holder, exercicio);

        // Visual diferenciado para selecionado
        if (exerciciosSelecionados.contains(exercicio)) {
            holder.itemView.setBackgroundColor(Color.parseColor("#408080")); // Cor de seleção
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;

            if (exerciciosSelecionados.contains(exercicio)) {
                exerciciosSelecionados.remove(exercicio);
            } else {
                exerciciosSelecionados.add(exercicio);
            }

            notifyItemChanged(adapterPosition);

            if (selectionChangeListener != null) {
                selectionChangeListener.onSelectionChanged(new ArrayList<>(exerciciosSelecionados));
            }
        });
    }
    private void bindImagem(@NonNull ViewHolder holder, @NonNull Exercicio exercicio) {
        String imagemResName = deriveDrawableName(exercicio);
        int fallback = R.drawable.ic_launcher_foreground;
        if (imagemResName == null || imagemResName.trim().isEmpty()) {
            holder.imgExercicio.setImageResource(fallback);
            return;
        }

        int resId = holder.itemView.getContext()
                .getResources()
                .getIdentifier(imagemResName, "drawable", holder.itemView.getContext().getPackageName());

        holder.imgExercicio.setImageResource(resId != 0 ? resId : fallback);
    }

    /**
     * Resolve o nome do drawable para o exercício. Se o Firestore não tiver o campo, gera a partir do nome
     * do exercício (ex.: "Prancha Lateral" -> "prancha_lateral").
     */
    private String deriveDrawableName(@NonNull Exercicio exercicio) {
        String base = !TextUtils.isEmpty(exercicio.getImagemResName())
                ? exercicio.getImagemResName()
                : exercicio.getNome();

        if (TextUtils.isEmpty(base)) return null;

        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String slug = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return slug;
    }

    @Override
    public int getItemCount() {
        return exercicios == null ? 0 : exercicios.size();
    }

    public List<Exercicio> getExerciciosSelecionados() {
        return new ArrayList<>(exerciciosSelecionados);
    }

    public void atualizarDados(List<Exercicio> novosExercicios) {
        exercicios.clear();
        if (novosExercicios != null) {
            exercicios.addAll(novosExercicios);
        }
        notifyDataSetChanged();

        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(new ArrayList<>(exerciciosSelecionados));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNome, tvTempo, tvDificuldade;
        ImageView imgExercicio;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNome = itemView.findViewById(R.id.tvNomeExercicio);
            tvTempo = itemView.findViewById(R.id.tvTempoExercicio);
            tvDificuldade = itemView.findViewById(R.id.tvDificuldade);
            imgExercicio = itemView.findViewById(R.id.imgExercicio);
        }
    }
}