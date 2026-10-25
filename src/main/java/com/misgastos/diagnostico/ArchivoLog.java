package com.misgastos.diagnostico;

import java.nio.file.Path;
import java.time.LocalDate;

/** Un archivo de la carpeta de logs. fecha == null significa "el archivo activo" (misgastos.log). */
public record ArchivoLog(Path ruta, LocalDate fecha, int indice, boolean comprimido, long bytes) {

    public boolean esActual() {
        return fecha == null;
    }

    public String nombre() {
        return ruta.getFileName().toString();
    }
}