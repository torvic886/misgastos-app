package com.misgastos.model;

/** Tipos de novedad. El nombre del enum es lo que se guarda en la columna tipo. */
public enum TipoNovedad {
    INCAPACIDAD("Incapacidad"),
    PERMISO("Permiso"),
    VACACIONES("Vacaciones"),
    FALTA("Falta"),
    CAMBIO_TURNO("Cambio de turno"),
    NOTA("Nota");

    private final String etiqueta;

    TipoNovedad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }

    /** Busca el tipo por el nombre guardado en la base. Devuelve null si no existe. */
    public static TipoNovedad deNombre(String nombre) {
        for (TipoNovedad t : values()) {
            if (t.name().equals(nombre)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}