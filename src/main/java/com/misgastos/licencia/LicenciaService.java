package com.misgastos.licencia;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prueba de 3 meses + activación con clave firmada (Ed25519) atada al equipo.
 * Sin Spring: se puede usar directamente con new LicenciaService().
 *
 * Sobre los logs: nunca se registra la clave de licencia, el secreto, el código
 * del equipo ni las rutas de los archivos de control (están escondidas a propósito).
 */
public final class LicenciaService {

    private static final Logger log = LoggerFactory.getLogger(LicenciaService.class);

    public enum Estado { LICENCIADA, PRUEBA, VENCIDA }

    public record Resultado(Estado estado, long diasRestantes, String motivo) {}

    private record Datos(LocalDate inicio, LocalDate uso) {}

    // Clave PÚBLICA (solo sirve para verificar, no para crear claves)
    private static final String CLAVE_PUBLICA =
        "MCowBQYDK2VwAyEAnpncKEKqgc7VxkMONRpqkfs+S8M6NR6jd32Hdp/GHlM=";

    // CAMBIA este texto por uno largo y aleatorio tuyo (protege la fecha de inicio)
    private static final String SECRETO = "Kq7vR2mX9pLw4Zt8NfB1sYd6HcJ3uEa5";

    private static final int MESES_PRUEBA = 3;
    private static final String K_DATOS = "d";
    private static final String K_LICENCIA = "l";

    private final Preferences prefs = Preferences.userNodeForPackage(LicenciaService.class);
    private final Path carpeta = Paths.get(System.getProperty("user.home"), "MisGastos");
    private final Path appData = Paths.get(
        System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "MisGastos");
    private final List<Path> archivosDatos = List.of(carpeta.resolve(".ini"), appData.resolve(".cfg"));
    private final List<Path> archivosLicencia = List.of(carpeta.resolve("licencia.lic"), appData.resolve("licencia.lic"));

    // ==================== API PÚBLICA ====================

    public Resultado evaluar() {
        log.debug("Evaluando la licencia");
        LocalDate hoy = LocalDate.now();

        // 1. Leer fecha de inicio y último uso (3 copias firmadas)
        List<String> crudos = new ArrayList<>();
        crudos.add(prefs.get(K_DATOS, null));
        for (Path p : archivosDatos) crudos.add(leerArchivo(p));

        List<Datos> validos = new ArrayList<>();
        boolean alterado = false;
        for (String c : crudos) {
            if (c == null) continue;
            Datos d = parsear(c);
            if (d == null) alterado = true; else validos.add(d);
        }
        log.debug("Copias de datos de la prueba: {} válida(s), alterada(s): {}", validos.size(), alterado);

        Datos datos;
        if (!validos.isEmpty()) {
            LocalDate ini = validos.stream().map(Datos::inicio).min(LocalDate::compareTo).get();
            LocalDate uso = validos.stream().map(Datos::uso).max(LocalDate::compareTo).get();
            datos = new Datos(ini, uso);
        } else if (alterado) {
            log.warn("Licencia vencida: los datos de la prueba fueron alterados");
            return new Resultado(Estado.VENCIDA, 0, "Los datos de la prueba fueron alterados");
        } else {
            log.info("Primer arranque: se inicia el periodo de prueba");
            datos = new Datos(hoy, hoy); // primer arranque
        }

        // 2. Reloj atrasado
        if (hoy.isBefore(datos.uso().minusDays(1))) {
            log.warn("Licencia vencida: la fecha del equipo es anterior al último uso registrado");
            return new Resultado(Estado.VENCIDA, 0, "La fecha del equipo es anterior al último uso. Corrígela.");
        }
        datos = new Datos(datos.inicio(), hoy.isAfter(datos.uso()) ? hoy : datos.uso());
        guardarDatos(datos);

        // 3. ¿Hay licencia válida?
        String lic = prefs.get(K_LICENCIA, null);
        if (lic == null) {
            for (Path p : archivosLicencia) {
                lic = leerArchivo(p);
                if (lic != null) break;
            }
        }
        if (lic != null) {
            Optional<LocalDate> vence = validarClave(lic);
            if (vence.isPresent()) {
                long diasLicencia = ChronoUnit.DAYS.between(hoy, vence.get());
                log.info("Licencia válida; días restantes: {}", diasLicencia);
                return new Resultado(Estado.LICENCIADA, diasLicencia, "");
            }
            log.warn("Hay una licencia guardada pero no es válida (otro equipo, vencida o dañada)");
        } else {
            log.debug("No hay licencia guardada");
        }

        // 4. Periodo de prueba
        long dias = ChronoUnit.DAYS.between(hoy, datos.inicio().plusMonths(MESES_PRUEBA));
        if (dias >= 0) {
            log.debug("Periodo de prueba: quedan {} día(s)", dias);
            return new Resultado(Estado.PRUEBA, dias, "");
        }
        log.warn("Licencia vencida: el periodo de prueba terminó");
        return new Resultado(Estado.VENCIDA, 0, "El periodo de prueba terminó");
    }

