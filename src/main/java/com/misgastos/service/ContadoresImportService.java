package com.misgastos.service;

import com.misgastos.model.Lectura;
import com.misgastos.model.Maquina;
import com.misgastos.repository.MaquinaRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa lecturas de contadores desde un Excel con los títulos:
 * Fecha (fecha_1, fecha_2, fecha_3), COD, DEN, Contador_In, Contador_Out y Prem(Jackpot).
 * El mes y el año los elige el usuario: periodo 1 = último día del mes anterior,
 * periodo 2 = día 16, periodo 3 = último día del mes.
 *
 * Paso 1: analizar() solo lee y clasifica, no guarda nada.
 * Paso 2: aplicar() guarda cada lectura con LecturaService.registrar (mismas validaciones
 * y auditoría que al ingresar a mano).
 */
@Service
public class ContadoresImportService {

    private static final Logger log = LoggerFactory.getLogger(ContadoresImportService.class);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private LecturaService lecturaService;

    @Autowired
    private AuditoriaService auditoriaService;

    public record FilaExcel(int fila, long codigo, int periodo, BigDecimal den, long in, long out, long prem) { }

    public record Accion(Long maquinaId, FilaExcel f, LocalDate fecha, boolean reinicio) { }

    public record VistaPrevia(List<Accion> porInsertar, int iguales, int reinicios,
                              List<String> conflictos, List<String> bajos,
                              List<Long> noEncontradas, List<String> problemas) { }

    public record Resultado(int importadas, int reinicios, int denominacionesAjustadas, List<String> errores) { }

    public static LocalDate fechaPeriodo(int periodo, int anio, int mes) {
        LocalDate primero = LocalDate.of(anio, mes, 1);
        return switch (periodo) {
            case 1 -> primero.minusDays(1);
            case 2 -> primero.withDayOfMonth(16);
            default -> primero.withDayOfMonth(primero.lengthOfMonth());
        };
    }

    public VistaPrevia analizar(File archivo, int anio, int mes) throws Exception {
        List<String> problemas = new ArrayList<>();
        Map<Long, List<FilaExcel>> porMaquina = new TreeMap<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook libro = WorkbookFactory.create(archivo, null, true)) {
            Sheet hoja = libro.getSheetAt(0);
            int primera = hoja.getFirstRowNum();
            Row encabezado = hoja.getRow(primera);

            int cFecha = -1, cCod = -1, cDen = -1, cIn = -1, cOut = -1, cPrem = -1;
            if (encabezado != null) {
                for (Cell c : encabezado) {
                    String k = clave(fmt.formatCellValue(c));
                    int idx = c.getColumnIndex();
                    if (cFecha < 0 && k.equals("FECHA")) cFecha = idx;
                    else if (cCod < 0 && (k.equals("COD") || k.equals("CODIGO"))) cCod = idx;
                    else if (cDen < 0 && k.startsWith("DEN")) cDen = idx;
                    else if (cIn < 0 && (k.equals("IN") || k.equals("CONTADORIN"))) cIn = idx;
                    else if (cOut < 0 && (k.equals("OUT") || k.equals("CONTADOROUT"))) cOut = idx;
                    else if (cPrem < 0 && (k.startsWith("PREM") || k.equals("CONTADORPREM"))) cPrem = idx;
                }
            }
            if (cFecha < 0 || cCod < 0 || cDen < 0 || cIn < 0 || cOut < 0) {
                throw new Exception("La primera fila debe tener los títulos: Fecha, COD, DEN, Contador_In "
                        + "y Contador_Out (y Prem(Jackpot)).");
            }

            for (int i = primera + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;
                int numero = i + 1;

                String tFecha = texto(fila, cFecha, fmt);
                String tCod = texto(fila, cCod, fmt);
                if (tFecha.isEmpty() && tCod.isEmpty()) continue; // fila en blanco

                Integer periodo = periodoDe(tFecha);
                if (periodo == null) {
                    problemas.add("Fila " + numero + ": la columna Fecha debe ser fecha_1, fecha_2 o fecha_3 ('" + tFecha + "')");
                    continue;
                }
                Long codigo = entero(fila, cCod, fmt);
                if (codigo == null || codigo <= 0) {
                    problemas.add("Fila " + numero + ": código vacío o inválido ('" + tCod + "')");
                    continue;
                }
                BigDecimal den = decimal(fila, cDen, fmt);
                if (den == null || den.signum() <= 0) {
                    problemas.add("Fila " + numero + ": denominación inválida (código " + codigo + ")");
                    continue;
                }
                Long in = entero(fila, cIn, fmt);
                Long out = entero(fila, cOut, fmt);
                Long prem = entero(fila, cPrem, fmt);
                if (in == null || out == null || prem == null) {
                    problemas.add("Fila " + numero + ": contador inválido, no es un número (código " + codigo + ")");
                    continue;
                }

                List<FilaExcel> lista = porMaquina.computeIfAbsent(codigo, k -> new ArrayList<>());
                if (lista.stream().anyMatch(x -> x.periodo() == periodo)) {
                    problemas.add("Fila " + numero + ": la máquina " + codigo + " ya tiene el periodo " + periodo
                            + " en el archivo");
                    continue;
                }
                lista.add(new FilaExcel(numero, codigo, periodo, den, in, out, prem));
            }
        }

