package com.example.fitnow;

import java.util.Objects;

public class Exercicio {
    private String nome;
    private String tempo;       // Ex: "20 min"
    private String dificuldade; // Ex: "Fácil"

    public Exercicio() {
        // Necessário para Firestore
    }

    public Exercicio(String nome, String tempo, String dificuldade) {
        this.nome = nome;
        this.tempo = tempo;
        this.dificuldade = dificuldade;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTempo() {
        return tempo;
    }

    public void setTempo(String tempo) {
        this.tempo = tempo;
    }

    public String getDificuldade() {
        return dificuldade;
    }

    public void setDificuldade(String dificuldade) {
        this.dificuldade = dificuldade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Exercicio exercicio = (Exercicio) o;
        return Objects.equals(nome, exercicio.nome)
                && Objects.equals(tempo, exercicio.tempo)
                && Objects.equals(dificuldade, exercicio.dificuldade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nome, tempo, dificuldade);
    }
}