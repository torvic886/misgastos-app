package com.misgastos;

import javafx.application.Application;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Punto de entrada real de la aplicación.
 * No extiende Application (necesario para ejecutar desde el fat jar y el instalador).
 * Prepara la carpeta y la base de datos ANTES de que Spring intente conectarse.
 */
public class MisGastosLauncher {

    private static final String PLANTILLA = "/plantilla/misgastos.db";

    // Respaldo automático: una copia por día (la de la primera apertura) y se conservan 30.
    // Antes de aplicar una importación se guarda una copia aparte y se conservan 10.
    private static final String CARPETA_RESPALDOS = "respaldos";
    private static final String PREFIJO_DIARIO = "misgastos_";
    private static final String PREFIJO_PREVIO = "antes-de-importar_";
    private static final int RESPALDOS_A_CONSERVAR = 30;
    private static final int PREVIOS_A_CONSERVAR = 10;

    /** Archivo que deja la pantalla "Respaldo de datos" al importar; se aplica en el siguiente arranque. */
    public static final String ARCHIVO_IMPORTACION = "importacion-pendiente.db";

    // Avisos del arranque: Logback todavía no existe cuando ocurren, así que se guardan
    // aquí y MisGastosApplication los pasa a misgastos.log cuando Spring ya arrancó.
    private static final List<String[]> AVISOS = new ArrayList<>();
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        RegistroLog.iniciar();

        // dev al ejecutar desde Maven/IDE; producción en el programa instalado (jpackage)
        if (System.getProperty("spring.profiles.active") == null
                && System.getProperty("jpackage.app-path") == null) {
            System.setProperty("spring.profiles.active", "dev");
        }

