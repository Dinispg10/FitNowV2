package com.example.fitnow;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNome = itemView.findViewById(R.id.tvNomeExercicio);
            tvTempo = itemView.findViewById(R.id.tvTempoExercicio);
            tvDificuldade = itemView.findViewById(R.id.tvDificuldade);
        }
    }
}