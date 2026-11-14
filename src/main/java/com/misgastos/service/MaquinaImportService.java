package com.misgastos.service;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Importa máquinas desde un Excel. Primera fila con títulos:
 * COD, DEN, NOMBRE, SERIAL, JUEGO y, opcionales, CONTADOR_IN, CONTADOR_OUT, PREM(Jackpot).
 * Paso 1: analizar() solo lee y clasifica, no guarda nada.
 * Paso 2: aplicar() crea cada máquina con MaquinaService.crear (que crea su lectura INICIAL).
 */
@Service
public class MaquinaImportService {

    private static final Logger log = LoggerFactory.getLogger(MaquinaImportService.class);

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private MaquinaService maquinaService;

    public record FilaMaquina(int fila, Long codigo, BigDecimal denominacion, String nombre,
                              String serial, String juego, Long contadorIn, Long contadorOut,
                              Long contadorPrem) { }

    public record VistaPrevia(List<FilaMaquina> nuevas, List<Long> yaExisten,
                              List<String> problemas, boolean traeContadores) { }

    public record Resultado(int creadas, List<String> errores) { }

    public VistaPrevia analizar(File archivo) throws Exception {
        List<FilaMaquina> nuevas = new ArrayList<>();
        List<Long> yaExisten = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        boolean traeContadores = false;
        DataFormatter fmt = new DataFormatter();

        try (Workbook libro = WorkbookFactory.create(archivo, null, true)) {
            Sheet hoja = libro.getSheetAt(0);
            int primera = hoja.getFirstRowNum();
            Row encabezado = hoja.getRow(primera);

            int cCod = -1, cDen = -1, cNom = -1, cSer = -1, cJue = -1, cIn = -1, cOut = -1, cPrem = -1;
            if (encabezado != null) {
                for (Cell c : encabezado) {
                    String k = clave(fmt.formatCellValue(c));
                    int idx = c.getColumnIndex();
                    if (cCod < 0 && (k.equals("COD") || k.equals("CODIGO"))) cCod = idx;
                    else if (cDen < 0 && k.startsWith("DEN")) cDen = idx;
                    else if (cNom < 0 && k.startsWith("NOM")) cNom = idx;
                    else if (cSer < 0 && k.startsWith("SER")) cSer = idx;
                    else if (cJue < 0 && k.startsWith("JUEG")) cJue = idx;
                    else if (cIn < 0 && (k.equals("IN") || k.equals("CONTADORIN"))) cIn = idx;
                    else if (cOut < 0 && (k.equals("OUT") || k.equals("CONTADOROUT"))) cOut = idx;
                    else if (cPrem < 0 && (k.startsWith("PREM") || k.equals("CONTADORPREM"))) cPrem = idx;
                }
            }
            if (cCod < 0 || cDen < 0 || cNom < 0 || cSer < 0 || cJue < 0) {
                throw new Exception("La primera fila debe tener los títulos: COD, DEN, NOMBRE, SERIAL y JUEGO "
                        + "(y, si hay contadores: CONTADOR_IN, CONTADOR_OUT, PREM(Jackpot)).");
            }

            for (int i = primera + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;
                int numero = i + 1;

                String tCod = texto(fila, cCod, fmt);
                String tDen = texto(fila, cDen, fmt);
                String nombre = texto(fila, cNom, fmt);
                String serial = texto(fila, cSer, fmt);
                String juego = texto(fila, cJue, fmt);
                String tIn = texto(fila, cIn, fmt);
                String tOut = texto(fila, cOut, fmt);
                String tPrem = texto(fila, cPrem, fmt);

                if (tCod.isEmpty() && tDen.isEmpty() && nombre.isEmpty() && serial.isEmpty()
                        && juego.isEmpty()) continue; // fila en blanco

                Long codigo = aLong(tCod);
                if (codigo == null || codigo <= 0) {
                    problemas.add("Fila " + numero + ": código vacío o inválido ('" + tCod + "')");
                    continue;
                }
                if (!vistos.add(codigo)) {
                    problemas.add("Fila " + numero + ": código repetido en el archivo (" + codigo + ")");
                    continue;
                }
                if (maquinaRepository.existsByCodigo(codigo)) {
                    yaExisten.add(codigo);
                    continue;
                }

                BigDecimal den = null;
                try {
                    den = new BigDecimal(tDen.replace(',', '.'));
                } catch (Exception e) {
                    den = null;
                }
                if (den == null || den.signum() <= 0) {
                    problemas.add("Fila " + numero + ": denominación inválida (código " + codigo + ", '" + tDen + "')");
                    continue;
                }
                if (nombre.isEmpty()) {
                    problemas.add("Fila " + numero + ": falta el nombre (código " + codigo + ")");
                    continue;
                }
                if (serial.isEmpty()) {
                    problemas.add("Fila " + numero + ": falta el serial (código " + codigo + ")");
                    continue;
                }
                if (juego.isEmpty()) {
                    problemas.add("Fila " + numero + ": falta el juego (código " + codigo + ")");
                    continue;
                }
                if (nombre.length() > 100 || serial.length() > 100 || juego.length() > 100) {
                    problemas.add("Fila " + numero + ": nombre, serial o juego de más de 100 letras (código " + codigo + ")");
                    continue;
                }

                Long in = aContador(tIn);
                Long out = aContador(tOut);
                Long prem = aContador(tPrem);
                if (in == null || out == null || prem == null) {
                    problemas.add("Fila " + numero + ": contador inválido (código " + codigo + ")");
                    continue;
                }
                if (!tIn.isEmpty() || !tOut.isEmpty() || !tPrem.isEmpty()) traeContadores = true;

                nuevas.add(new FilaMaquina(numero, codigo, den, nombre, serial, juego, in, out, prem));
            }
        }
        return new VistaPrevia(nuevas, yaExisten, problemas, traeContadores);
    }

