-- Índices parciales de gastos: el de la papelera solo cubre filas eliminadas y el de fecha y hora solo las activas
DROP INDEX IF EXISTS idx_gastos_eliminacion;
CREATE INDEX IF NOT EXISTS idx_gastos_eliminacion ON gastos (fecha_eliminacion) WHERE fecha_eliminacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gastos_activos_fecha_hora ON gastos (fecha, hora) WHERE fecha_eliminacion IS NULL;