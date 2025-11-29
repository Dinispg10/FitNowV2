// MainActivity.java (substitui pelo teu + estas alterações)
package com.example.fitnow;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_treinos) {
                selectedFragment = new FragmentTreino();
            } else if (id == R.id.nav_perfil) {
                selectedFragment = new PerfilFragment();
            } else if (id == R.id.nav_friends) {
                selectedFragment = new FriendsFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 👉 garante /users e /user_lookup logo após login
        ensureUserProfileAndLookup();
    }

    private void ensureUserProfileAndLookup() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String uid   = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();
        String name  = auth.getCurrentUser().getDisplayName();
        if (email == null || email.trim().isEmpty()) return;

        String emailKey = email.trim().toLowerCase();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1) /users/{uid}
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("email", email);
                        user.put("displayName", (name != null && !name.isEmpty()) ? name : email);
                        user.put("createdAt", FieldValue.serverTimestamp());
                        db.collection("users").document(uid).set(user);
                    } else {
                        Map<String, Object> updates = new HashMap<>();
                        if (!email.equals(doc.getString("email"))) updates.put("email", email);
                        if (name != null && !name.isEmpty() && !name.equals(doc.getString("displayName")))
                            updates.put("displayName", name);
                        if (!updates.isEmpty())
                            db.collection("users").document(uid).update(updates);
                    }
                });

        // 2) /user_lookup/{email_lowercase} -> { uid }
        db.collection("user_lookup").document(emailKey).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Map<String, Object> lookup = new HashMap<>();
                        lookup.put("uid", uid);
                        db.collection("user_lookup").document(emailKey).set(lookup);
                    } else {
                        String cur = doc.getString("uid");
                        if (cur == null || !cur.equals(uid)) {
                            db.collection("user_lookup").document(emailKey).update("uid", uid);
                        }
                    }
                });
    }

    public void switchFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