    /** No es @Transactional a propósito: cada máquina se guarda sola y un error en una no frena las demás. */
    public Resultado aplicar(VistaPrevia vista, LocalDate fechaAlta) {
        LocalDate fecha = fechaAlta != null ? fechaAlta : LocalDate.now();
        int creadas = 0;
        List<String> errores = new ArrayList<>();
        for (FilaMaquina f : vista.nuevas()) {
            try {
                maquinaService.crear(f.codigo(), f.denominacion(), f.nombre(), f.serial(), f.juego(),
                        f.contadorIn(), f.contadorOut(), f.contadorPrem(), fecha);
                creadas++;
            } catch (Exception e) {
                log.warn("No se pudo importar una máquina: {}", e.getMessage());
                errores.add("Código " + f.codigo() + ": " + e.getMessage());
            }
        }
        log.info("Importación de máquinas: {} creadas, {} con error", creadas, errores.size());
        return new Resultado(creadas, errores);
    }

    // ---------------- ayudas ----------------

    private static String clave(String t) {
        String s = Normalizer.normalize(t == null ? "" : t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static String texto(Row fila, int col, DataFormatter fmt) {
        if (col < 0) return "";
        Cell c = fila.getCell(col);
        if (c == null) return "";
        String t;
        if (c.getCellType() == CellType.NUMERIC) {
            // Sin notación científica ni decimales de más (seriales como 603715)
            t = BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
        } else {
            t = fmt.formatCellValue(c);
        }
        return t.replaceAll("\\s+", " ").trim();
    }

    /** Solo dígitos (acepta puntos, comas o espacios como separador de miles); null si no es válido. */
    private static Long aLong(String t) {
        String s = t == null ? "" : t.replaceAll("[.,\\s]", "");
        if (!s.matches("\\d{1,15}")) return null;
        return Long.parseLong(s);
    }

    /** Vacío = 0; null si no se puede leer. */
    private static Long aContador(String t) {
        if (t == null || t.isEmpty()) return 0L;
        return aLong(t);
    }
}