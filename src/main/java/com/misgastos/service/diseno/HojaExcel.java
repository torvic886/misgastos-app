package com.misgastos.service.diseno;

import com.misgastos.service.diseno.ExcelEstilo.Fmt;
import com.misgastos.service.diseno.ExcelEstilo.Rol;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Una hoja de Excel con el diseño común: título, tablas con encabezado,
 * filas alternas, totales, filtros, panel congelado y configuración de impresión.
 *
 * Uso típico: titulo() -> encabezado() -> fila(...) varias veces -> terminar().
 */
public class HojaExcel {

    private final ExcelEstilo estilos;
    private final XSSFSheet hoja;
    private final int columnas;
    private final boolean horizontal;
    private final int[] anchos;

    private int fila = 0;               // índice (0-based) de la próxima fila
    private int filaEncabezado = -1;
    private int ultimaFilaDatos = -1;
    private int contadorZebra = 0;
    private FilaExcel ultima;
    private int columnasCongeladas = 0;
    private int anchoMinimo = 10;

    public HojaExcel(ExcelEstilo estilos, String nombre, int columnas, boolean horizontal) {
        this.estilos = estilos;
        this.hoja = estilos.libro().createSheet(nombre);
        this.columnas = columnas;
        this.horizontal = horizontal;
        this.anchos = new int[columnas];
    }
    
    public HojaExcel congelarColumnas(int cantidad) {
        this.columnasCongeladas = cantidad;
        return this;
    }

    public HojaExcel anchoMinimo(int caracteres) {
        this.anchoMinimo = caracteres;
        return this;
    }

    /** Referencia de celda en notación A1 (columna 0-based, fila de Excel 1-based). */
    public static String ref(int columna, int filaExcel) {
        return CellReference.convertNumToColString(columna) + filaExcel;
    }

    /** Número de fila de Excel (1-based) de la próxima fila que se escribirá. */
    public int siguienteFila() {
        return fila + 1;
    }

    // ==================== ESTRUCTURA ====================

    public HojaExcel titulo(String titulo, String subtitulo) {
        cerrarFila();
        Row r = hoja.createRow(fila++);
        r.setHeightInPoints(26);
        Cell c = r.createCell(0);
        c.setCellValue(titulo);
        c.setCellStyle(estilos.titulo());
        if (subtitulo != null && !subtitulo.isEmpty()) {
            Row r2 = hoja.createRow(fila++);
            r2.setHeightInPoints(16);
            Cell c2 = r2.createCell(0);
            c2.setCellValue(subtitulo);
            c2.setCellStyle(estilos.subtitulo());
        }
        fila++; // fila en blanco
        return this;
    }

    public HojaExcel seccion(String texto) {
        cerrarFila();
        Row r = hoja.createRow(fila++);
        r.setHeightInPoints(20);
        for (int i = 0; i < columnas; i++) {
            Cell c = r.createCell(i);
            c.setCellStyle(estilos.seccion());
            if (i == 0) {
                c.setCellValue(texto);
            }
        }
        contadorZebra = 0;
        return this;
    }

    public HojaExcel espacio() {
        cerrarFila();
        fila++;
        return this;
    }

    public HojaExcel nota(String texto) {
        cerrarFila();
        Row r = hoja.createRow(fila++);
        Cell c = r.createCell(0);
        c.setCellValue(texto);
        c.setCellStyle(estilos.nota());
        return this;
    }
    
    /** Línea de estadística: etiqueta destacada en la columna A y valor en la B (sin rellenar el resto). */
    public HojaExcel estadistica(String etiqueta, String valor) {
        cerrarFila();
        Row r = hoja.createRow(fila++);
        r.setHeightInPoints(16);
        Cell a = r.createCell(0);
        a.setCellValue(etiqueta);
        a.setCellStyle(estilos.estilo(Rol.GRUPO, false, Fmt.TEXTO));
        Cell b = r.createCell(1);
        b.setCellValue(valor);
        b.setCellStyle(estilos.estilo(Rol.DATO, false, Fmt.MONEDA));
        registrarAncho(0, etiqueta.length());
        registrarAncho(1, valor.length());
        return this;
    }

