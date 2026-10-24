-- Presupuesto mensual por categoría
CREATE TABLE IF NOT EXISTS presupuestos_categoria (
    categoria_id INTEGER PRIMARY KEY,
    monto NUMERIC(14,2) NOT NULL
);