        prepararBaseDeDatos();
        Application.launch(MisGastosApplication.class, args);
    }

    // ==================== AVISOS (consola + registro) ====================

    private static void info(String mensaje) {
        System.out.println(mensaje);
        anotar("INFO", mensaje);
    }

    private static void warn(String mensaje) {
        System.err.println("ADVERTENCIA: " + mensaje);
        anotar("WARN", mensaje);
    }

    private static void error(String mensaje) {
        System.err.println("ERROR: " + mensaje);
        anotar("ERROR", mensaje);
    }

    private static synchronized void anotar(String nivel, String mensaje) {
        try {
            AVISOS.add(new String[]{nivel, LocalTime.now().format(HORA), mensaje});
        } catch (Exception ignorada) {
            // nunca debe impedir que el programa abra
        }
    }

    /**
     * Pasa los avisos del arranque al registro principal (misgastos.log).
     * Se llama una sola vez, cuando Spring y Logback ya arrancaron.
     * La hora real del hecho va dentro del mensaje.
     */
    public static synchronized void volcarAvisos(org.slf4j.Logger log) {
        try {
            for (String[] a : AVISOS) {
                switch (a[0]) {
                    case "ERROR" -> log.error("[arranque {}] {}", a[1], a[2]);
                    case "WARN" -> log.warn("[arranque {}] {}", a[1], a[2]);
                    default -> log.info("[arranque {}] {}", a[1], a[2]);
                }
            }
        } catch (Exception ignorada) {
            // el programa sigue igual
        } finally {
            AVISOS.clear();
        }
    }

    // ==================== PREPARAR BASE ====================

    private static void prepararBaseDeDatos() {
        try {
            Path carpeta = Paths.get(System.getProperty("user.home"), "MisGastos");
            Files.createDirectories(carpeta);

            Path db = carpeta.resolve("misgastos.db");

            // Primer arranque: si no hay base (o está vacía), copiar la plantilla
            if (!Files.exists(db) || Files.size(db) == 0) {
                try (InputStream in = MisGastosLauncher.class.getResourceAsStream(PLANTILLA)) {
                    if (in != null) {
                        Files.copy(in, db, StandardCopyOption.REPLACE_EXISTING);
                        info("Base inicial creada desde la plantilla: " + db);
                    } else {
                        warn("no se encontró la plantilla " + PLANTILLA);
                    }
                }
            }

            // Copia diaria ANTES de que Spring abra la base. Si falla, solo se avisa:
            // nunca impide abrir el programa.
            respaldarBaseDeDatos(carpeta, db);

            // Si hay una importación en espera, primero se guarda una copia aparte de lo actual.
            // Si esa copia falla, NO se aplica la importación (para no perder datos sin copia).
            if (Files.exists(carpeta.resolve(ARCHIVO_IMPORTACION))) {
                if (respaldarAntesDeImportar(carpeta, db)) {
                    aplicarImportacionPendiente(carpeta, db);
                } else {
                    warn("no se pudo guardar la copia previa, así que NO se aplica "
                            + "la importación pendiente. Se intentará en el próximo arranque.");
                }
            }
            MigracionesBase.aplicar(db);

            String dbPath = db.toString().replace('\\', '/');
            System.setProperty("spring.datasource.url", "jdbc:sqlite:" + dbPath);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la base de datos", e);
        }
    }

    /**
     * Guarda una copia de la base en MisGastos\respaldos: una por día (la de la primera
     * apertura del día) y conserva las últimas 30. Se hace al arrancar, cuando la base
     * todavía no está en uso, así que el archivo está completo y consistente.
     *
     * @return true si la copia quedó hecha (o ya existía la de hoy, o no había nada que respaldar);
     *         false si no se pudo crear la copia.
     */
    private static boolean respaldarBaseDeDatos(Path carpeta, Path db) {
        Path carpetaRespaldos = carpeta.resolve(CARPETA_RESPALDOS);
        try {
            if (!Files.exists(db) || Files.size(db) == 0) {
                return true; // nada que respaldar
            }

            Files.createDirectories(carpetaRespaldos);

            if (yaHayCopiaDeHoy(carpetaRespaldos)) {
                info("Ya existe la copia de hoy, no se crea otra.");
            } else {
                String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                Path copia = carpetaRespaldos.resolve(PREFIJO_DIARIO + marca + ".db");
                Files.copy(db, copia, StandardCopyOption.REPLACE_EXISTING);
                info("Respaldo creado: " + copia);
            }
        } catch (Exception e) {
            warn("no se pudo crear el respaldo automático: " + e.getMessage());
            return false;
        }

        // La copia ya existe: si la limpieza falla, no cambia el resultado
        try {
            limpiarRespaldosAntiguos(carpetaRespaldos, PREFIJO_DIARIO, RESPALDOS_A_CONSERVAR);
        } catch (Exception e) {
            warn("no se pudieron limpiar los respaldos antiguos: " + e.getMessage());
        }
        return true;
    }

    /** true si en la carpeta de respaldos ya hay una copia diaria con la fecha de hoy. */
    private static boolean yaHayCopiaDeHoy(Path carpetaRespaldos) throws java.io.IOException {
        String prefijoHoy = PREFIJO_DIARIO + LocalDate.now() + "_";
        try (Stream<Path> archivos = Files.list(carpetaRespaldos)) {
            return archivos.anyMatch(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefijoHoy) && n.endsWith(".db");
            });
        }
    }

    /**
     * Guarda una copia aparte de lo actual justo antes de aplicar una importación.
     *
     * @return true si la copia quedó hecha (o si no había nada que respaldar);
     *         false si no se pudo crear.
     */
    private static boolean respaldarAntesDeImportar(Path carpeta, Path db) {
        Path carpetaRespaldos = carpeta.resolve(CARPETA_RESPALDOS);
        try {
            if (!Files.exists(db) || Files.size(db) == 0) {
                return true; // nada que respaldar
            }

            Files.createDirectories(carpetaRespaldos);

            String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path copia = carpetaRespaldos.resolve(PREFIJO_PREVIO + marca + ".db");
            Files.copy(db, copia, StandardCopyOption.REPLACE_EXISTING);
            info("Copia previa a la importación creada: " + copia);
        } catch (Exception e) {
            warn("no se pudo crear la copia previa a la importación: " + e.getMessage());
            return false;
        }

        // La copia ya existe: si la limpieza falla, no cambia el resultado
        try {
            limpiarRespaldosAntiguos(carpetaRespaldos, PREFIJO_PREVIO, PREVIOS_A_CONSERVAR);
        } catch (Exception e) {
            warn("no se pudieron limpiar las copias previas antiguas: " + e.getMessage());
        }
        return true;
    }

    /** Borra los respaldos más viejos con ese prefijo; el nombre lleva la fecha, así que ordenar por nombre basta. */
    private static void limpiarRespaldosAntiguos(Path carpetaRespaldos, String prefijo, int conservar)
            throws java.io.IOException {
        List<Path> respaldos;
        try (Stream<Path> archivos = Files.list(carpetaRespaldos)) {
            respaldos = archivos
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(prefijo) && n.endsWith(".db");
                    })
                    .sorted()
                    .toList();
        }

        int sobrantes = respaldos.size() - conservar;
        for (int i = 0; i < sobrantes; i++) {
            try {
                Files.deleteIfExists(respaldos.get(i));
            } catch (Exception e) {
                warn("no se pudo borrar el respaldo antiguo " + respaldos.get(i) + ": " + e.getMessage());
            }
        }
    }

    /**
     * Si el usuario importó una copia, la reemplaza ahora, antes de que Spring abra la base.
     * Ya se guardó una copia previa de la base anterior justo arriba (respaldarAntesDeImportar).
     */
    private static void aplicarImportacionPendiente(Path carpeta, Path db) {
        Path pendiente = carpeta.resolve(ARCHIVO_IMPORTACION);
        try {
            if (!Files.exists(pendiente)) {
                return;
            }
            if (Files.size(pendiente) == 0) {
                Files.deleteIfExists(pendiente);
                return;
            }
            // Archivos auxiliares de la base anterior (si existieran) no deben mezclarse con la nueva
            Files.deleteIfExists(carpeta.resolve("misgastos.db-wal"));
            Files.deleteIfExists(carpeta.resolve("misgastos.db-shm"));
            Files.deleteIfExists(carpeta.resolve("misgastos.db-journal"));

            Files.move(pendiente, db, StandardCopyOption.REPLACE_EXISTING);
            info("Copia importada y aplicada: " + db);
        } catch (Exception e) {
            error("no se pudo aplicar la copia importada: " + e.getMessage());
        }
    }
}