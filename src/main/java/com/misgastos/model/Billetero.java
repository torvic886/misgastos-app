package com.misgastos.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "billeteros")
public class Billetero {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDate fecha;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal billetero;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premios;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal diferencia;
    
    // Constructor vacío
    public Billetero() {
    }
    
    // Constructor con parámetros
    public Billetero(LocalDate fecha, BigDecimal billetero, BigDecimal premios) {
        this.fecha = fecha;
        this.billetero = billetero;
        this.premios = premios;
        calcularDiferencia();
    }
    
    // Método para calcular diferencia automáticamente
    @PrePersist
    @PreUpdate
    public void calcularDiferencia() {
        if (billetero != null && premios != null) {
            this.diferencia = billetero.subtract(premios);
        }
    }
    
    // Getters y Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public LocalDate getFecha() {
        return fecha;
    }
    
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }
    
    public BigDecimal getBilletero() {
        return billetero;
    }
    
    public void setBilletero(BigDecimal billetero) {
        this.billetero = billetero;
        calcularDiferencia();
    }
    
    public BigDecimal getPremios() {
        return premios;
    }
    
    public void setPremios(BigDecimal premios) {
        this.premios = premios;
        calcularDiferencia();
    }
    
    public BigDecimal getDiferencia() {
        return diferencia;
    }
    
    public void setDiferencia(BigDecimal diferencia) {
        this.diferencia = diferencia;
    }
    
    @Override
    public String toString() {
        return "Billetero{" +
                "id=" + id +
                ", fecha=" + fecha +
                ", billetero=" + billetero +
                ", premios=" + premios +
                ", diferencia=" + diferencia +
                '}';
    }
}