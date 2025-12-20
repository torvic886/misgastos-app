package com.misgastos.util;

import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;

/**
 * Resultado del popup de producto nuevo
 */
public class SeleccionProductoNueva {

    public final Categoria categoria;
    public final Subcategoria subcategoria;

    public SeleccionProductoNueva(Categoria categoria, Subcategoria subcategoria) {
        this.categoria = categoria;
        this.subcategoria = subcategoria;
    }
}
