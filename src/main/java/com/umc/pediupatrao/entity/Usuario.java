package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios") // Define a coleção no MongoDB
public class Usuario {

    @Id
    private String id;

    private String username; // Nome de usuário para login

    // Senha criptografada (BCrypt). WRITE_ONLY: pode ser recebida, mas nunca é devolvida em JSON.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private Perfil role; // ADMIN, GERENTE ou ATENDENTE

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Perfil getRole() {
        return role;
    }

    public void setRole(Perfil role) {
        this.role = role;
    }
}
