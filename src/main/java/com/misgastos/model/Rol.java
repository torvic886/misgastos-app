package com.misgastos.model;

public enum Rol {
    ADMINISTRADOR("Administrador"),
    USUARIO("Usuario");
    
    private final String displayName;
    
    Rol(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}