    public HojaExcel encabezado(String[] titulos, HorizontalAlignment[] alineaciones) {
        cerrarFila();
        Row r = hoja.createRow(fila);
        r.setHeightInPoints(22);
        for (int i = 0; i < titulos.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(titulos[i]);
            c.setCellStyle(estilos.encabezado(alineaciones[i]));
            registrarAncho(i, titulos[i].length() + 2);
        }
        filaEncabezado = fila;
        ultimaFilaDatos = fila;
        fila++;
        contadorZebra = 0;
        return this;
    }

    public FilaExcel fila(Rol rol) {
        cerrarFila();
        Row r = hoja.createRow(fila++);
        r.setHeightInPoints(16);
        boolean zebra = rol == Rol.DATO && (contadorZebra++ % 2 == 1);
        if (rol == Rol.DATO) {
            ultimaFilaDatos = fila - 1;
        }
        ultima = new FilaExcel(this, r, rol, zebra);
        return ultima;
    }

    // Indicadores de resumen: etiqueta en la primera columna y valor en la segunda
    public HojaExcel indicadorMoneda(String etiqueta, BigDecimal valor) {
        fila(Rol.DATO).texto(etiqueta).moneda(valor);
        return this;
    }

    public HojaExcel indicadorEntero(String etiqueta, long valor) {
        fila(Rol.DATO).texto(etiqueta).entero(valor);
        return this;
    }

    public HojaExcel indicadorTexto(String etiqueta, String valor) {
        fila(Rol.DATO).texto(etiqueta).texto(valor);
        return this;
    }

    public HojaExcel indicadorFormulaMoneda(String etiqueta, String formula) {
        fila(Rol.DATO).texto(etiqueta).formulaMoneda(formula);
        return this;
    }

    public HojaExcel indicadorFormulaEntero(String etiqueta, String formula) {
        fila(Rol.DATO).texto(etiqueta).formulaEntero(formula);
        return this;
    }

    // ==================== CIERRE ====================

