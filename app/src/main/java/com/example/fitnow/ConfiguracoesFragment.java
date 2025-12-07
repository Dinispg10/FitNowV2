package com.example.fitnow;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

public class ConfiguracoesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Cria um layout simples para configurações (cria xml no res/layout/fragment_configuracoes.xml)
        return inflater.inflate(R.layout.fragment_configuracoes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        Button btnResetPassword = view.findViewById(R.id.btnResetPassword);
        Button btnLogout = view.findViewById(R.id.btnLogout);


        btnResetPassword.setOnClickListener(v -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null || auth.getCurrentUser().getEmail() == null) {
                Toast.makeText(requireContext(),
                        "Inicia sessão novamente para redefinir a palavra-passe.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            String email = auth.getCurrentUser().getEmail();

            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    "Enviámos um link para redefinir a palavra-passe.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(requireContext(),
                                    "Erro ao enviar email: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            getActivity().finish();
        });


    }
}
