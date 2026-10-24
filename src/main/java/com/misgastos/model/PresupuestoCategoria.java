package com.misgastos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Tope mensual de gasto de una categoría. La clave es el id de la categoría. */
@Entity
@Table(name = "presupuestos_categoria")
public class PresupuestoCategoria {

    @Id
    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    public PresupuestoCategoria() {
    }

    public PresupuestoCategoria(Long categoriaId, BigDecimal monto) {
        this.categoriaId = categoriaId;
        this.monto = monto;
    }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
}