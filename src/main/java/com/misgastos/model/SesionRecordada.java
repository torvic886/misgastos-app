package com.misgastos.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_recordadas")
public class SesionRecordada {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    
    @Column(nullable = false, unique = true)
    private String token;
    
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;
    
    // Constructores
    public SesionRecordada() {
        this.fechaCreacion = LocalDateTime.now();
    }
    
    public SesionRecordada(Long usuarioId, String token, LocalDateTime fechaExpiracion) {
        this.usuarioId = usuarioId;
        this.token = token;
        this.fechaCreacion = LocalDateTime.now();
        this.fechaExpiracion = fechaExpiracion;
    }
    
    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
}