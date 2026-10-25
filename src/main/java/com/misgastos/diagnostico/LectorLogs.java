package com.misgastos.diagnostico;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/**
 * Lee los archivos de log de MisGastos SIN cargarlos completos en memoria.
 *
 * Formato esperado (el de logback-spring.xml, LOG_PATTERN):
 *   2026-10-24 10:37:10.112 ERROR [hilo] com.misgastos.clase - mensaje
 * Las líneas que no empiezan así (excepción, "at ...") se unen al registro anterior.
 *
 * Solo se conserva en memoria la página pedida (TAM_PAGINA registros).
 * No depende de JavaFX ni de la lógica de negocio.
 */
@Slf4j
@Service
public class LectorLogs {

    public static final int TAM_PAGINA = 100;

    private static final int MAX_LINEAS_DETALLE = 300;
    private static final String ARCHIVO_ACTUAL = "misgastos.log";

    private static final Pattern CABECERA = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) (TRACE|DEBUG|INFO|WARN|ERROR)\\s+\\[(.*?)\\] (\\S+) - (.*)$");
    private static final Pattern ARCHIVADO = Pattern.compile(
            "^misgastos-(\\d{4}-\\d{2}-\\d{2})\\.(\\d+)\\.log\\.gz$");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Orden cronológico: archivados por fecha e índice; el archivo activo al final. */
    private static final Comparator<ArchivoLog> ORDEN_LECTURA =
            Comparator.comparing(ArchivoLog::fecha, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                    .thenComparingInt(ArchivoLog::indice);

    /** Orden para mostrar: el activo primero, luego los archivados del más nuevo al más viejo. */
    private static final Comparator<ArchivoLog> ORDEN_LISTA =
            Comparator.comparing(ArchivoLog::fecha, Comparator.nullsFirst(Comparator.<LocalDate>reverseOrder()))
                    .thenComparing(ArchivoLog::indice, Comparator.<Integer>reverseOrder());

    /** Misma carpeta que LOG_DIR en logback-spring.xml. Si cambias una, cambia la otra. */
    public Path carpeta() {
        return Paths.get(System.getProperty("user.home"), "MisGastos", "logs");
    }

    public List<ArchivoLog> listarArchivos() {
        Path dir = carpeta();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<ArchivoLog> lista = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(dir)) {
            archivos.forEach(p -> {
                String nombre = p.getFileName().toString();
                try {
                    if (nombre.equals(ARCHIVO_ACTUAL)) {
                        lista.add(new ArchivoLog(p, null, 0, false, Files.size(p)));
                    } else {
                        Matcher m = ARCHIVADO.matcher(nombre);
                        if (m.matches()) {
                            lista.add(new ArchivoLog(p, LocalDate.parse(m.group(1)),
                                    Integer.parseInt(m.group(2)), true, Files.size(p)));
                        }
                    }
                } catch (Exception ignorada) {
                    // archivo borrado o ilegible en este instante: se omite
                }
            });
        } catch (IOException e) {
            log.warn("No se pudo listar la carpeta de logs", e);
        }
        lista.sort(ORDEN_LISTA);
        return lista;
    }

    /**
     * Devuelve una página de registros, los más recientes primero.
     *
     * @param unico         un solo archivo, o null para usar todos los que caigan en el rango de fechas
     * @param pagina        0 = la más reciente
     * @param totalConocido total de la consulta anterior (evita una pasada extra); -1 si no se conoce
     * @param cancelado     se consulta en cada línea para poder abortar rápido
     */
    public PaginaLogs leer(FiltroLogs filtro, ArchivoLog unico, int pagina, int totalConocido,
                           BooleanSupplier cancelado) throws IOException {
        FiltroLogs f = normalizar(filtro);
        List<ArchivoLog> fuentes = seleccionar(f, unico);

        if (pagina <= 0) {
            // Una sola pasada: se cuenta todo y solo se guardan los últimos TAM_PAGINA
            ArrayDeque<EntradaLog> ultimos = new ArrayDeque<>();
            int[] cuenta = {0};
            recorrer(fuentes, f, cancelado, e -> {
                cuenta[0]++;
                ultimos.addLast(e);
                if (ultimos.size() > TAM_PAGINA) {
                    ultimos.removeFirst();
                }
                return true;
            });
            List<EntradaLog> pag = new ArrayList<>(ultimos);
            Collections.reverse(pag);
            return new PaginaLogs(pag, cuenta[0], 0, paginasDe(cuenta[0]), fuentes.size());
        }

        int total = totalConocido;
        if (total < 0) {
            int[] cuenta = {0};
            recorrer(fuentes, f, cancelado, e -> {
                cuenta[0]++;
                return true;
            });
            total = cuenta[0];
        }

        final int fin = total - pagina * TAM_PAGINA;          // exclusivo, en orden cronológico
        final int inicio = Math.max(0, fin - TAM_PAGINA);
        if (fin <= 0) {
            return new PaginaLogs(List.of(), total, pagina, paginasDe(total), fuentes.size());
        }

        List<EntradaLog> ventana = new ArrayList<>();
        int[] indice = {0};
        recorrer(fuentes, f, cancelado, e -> {
            int i = indice[0]++;
            if (i >= inicio && i < fin) {
                ventana.add(e);
            }
            return i + 1 < fin;      // al pasar la ventana se deja de leer
        });
        Collections.reverse(ventana);
        return new PaginaLogs(ventana, total, pagina, paginasDe(total), fuentes.size());
    }
    
    /**
     * Devuelve las últimas {@code max} líneas de un archivo (también si es .gz).
     * Lee en secuencia y solo guarda esas líneas en memoria.
     */
    public List<String> ultimasLineas(ArchivoLog archivo, int max, BooleanSupplier cancelado) throws IOException {
        ArrayDeque<String> cola = new ArrayDeque<>(max + 1);
        try (InputStream crudo = Files.newInputStream(archivo.ruta());
             InputStream in = archivo.comprimido() ? new GZIPInputStream(crudo, 65536) : crudo;
             BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536)) {

            String linea;
            while ((linea = lector.readLine()) != null) {
                if (cancelado.getAsBoolean()) {
                    break;
                }
                if (cola.size() == max) {
                    cola.removeFirst();
                }
                cola.addLast(linea.length() > 2000 ? linea.substring(0, 2000) + "..." : linea);
            }
        } catch (NoSuchFileException e) {
            return List.of();      // la rotación lo movió mientras se leía
        } catch (EOFException | ZipException e) {
            log.warn("El archivo {} está incompleto; se muestra lo que se pudo leer", archivo.nombre());
        }
        return new ArrayList<>(cola);
    }

    // ==================== INTERNOS ====================

    private static int paginasDe(int total) {
        return Math.max(1, (total + TAM_PAGINA - 1) / TAM_PAGINA);
    }

    private FiltroLogs normalizar(FiltroLogs f) {
        String texto = (f.texto() == null || f.texto().isBlank()) ? null
                : f.texto().trim().toLowerCase(Locale.ROOT);
        String nivel = (f.nivel() == null || f.nivel().isBlank()) ? null : f.nivel();
        return new FiltroLogs(nivel, texto, f.desde(), f.hasta());
    }

    private List<ArchivoLog> seleccionar(FiltroLogs f, ArchivoLog unico) {
        if (unico != null) {
            return List.of(unico);
        }
        // El nombre de un archivo archivado trae su día: los que quedan fuera del rango ni se abren
        return listarArchivos().stream()
                .filter(a -> a.fecha() == null
                        || ((f.desde() == null || !a.fecha().isBefore(f.desde()))
                        && (f.hasta() == null || !a.fecha().isAfter(f.hasta()))))
                .sorted(ORDEN_LECTURA)
                .toList();
    }

    private void recorrer(List<ArchivoLog> fuentes, FiltroLogs f, BooleanSupplier cancelado,
                          Predicate<EntradaLog> consumidor) throws IOException {
        for (ArchivoLog a : fuentes) {
            if (cancelado.getAsBoolean() || !recorrerArchivo(a, f, cancelado, consumidor)) {
                return;
            }
        }
    }

    /** Devuelve false si hay que dejar de leer (cancelado o el consumidor ya tiene lo que necesita). */
    private boolean recorrerArchivo(ArchivoLog a, FiltroLogs f, BooleanSupplier cancelado,
                                    Predicate<EntradaLog> consumidor) throws IOException {
        try (InputStream crudo = Files.newInputStream(a.ruta());
             InputStream in = a.comprimido() ? new GZIPInputStream(crudo, 65536) : crudo;
             BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536)) {

            Registro actual = null;
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (cancelado.getAsBoolean()) {
                    return false;
                }
                Matcher m = CABECERA.matcher(linea);
                if (m.matches()) {
                    if (actual != null && !entregar(actual, f, a, consumidor)) {
                        return false;
                    }
                    actual = abrir(m, f);
                } else if (actual != null) {
                    actual.agregarLinea(linea);
                }
            }
            return actual == null || entregar(actual, f, a, consumidor);

        } catch (NoSuchFileException e) {
            return true;   // la rotación lo movió o lo borró mientras se leía
        } catch (EOFException | ZipException e) {
            log.warn("El archivo {} está incompleto o dañado; se muestra lo que se pudo leer", a.nombre());
            return true;   // p. ej. un .gz que todavía se está comprimiendo
        }
    }

    private Registro abrir(Matcher m, FiltroLogs f) {
        LocalDateTime fecha = LocalDateTime.parse(m.group(1) + " " + m.group(2), FECHA_HORA);
        String nivel = m.group(3);
        LocalDate dia = fecha.toLocalDate();
        boolean descartado = (f.nivel() != null && !f.nivel().equals(nivel))
                || (f.desde() != null && dia.isBefore(f.desde()))
                || (f.hasta() != null && dia.isAfter(f.hasta()));
        return new Registro(fecha, nivel, m.group(4), m.group(5), m.group(6), descartado);
    }

    private boolean entregar(Registro r, FiltroLogs f, ArchivoLog a, Predicate<EntradaLog> consumidor) {
        if (r.descartado) {
            return true;
        }
        if (f.texto() != null) {
            String t = f.texto();
            boolean coincide = contiene(r.mensaje, t) || contiene(r.clase, t) || contiene(r.detalle.toString(), t);
            if (!coincide) {
                return true;
            }
        }
        return consumidor.test(new EntradaLog(r.fecha, r.nivel, r.hilo, r.clase, r.mensaje,
                r.detalle.toString(), a.ruta()));
    }

    private static boolean contiene(String texto, String buscado) {
        return texto.toLowerCase(Locale.ROOT).contains(buscado);
    }

    /** Registro en construcción: la cabecera más las líneas de traza que le siguen. */
    private static final class Registro {
        final LocalDateTime fecha;
        final String nivel;
        final String hilo;
        final String clase;
        final String mensaje;
        final boolean descartado;
        final StringBuilder detalle = new StringBuilder();
        int lineas;

        Registro(LocalDateTime fecha, String nivel, String hilo, String clase, String mensaje, boolean descartado) {
            this.fecha = fecha;
            this.nivel = nivel;
            this.hilo = hilo;
            this.clase = clase;
            this.mensaje = mensaje;
            this.descartado = descartado;
        }

        void agregarLinea(String linea) {
            if (descartado) {
                return;   // no se acumula lo que no se va a mostrar
            }
            if (lineas < MAX_LINEAS_DETALLE) {
                if (lineas > 0) {
                    detalle.append('\n');
                }
                detalle.append(linea);
            } else if (lineas == MAX_LINEAS_DETALLE) {
                detalle.append("\n... (traza recortada)");
            }
            lineas++;
        }
    }
}
