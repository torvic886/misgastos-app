-- Preferencias suaves por empleado y dia de la semana, solo sugerencias y avisos que nunca bloquean, sin datos del casino
-- El tipo DESCANSO_FIJO es el unico que se usa por ahora, otros tipos se pueden agregar sin cambiar la tabla
CREATE TABLE IF NOT EXISTS preferencia_empleado (
    id integer primary key autoincrement,
    empleado_id integer not null,
    dia_semana varchar(10) not null,
    tipo varchar(30) not null,
    nota varchar(200),
    unique (empleado_id, dia_semana, tipo),
    foreign key (empleado_id) references empleado(id) on delete cascade
);