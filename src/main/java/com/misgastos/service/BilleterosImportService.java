package com.misgastos.service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.misgastos.model.Billetero;
import com.misgastos.repository.BilleteroRepository;
import com.misgastos.service.diseno.Diseno;

/**
 * Importa el historial de billeteros desde un Excel de un mes (Fecha, Billeteros, Premios).
 * analizar() solo clasifica y no guarda nada; aplicar() crea únicamente los registros nuevos.
 * La diferencia, la acumulada y el promedio no se leen del Excel: la app los calcula.
 */
@Service
public class BilleterosImportService {

    private static final Logger log = LoggerFactory.getLogger(BilleterosImportService.class);
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter[] FORMATOS = {
        DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("yyyy-MM-dd")};

    @Autowired
    private BilleteroRepository billeteroRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public record Nuevo(LocalDate fecha, BigDecimal billetero, BigDecimal premios) {
    }

    public record Conflicto(LocalDate fecha, BigDecimal billeteroExcel, BigDecimal premiosExcel,
            BigDecimal billeteroActual, BigDecimal premiosActual) {
    }

    public record VistaPrevia(YearMonth mes, int filasLeidas, List<Nuevo> nuevos, List<LocalDate> iguales,
            List<Conflicto> conflictos, List<String> problemas) {
    }

    // ==================== ANALIZAR ====================

    @Transactional(readOnly = true)
    public VistaPrevia analizar(File archivo) throws Exception {
        log.debug("Importar billeteros: leyendo {}", archivo.getName());
        List<Nuevo> nuevos = new ArrayList<>();
        List<LocalDate> iguales = new ArrayList<>();
        List<Conflicto> conflictos = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        YearMonth mes = null;
        int leidas = 0;

        try (Workbook libro = WorkbookFactory.create(archivo, null, true)) {
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(new Locale("es", "CO"));

            int filaTitulos = -1;
            int colFecha = -1;
            int colBill = -1;
            int colPrem = -1;
            for (int r = 0; r <= Math.min(hoja.getLastRowNum(), 5) && filaTitulos < 0; r++) {
                Row row = hoja.getRow(r);
                if (row == null) {
                    continue;
                }
                int f = -1;
                int b = -1;
                int p = -1;
                for (Cell c : row) {
                    String k = clave(fmt.formatCellValue(c));
                    if (k.equals("fecha")) {
                        f = c.getColumnIndex();
                    } else if (k.startsWith("billetero")) {
                        b = c.getColumnIndex();
                    } else if (k.equals("premios")) {
                        p = c.getColumnIndex();
                    }
                }
                if (f >= 0 && b >= 0 && p >= 0) {
                    filaTitulos = r;
                    colFecha = f;
                    colBill = b;
                    colPrem = p;
                }
            }
            if (filaTitulos < 0) {
                throw new IllegalArgumentException(
                    "No encontré los títulos Fecha, Billeteros y Premios en las primeras filas del Excel.");
            }

            Set<LocalDate> vistas = new HashSet<>();
            for (int r = filaTitulos + 1; r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null) {
                    continue;
                }
                Cell cf = row.getCell(colFecha);
                Cell cb = row.getCell(colBill);
                Cell cp = row.getCell(colPrem);
                if (vacia(cf, fmt) && vacia(cb, fmt) && vacia(cp, fmt)) {
                    continue;
                }
                leidas++;
                int fila = r + 1;

                LocalDate fecha = leerFecha(cf);
                BigDecimal bill = leerPesos(cb);
                BigDecimal prem = leerPesos(cp);
                if (fecha == null) {
                    problemas.add("Fila " + fila + ": la fecha no es válida");
                    continue;
                }
                if (bill == null || prem == null) {
                    problemas.add("Fila " + fila + " (" + DMY.format(fecha)
                        + "): Billeteros o Premios está vacío o no es un número");
                    continue;
                }
                if (bill.signum() < 0 || prem.signum() < 0) {
                    problemas.add("Fila " + fila + " (" + DMY.format(fecha) + "): valores negativos, no se importó");
                    continue;
                }

                YearMonth m = YearMonth.from(fecha);
                if (mes == null) {
                    mes = m;
                }
                if (!m.equals(mes)) {
                    problemas.add("Fila " + fila + " (" + DMY.format(fecha) + "): es de otro mes, el archivo debe traer "
                        + etiqueta(mes) + ". No se importó");
                    continue;
                }
                if (!vistas.add(fecha)) {
                    problemas.add("Fila " + fila + " (" + DMY.format(fecha) + "): fecha repetida en el Excel, no se importó");
                    continue;
                }

                List<Billetero> existentes = billeteroRepository.findByFecha(fecha);
                if (existentes.isEmpty()) {
                    nuevos.add(new Nuevo(fecha, bill, prem));
                } else if (existentes.stream().anyMatch(e -> e.getBilletero().compareTo(bill) == 0
                        && e.getPremios().compareTo(prem) == 0)) {
                    iguales.add(fecha);
                } else {
                    Billetero actual = existentes.get(0);
                    conflictos.add(new Conflicto(fecha, bill, prem, actual.getBilletero(), actual.getPremios()));
                }
            }
        }

