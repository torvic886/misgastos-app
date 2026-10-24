package com.misgastos.service;

import org.springframework.stereotype.Component;

/** Guarda quién está usando el programa (app de escritorio: un solo usuario a la vez). */
@Component
public class SesionActual {

    private volatile Long usuarioId;
    private volatile String username;

    public void iniciar(Long usuarioId, String username) {
        this.usuarioId = usuarioId;
        this.username = username;
    }

    public void cerrar() {
        this.usuarioId = null;
        this.username = null;
    }

    public Long getUsuarioId() { return usuarioId; }

    public String getUsername() { return username; }

    public boolean hayUsuario() { return usuarioId != null; }
}