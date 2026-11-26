package com.misgastos.model;

import java.time.DayOfWeek;

/** Día de la semana en español: el nombre del enum es lo que se guarda en la base. */
public enum DiaSemanaEs {
    LUNES("Lunes", DayOfWeek.MONDAY),
    MARTES("Martes", DayOfWeek.TUESDAY),
    MIERCOLES("Miércoles", DayOfWeek.WEDNESDAY),
    JUEVES("Jueves", DayOfWeek.THURSDAY),
    VIERNES("Viernes", DayOfWeek.FRIDAY),
    SABADO("Sábado", DayOfWeek.SATURDAY),
    DOMINGO("Domingo", DayOfWeek.SUNDAY);

    private final String etiqueta;
    private final DayOfWeek diaJava;

    DiaSemanaEs(String etiqueta, DayOfWeek diaJava) {
        this.etiqueta = etiqueta;
        this.diaJava = diaJava;
    }

    public String getEtiqueta() { return etiqueta; }

    public DayOfWeek getDiaJava() { return diaJava; }

    /** Convierte el día de Java (MONDAY...) al nuestro. */
    public static DiaSemanaEs de(DayOfWeek dia) {
        for (DiaSemanaEs d : values()) {
            if (d.diaJava == dia) {
                return d;
            }
        }
        throw new IllegalArgumentException("Día no válido: " + dia);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}