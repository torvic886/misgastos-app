package com.misgastos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Migraciones de la base de datos. Se ejecutan en el arranque, ANTES de que Spring abra la base.
 *
 * - La tabla schema_migraciones guarda las versiones ya aplicadas.
 * - Una base que ya existe se marca como versión 1 (esquema actual) sin tocar sus datos.
 * - Las migraciones son archivos V2.sql, V3.sql... en /db/migraciones/ (dentro de resources).
 *   Se aplican en orden, una sola vez, y cada una en una transacción.
 */
public final class MigracionesBase {

    private static final String CARPETA = "/db/migraciones/";
    private static final int VERSION_BASE = 1;

    private MigracionesBase() {
    }

    public static void aplicar(Path db) {
        String url = "jdbc:sqlite:" + db.toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url)) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS schema_migraciones ("
                        + "version INTEGER PRIMARY KEY, "
                        + "descripcion TEXT NOT NULL, "
                        + "aplicada_en TEXT NOT NULL)");
            }

            int actual = versionActual(c);

            if (actual == 0) {
                if (!existeTabla(c, "usuarios")) {
                    // Base sin tablas todavía: Hibernate las creará; se marcará la base en el próximo arranque
                    System.out.println("Migraciones: la base aún no tiene tablas, se omite por ahora.");
                    return;
                }
                registrar(c, VERSION_BASE, "Esquema inicial (estado existente)");
                actual = VERSION_BASE;
                System.out.println("Migraciones: base marcada como versión " + VERSION_BASE);
            }

            int siguiente = actual + 1;
            String script;
            while ((script = leerScript(siguiente)) != null) {
                aplicarMigracion(c, siguiente, script);
                siguiente++;
            }
            System.out.println("Migraciones: base en versión " + (siguiente - 1));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudieron aplicar las migraciones de la base de datos. "
                  + "Tus datos están a salvo en la carpeta MisGastos\\respaldos. Motivo: " + e.getMessage(), e);
        }
    }

    private static void aplicarMigracion(Connection c, int version, String script) throws Exception {
        System.out.println("Migraciones: aplicando V" + version + "...");
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            for (String sentencia : dividir(script)) {
                st.execute(sentencia);
            }
            registrar(c, version, descripcion(script, version));
            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw new IllegalStateException("Falló la migración V" + version + ": " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
        }
    }

    private static int versionActual(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migraciones")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean existeTabla(Connection c, String nombre) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void registrar(Connection c, int version, String descripcion) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schema_migraciones (version, descripcion, aplicada_en) VALUES (?, ?, ?)")) {
            ps.setInt(1, version);
            ps.setString(2, descripcion);
            ps.setString(3, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    /** Devuelve el contenido de V{version}.sql, o null si no existe (fin de la cadena). */
    private static String leerScript(int version) throws Exception {
        try (var in = MigracionesBase.class.getResourceAsStream(CARPETA + "V" + version + ".sql")) {
            if (in == null) {
                return null;
            }
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return texto.startsWith("\uFEFF") ? texto.substring(1) : texto;
        }
    }

    /** La primera línea de comentario (-- ...) del script es su descripción. */
    private static String descripcion(String script, int version) {
        for (String linea : script.split("\\R")) {
            String t = linea.trim();
            if (t.startsWith("--")) {
                String d = t.substring(2).trim();
                if (!d.isEmpty()) {
                    return d;
                }
            } else if (!t.isEmpty()) {
                break;
            }
        }
        return "Migración V" + version;
    }

    /** Separa por ';' ignorando líneas de comentario. No uses ';' dentro de textos del script. */
    private static List<String> dividir(String script) {
        StringBuilder limpio = new StringBuilder();
        for (String linea : script.split("\\R")) {
            if (!linea.trim().startsWith("--")) {
                limpio.append(linea).append('\n');
            }
        }
        List<String> sentencias = new ArrayList<>();
        for (String parte : limpio.toString().split(";")) {
            String s = parte.trim();
            if (!s.isEmpty()) {
                sentencias.add(s);
            }
        }
        return sentencias;
    }
}