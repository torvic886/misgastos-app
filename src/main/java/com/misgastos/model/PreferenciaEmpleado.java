package com.misgastos.model;

import jakarta.persistence.*;

@Entity
@Table(name = "preferencia_empleado")
public class PreferenciaEmpleado {

    /** Tipo de preferencia: el empleado descansa ese día de la semana, todas las semanas. */
    public static final String DESCANSO_FIJO = "DESCANSO_FIJO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    // Nombre del enum DiaSemanaEs: LUNES, MARTES...
    @Column(name = "dia_semana", nullable = false, length = 10)
    private String diaSemana;

    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    @Column(name = "nota", length = 200)
    private String nota;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Long empleadoId) { this.empleadoId = empleadoId; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
}