package com.misgastos;

import javafx.application.Application;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Punto de entrada real de la aplicación.
 * No extiende Application (necesario para ejecutar desde el fat jar y el instalador).
 * Prepara la carpeta y la base de datos ANTES de que Spring intente conectarse.
 */
public class MisGastosLauncher {

    private static final String PLANTILLA = "/plantilla/misgastos.db";

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

            String dbPath = db.toString().replace('\\', '/');
            System.setProperty("spring.datasource.url", "jdbc:sqlite:" + dbPath);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la base de datos", e);
        }
    }
}