package com.example.fitnow;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Amigos (com user_lookup)
 * - Adicionar por email (lê /user_lookup/{email_lower})
 * - Pedidos recebidos (accept/reject)
 * - AO ACEITAR: cria amizade só do meu lado (/users/{me}/friends/{fromUid})
 * - Watcher: quando pedidos QUE EU ENVIEI forem aceites, cria amizade do meu lado
 * - Lista os meus amigos
 */
public class FriendsFragment extends Fragment {

    private EditText etEmail;
    private Button btnAdd;

    private RecyclerView rvRequests;
    private RecyclerView rvFriends;
    private RequestsAdapter requestsAdapter;
    private FriendsAdapter friendsAdapter;

    private FirebaseFirestore db;
    private String myUid;

    // Listeners em tempo-real
    private ListenerRegistration outgoingReg;   // pedidos que EU enviei e foram aceites

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        myUid = FirebaseAuth.getInstance().getUid();

        etEmail = v.findViewById(R.id.etEmailAmigo);
        btnAdd  = v.findViewById(R.id.btnAdicionarAmigo);

        rvRequests = v.findViewById(R.id.rvPedidos);
        rvFriends  = v.findViewById(R.id.rvAmigos);

        // Pedidos
        rvRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRequests.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        requestsAdapter = new RequestsAdapter();
        rvRequests.setAdapter(requestsAdapter);

        // Amigos
        rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFriends.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        friendsAdapter = new FriendsAdapter();
        rvFriends.setAdapter(friendsAdapter);

        btnAdd.setOnClickListener(v1 -> onAddClicked());

        carregarPedidos();
        carregarAmigos();

