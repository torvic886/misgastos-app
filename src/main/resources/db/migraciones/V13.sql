-- Horarios especiales de los turnos para sabado y domingo o festivo, sin datos del casino
-- Si un turno no tiene fila para un tipo de dia se usa su horario base de la tabla turno
CREATE TABLE IF NOT EXISTS turno_horario_especial (
    id integer primary key autoincrement,
    turno_id integer not null,
    tipo_dia varchar(20) not null,
    hora_inicio varchar(5) not null,
    hora_fin varchar(5) not null,
    unique (turno_id, tipo_dia),
    foreign key (turno_id) references turno(id) on delete cascade
);