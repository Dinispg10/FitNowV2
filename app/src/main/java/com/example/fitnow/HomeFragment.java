package com.example.fitnow;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment {

    private RecyclerView rvFeed;
    private PostAdapter adapter;

    private MaterialButton btnFiltroTodos;
    private MaterialButton btnFiltroMeus;

    private boolean mostrarApenasMeus = false;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String myUid = FirebaseAuth.getInstance().getUid();

    private SharedPreferences notifPrefs;
    private final Map<String, ListenerRegistration> friendPostListeners = new HashMap<>();
    private final Map<String, Boolean> listenerInitialized = new HashMap<>();
    private final Map<String, String> friendNames = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        notifPrefs = requireContext().getSharedPreferences("friend_post_notifications", Context.MODE_PRIVATE);

// Garante canais de notificação e permissões
        NotificationHelper.ensureNotificationChannels(requireContext());
        requestNotificationPermissionIfNeeded();

// Sincroniza token FCM sempre que este fragment é aberto
        MessagingTokenRegistrar.sync(requireContext());

// --- FEED ---
        rvFeed = v.findViewById(R.id.rvFeed);
        rvFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFeed.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        adapter = new PostAdapter(post -> {
            if (post != null && post.treinoId != null && !post.treinoId.isEmpty()) {
                Fragment f = TreinoDetalheFragment.newInstance(post.treinoId);
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, f)
                        .addToBackStack(null)
                        .commit();
            }
        });
        rvFeed.setAdapter(adapter);

        btnFiltroTodos = v.findViewById(R.id.btnFiltroTodos);
        btnFiltroMeus = v.findViewById(R.id.btnFiltroMeus);
        configurarFiltros();

