-- Promedio mensual: acumulado de billetes y premios por mes (hoja Promedio del Informe Mensual de Gestion)
CREATE TABLE IF NOT EXISTS promedio_mensual (
    anio INTEGER NOT NULL,
    mes INTEGER NOT NULL CHECK (mes BETWEEN 1 AND 12),
    bill INTEGER NOT NULL DEFAULT 0,
    premios INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (anio, mes)
);