        // >>> Watcher: quando pedidos ENVIADOS por mim forem aceites
        startOutgoingAcceptedWatcher();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (outgoingReg != null) { outgoingReg.remove(); outgoingReg = null; }
    }

    // ---------- Helpers ----------
    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /** ID determinístico para friend_requests entre dois UIDs (ordem alfabética). */
    private String pairKey(String a, String b) {
        return (a.compareTo(b) < 0) ? a + "_" + b : b + "_" + a;
    }

    // ---------- Watcher: pedidos que EU enviei e já estão accepted ----------
    private void startOutgoingAcceptedWatcher() {
        if (myUid == null) return;
        if (outgoingReg != null) { outgoingReg.remove(); outgoingReg = null; }

        // Necessita índice: fromUid Asc, status Asc, createdAt Desc
        outgoingReg = db.collection("friend_requests")
                .whereEqualTo("fromUid", myUid)
                .whereEqualTo("status", "accepted")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String toUid = d.getString("toUid");
                        if (TextUtils.isEmpty(toUid)) continue;

                        // Garante que existe /users/{me}/friends/{toUid}
                        db.collection("users").document(myUid)
                                .collection("friends").document(toUid)
                                .get()
                                .addOnSuccessListener(existsDoc -> {
                                    if (!existsDoc.exists()) {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("since", FieldValue.serverTimestamp());
                                        db.collection("users").document(myUid)
                                                .collection("friends").document(toUid)
                                                .set(data)
                                                .addOnSuccessListener(v -> carregarAmigos());
                                    }
                                });
                    }
                });
    }

    // ---------- Adicionar amigo por email (via user_lookup) ----------
    private void onAddClicked() {
        if (myUid == null) return;
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Email inválido.");
            return;
        }
        String emailLower = email.toLowerCase(Locale.ROOT);

        // LER EM /user_lookup/{email_lower}
        db.collection("user_lookup").document(emailLower).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        toast("Não encontrei esse utilizador.");
                        return;
                    }
                    String friendUid = doc.getString("uid");
                    if (TextUtils.isEmpty(friendUid)) {
                        toast("Lookup sem UID válido.");
                        return;
                    }
                    if (friendUid.equals(myUid)) {
                        toast("Não podes adicionar-te a ti próprio 😅");
                        return;
                    }
                    verificarOuCriarPedido(myUid, friendUid);
                })
                .addOnFailureListener(e -> toast("Falha na procura: " + e.getMessage()));
    }

    /** Tenta criar pedido; se já existir, as rules podem recusar (evita duplicados). */
    private void verificarOuCriarPedido(String fromUid, String toUid) {
        String id = pairKey(fromUid, toUid);
        DocumentReference ref = db.collection("friend_requests").document(id);

        Map<String, Object> req = new HashMap<>();
        req.put("fromUid", fromUid);
        req.put("toUid",   toUid);
        req.put("status",  "pending");
        req.put("createdAt", FieldValue.serverTimestamp());

        ref.set(req)
                .addOnSuccessListener(unused -> {
                    toast("Pedido enviado ✉️");
                    etEmail.setText("");
                    carregarPedidos();
                })
                .addOnFailureListener(e ->
                        toast("Já existe um pedido entre vocês ou falta permissão. (" + e.getMessage() + ")"));
    }

    // ---------- Carregar pedidos recebidos (pendentes) ----------
    private void carregarPedidos() {
        if (myUid == null) return;
        db.collection("friend_requests")
                .whereEqualTo("toUid", myUid)
                .whereEqualTo("status", "pending")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<RequestItem> items = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        RequestItem it = RequestItem.from(d);
                        if (it != null) items.add(it);
                    }
                    requestsAdapter.submit(items);
                })
                .addOnFailureListener(e -> toast("Falha a carregar pedidos: " + e.getMessage()));
    }

    // ---------- Carregar lista de amigos ----------
    private void carregarAmigos() {
        if (myUid == null) return;
        db.collection("users").document(myUid).collection("friends").get()
                .addOnSuccessListener(snap -> {
                    List<FriendItem> items = new ArrayList<>();
                    List<Task<DocumentSnapshot>> nameFetches = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        FriendItem it = new FriendItem();
                        it.uid = d.getId();
                        Timestamp ts = d.getTimestamp("since");
                        it.since = ts != null ? ts.toDate() : null;
                        items.add(it);

                        nameFetches.add(db.collection("users").document(it.uid).get()
                                .addOnSuccessListener(ud -> it.displayName = ud.getString("displayName")));
                    }
                    Tasks.whenAllComplete(nameFetches)
                            .addOnSuccessListener(done -> friendsAdapter.submit(items));
                })
                .addOnFailureListener(e -> toast("Falha a carregar amigos: " + e.getMessage()));
    }

    // ---------- Aceitar / Rejeitar ----------
    private void aceitarPedido(RequestItem req) {
        if (myUid == null || req == null) return;

        String id = pairKey(req.fromUid, req.toUid);
        DocumentReference reqRef = db.collection("friend_requests").document(id);

        db.runTransaction(trx -> {
            DocumentSnapshot snap = trx.get(reqRef);
            if (!snap.exists()) return null;

            String status = snap.getString("status");
            String fromUid = snap.getString("fromUid");
            String toUid   = snap.getString("toUid");

            if (!"pending".equals(status) || !myUid.equals(toUid)) return null;

            trx.update(reqRef, "status", "accepted", "acceptedAt", FieldValue.serverTimestamp());

            DocumentReference myFriendDoc = db.collection("users")
                    .document(myUid)
                    .collection("friends")
                    .document(fromUid);

            Map<String, Object> data = new HashMap<>();
            data.put("since", FieldValue.serverTimestamp());
            trx.set(myFriendDoc, data);

            return null;
        }).addOnSuccessListener(unused -> {
            toast("Pedido aceite ✅");
            carregarPedidos();
            carregarAmigos();
        }).addOnFailureListener(e -> toast("Erro: " + e.getMessage()));
    }

    private void rejeitarPedido(RequestItem req) {
        if (req == null) return;
        String id = pairKey(req.fromUid, req.toUid);
        db.collection("friend_requests").document(id)
                .update("status", "rejected", "rejectedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused -> {
                    toast("Pedido rejeitado.");
                    carregarPedidos();
                })
                .addOnFailureListener(e -> toast("Erro: " + e.getMessage()));
    }

    // ------------------ MODELOS + ADAPTERS ------------------
    static class RequestItem {
        String id;
        String fromUid;
        String toUid;
        Date createdAt;
        String fromName;

        static RequestItem from(DocumentSnapshot d) {
            if (d == null || !d.exists()) return null;
            RequestItem r = new RequestItem();
            r.id = d.getId();
            r.fromUid = d.getString("fromUid");
            r.toUid = d.getString("toUid");
            Timestamp ts = d.getTimestamp("createdAt");
            r.createdAt = ts != null ? ts.toDate() : null;
            return r;
        }
    }

    static class FriendItem {
        String uid;
        String displayName;
        Date since;
    }

    class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {
        private final List<RequestItem> data = new ArrayList<>();

        void submit(List<RequestItem> items) {
            data.clear();
            data.addAll(items);
            for (RequestItem it : data) {
                if (!TextUtils.isEmpty(it.fromUid)) {
                    db.collection("users").document(it.fromUid).get()
                            .addOnSuccessListener(doc -> {
                                it.fromName = doc.getString("displayName");
                                notifyDataSetChanged();
                            });
                }
            }
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RequestItem it = data.get(pos);
            h.tvName.setText(!TextUtils.isEmpty(it.fromName) ? it.fromName : "Utilizador");
            h.tvWhen.setText(it.createdAt != null
                    ? android.text.format.DateFormat.format("dd MMM, HH:mm", it.createdAt)
                    : "");
            h.btnAccept.setOnClickListener(v -> aceitarPedido(it));
            h.btnReject.setOnClickListener(v -> rejeitarPedido(it));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvWhen;
            Button btnAccept, btnReject;
            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tvReqName);
                tvWhen = v.findViewById(R.id.tvReqWhen);
                btnAccept = v.findViewById(R.id.btnReqAccept);
                btnReject = v.findViewById(R.id.btnReqReject);
            }
        }
    }

    class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.VH> {
        private final List<FriendItem> data = new ArrayList<>();

        void submit(List<FriendItem> items) {
            data.clear();
            data.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            FriendItem it = data.get(pos);
            h.tvName.setText(!TextUtils.isEmpty(it.displayName) ? it.displayName : it.uid);
            h.tvSince.setText(it.since != null
                    ? "Desde " + android.text.format.DateFormat.format("dd MMM yyyy", it.since)
                    : "");
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvSince;
            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tvFriendName);
                tvSince = v.findViewById(R.id.tvFriendSince);
            }
        }
    }
}
