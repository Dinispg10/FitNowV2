package com.example.fitnow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TreinoAdapter extends RecyclerView.Adapter<TreinoAdapter.ViewHolder> {

    public interface OnTreinoClickListener {
        void onTreinoClick(Treino treino);
    }

    public interface OnTreinoDeleteListener {
        void onTreinoDelete(Treino treino);
    }

    private final List<Treino> treinos = new ArrayList<>();
    private OnTreinoClickListener clickListener;
    private OnTreinoDeleteListener deleteListener;

    public void setOnTreinoClickListener(OnTreinoClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTreinoDeleteListener(OnTreinoDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setTreinos(List<Treino> lista) {
        treinos.clear();
        if (lista != null) treinos.addAll(lista);
        notifyDataSetChanged();
    }

    public void removeTreinoById(String id) {
        if (id == null) return;
        Iterator<Treino> it = treinos.iterator();
        int index = 0;
        while (it.hasNext()) {
            Treino t = it.next();
            if (id.equals(t.getId())) {
                it.remove();
                notifyItemRemoved(index);
                return;
            }
            index++;
        }
        // fallback se não encontrarmos (mantém consistência)
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TreinoAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_treino, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TreinoAdapter.ViewHolder h, int position) {
        Treino t = treinos.get(position);

        h.tvNome.setText(t.getNome() != null ? t.getNome() : "Sem nome");
        String detalhes = "";
        if (t.getTipo() != null) detalhes += t.getTipo();
        if (t.getDuracao() > 0) detalhes += (detalhes.isEmpty() ? "" : " • ") + t.getDuracao() + " min";
        h.tvDetalhes.setText(detalhes);

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onTreinoClick(t);
        });

        h.btnEliminar.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onTreinoDelete(t);
        });
    }

    @Override
    public int getItemCount() {
        return treinos.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNome, tvDetalhes;
        Button btnEliminar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNome = itemView.findViewById(R.id.tvNome);
            tvDetalhes = itemView.findViewById(R.id.tvDetalhes);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }
    }
}