        List<Accion> porInsertar = new ArrayList<>();
        List<String> conflictos = new ArrayList<>();
        List<String> bajos = new ArrayList<>();
        List<Long> noEncontradas = new ArrayList<>();
        int iguales = 0;
        int reinicios = 0;

        for (Map.Entry<Long, List<FilaExcel>> e : porMaquina.entrySet()) {
            Long codigo = e.getKey();
            Optional<Maquina> om = maquinaRepository.findByCodigo(codigo);
            if (om.isEmpty()) {
                noEncontradas.add(codigo);
                continue;
            }
            Maquina m = om.get();
            if (!m.isActiva()) {
                problemas.add("Máquina " + codigo + ": está de baja, no se importó");
                continue;
            }

            List<FilaExcel> filas = e.getValue();
            filas.sort(Comparator.comparingInt(FilaExcel::periodo));
            List<Lectura> historial = lecturaService.historial(m.getId()).stream()
                    .filter(l -> !l.isAnulada()).toList();

            // Estado de la última lectura, que se va "avanzando" con lo que se piensa insertar
            Lectura ult = lecturaService.ultimaLectura(m.getId());
            boolean hay = ult != null;
            long sIn = hay ? ult.getContadorIn() : 0;
            long sOut = hay ? ult.getContadorOut() : 0;
            long sPrem = hay ? ult.getContadorPrem() : 0;
            LocalDate sFecha = hay ? ult.getFecha() : null;
            int sPeriodo = hay ? ult.getPeriodo() : 0;

            for (FilaExcel f : filas) {
                LocalDate fecha = fechaPeriodo(f.periodo(), anio, mes);

                Lectura existente = historial.stream()
                        .filter(l -> l.getPeriodo() == f.periodo() && l.getFecha().equals(fecha))
                        .findFirst().orElse(null);
                if (existente != null) {
                    if (existente.getContadorIn() == f.in() && existente.getContadorOut() == f.out()
                            && existente.getContadorPrem() == f.prem()) {
                        iguales++;
                    } else {
                        conflictos.add("Máquina " + codigo + ", periodo " + f.periodo() + " (" + fecha.format(DMY)
                                + "): ya tiene In " + n(existente.getContadorIn()) + " / Out " + n(existente.getContadorOut())
                                + " / Prem " + n(existente.getContadorPrem()) + "; el Excel trae In " + n(f.in())
                                + " / Out " + n(f.out()) + " / Prem " + n(f.prem()));
                    }
                    continue;
                }

                if (hay && fecha.isBefore(sFecha)) {
                    problemas.add("Máquina " + codigo + ", periodo " + f.periodo() + ": la fecha " + fecha.format(DMY)
                            + " es anterior a su última lectura (" + sFecha.format(DMY) + "). Se omiten las siguientes de esa máquina.");
                    break;
                }
                int esperado = hay ? sPeriodo % 3 + 1 : f.periodo();
                if (f.periodo() != esperado) {
                    problemas.add("Máquina " + codigo + ": le toca el periodo " + esperado + " y el Excel trae el "
                            + f.periodo() + ". Se omiten esa y las siguientes de esa máquina.");
                    break;
                }

                boolean reinicio = false;
                boolean menor = hay && (f.in() < sIn || f.out() < sOut || f.prem() < sPrem);
                if (menor) {
                    if (f.in() == 0 && f.out() == 0) {
                        reinicio = true;
                    } else {
                        bajos.add("Máquina " + codigo + ", periodo " + f.periodo() + ": contador bajo, revisar si se "
                                + "digitó mal un número. Anterior: In " + n(sIn) + " / Out " + n(sOut) + " / Prem " + n(sPrem)
                                + ". Excel: In " + n(f.in()) + " / Out " + n(f.out()) + " / Prem " + n(f.prem())
                                + ". Se omiten esa y las siguientes de esa máquina.");
                        break;
                    }
                }

                porInsertar.add(new Accion(m.getId(), f, fecha, reinicio));
                if (reinicio) reinicios++;
                hay = true;
                sIn = f.in();
                sOut = f.out();
                sPrem = f.prem();
                sFecha = fecha;
                sPeriodo = f.periodo() == 3 ? 1 : f.periodo(); // al cerrar el 3 queda la copia en periodo 1
            }
        }

