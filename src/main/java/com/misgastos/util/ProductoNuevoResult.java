package com.misgastos.util;

import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;

public class ProductoNuevoResult {

    private final Categoria categoria;
    private final Subcategoria subcategoria;

    public ProductoNuevoResult(Categoria categoria, Subcategoria subcategoria) {
        this.categoria = categoria;
        this.subcategoria = subcategoria;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Subcategoria getSubcategoria() {
        return subcategoria;
    }
}

