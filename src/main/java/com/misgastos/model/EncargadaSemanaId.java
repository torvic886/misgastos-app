package com.misgastos.model;

import java.io.Serializable;
import java.util.Objects;

/** Llave compuesta de EncargadaSemana: lunes de la semana (texto yyyy-MM-dd) + turno. */
public class EncargadaSemanaId implements Serializable {

    private String semanaInicio;
    private Long turno;

    public EncargadaSemanaId() { }

    public EncargadaSemanaId(String semanaInicio, Long turno) {
        this.semanaInicio = semanaInicio;
        this.turno = turno;
    }

    public String getSemanaInicio() { return semanaInicio; }
    public Long getTurno() { return turno; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncargadaSemanaId)) return false;
        EncargadaSemanaId otro = (EncargadaSemanaId) o;
        return Objects.equals(semanaInicio, otro.semanaInicio) && Objects.equals(turno, otro.turno);
    }

    @Override
    public int hashCode() {
        return Objects.hash(semanaInicio, turno);
    }
}