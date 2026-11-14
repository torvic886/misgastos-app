-- Llaves foráneas en subcategorias, gastos, presupuestos_categoria y auditoria_logs, e índices
CREATE TABLE subcategorias_nueva (
    id integer primary key autoincrement,
    nombre varchar(50) not null,
    categoria_id bigint not null references categorias(id) on delete restrict
);
INSERT INTO subcategorias_nueva (id, nombre, categoria_id) SELECT id, nombre, categoria_id FROM subcategorias;
DROP TABLE subcategorias;
ALTER TABLE subcategorias_nueva RENAME TO subcategorias;

CREATE TABLE gastos_nueva (
    id integer primary key autoincrement,
    cantidad integer not null,
    fecha varchar(255) not null,
    hora varchar(255) not null,
    notas varchar(500),
    producto varchar(100) not null,
    valor_total numeric(10,2) not null,
    valor_unitario numeric(10,2) not null,
    categoria_id bigint not null references categorias(id) on delete restrict,
    subcategoria_id bigint not null references subcategorias(id) on delete restrict,
    usuario_id bigint not null references usuarios(id) on delete restrict,
    cedula varchar(20),
    nombre_persona varchar(100),
    fecha_eliminacion timestamp
);
INSERT INTO gastos_nueva (id, cantidad, fecha, hora, notas, producto, valor_total, valor_unitario, categoria_id, subcategoria_id, usuario_id, cedula, nombre_persona, fecha_eliminacion)
SELECT id, cantidad, fecha, hora, notas, producto, valor_total, valor_unitario, categoria_id, subcategoria_id, usuario_id, cedula, nombre_persona, fecha_eliminacion FROM gastos;
DROP TABLE gastos;
ALTER TABLE gastos_nueva RENAME TO gastos;

CREATE TABLE presupuestos_categoria_nueva (
    categoria_id integer primary key references categorias(id) on delete cascade,
    monto numeric(14,2) not null
);
INSERT INTO presupuestos_categoria_nueva (categoria_id, monto) SELECT categoria_id, monto FROM presupuestos_categoria;
DROP TABLE presupuestos_categoria;
ALTER TABLE presupuestos_categoria_nueva RENAME TO presupuestos_categoria;

CREATE TABLE auditoria_logs_nueva (
    id integer primary key autoincrement,
    accion varchar(100) not null,
    detalles varchar(500),
    fecha timestamp not null,
    usuario_id bigint not null references usuarios(id) on delete restrict
);
INSERT INTO auditoria_logs_nueva (id, accion, detalles, fecha, usuario_id) SELECT id, accion, detalles, fecha, usuario_id FROM auditoria_logs;
DROP TABLE auditoria_logs;
ALTER TABLE auditoria_logs_nueva RENAME TO auditoria_logs;

CREATE INDEX IF NOT EXISTS idx_gastos_fecha ON gastos (fecha);
CREATE INDEX IF NOT EXISTS idx_gastos_categoria_fecha ON gastos (categoria_id, fecha);
CREATE INDEX IF NOT EXISTS idx_gastos_usuario_fecha ON gastos (usuario_id, fecha);
CREATE INDEX IF NOT EXISTS idx_gastos_eliminacion ON gastos (fecha_eliminacion);
CREATE INDEX IF NOT EXISTS idx_subcategorias_categoria ON subcategorias (categoria_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria_logs (fecha);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON auditoria_logs (usuario_id);