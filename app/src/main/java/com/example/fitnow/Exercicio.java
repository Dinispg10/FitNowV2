package com.example.fitnow;

import java.util.Objects;

public class Exercicio {
    private String nome;
    private String tempo;       // Ex: "20 min"
    private String dificuldade;  // Ex: "Fácil"
    private String imagemResName;

    public Exercicio() {
        // Necessário para Firestore
    }

    public Exercicio(String nome, String tempo, String dificuldade, String imagemResName) {
        this.nome = nome;
        this.tempo = tempo;
        this.dificuldade = dificuldade;
        this.imagemResName = imagemResName;
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

    public String getImagemResName() {
        return imagemResName;
    }

    public void setImagemResName(String imagemResName) {
        this.imagemResName = imagemResName;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Exercicio exercicio = (Exercicio) o;
        return Objects.equals(nome, exercicio.nome)
                && Objects.equals(tempo, exercicio.tempo)
                && Objects.equals(dificuldade, exercicio.dificuldade)
                && Objects.equals(imagemResName, exercicio.imagemResName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nome, tempo, dificuldade, imagemResName);
    }
}