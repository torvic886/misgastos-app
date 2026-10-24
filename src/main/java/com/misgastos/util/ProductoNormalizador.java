package com.misgastos.util;

import java.text.Normalizer;
import java.util.Locale;

/** Reglas para comparar nombres de producto sin importar mayúsculas, tildes o espacios sobrantes. */
public final class ProductoNormalizador {

    private ProductoNormalizador() {
    }

    /** Quita espacios al inicio, al final y los repetidos. Conserva mayúsculas y tildes. */
    public static String limpiar(String producto) {
        return producto == null ? "" : producto.trim().replaceAll("\\s+", " ");
    }

    /** Clave de comparación: "Café", "CAFÉ" y " cafe " dan la misma. La ñ se conserva. */
    public static String clave(String producto) {
        String minuscula = limpiar(producto).toLowerCase(Locale.ROOT).replace("ñ", "\u0001");
        String sinTildes = Normalizer.normalize(minuscula, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.replace("\u0001", "ñ");
    }
}