    /**
     * Ajusta anchos, filtros, panel congelado y configuración de impresión.
     *
     * @param filtros  activa el autofiltro sobre la última tabla
     * @param congelar congela las filas hasta el encabezado de la última tabla
     */
    public void terminar(boolean filtros, boolean congelar) {
        cerrarFila();

        for (int c = 0; c < columnas; c++) {
            int ancho = Math.min(Math.max(anchos[c] + 3, anchoMinimo), 46);;
            hoja.setColumnWidth(c, ancho * 256);
        }

        hoja.setDisplayGridlines(false);

        if (congelar && filaEncabezado >= 0) {
            hoja.createFreezePane(columnasCongeladas, filaEncabezado + 1);
        }
        if (filtros && filaEncabezado >= 0 && ultimaFilaDatos > filaEncabezado) {
            hoja.setAutoFilter(new CellRangeAddress(filaEncabezado, ultimaFilaDatos, 0, columnas - 1));
        }

        // Impresión
        hoja.setFitToPage(true);
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setLandscape(horizontal);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0);
        hoja.setMargin(PageMargin.LEFT, 0.5);
        hoja.setMargin(PageMargin.RIGHT, 0.5);
        hoja.setMargin(PageMargin.TOP, 0.7);
        hoja.setMargin(PageMargin.BOTTOM, 0.7);
        hoja.setHorizontallyCenter(true);
        hoja.getFooter().setLeft(Diseno.APP);
        hoja.getFooter().setCenter("P\u00e1gina &P de &N");
        hoja.getFooter().setRight("&D");
        if (filaEncabezado >= 0) {
            String fila1 = String.valueOf(filaEncabezado + 1);
            hoja.setRepeatingRows(CellRangeAddress.valueOf(fila1 + ":" + fila1));
        }
        estilos.libro().setPrintArea(estilos.libro().getSheetIndex(hoja), 0, columnas - 1, 0, Math.max(fila - 1, 0));
    }

    // ==================== INTERNOS ====================

    private void cerrarFila() {
        if (ultima != null) {
            ultima.completar();
            ultima = null;
        }
    }

    private void registrarAncho(int columna, int largo) {
        if (columna >= 0 && columna < anchos.length && largo > anchos[columna]) {
            anchos[columna] = largo;
        }
    }

    // ==================== FILA ====================

    /** Escritura secuencial de una fila: cada método llena la siguiente columna. */
    public static class FilaExcel {
        private final HojaExcel hoja;
        private final Row fila;
        private final Rol rol;
        private final boolean zebra;
        private int columna = 0;

        private FilaExcel(HojaExcel hoja, Row fila, Rol rol, boolean zebra) {
            this.hoja = hoja;
            this.fila = fila;
            this.rol = rol;
            this.zebra = zebra;
        }

        private Cell nueva(Fmt fmt, int anchoEstimado) {
            Cell c = fila.createCell(columna);
            c.setCellStyle(hoja.estilos.estilo(rol, zebra, fmt));
            hoja.registrarAncho(columna, anchoEstimado);
            columna++;
            return c;
        }

        public FilaExcel texto(String valor) {
            String v = valor == null ? "" : valor;
            nueva(Fmt.TEXTO, v.length()).setCellValue(v);
            return this;
        }

        public FilaExcel centro(String valor) {
            String v = valor == null ? "" : valor;
            nueva(Fmt.CENTRO, v.length()).setCellValue(v);
            return this;
        }

        public FilaExcel fecha(LocalDate valor) {
            Cell c = nueva(Fmt.FECHA, 10);
            if (valor != null) {
                c.setCellValue(Date.from(valor.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            return this;
        }

        /** Hora como valor de hora real de Excel (si no es LocalTime, como texto). */
        public FilaExcel hora(Object valor) {
            if (valor instanceof LocalTime) {
                nueva(Fmt.HORA, 8).setCellValue(((LocalTime) valor).toSecondOfDay() / 86400.0);
            } else {
                centro(valor == null ? "" : valor.toString());
            }
            return this;
        }

        public FilaExcel id(Number valor) {
            Cell c = nueva(Fmt.ID, 6);
            if (valor != null) {
                c.setCellValue(valor.doubleValue());
            }
            return this;
        }

        public FilaExcel entero(Number valor) {
            Cell c = nueva(Fmt.ENTERO, 8);
            if (valor != null) {
                c.setCellValue(valor.doubleValue());
            }
            return this;
        }

        public FilaExcel moneda(BigDecimal valor) {
            Cell c = nueva(Fmt.MONEDA, valor == null ? 4 : Diseno.moneda(valor).length());
            if (valor != null) {
                c.setCellValue(valor.doubleValue());
            }
            return this;
        }

        public FilaExcel monedaSigno(BigDecimal valor) {
            Cell c = nueva(Fmt.SIGNO, valor == null ? 4 : Diseno.monedaConSigno(valor).length());
            if (valor != null) {
                c.setCellValue(valor.doubleValue());
            }
            return this;
        }

        /** @param fraccion valor entre 0 y 1 (0,125 se muestra como 12,5%) */
        public FilaExcel pct(double fraccion) {
            nueva(Fmt.PCT, 7).setCellValue(fraccion);
            return this;
        }

        public FilaExcel pctSigno(Double fraccion) {
            Cell c = nueva(Fmt.PCT_SIGNO, 8);
            if (fraccion != null) {
                c.setCellValue(fraccion);
            }
            return this;
        }

        public FilaExcel formulaMoneda(String formula) {
            nueva(Fmt.MONEDA, 12).setCellFormula(formula);
            return this;
        }

        public FilaExcel formulaMonedaSigno(String formula) {
            nueva(Fmt.SIGNO, 12).setCellFormula(formula);
            return this;
        }

        public FilaExcel formulaEntero(String formula) {
            nueva(Fmt.ENTERO, 8).setCellFormula(formula);
            return this;
        }

        public FilaExcel formulaPct(String formula) {
            nueva(Fmt.PCT, 7).setCellFormula(formula);
            return this;
        }

        public FilaExcel formulaPctSigno(String formula) {
            nueva(Fmt.PCT_SIGNO, 8).setCellFormula(formula);
            return this;
        }

        public FilaExcel vacio() {
            nueva(Fmt.TEXTO, 0);
            return this;
        }

        /** Rellena con celdas vacías el resto de columnas para que el fondo y los bordes lleguen al final. */
        private void completar() {
            while (columna < hoja.columnas) {
                vacio();
            }
        }
    }
}
