-- Crea el usuario owner como Administrador general si la base no tiene ninguno
INSERT INTO usuarios (activo, fecha_creacion, password, rol, username)
SELECT 1,
       (SELECT fecha_creacion FROM usuarios WHERE username = 'admin'),
       (SELECT password FROM usuarios WHERE username = 'admin'),
       'ADMIN_GENERAL',
       'owner'
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE rol = 'ADMIN_GENERAL')
  AND NOT EXISTS (SELECT 1 FROM usuarios WHERE username = 'owner')
  AND EXISTS (SELECT 1 FROM usuarios WHERE username = 'admin');