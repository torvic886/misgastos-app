package com.misgastos.diagnostico;

import java.nio.file.Path;
import java.time.LocalDateTime;

/** Un registro del log: la línea de cabecera y, si existe, la excepción con su traza. */
public record EntradaLog(LocalDateTime fecha, String nivel, String hilo, String clase,
                         String mensaje, String detalle, Path archivo) {

    public boolean tieneDetalle() {
        return detalle != null && !detalle.isBlank();
    }
}