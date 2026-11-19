-- Lecturas con llave foránea hacia máquinas (borra en cascada) e índice por máquina y fecha
CREATE TABLE IF NOT EXISTS maquinas (id integer, activa boolean not null, codigo bigint not null unique, denominacion numeric(12,2) not null, fecha_alta varchar(255) not null, fecha_baja varchar(255), motivo_baja varchar(200), contador_in bigint, contador_out bigint, juego varchar(100), nombre varchar(100), serial varchar(100), contador_prem bigint, primary key (id));
CREATE TABLE IF NOT EXISTS lecturas (id integer, anulada boolean not null, contador_in bigint not null, contador_out bigint not null, contador_prem bigint not null, denominacion numeric(12,2) not null, fecha varchar(255) not null, motivo_anulacion varchar(200), periodo integer not null, registrada_en timestamp not null, tipo varchar(15) not null, maquina_id bigint not null, primary key (id));
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