// --- BOTÃO "NOVO TREINO" ---
        Button btnNovoTreino = v.findViewById(R.id.btnNovoTreino);
        if (btnNovoTreino != null) {
            btnNovoTreino.setOnClickListener(view -> {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new CriarTreinoFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        carregarFeed();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearFriendPostListeners();
    }

    private void configurarFiltros() {
        if (btnFiltroTodos != null && btnFiltroMeus != null) {
            btnFiltroTodos.setOnClickListener(v -> {
                if (mostrarApenasMeus) {
                    mostrarApenasMeus = false;
                    atualizarEstadoBotoes();
                    carregarFeed();
                }
            });

            btnFiltroMeus.setOnClickListener(v -> {
                if (!mostrarApenasMeus) {
                    mostrarApenasMeus = true;
                    atualizarEstadoBotoes();
                    carregarFeed();
                }
            });

            atualizarEstadoBotoes();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return;

        Context context = requireContext();
        boolean alreadyAllowed = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;

        if (alreadyAllowed) return;

        ActivityCompat.requestPermissions(
                requireActivity(),
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                2001
        );
    }

    private void atualizarEstadoBotoes() {
        if (btnFiltroTodos == null || btnFiltroMeus == null) return;
        btnFiltroTodos.setChecked(!mostrarApenasMeus);
        btnFiltroMeus.setChecked(mostrarApenasMeus);
    }

    private void carregarFeed() {
        if (myUid == null) return;

        db.collection("users")
                .document(myUid)
                .collection("friends")
                .get()
                .addOnSuccessListener(snap -> {
                    Set<String> owners = new HashSet<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        owners.add(d.getId());
                    }

                    owners.remove(myUid);

                    Set<String> todosOwners = new HashSet<>(owners);
                    todosOwners.add(myUid);

                    preloadFriendNames(todosOwners);
                    startFriendPostListeners(new HashSet<>(todosOwners));

                    Set<String> ownersParaFeed = new HashSet<>();
                    if (mostrarApenasMeus) {
                        ownersParaFeed.add(myUid);
                    } else {
                        ownersParaFeed.addAll(todosOwners);
                    }

                    if (ownersParaFeed.isEmpty()) {
                        adapter.submitList(new ArrayList<>());
                        return;
                    }

                    List<Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
                    for (String uid : ownersParaFeed) {
                        tasks.add(
                                db.collection("posts")
                                        .whereEqualTo("ownerUid", uid)
                                        .orderBy("createdAt", Query.Direction.DESCENDING)
                                        .limit(20)
                                        .get()
                        );
                    }

                    Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                        List<Post> all = new ArrayList<>();
                        for (Object obj : results) {
                            com.google.firebase.firestore.QuerySnapshot qs =
                                    (com.google.firebase.firestore.QuerySnapshot) obj;
                            for (DocumentSnapshot d : qs.getDocuments()) {
                                Post p = Post.from(d);
                                if (p != null) all.add(p);
                            }
                        }

                        all.sort((a, b) -> {
                            long tA = (a.createdAt != null) ? a.createdAt.toDate().getTime() : 0L;
                            long tB = (b.createdAt != null) ? b.createdAt.toDate().getTime() : 0L;
                            return Long.compare(tB, tA);
                        });

                        adapter.submitList(all);
                    });
                });
    }

    private void startFriendPostListeners(Set<String> allOwners) {
        Set<String> friendUids = new HashSet<>(allOwners);
        friendUids.remove(myUid);

// Remove listeners de amigos removidos
        List<String> toRemove = new ArrayList<>();
        for (String uid : friendPostListeners.keySet()) {
            if (!friendUids.contains(uid)) toRemove.add(uid);
        }
        for (String uid : toRemove) {
            ListenerRegistration reg = friendPostListeners.remove(uid);
            if (reg != null) reg.remove();
            listenerInitialized.remove(uid);
        }

        for (String uid : friendUids) {
            if (friendPostListeners.containsKey(uid)) continue;

            ListenerRegistration reg = db.collection("posts")
                    .whereEqualTo("ownerUid", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(5)
                    .addSnapshotListener((snap, e) -> {
                        if (e != null || snap == null) return;

                        boolean initialized = listenerInitialized.getOrDefault(uid, false);
                        long lastSeen = notifPrefs.getLong(uid, 0L);
                        long newestSeen = lastSeen;

                        for (DocumentChange dc : snap.getDocumentChanges()) {
                            if (dc.getType() != DocumentChange.Type.ADDED) continue;

                            Post p = Post.from(dc.getDocument());
                            if (p == null || p.createdAt == null) continue;

                            long created = p.createdAt.toDate().getTime();
                            newestSeen = Math.max(newestSeen, created);

                            if (!initialized && created <= lastSeen) continue;
                            if (!initialized) continue; // ignora carregamento inicial

                            if (created > lastSeen) {
                                showFriendWorkoutNotification(uid, p);
                            }
                        }

                        listenerInitialized.put(uid, true);
                        if (newestSeen > lastSeen) {
                            notifPrefs.edit().putLong(uid, newestSeen).apply();
                        }
                    });

            friendPostListeners.put(uid, reg);
        }
    }

    private void clearFriendPostListeners() {
        for (ListenerRegistration reg : friendPostListeners.values()) {
            if (reg != null) reg.remove();
        }
        friendPostListeners.clear();
        listenerInitialized.clear();
    }

    private void preloadFriendNames(Set<String> owners) {
        for (String uid : owners) {
            if (friendNames.containsKey(uid)) continue;
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        String name = doc.getString("displayName");
                        if (name != null && !name.isEmpty()) {
                            friendNames.put(uid, name);
                        }
                    });
        }
    }

    private void showFriendWorkoutNotification(String friendUid, Post post) {
        Context ctx = getContext();
        if (ctx == null || post == null) return;

        String friendName = friendNames.get(friendUid);
        if (friendName == null || friendName.isEmpty()) friendName = "Um amigo";
        String treinoNome = (post.treinoNome != null && !post.treinoNome.isEmpty())
                ? post.treinoNome
                : "Treino";

        NotificationManagerCompat.from(ctx).notify(
                friendUid.hashCode(),
                NotificationHelper.buildFriendWorkoutNotification(
                        ctx,
                        friendName + " fez um treino",
                        treinoNome,
                        (friendUid + treinoNome).hashCode()
                ).build()
        );
    }
}