        log.info("Importar billeteros: análisis terminado ({} nuevos, {} iguales, {} conflictos, {} problemas)",
            nuevos.size(), iguales.size(), conflictos.size(), problemas.size());
        if (!conflictos.isEmpty()) {
            log.warn("Importar billeteros: {} fecha(s) con valores distintos a los ya registrados", conflictos.size());
        }
        return new VistaPrevia(mes, leidas, nuevos, iguales, conflictos, problemas);
    }

    // ==================== APLICAR ====================

    /** Crea solo los registros nuevos. Devuelve cuántos guardó. */
    @Transactional
    public int aplicar(VistaPrevia vista) {
        List<Billetero> aGuardar = new ArrayList<>();
        for (Nuevo n : vista.nuevos()) {
            aGuardar.add(new Billetero(n.fecha(), n.billetero(), n.premios()));
        }
        billeteroRepository.saveAll(aGuardar);
        log.info("Billeteros importados desde Excel: {}", aGuardar.size());
        auditoriaService.registrarCambio("IMPORTAR_BILLETEROS",
            "Billeteros importados desde Excel: " + aGuardar.size() + " registro(s) de "
                + (vista.mes() == null ? "?" : etiqueta(vista.mes())));
        return aGuardar.size();
    }

    // ==================== TEXTO DEL RESUMEN ====================

    public String textoResumen(VistaPrevia v) {
        StringBuilder sb = new StringBuilder();
        sb.append("MES: ").append(v.mes() == null ? "(no se encontraron fechas válidas)" : etiqueta(v.mes()))
            .append("\n");
        sb.append("Filas leídas del Excel: ").append(v.filasLeidas()).append("\n\n");

        BigDecimal bill = BigDecimal.ZERO;
        BigDecimal prem = BigDecimal.ZERO;
        for (Nuevo n : v.nuevos()) {
            bill = bill.add(n.billetero());
            prem = prem.add(n.premios());
        }
        sb.append("• Nuevos (se van a crear): ").append(v.nuevos().size()).append("\n");
        if (!v.nuevos().isEmpty()) {
            sb.append("    Billeteros ").append(Diseno.moneda(bill)).append("  |  Premios ")
                .append(Diseno.moneda(prem)).append("\n");
        }

        sb.append("\n• Ya existían con los mismos valores (se dejan igual): ").append(v.iguales().size()).append("\n");
        if (!v.iguales().isEmpty()) {
            sb.append("    AVISO: estas fechas ya estaban registradas y no se cambiaron: ")
                .append(v.iguales().stream().map(DM::format).collect(Collectors.joining(", "))).append("\n");
        }

        sb.append("\n• En conflicto, valores distintos (NO se tocan): ").append(v.conflictos().size()).append("\n");
        for (Conflicto c : v.conflictos()) {
            sb.append("    ").append(DM.format(c.fecha())).append("  Excel: Billeteros ")
                .append(Diseno.moneda(c.billeteroExcel())).append(", Premios ").append(Diseno.moneda(c.premiosExcel()))
                .append("\n          En la app: Billeteros ").append(Diseno.moneda(c.billeteroActual()))
                .append(", Premios ").append(Diseno.moneda(c.premiosActual())).append("\n");
        }

        sb.append("\n• Con problemas (no se importan): ").append(v.problemas().size()).append("\n");
        for (String p : v.problemas()) {
            sb.append("    ").append(p).append("\n");
        }
        return sb.toString();
    }

    // ==================== LECTURA DE CELDAS ====================

    public static String etiqueta(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")).toUpperCase(new Locale("es"))
            + " " + mes.getYear();
    }

    private static boolean vacia(Cell c, DataFormatter fmt) {
        return c == null || fmt.formatCellValue(c).isBlank();
    }

    private static CellType tipo(Cell c) {
        return c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    }

    private static LocalDate leerFecha(Cell c) {
        if (c == null) {
            return null;
        }
        CellType t = tipo(c);
        if (t == CellType.NUMERIC) {
            if (!DateUtil.isCellDateFormatted(c)) {
                return null;
            }
            try {
                return c.getLocalDateTimeCellValue().toLocalDate();
            } catch (Exception e) {
                return null;
            }
        }
        if (t == CellType.STRING) {
            String s = c.getStringCellValue().trim();
            for (DateTimeFormatter f : FORMATOS) {
                try {
                    return LocalDate.parse(s, f);
                } catch (DateTimeParseException e) {
                    // se prueba el siguiente formato
                }
            }
        }
        return null;
    }

    /** Pesos enteros. Acepta números y textos como "$ 34.509.000". Un guion solo vale 0. */
    private static BigDecimal leerPesos(Cell c) {
        if (c == null) {
            return null;
        }
        CellType t = tipo(c);
        if (t == CellType.NUMERIC) {
            return BigDecimal.valueOf(c.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (t == CellType.STRING) {
            String s = c.getStringCellValue().trim();
            if (s.equals("-") || s.equals("–")) {
                return BigDecimal.ZERO.setScale(2);
            }
            String limpio = s.replaceAll("[^0-9-]", "");
            if (limpio.isEmpty() || limpio.equals("-")) {
                return null;
            }
            try {
                return new BigDecimal(limpio).setScale(2);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String clave(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).trim();
    }
}