    /** Devuelve true y guarda la clave si es válida para ESTE equipo y no ha vencido. */
    public boolean activar(String clave) {
        if (clave == null) {
            log.debug("Activación sin clave");
            return false;
        }
        clave = clave.trim();
        if (validarClave(clave).isEmpty()) {
            log.warn("Activación rechazada: la clave no es válida para este equipo");
            return false;
        }
        prefs.put(K_LICENCIA, clave);
        for (Path p : archivosLicencia) escribirArchivo(p, clave);
        log.info("Licencia activada correctamente");
        return true;
    }

    /** Código que el cliente te envía para que generes su clave. */
    public String codigoEquipo() {
        String hex = HexFormat.of().withUpperCase()
            .formatHex(sha256(("MisGastos|" + idMaquina()).getBytes(StandardCharsets.UTF_8)))
            .substring(0, 12);
        return hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-" + hex.substring(8, 12);
    }

    // ==================== LICENCIA ====================

    private Optional<LocalDate> validarClave(String clave) {
        try {
            String[] p = clave.trim().split("\\.");
            if (p.length != 2) {
                log.debug("Clave de licencia con formato incorrecto");
                return Optional.empty();
            }
            Base64.Decoder dec = Base64.getUrlDecoder();
            byte[] payload = dec.decode(p[0]);
            byte[] firma = dec.decode(p[1]);

            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(CLAVE_PUBLICA))));
            s.update(payload);
            if (!s.verify(firma)) {
                log.debug("Clave de licencia con firma no válida");
                return Optional.empty();
            }

            String[] d = new String(payload, StandardCharsets.UTF_8).split("\\|");
            if (d.length != 2 || !d[0].equals(codigoEquipo())) {
                log.debug("Clave de licencia de otro equipo o con contenido incorrecto");
                return Optional.empty();
            }

            LocalDate vence = LocalDate.parse(d[1]);
            if (vence.isBefore(LocalDate.now())) {
                log.debug("Clave de licencia vencida el {}", vence);
                return Optional.empty();
            }
            return Optional.of(vence);
        } catch (Exception e) {
            // Sin stacktrace completo: puede venir de una clave mal escrita por el usuario
            log.warn("No se pudo validar la clave de licencia ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // ==================== DATOS DE LA PRUEBA ====================

    private void guardarDatos(Datos d) {
        String base = d.inicio() + "|" + d.uso();
        String valor = base + "|" + hmac(base);
        prefs.put(K_DATOS, valor);
        for (Path p : archivosDatos) escribirArchivo(p, valor);
    }

    private Datos parsear(String raw) {
        try {
            String[] p = raw.trim().split("\\|");
            if (p.length != 3) {
                log.warn("Una copia de los datos de la prueba tiene formato incorrecto");
                return null;
            }
            if (!hmac(p[0] + "|" + p[1]).equals(p[2])) {
                log.warn("Una copia de los datos de la prueba no pasó la verificación de firma");
                return null;
            }
            return new Datos(LocalDate.parse(p[0]), LocalDate.parse(p[1]));
        } catch (Exception e) {
            log.warn("Una copia de los datos de la prueba no se pudo leer ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    // ==================== UTILIDADES ====================

    private String hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("No se pudo calcular la firma de los datos de la licencia", e);
            throw new IllegalStateException(e);
        }
    }

    private byte[] sha256(byte[] datos) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (Exception e) {
            log.error("No se pudo calcular el SHA-256 del código del equipo", e);
            throw new IllegalStateException(e);
        }
    }

    private String idMaquina() {
        try {
            Process proc = new ProcessBuilder("reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                .redirectErrorStream(true).start();
            String salida = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            for (String linea : salida.split("\\R")) {
                if (linea.contains("MachineGuid")) {
                    String[] t = linea.trim().split("\\s+");
                    return t[t.length - 1];
                }
            }
            log.warn("No se encontró el identificador de la máquina en el registro; se usa el nombre del equipo");
        } catch (Exception ignored) {
            // Si cae al plan B, el código del equipo cambia: conviene que quede anotado
            log.warn("No se pudo leer el identificador de la máquina ({}); se usa el nombre del equipo",
                ignored.getClass().getSimpleName());
        }
        return System.getenv().getOrDefault("COMPUTERNAME", "?") + "|" + System.getProperty("user.name");
    }

    private String leerArchivo(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p).trim() : null;
        } catch (Exception e) {
            // No se escribe la ruta: los archivos de control están escondidos a propósito
            log.warn("No se pudo leer un archivo de control de la licencia ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    private void escribirArchivo(Path p, String contenido) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, contenido);
        } catch (Exception ignored) {
            log.warn("No se pudo escribir un archivo de control de la licencia ({})",
                ignored.getClass().getSimpleName());
        }
    }
}