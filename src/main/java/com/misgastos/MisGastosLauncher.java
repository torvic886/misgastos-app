package com.misgastos;

import javafx.application.Application;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Punto de entrada real de la aplicación.
 * No extiende Application (necesario para ejecutar desde el fat jar y el instalador).
 * Prepara la carpeta y la base de datos ANTES de que Spring intente conectarse.
 */
public class MisGastosLauncher {

    private static final String PLANTILLA = "/plantilla/misgastos.db";

    // Respaldo automático: carpeta dentro de MisGastos y cuántas copias se conservan
    private static final String CARPETA_RESPALDOS = "respaldos";
    private static final int RESPALDOS_A_CONSERVAR = 5;

    /** Archivo que deja la pantalla "Respaldo de datos" al importar; se aplica en el siguiente arranque. */
    public static final String ARCHIVO_IMPORTACION = "importacion-pendiente.db";

    public static void main(String[] args) {
        prepararBaseDeDatos();
        Application.launch(MisGastosApplication.class, args);
    }

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
                        System.out.println("Base inicial creada desde la plantilla: " + db);
                    } else {
                        System.err.println("ADVERTENCIA: no se encontró la plantilla " + PLANTILLA);
                    }
                }
            }

            // Copia de seguridad ANTES de que Spring abra la base (y antes de cualquier
            // cambio de estructura). Si falla, solo se avisa: nunca impide abrir el programa.
            respaldarBaseDeDatos(carpeta, db);
            aplicarImportacionPendiente(carpeta, db);
            
            respaldarBaseDeDatos(carpeta, db);
            aplicarImportacionPendiente(carpeta, db);
            MigracionesBase.aplicar(db);   // <-- NUEVA

            String dbPath = db.toString().replace('\\', '/');
            System.setProperty("spring.datasource.url", "jdbc:sqlite:" + dbPath);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la base de datos", e);
        }
    }

    /**
     * Guarda una copia de la base en MisGastos\respaldos y conserva solo las últimas 5.
     * Se hace al arrancar, cuando la base todavía no está en uso, así que el archivo
     * está completo y consistente.
     */
    private static void respaldarBaseDeDatos(Path carpeta, Path db) {
        try {
            if (!Files.exists(db) || Files.size(db) == 0) {
                return; // nada que respaldar
            }

            Path carpetaRespaldos = carpeta.resolve(CARPETA_RESPALDOS);
            Files.createDirectories(carpetaRespaldos);

            String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path copia = carpetaRespaldos.resolve("misgastos_" + marca + ".db");
            Files.copy(db, copia, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Respaldo creado: " + copia);

            limpiarRespaldosAntiguos(carpetaRespaldos);
        } catch (Exception e) {
            System.err.println("ADVERTENCIA: no se pudo crear el respaldo automático: " + e.getMessage());
        }
    }

    /** Borra los respaldos más viejos; el nombre lleva la fecha, así que ordenar por nombre basta. */
    private static void limpiarRespaldosAntiguos(Path carpetaRespaldos) throws java.io.IOException {
        List<Path> respaldos;
        try (Stream<Path> archivos = Files.list(carpetaRespaldos)) {
            respaldos = archivos
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("misgastos_") && n.endsWith(".db");
                    })
                    .sorted()
                    .toList();
        }

        int sobrantes = respaldos.size() - RESPALDOS_A_CONSERVAR;
        for (int i = 0; i < sobrantes; i++) {
            try {
                Files.deleteIfExists(respaldos.get(i));
            } catch (Exception e) {
                System.err.println("No se pudo borrar el respaldo antiguo " + respaldos.get(i) + ": " + e.getMessage());
            }
        }
    }

    /**
     * Si el usuario importó una copia, la reemplaza ahora, antes de que Spring abra la base.
     * Ya se hizo un respaldo de la base anterior justo arriba (respaldarBaseDeDatos).
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
            System.out.println("Copia importada y aplicada: " + db);
        } catch (Exception e) {
            System.err.println("ADVERTENCIA: no se pudo aplicar la copia importada: " + e.getMessage());
        }
    }
}