package com.misgastos.model;

import java.math.BigDecimal;

/** Una lectura con sus valores en pesos. total es null cuando no hay con qué compararla. */
public record FilaContador(Lectura lectura, BigDecimal diferencia, BigDecimal total) {
}