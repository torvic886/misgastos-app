-- Índices para las consultas de gastos, auditoría, subcategorías y billeteros
CREATE INDEX IF NOT EXISTS idx_gastos_fecha ON gastos (fecha);
CREATE INDEX IF NOT EXISTS idx_gastos_categoria_fecha ON gastos (categoria_id, fecha);
CREATE INDEX IF NOT EXISTS idx_gastos_usuario_fecha ON gastos (usuario_id, fecha);
CREATE INDEX IF NOT EXISTS idx_gastos_eliminacion ON gastos (fecha_eliminacion);
CREATE INDEX IF NOT EXISTS idx_subcategorias_categoria ON subcategorias (categoria_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria_logs (fecha);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON auditoria_logs (usuario_id);
CREATE INDEX IF NOT EXISTS idx_billeteros_fecha ON billeteros (fecha);