        log.debug("Análisis del Excel de contadores: {} por insertar, {} iguales, {} conflictos, {} bajos, {} máquinas no encontradas",
                porInsertar.size(), iguales, conflictos.size(), bajos.size(), noEncontradas.size());
        return new VistaPrevia(porInsertar, iguales, reinicios, conflictos, bajos, noEncontradas, problemas);
    }

    /** No es @Transactional a propósito: cada lectura se guarda sola y un error en una no frena las demás máquinas. */
    public Resultado aplicar(VistaPrevia vista) {
        int importadas = 0;
        int reinicios = 0;
        int denominaciones = 0;
        List<String> errores = new ArrayList<>();
        Set<Long> fallidas = new HashSet<>();

        for (Accion a : vista.porInsertar()) {
            FilaExcel f = a.f();
            if (fallidas.contains(a.maquinaId())) {
                errores.add("Máquina " + f.codigo() + ", periodo " + f.periodo() + ": omitida porque falló la lectura anterior");
                continue;
            }
            try {
                Lectura l = lecturaService.registrar(a.maquinaId(), a.fecha(), f.in(), f.out(), f.prem(),
                        a.reinicio(), f.periodo());
                // La denominación del Excel es la que cuenta para los cálculos de esa lectura
                if (l.getDenominacion().compareTo(f.den()) != 0) {
                    lecturaService.editarDenominacion(l.getId(), f.den());
                    denominaciones++;
                }
                importadas++;
                if (a.reinicio()) reinicios++;
            } catch (Exception e) {
                fallidas.add(a.maquinaId());
                log.warn("No se pudo importar una lectura: {}", e.getMessage());
                errores.add("Máquina " + f.codigo() + ", periodo " + f.periodo() + ": " + e.getMessage());
            }
        }

        log.info("Importación de contadores: {} lecturas importadas, {} con error", importadas, errores.size());
        auditoriaService.registrarCambio("IMPORTAR_CONTADORES_EXCEL",
                "Lecturas importadas desde Excel: " + importadas + " (reinicios: " + reinicios + ", errores: " + errores.size() + ")");
        return new Resultado(importadas, reinicios, denominaciones, errores);
    }

    // ---------------- ayudas ----------------

    private static String n(long v) {
        return String.format(Locale.forLanguageTag("es-CO"), "%,d", v);
    }

    private static String clave(String t) {
        String s = Normalizer.normalize(t == null ? "" : t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static String texto(Row fila, int col, DataFormatter fmt) {
        if (col < 0) return "";
        Cell c = fila.getCell(col);
        if (c == null) return "";
        return fmt.formatCellValue(c).replaceAll("\\s+", " ").trim();
    }

    /** fecha_1, fecha_2, fecha_3 -> 1, 2, 3; null si no es válido. */
    private static Integer periodoDe(String t) {
        Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(t == null ? "" : t);
        if (!m.find() || m.group(1).length() > 1) return null;
        int p = Integer.parseInt(m.group(1));
        return p >= 1 && p <= 3 ? p : null;
    }

    /** Número entero no negativo. Vacío o "-" = 0. null si no es un número válido. */
    private static Long entero(Row fila, int col, DataFormatter fmt) {
        if (col < 0) return 0L;
        Cell c = fila.getCell(col);
        if (c == null) return 0L;
        CellType tipo = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (tipo == CellType.NUMERIC) {
            try {
                BigDecimal b = BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros();
                if (b.signum() < 0 || b.scale() > 0) return null;
                return b.toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                return null;
            }
        }
        String s = fmt.formatCellValue(c).trim().replaceAll("[$.,\\s]", "");
        if (s.isEmpty() || s.equals("-")) return 0L;
        if (!s.matches("\\d{1,15}")) return null;
        return Long.parseLong(s);
    }

    private static BigDecimal decimal(Row fila, int col, DataFormatter fmt) {
        Cell c = fila.getCell(col);
        if (c == null) return null;
        CellType tipo = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        try {
            if (tipo == CellType.NUMERIC) {
                return BigDecimal.valueOf(c.getNumericCellValue());
            }
            String s = fmt.formatCellValue(c).trim().replace("$", "").replaceAll("\\s", "").replace(',', '.');
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}