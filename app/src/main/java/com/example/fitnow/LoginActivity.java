package com.example.fitnow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvCreateAccount;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // Redireciona imediatamente se o utilizador já estiver autenticado,
        // evitando o flash da tela de login antes do auto-login.
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvCreateAccount = findViewById(R.id.tvCreateAccount);


        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email obrigatório");
                return;
            }
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Senha obrigatória");
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user == null) {
                                Toast.makeText(LoginActivity.this,
                                        "Erro inesperado. Tenta novamente.",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            // Atualiza os dados do utilizador (inclui emailVerified)
                            user.reload().addOnCompleteListener(reloadTask -> {
                                if (!reloadTask.isSuccessful()) {
                                    Toast.makeText(LoginActivity.this,
                                            "Erro ao verificar conta. Tenta novamente.",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                if (user.isEmailVerified()) {
                                    // ⚠️ IMPORTANTE: associar o token FCM ao user aqui
                                    MessagingTokenRegistrar.onLogin(LoginActivity.this);

                                    Toast.makeText(LoginActivity.this,
                                            "Login bem sucedido!",
                                            Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                } else {
                                    // Logout se ainda não verificou o email
                                    mAuth.signOut();
                                    Toast.makeText(LoginActivity.this,
                                            "Por favor, confirma o teu email antes de aceder à app.",
                                            Toast.LENGTH_LONG).show();
                                }
                            });

                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Erro no login: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, CreateAccountActivity.class))
        );
    }
}
