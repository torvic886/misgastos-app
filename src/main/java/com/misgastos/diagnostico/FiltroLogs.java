package com.misgastos.diagnostico;

import java.time.LocalDate;

/** nivel == null significa "todos". Las fechas son inclusivas y pueden ser null. */
public record FiltroLogs(String nivel, String texto, LocalDate desde, LocalDate hasta) {
}