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

/**
 * Prueba de 3 meses + activación con clave firmada (Ed25519) atada al equipo.
 * Sin Spring: se puede usar directamente con new LicenciaService().
 */
public final class LicenciaService {

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

        Datos datos;
        if (!validos.isEmpty()) {
            LocalDate ini = validos.stream().map(Datos::inicio).min(LocalDate::compareTo).get();
            LocalDate uso = validos.stream().map(Datos::uso).max(LocalDate::compareTo).get();
            datos = new Datos(ini, uso);
        } else if (alterado) {
            return new Resultado(Estado.VENCIDA, 0, "Los datos de la prueba fueron alterados");
        } else {
            datos = new Datos(hoy, hoy); // primer arranque
        }

        // 2. Reloj atrasado
        if (hoy.isBefore(datos.uso().minusDays(1))) {
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
                return new Resultado(Estado.LICENCIADA, ChronoUnit.DAYS.between(hoy, vence.get()), "");
            }
        }

        // 4. Periodo de prueba
        long dias = ChronoUnit.DAYS.between(hoy, datos.inicio().plusMonths(MESES_PRUEBA));
        if (dias >= 0) {
            return new Resultado(Estado.PRUEBA, dias, "");
        }
        return new Resultado(Estado.VENCIDA, 0, "El periodo de prueba terminó");
    }

    /** Devuelve true y guarda la clave si es válida para ESTE equipo y no ha vencido. */
    public boolean activar(String clave) {
        if (clave == null) return false;
        clave = clave.trim();
        if (validarClave(clave).isEmpty()) return false;
        prefs.put(K_LICENCIA, clave);
        for (Path p : archivosLicencia) escribirArchivo(p, clave);
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
            if (p.length != 2) return Optional.empty();
            Base64.Decoder dec = Base64.getUrlDecoder();
            byte[] payload = dec.decode(p[0]);
            byte[] firma = dec.decode(p[1]);

            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(CLAVE_PUBLICA))));
            s.update(payload);
            if (!s.verify(firma)) return Optional.empty();

            String[] d = new String(payload, StandardCharsets.UTF_8).split("\\|");
            if (d.length != 2 || !d[0].equals(codigoEquipo())) return Optional.empty();

            LocalDate vence = LocalDate.parse(d[1]);
            return vence.isBefore(LocalDate.now()) ? Optional.empty() : Optional.of(vence);
        } catch (Exception e) {
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
            if (p.length != 3) return null;
            if (!hmac(p[0] + "|" + p[1]).equals(p[2])) return null;
            return new Datos(LocalDate.parse(p[0]), LocalDate.parse(p[1]));
        } catch (Exception e) {
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
            throw new IllegalStateException(e);
        }
    }

    private byte[] sha256(byte[] datos) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (Exception e) {
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
        } catch (Exception ignored) { }
        return System.getenv().getOrDefault("COMPUTERNAME", "?") + "|" + System.getProperty("user.name");
    }

    private String leerArchivo(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void escribirArchivo(Path p, String contenido) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, contenido);
        } catch (Exception ignored) { }
    }
}