-- Una sola lectura INICIAL activa por máquina
CREATE UNIQUE INDEX IF NOT EXISTS ux_lecturas_inicial ON lecturas (maquina_id) WHERE tipo = 'INICIAL' AND anulada = 0;