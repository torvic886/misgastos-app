package com.misgastos;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Red de seguridad del registro: guarda en un archivo lo que Logback todavía no puede ver.
 *
 * El registro principal lo hace Logback (logback-spring.xml) en misgastos.log. Esta clase
 * cubre lo que queda fuera de él:
 *  - Lo que ocurre ANTES de que Spring y Logback arranquen (preparar la base, fallos al abrir).
 *  - Los System.out.println / printStackTrace que todavía no se han cambiado por log.info / log.error.
 *  - Los mensajes propios de Logback si su configuración tuviera un error.
 *
 * Archivo: %USERPROFILE%\MisGastos\logs\consola-AAAA-MM-DD.log
 * Límites: se conservan los últimos 7 archivos y cada día se guardan como máximo 5 MB
 * (al pasar el límite solo se siguen guardando los errores).
 *
 * También envía los errores no controlados de cualquier hilo (incluida la ventana) al registro
 * principal, para que aparezcan en la pantalla de diagnóstico.
 *
 * Nunca impide que el programa abra: si algo falla aquí, simplemente no se registra.
 */
public final class RegistroLog {

    private static final int ARCHIVOS_A_CONSERVAR = 7;
    private static final long LIMITE_BYTES_DIA = 5L * 1024 * 1024;
    private static final String PREFIJO = "consola-";
    private static final DateTimeFormatter MARCA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m");

    private static BufferedWriter escritor;
    private static long bytesDelDia;
    private static boolean limiteAvisado;

    private RegistroLog() {
    }

    /** Carpeta donde quedan todos los archivos de registro (también los de Logback). */
    public static Path carpeta() {
        return Paths.get(System.getProperty("user.home"), "MisGastos", "logs");
    }

    /** Se llama una sola vez, al principio de main(). */
    public static synchronized void iniciar() {
        if (escritor != null) {
            return;
        }
        try {
            Path carpeta = carpeta();
            Files.createDirectories(carpeta);
            limpiarAntiguos(carpeta);

            Path archivo = carpeta.resolve(PREFIJO + LocalDate.now() + ".log");
            bytesDelDia = Files.exists(archivo) ? Files.size(archivo) : 0;
            escritor = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // Lo que se imprima se sigue mostrando en la consola (útil en desarrollo) y además se guarda
            PrintStream consolaOut = System.out;
            PrintStream consolaErr = System.err;
            System.setOut(new PrintStream(new FlujoLineas(consolaOut, false), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FlujoLineas(consolaErr, true), true, StandardCharsets.UTF_8));

            Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
                try {
                    // Va al registro principal (misgastos.log), el que lee la pantalla de diagnóstico
                    org.slf4j.LoggerFactory.getLogger("com.misgastos.ErrorNoControlado")
                            .error("Error no controlado en el hilo '{}'", hilo.getName(), error);
                } catch (Throwable t) {
                    System.err.println("ERROR NO CONTROLADO en el hilo '" + hilo.getName() + "':");
                    error.printStackTrace();
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> escribir("===== FIN DE MISGASTOS =====", false), "cierre-registro"));

            escribir("===== INICIO DE MISGASTOS =====", false);
            escribir("Java " + System.getProperty("java.version") + " | "
                    + System.getProperty("os.name") + " " + System.getProperty("os.version"), false);
        } catch (Exception e) {
            // El registro nunca debe impedir que el programa abra
            escritor = null;
        }
    }

    /** Escribe una línea con fecha y hora. Los errores siempre se guardan, aunque se pase el límite diario. */
    private static synchronized void escribir(String texto, boolean esError) {
        if (escritor == null) {
            return;
        }
        try {
            String mensaje = texto;
            if (bytesDelDia > LIMITE_BYTES_DIA && !esError) {
                if (limiteAvisado) {
                    return;
                }
                limiteAvisado = true;
                mensaje = "Se alcanzó el límite diario de este archivo: desde aquí solo se guardan errores.";
            }
            String linea = LocalDateTime.now().format(MARCA) + (esError ? " [ERR] " : " ") + mensaje;
            escritor.write(linea);
            escritor.newLine();
            escritor.flush();
            bytesDelDia += linea.length() + 2;
        } catch (IOException e) {
            // No se puede registrar: se ignora
        }
    }

    /** Borra los archivos más viejos; el nombre lleva la fecha, así que ordenar por nombre basta. */
    private static void limpiarAntiguos(Path carpeta) {
        try (Stream<Path> archivos = Files.list(carpeta)) {
            List<Path> logs = archivos
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(PREFIJO) && n.endsWith(".log");
                    })
                    .sorted()
                    .toList();
            int sobrantes = logs.size() - ARCHIVOS_A_CONSERVAR;
            for (int i = 0; i < sobrantes; i++) {
                Files.deleteIfExists(logs.get(i));
            }
        } catch (Exception e) {
            // No es crítico
        }
    }

    /** Recibe lo que imprime el programa, línea por línea: lo muestra en la consola y lo guarda en el archivo. */
    private static final class FlujoLineas extends OutputStream {
        private final PrintStream consola;
        private final boolean esError;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        FlujoLineas(PrintStream consola, boolean esError) {
            this.consola = consola;
            this.esError = esError;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                vaciarLinea();
            } else {
                buffer.write(b);
            }
        }

        private void vaciarLinea() {
            String linea = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            if (linea.endsWith("\r")) {
                linea = linea.substring(0, linea.length() - 1);
            }
            consola.println(linea);
            // Sin los colores ANSI que a veces agrega Spring en la consola
            escribir(ANSI.matcher(linea).replaceAll(""), esError);
        }
    }
}
