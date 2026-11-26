package com.misgastos.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "empleado")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "es_encargada", nullable = false)
    private boolean esEncargada = false;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    // Vacío mientras la persona siga trabajando. Se conserva el historial: nunca se borra al empleado.
    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isEsEncargada() { return esEncargada; }
    public void setEsEncargada(boolean esEncargada) { this.esEncargada = esEncargada; }

    public LocalDate getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(LocalDate fechaIngreso) { this.fechaIngreso = fechaIngreso; }

    public LocalDate getFechaRetiro() { return fechaRetiro; }
    public void setFechaRetiro(LocalDate fechaRetiro) { this.fechaRetiro = fechaRetiro; }

    /** Si trabajaba ese día. No se guarda, se calcula con las fechas de ingreso y retiro. */
    public boolean vigenteEn(LocalDate dia) {
        if (dia == null || fechaIngreso == null || dia.isBefore(fechaIngreso)) return false;
        return fechaRetiro == null || !dia.isAfter(fechaRetiro);
    }

    @Override
    public String toString() {
        return nombre;
    }
}