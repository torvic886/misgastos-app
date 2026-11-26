package com.misgastos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Tipos de día que pueden tener un horario distinto al base. Lunes a viernes usa el base. */
public enum TipoDiaHorario {

    SABADO("Sábado"),
    DOMINGO_FESTIVO("Domingo o festivo");

    private final String etiqueta;

    TipoDiaHorario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Tipo de día de una fecha, o null si es un día normal (lunes a viernes sin festivo).
     * Un festivo que cae en sábado cuenta como domingo o festivo.
     */
    public static TipoDiaHorario deFecha(LocalDate fecha, boolean festivo) {
        if (festivo || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return DOMINGO_FESTIVO;
        }
        if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return SABADO;
        }
        return null;
    }
}