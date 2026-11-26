package com.misgastos.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Quién es la encargada de cada turno en una semana. semanaInicio es el lunes, guardado como texto yyyy-MM-dd. */
@Entity
@Table(name = "encargada_semana")
@IdClass(EncargadaSemanaId.class)
public class EncargadaSemana {

    @Id
    @Column(name = "semana_inicio", nullable = false)
    private String semanaInicio;

    @Id
    @ManyToOne
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    public String getSemanaInicio() { return semanaInicio; }
    public void setSemanaInicio(String semanaInicio) { this.semanaInicio = semanaInicio; }

    /** El lunes de la semana como fecha. No es columna: se calcula del texto. */
    public LocalDate getLunes() { return semanaInicio == null ? null : LocalDate.parse(semanaInicio); }
    public void setLunes(LocalDate lunes) { this.semanaInicio = lunes == null ? null : lunes.toString(); }

    public Turno getTurno() { return turno; }
    public void setTurno(Turno turno) { this.turno = turno; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }
}