-- Lecturas con llave foránea hacia máquinas (borra en cascada) e índice por máquina y fecha
CREATE TABLE lecturas_nueva (
    id integer primary key autoincrement,
    anulada boolean not null,
    contador_in bigint not null,
    contador_out bigint not null,
    contador_prem bigint not null,
    denominacion numeric(12,2) not null,
    fecha varchar(255) not null,
    motivo_anulacion varchar(200),
    periodo integer not null,
    registrada_en timestamp not null,
    tipo varchar(15) not null,
    maquina_id bigint not null references maquinas(id) on delete cascade
);
INSERT INTO lecturas_nueva (id, anulada, contador_in, contador_out, contador_prem, denominacion, fecha, motivo_anulacion, periodo, registrada_en, tipo, maquina_id)
SELECT id, anulada, contador_in, contador_out, contador_prem, denominacion, fecha, motivo_anulacion, periodo, registrada_en, tipo, maquina_id
FROM lecturas WHERE maquina_id IN (SELECT id FROM maquinas);
DROP TABLE lecturas;
ALTER TABLE lecturas_nueva RENAME TO lecturas;
CREATE INDEX idx_lecturas_maquina_fecha ON lecturas (maquina_id, fecha, id);