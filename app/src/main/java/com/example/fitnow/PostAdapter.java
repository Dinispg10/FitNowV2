package com.example.fitnow;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.DateUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PostAdapter extends ListAdapter<Post, PostAdapter.VH> {

    public interface OnPostClick {
        void onPostClick(@NonNull Post post);
    }

    private final OnPostClick onPostClick;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Map<String, String> nameCache = new HashMap<>();
    private final Map<String, Bitmap> photoCache = new HashMap<>();

    public PostAdapter(@NonNull OnPostClick listener) {
        super(DIFF);
        this.onPostClick = listener;
        primeCurrentUserName();
    }

    private static final DiffUtil.ItemCallback<Post> DIFF = new DiffUtil.ItemCallback<Post>() {
        @Override
        public boolean areItemsTheSame(@NonNull Post a, @NonNull Post b) {
            return Objects.equals(a.id, b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Post a, @NonNull Post b) {
            return Objects.equals(a.ownerUid, b.ownerUid)
                    && Objects.equals(a.treinoId, b.treinoId)
                    && Objects.equals(a.treinoNome, b.treinoNome)
                    && Objects.equals(a.durationMin, b.durationMin)
                    && Objects.equals(a.xpGanho, b.xpGanho)
                    && Objects.equals(a.likeCount, b.likeCount)
                    && Objects.equals(a.commentCount, b.commentCount)
                    && Objects.equals(a.likedBy, b.likedBy)
                    && ((a.createdAt == null && b.createdAt == null) ||
                    (a.createdAt != null && b.createdAt != null
                            && a.createdAt.getSeconds() == b.createdAt.getSeconds()
                            && a.createdAt.getNanoseconds() == b.createdAt.getNanoseconds()));
        }
    };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Post p = getItem(pos);

        h.tvTreinoNome.setText(p.treinoNome != null ? p.treinoNome : "Treino");
        h.tvXpChip.setText("⚡ " + (p.xpGanho != null ? p.xpGanho : 0));
        h.tvDurationChip.setText("⏱️ " + (p.durationMin != null ? p.durationMin : 0) + " min");

        long when = p.createdAt != null ? p.createdAt.toDate().getTime() : new Date().getTime();
        CharSequence rel = DateUtils.getRelativeTimeSpanString(
                when, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        h.tvDate.setText(rel);

        h.tvOwnerName.setText("Amigo");
        carregarPerfil(p.ownerUid, h);

        boolean possoApagar = p.ownerUid != null && p.ownerUid.equals(currentUid());
        h.tvDeletePost.setVisibility(possoApagar ? View.VISIBLE : View.GONE);
        h.tvDeletePost.setOnClickListener(v -> confirmarApagarPost(p, h));

        updateLikeUi(h, p);
        updateCommentUi(h, p);

        h.tvLike.setOnClickListener(v -> toggleLike(p, h));
        h.tvComment.setOnClickListener(v -> abrirSecaoComentarios(p, h));

        h.itemView.setOnClickListener(v -> {
            if (onPostClick != null) onPostClick.onPostClick(p);
        });
    }

    private void toggleLike(@NonNull Post p, @NonNull VH h) {
        String uid = currentUid();
        if (p.id == null || uid == null) return;

        DocumentReference ref = db.collection("posts").document(p.id);

        db.runTransaction((Transaction.Function<Post>) transaction -> {
            Post atual = Post.from(transaction.get(ref));
            if (atual == null) return null;

            boolean jaCurtiu = atual.isLikedBy(uid);
            int likesAtuais = atual.likeCount != null ? atual.likeCount : 0;

            if (jaCurtiu) {
                transaction.update(ref, "likedBy", FieldValue.arrayRemove(uid));
                transaction.update(ref, "likes", FieldValue.increment(-1));
                likesAtuais = Math.max(0, likesAtuais - 1);
                if (atual.likedBy != null) atual.likedBy.remove(uid);
            } else {
                transaction.update(ref, "likedBy", FieldValue.arrayUnion(uid));
                transaction.update(ref, "likes", FieldValue.increment(1));
                likesAtuais = likesAtuais + 1;
                if (atual.likedBy == null) atual.likedBy = new ArrayList<>();
                if (!atual.likedBy.contains(uid)) atual.likedBy.add(uid);
            }

            atual.likeCount = likesAtuais;
            return atual;
        }).addOnSuccessListener(atual -> {
            if (atual != null) {
                p.likedBy = atual.likedBy;
                p.likeCount = atual.likeCount;
                updateLikeUi(h, p);
            }
        });
    }

    private void abrirSecaoComentarios(@NonNull Post p, @NonNull VH h) {
        if (p.id == null) return;

        View view = LayoutInflater.from(h.itemView.getContext())
                .inflate(R.layout.dialog_comments, null, false);
        RecyclerView rv = view.findViewById(R.id.rvComments);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyComments);
        EditText input = view.findViewById(R.id.etNovoComentario);
        TextView btnEnviar = view.findViewById(R.id.btnEnviarComentario);

        tvEmpty.setVisibility(View.VISIBLE);

        CommentAdapter adapter = new CommentAdapter(c -> confirmarApagarComentario(p, h, c));
        rv.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        rv.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(h.itemView.getContext())
                .setTitle("Comentários")
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        ListenerRegistration[] regHolder = new ListenerRegistration[1];
        regHolder[0] = db.collection("posts")
                .document(p.id)
                .collection("comments")
                .orderBy("createdAt")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        String msg = "Erro ao carregar comentários";

                        if (e instanceof FirebaseFirestoreException) {
                            FirebaseFirestoreException fe = (FirebaseFirestoreException) e;
                            switch (fe.getCode()) {
                                case PERMISSION_DENIED:
                                    msg = "Sem permissão para ver comentários";
                                    break;
                                case UNAVAILABLE:
                                    msg = "Sem ligação. A tentar novamente…";
                                    break;
                                default:
                                    msg = "Não foi possível carregar comentários";
                            }
                        }

                        Toast.makeText(h.itemView.getContext(), msg, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snap == null) return;
                    List<Comment> lista = new ArrayList<>();
                    Set<String> missingNames = new HashSet<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Comment c = Comment.from(doc);
                        if (c != null) {
                            if (c.authorName == null && c.authorUid != null) {
                                String cached = nameCache.get(c.authorUid);
                                if (cached != null) {
                                    c.authorName = cached;
                                } else {
                                    missingNames.add(c.authorUid);
                                }
                            }
                            lista.add(c);
                        }
                    }
                    preencherNomes(missingNames, adapter);
                    adapter.submitList(lista);
                    tvEmpty.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
                    p.commentCount = lista.size();
                    updateCommentUi(h, p);
                    rv.scrollToPosition(Math.max(0, lista.size() - 1));
                });

        dialog.setOnDismissListener(d -> {
            if (regHolder[0] != null) regHolder[0].remove();
        });

        btnEnviar.setOnClickListener(v -> salvarComentario(p, h, input.getText().toString(), () -> {
            input.setText("");
            rv.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
        }));

        dialog.show();
    }

    private void salvarComentario(@NonNull Post p, @NonNull VH h, @NonNull String texto, Runnable onSuccess) {
        String uid = currentUid();
        if (uid == null) {
            Toast.makeText(h.itemView.getContext(), "Inicia sessão para comentar", Toast.LENGTH_SHORT).show();
            return;
        }

        String mensagem = texto.trim();
        if (mensagem.isEmpty()) {
            Toast.makeText(h.itemView.getContext(), "Escreve uma mensagem", Toast.LENGTH_SHORT).show();
            return;
        }

        resolverNome(uid, nome -> escreverComentario(p, h, mensagem, nome, onSuccess));
    }

    private interface NomeCallback {
        void onReady(String nome);
    }

    private void resolverNome(@NonNull String uid, @NonNull NomeCallback cb) {
        String cached = nameCache.get(uid);
        if (cached != null && !cached.isEmpty()) {
            cb.onReady(cached);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String nome = doc.getString("displayName");
                    if (nome == null || nome.isEmpty()) nome = uid;
                    nameCache.put(uid, nome);
                    cb.onReady(nome);
                })
                .addOnFailureListener(e -> cb.onReady(uid));
    }

    private void escreverComentario(@NonNull Post p, @NonNull VH h, @NonNull String mensagem, @NonNull String nome, Runnable onSuccess) {
        Map<String, Object> comment = new HashMap<>();
        String uid = currentUid();
        comment.put("authorUid", uid);
        comment.put("text", mensagem);
        comment.put("createdAt", FieldValue.serverTimestamp());
        if (nome != null && !nome.isEmpty()) {
            comment.put("authorName", nome);
        }

        DocumentReference ref = db.collection("posts").document(p.id);

        ref.collection("comments")
                .add(comment)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return db.runTransaction(transaction -> {
                        DocumentSnapshot snap = transaction.get(ref);
                        Long atual = snap.getLong("commentCount");
                        if (atual == null) atual = snap.getLong("comments");
                        int novoTotal = (atual != null ? atual.intValue() : 0) + 1;
                        transaction.update(ref, "commentCount", novoTotal);
                        return novoTotal;
                    });
                })
                .addOnSuccessListener(novoTotal -> {
                    p.commentCount = novoTotal;
                    updateCommentUi(h, p);
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    String msg = e != null && e.getMessage() != null
                            ? e.getMessage()
                            : "Não foi possível gravar o comentário";
                    Toast.makeText(h.itemView.getContext(), msg, Toast.LENGTH_LONG).show();
                });
    }

    private void confirmarApagarComentario(@NonNull Post p, @NonNull VH h, @NonNull Comment c) {
        String uid = currentUid();
        if (uid == null) {
            Toast.makeText(h.itemView.getContext(), "Inicia sessão para apagar", Toast.LENGTH_SHORT).show();
            return;
        }
        if (c.authorUid == null || !uid.equals(c.authorUid)) {
            Toast.makeText(h.itemView.getContext(), "Só podes apagar os teus comentários", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(h.itemView.getContext())
                .setMessage("Apagar este comentário?")
                .setPositiveButton("Apagar", (d, i) -> apagarComentario(p, h, c))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmarApagarPost(@NonNull Post p, @NonNull VH h) {
        String uid = currentUid();
        if (uid == null) {
            Toast.makeText(h.itemView.getContext(), "Inicia sessão para apagar", Toast.LENGTH_SHORT).show();
            return;
        }
        if (p.ownerUid == null || !uid.equals(p.ownerUid)) {
            Toast.makeText(h.itemView.getContext(), "Só podes apagar os teus posts", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(h.itemView.getContext())
                .setMessage("Apagar este post?")
                .setPositiveButton("Apagar", (d, i) -> apagarPost(p, h))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void apagarPost(@NonNull Post p, @NonNull VH h) {
        if (p.id == null) return;

        db.collection("posts").document(p.id)
                .delete()
                .addOnSuccessListener(v -> {
                    Toast.makeText(h.itemView.getContext(), "Post apagado", Toast.LENGTH_SHORT).show();
                    removerDaLista(p);
                })
                .addOnFailureListener(e -> {
                    String msg = e != null && e.getMessage() != null
                            ? e.getMessage()
                            : "Não foi possível apagar o post";
                    Toast.makeText(h.itemView.getContext(), msg, Toast.LENGTH_LONG).show();
                });
    }

    private void removerDaLista(@NonNull Post p) {
        List<Post> atuais = new ArrayList<>(getCurrentList());
        for (int i = 0; i < atuais.size(); i++) {
            Post item = atuais.get(i);
            if (item.id != null && item.id.equals(p.id)) {
                atuais.remove(i);
                break;
            }
        }
        submitList(atuais);
    }

    private void apagarComentario(@NonNull Post p, @NonNull VH h, @NonNull Comment c) {
        if (p.id == null || c.id == null) return;

        DocumentReference postRef = db.collection("posts").document(p.id);
        DocumentReference commentRef = postRef.collection("comments").document(c.id);

        commentRef.delete()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return db.runTransaction(transaction -> {
                        DocumentSnapshot snap = transaction.get(postRef);
                        Long atual = snap.getLong("commentCount");
                        if (atual == null) atual = snap.getLong("comments");
                        int novoTotal = Math.max(0, (atual != null ? atual.intValue() : 0) - 1);
                        transaction.update(postRef, "commentCount", novoTotal);
                        return novoTotal;
                    });
                })
                .addOnSuccessListener(novoTotal -> {
                    p.commentCount = novoTotal;
                    updateCommentUi(h, p);
                })
                .addOnFailureListener(e -> {
                    String msg = e != null && e.getMessage() != null
                            ? e.getMessage()
                            : "Não foi possível apagar o comentário";
                    Toast.makeText(h.itemView.getContext(), msg, Toast.LENGTH_LONG).show();
                });
    }

    private void preencherNomes(@NonNull Set<String> missingUids, @NonNull CommentAdapter adapter) {
        for (String uid : missingUids) {
            if (nameCache.containsKey(uid)) continue;
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        String nome = doc.getString("displayName");
                        if (nome == null || nome.isEmpty()) return;
                        nameCache.put(uid, nome);
                        List<Comment> atuais = new ArrayList<>(adapter.getCurrentList());
                        boolean mudou = false;
                        for (Comment c : atuais) {
                            if (uid.equals(c.authorUid) && !nome.equals(c.authorName)) {
                                c.authorName = nome;
                                mudou = true;
                            }
                        }
                        if (mudou) adapter.submitList(atuais);
                    });
        }
    }

    private void carregarPerfil(String ownerUid, @NonNull VH h) {
        h.ivOwnerAvatar.setImageResource(R.drawable.ic_group);
        if (ownerUid == null) return;

        if (nameCache.containsKey(ownerUid)) {
            String nome = nameCache.get(ownerUid);
            if (nome != null && !nome.isEmpty()) h.tvOwnerName.setText(nome);
        }

        if (photoCache.containsKey(ownerUid)) {
            Bitmap bmp = photoCache.get(ownerUid);
            if (bmp != null) h.ivOwnerAvatar.setImageBitmap(bmp);
        }

        db.collection("users").document(ownerUid).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    String nome = doc.getString("displayName");
                    if (nome != null && !nome.isEmpty()) {
                        nameCache.put(ownerUid, nome);
                        h.tvOwnerName.setText(nome);
                    }

                    String fotoBase64 = doc.getString("photoBase64");
                    Bitmap bmp = decodeBase64(fotoBase64);
                    if (bmp != null) {
                        photoCache.put(ownerUid, bmp);
                        h.ivOwnerAvatar.setImageBitmap(bmp);
                    }
                });
    }

    private Bitmap decodeBase64(String fotoBase64) {
        if (fotoBase64 == null || fotoBase64.isEmpty()) return null;
        try {
            byte[] data = Base64.decode(fotoBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void primeCurrentUserName() {
        String uid = currentUid();
        if (uid == null) return;
        resolverNome(uid, nome -> {
        });
    }

    private void updateLikeUi(@NonNull VH h, @NonNull Post p) {
        int count = p.likeCount != null ? p.likeCount : 0;
        boolean liked = p.isLikedBy(currentUid());
        int color = ContextCompat.getColor(h.itemView.getContext(),
                liked ? R.color.colorAccent : R.color.colorTextSecondary);
        h.tvLike.setText("❤ " + count);
        h.tvLike.setTextColor(color);
    }

    private void updateCommentUi(@NonNull VH h, @NonNull Post p) {
        int count = p.commentCount != null ? p.commentCount : 0;
        h.tvComment.setText("💬 " + count);
    }

    private String currentUid() {
        return FirebaseAuth.getInstance().getUid();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOwnerName, tvDate, tvTreinoNome, tvXpChip, tvDurationChip, tvLike, tvComment;
        TextView tvDeletePost;
        ImageView ivOwnerAvatar;

        VH(@NonNull View v) {
            super(v);
            tvOwnerName = v.findViewById(R.id.tvOwnerName);
            tvDate = v.findViewById(R.id.tvDate);
            tvTreinoNome = v.findViewById(R.id.tvTreinoNome);
            tvXpChip = v.findViewById(R.id.tvXpChip);
            tvDurationChip = v.findViewById(R.id.tvDurationChip);
            tvLike = v.findViewById(R.id.tvLike);
            tvComment = v.findViewById(R.id.tvComment);
            tvDeletePost = v.findViewById(R.id.tvDeletePost);
            ivOwnerAvatar = v.findViewById(R.id.ivOwnerAvatar);
        }
    }
}
