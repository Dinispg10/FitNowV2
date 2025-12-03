package com.example.fitnow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeFragment extends Fragment {

    private RecyclerView rvFeed;
    private PostAdapter adapter;
    private MaterialButtonToggleGroup filterGroup;
    private boolean showOnlyMine = false;


    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String myUid = FirebaseAuth.getInstance().getUid();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

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

        filterGroup = v.findViewById(R.id.filterGroup);
        MaterialButton btnAll = v.findViewById(R.id.btnFilterTodos);
        MaterialButton btnMine = v.findViewById(R.id.btnFilterMeus);
        if (filterGroup != null && btnAll != null && btnMine != null) {
            filterGroup.check(btnAll.getId());
            filterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                boolean newShowOnlyMine = checkedId == btnMine.getId();
                if (newShowOnlyMine != showOnlyMine) {
                    showOnlyMine = newShowOnlyMine;
                    carregarFeed();
                }
            });
        }

        // --- BOTÃO "NOVO TREINO" ---
        // Se o teu layout tiver Button:
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

    private void carregarFeed() {
        if (myUid == null) return;

        db.collection("users")
                .document(myUid)
                .collection("friends")
                .get()
                .addOnSuccessListener(snap -> {
                    Set<String> owners = new HashSet<>();
                    for (DocumentSnapshot d : snap.getDocuments()) owners.add(d.getId());
                    owners.add(myUid);

                    if (owners.isEmpty()) {
                        adapter.submitList(new ArrayList<>());
                        return;
                    }

                    List<Task<QuerySnapshot>> tasks = new ArrayList<>();
                    for (String uid : owners) {
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
                            QuerySnapshot qs = (QuerySnapshot) obj;
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
}
