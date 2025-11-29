package com.example.fitnow;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.Objects;

public class PostAdapter extends ListAdapter<Post, PostAdapter.VH> {

    public interface OnPostClick {
        void onPostClick(@NonNull Post post);
    }

    private final OnPostClick onPostClick;

    public PostAdapter(@NonNull OnPostClick listener) {
        super(DIFF);
        this.onPostClick = listener;
    }

    private static final DiffUtil.ItemCallback<Post> DIFF = new DiffUtil.ItemCallback<Post>() {
        @Override public boolean areItemsTheSame(@NonNull Post a, @NonNull Post b) {
            return Objects.equals(a.id, b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull Post a, @NonNull Post b) {
            return Objects.equals(a.ownerUid, b.ownerUid)
                    && Objects.equals(a.treinoId, b.treinoId)
                    && Objects.equals(a.treinoNome, b.treinoNome)
                    && Objects.equals(a.durationMin, b.durationMin)
                    && Objects.equals(a.xpGanho, b.xpGanho)
                    && ((a.createdAt == null && b.createdAt == null) ||
                    (a.createdAt != null && b.createdAt != null
                            && a.createdAt.getSeconds() == b.createdAt.getSeconds()
                            && a.createdAt.getNanoseconds() == b.createdAt.getNanoseconds()));
        }
    };

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Post p = getItem(pos);

        h.tvTreinoNome.setText(p.treinoNome != null ? p.treinoNome : "Treino");

        String resumo = "XP: " + (p.xpGanho != null ? p.xpGanho : 0)
                + " • Duração: " + (p.durationMin != null ? p.durationMin : 0) + " min";
        h.tvResumo.setText(resumo);

        long when = p.createdAt != null ? p.createdAt.toDate().getTime() : new Date().getTime();
        CharSequence rel = DateUtils.getRelativeTimeSpanString(
                when, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        h.tvDate.setText(rel);

        h.tvOwnerName.setText("Amigo");
        if (p.ownerUid != null) {
            FirebaseFirestore.getInstance().collection("users").document(p.ownerUid).get()
                    .addOnSuccessListener(doc -> {
                        String name = doc.getString("displayName");
                        if (name != null && !name.isEmpty()) h.tvOwnerName.setText(name);
                    });
        }

        // Clique para abrir detalhe do treino
        h.itemView.setOnClickListener(v -> {
            if (onPostClick != null) onPostClick.onPostClick(p);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOwnerName, tvDate, tvTreinoNome, tvResumo;
        VH(@NonNull View v) {
            super(v);
            tvOwnerName = v.findViewById(R.id.tvOwnerName);
            tvDate      = v.findViewById(R.id.tvDate);
            tvTreinoNome= v.findViewById(R.id.tvTreinoNome);
            tvResumo    = v.findViewById(R.id.tvResumo);
        }
    }
}
