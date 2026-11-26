-- Turnos y nomina quincenal: empleados, turnos, programacion, descansos, novedades, encargadas por semana y festivos
CREATE TABLE IF NOT EXISTS empleado (
    id integer primary key autoincrement,
    nombre varchar(100) not null,
    es_encargada boolean not null default 0,
    fecha_ingreso varchar(255) not null,
    fecha_retiro varchar(255)
);

-- Etiqueta es el texto corto del chip (1, 2, M) y orden define como se muestran
CREATE TABLE IF NOT EXISTS turno (
    id integer primary key autoincrement,
    nombre varchar(50) not null unique,
    etiqueta varchar(5) not null,
    hora_inicio varchar(5) not null,
    hora_fin varchar(5) not null,
    cupos integer not null default 1 check (cupos >= 0),
    orden integer not null default 0,
    activo boolean not null default 1
);

-- Una fila por persona que trabaja ese dia, posicion 1 es la encargada, la entrada y la salida son las reales del dia
CREATE TABLE IF NOT EXISTS programacion (
    id integer primary key autoincrement,
    fecha varchar(255) not null,
    turno_id bigint not null references turno(id) on delete restrict,
    posicion integer not null check (posicion >= 1),
    empleado_id bigint not null references empleado(id) on delete restrict,
    hora_entrada varchar(5) not null,
    hora_salida varchar(5) not null,
    UNIQUE (fecha, empleado_id)
);
CREATE INDEX IF NOT EXISTS idx_programacion_fecha ON programacion (fecha, turno_id, posicion);
CREATE INDEX IF NOT EXISTS idx_programacion_empleado ON programacion (empleado_id, fecha);

-- Descanso normal de la rotacion
CREATE TABLE IF NOT EXISTS descanso (
    id integer primary key autoincrement,
    fecha varchar(255) not null,
    empleado_id bigint not null references empleado(id) on delete restrict,
    UNIQUE (fecha, empleado_id)
);
CREATE INDEX IF NOT EXISTS idx_descanso_fecha ON descanso (fecha);

-- Incapacidades, permisos, vacaciones, faltas, cambios de turno y notas, con rango de fechas
CREATE TABLE IF NOT EXISTS novedad (
    id integer primary key autoincrement,
    fecha_desde varchar(255) not null,
    fecha_hasta varchar(255) not null,
    empleado_id bigint not null references empleado(id) on delete restrict,
    tipo varchar(20) not null,
    empleado2_id bigint references empleado(id) on delete restrict,
    motivo varchar(300),
    registrado_por bigint not null references usuarios(id) on delete restrict,
    registrada_en timestamp not null,
    CHECK (fecha_hasta >= fecha_desde)
);
CREATE INDEX IF NOT EXISTS idx_novedad_empleado ON novedad (empleado_id, fecha_desde);
CREATE INDEX IF NOT EXISTS idx_novedad_fechas ON novedad (fecha_desde, fecha_hasta);

-- Quien es la encargada de cada turno en una semana, semana_inicio es el lunes
CREATE TABLE IF NOT EXISTS encargada_semana (
    semana_inicio varchar(255) not null,
    turno_id bigint not null references turno(id) on delete restrict,
    empleado_id bigint not null references empleado(id) on delete restrict,
    PRIMARY KEY (semana_inicio, turno_id)
);

CREATE TABLE IF NOT EXISTS festivo (
    id integer primary key autoincrement,
    fecha varchar(255) not null unique,
    nombre varchar(100) not null
);