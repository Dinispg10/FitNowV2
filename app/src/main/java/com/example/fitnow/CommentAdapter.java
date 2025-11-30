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

import java.util.Date;
import java.util.Objects;

public class CommentAdapter extends ListAdapter<Comment, CommentAdapter.VH> {

    public interface OnCommentAction {
        void onLongPress(@NonNull Comment c);
    }

    private final OnCommentAction listener;

    public CommentAdapter() {
        this(null);
    }

    public CommentAdapter(OnCommentAction listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Comment> DIFF = new DiffUtil.ItemCallback<Comment>() {
        @Override public boolean areItemsTheSame(@NonNull Comment a, @NonNull Comment b) {
            return Objects.equals(a.id, b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull Comment a, @NonNull Comment b) {
            return Objects.equals(a.authorUid, b.authorUid)
                    && Objects.equals(a.authorName, b.authorName)
                    && Objects.equals(a.text, b.text)
                    && ((a.createdAt == null && b.createdAt == null) ||
                    (a.createdAt != null && b.createdAt != null
                            && a.createdAt.getSeconds() == b.createdAt.getSeconds()
                            && a.createdAt.getNanoseconds() == b.createdAt.getNanoseconds()));
        }
    };

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Comment c = getItem(position);
        holder.tvText.setText(c.text != null ? c.text : "");
        String nome = c.authorName != null ? c.authorName : c.authorUid;
        holder.tvAuthor.setText(nome != null ? nome : "Utilizador");

        long when = c.createdAt != null ? c.createdAt.toDate().getTime() : new Date().getTime();
        CharSequence rel = DateUtils.getRelativeTimeSpanString(
                when, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        holder.tvDate.setText(rel);

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongPress(c);
                return true;
            }
            return false;
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAuthor, tvDate, tvText;
        VH(@NonNull View v) {
            super(v);
            tvAuthor = v.findViewById(R.id.tvCommentAuthor);
            tvDate = v.findViewById(R.id.tvCommentDate);
            tvText = v.findViewById(R.id.tvCommentText);
        }
    }
}