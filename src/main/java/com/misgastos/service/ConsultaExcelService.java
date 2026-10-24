package com.misgastos.service;

import com.misgastos.model.Gasto;
import com.misgastos.service.ConsultaTotalesService.Fila;
import com.misgastos.service.ConsultaTotalesService.Resultado;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Exporta a Excel el resultado de la pantalla "Consulta de totales".
 *
 * Es un servicio NUEVO: no modifica la exportación de Informes ni ninguna otra.
 * Usa exactamente los mismos números que se ven en pantalla (el Resultado ya
 * calculado), así que el Excel siempre coincide con lo que la persona está viendo.
 *
 * Hoja "Resumen": qué se sumó, total, cantidad, promedio y el desglose.
 * Hoja "Detalle": cada gasto que forma el total, con filtros y total que se
 * recalcula si se filtra dentro de Excel.
 */
@Service
public class ConsultaExcelService {

    private static final String FORMATO_MONEDA = "\"$\" #,##0.00";
    private static final String FORMATO_PORCENTAJE = "0.0%";
    private static final String FORMATO_FECHA = "dd/mm/yyyy";
    private static final DateTimeFormatter GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void exportar(Resultado r, String descripcionFiltros, String tituloDesglose, Path destino)
            throws IOException {

        try (XSSFWorkbook libro = new XSSFWorkbook();
             OutputStream salida = Files.newOutputStream(destino)) {

            Estilos e = new Estilos(libro);
            hojaResumen(libro, e, r, descripcionFiltros, tituloDesglose);
            hojaDetalle(libro, e, r.gastos());

            // Que Excel recalcule los totales con fórmula al abrir
            libro.setForceFormulaRecalculation(true);
            libro.write(salida);
        }
    }

    // ==================== HOJA RESUMEN ====================

    private void hojaResumen(XSSFWorkbook libro, Estilos e, Resultado r,
                             String descripcionFiltros, String tituloDesglose) {
        Sheet hoja = libro.createSheet("Resumen");
        hoja.setColumnWidth(0, 44 * 256);
        hoja.setColumnWidth(1, 14 * 256);
        hoja.setColumnWidth(2, 22 * 256);
        hoja.setColumnWidth(3, 16 * 256);

        int fila = 0;

        celda(hoja, fila++, 0, "Consulta de totales — MisGastos", e.titulo);
        celda(hoja, fila++, 0, descripcionFiltros == null ? "" : descripcionFiltros, e.texto);
        celda(hoja, fila++, 0, "Generado el " + LocalDateTime.now().format(GENERADO), e.nota);
        fila++;

        celda(hoja, fila, 0, "Total", e.etiqueta);
        numero(hoja, fila++, 1, r.total(), e.monedaNegrita);

        celda(hoja, fila, 0, "N° de gastos", e.etiqueta);
        entero(hoja, fila++, 1, r.cantidad(), e.entero);

        celda(hoja, fila, 0, "Promedio por gasto", e.etiqueta);
        numero(hoja, fila++, 1, r.promedio(), e.moneda);
        fila++;

        celda(hoja, fila++, 0, tituloDesglose == null ? "Desglose" : tituloDesglose, e.subtitulo);

        Row encabezado = hoja.createRow(fila++);
        String[] titulos = {"Nombre", "Gastos", "Total", "Del total (%)"};
        for (int i = 0; i < titulos.length; i++) {
            Cell c = encabezado.createCell(i);
            c.setCellValue(titulos[i]);
            c.setCellStyle(e.encabezado);
        }

        List<Fila> desglose = r.desglose();
        for (Fila f : desglose) {
            Row row = hoja.createRow(fila++);
            Cell nombre = row.createCell(0);
            nombre.setCellValue(f.nombre() == null ? "" : f.nombre());
            nombre.setCellStyle(e.texto);

            Cell cantidad = row.createCell(1);
            cantidad.setCellValue(f.cantidad());
            cantidad.setCellStyle(e.entero);

            Cell total = row.createCell(2);
            total.setCellValue(f.total().doubleValue());
            total.setCellStyle(e.moneda);

            Cell pct = row.createCell(3);
            pct.setCellValue(f.porcentaje() / 100.0); // el servicio lo entrega de 0 a 100
            pct.setCellStyle(e.porcentaje);
        }

        // Fila de cierre: lo que suma el desglose (debe coincidir con el total de arriba)
        Row cierre = hoja.createRow(fila);
        Cell etiqueta = cierre.createCell(0);
        etiqueta.setCellValue("TOTAL");
        etiqueta.setCellStyle(e.cierreTexto);
        Cell cant = cierre.createCell(1);
        cant.setCellValue(r.cantidad());
        cant.setCellStyle(e.cierreEntero);
        Cell tot = cierre.createCell(2);
        tot.setCellValue(r.total().doubleValue());
        tot.setCellStyle(e.cierreMoneda);
        Cell pctTotal = cierre.createCell(3);
        pctTotal.setCellValue(r.total().signum() == 0 ? 0 : 1.0);
        pctTotal.setCellStyle(e.cierrePorcentaje);
    }

    // ==================== HOJA DETALLE ====================

    private void hojaDetalle(XSSFWorkbook libro, Estilos e, List<Gasto> gastos) {
        Sheet hoja = libro.createSheet("Detalle");

        String[] titulos = {"Fecha", "Categoría", "Subcategoría", "Producto", "Cant.",
                "Valor unitario", "Valor total", "Registrado por", "Persona"};
        int[] anchos = {12, 22, 22, 34, 8, 16, 18, 18, 30};

        Row encabezado = hoja.createRow(0);
        for (int i = 0; i < titulos.length; i++) {
            Cell c = encabezado.createCell(i);
            c.setCellValue(titulos[i]);
            c.setCellStyle(e.encabezado);
            hoja.setColumnWidth(i, anchos[i] * 256);
        }

        int fila = 1;
        for (Gasto g : gastos) {
            Row row = hoja.createRow(fila++);

            Cell fecha = row.createCell(0);
            if (g.getFecha() != null) {
                fecha.setCellValue(Date.from(g.getFecha().atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            fecha.setCellStyle(e.fecha);

            texto(row, 1, g.getCategoria() == null ? "" : g.getCategoria().getNombre(), e.texto);
            texto(row, 2, g.getSubcategoria() == null ? "" : g.getSubcategoria().getNombre(), e.texto);
            texto(row, 3, g.getProducto(), e.texto);

            Cell cantidad = row.createCell(4);
            if (g.getCantidad() != null) {
                cantidad.setCellValue(g.getCantidad());
            }
            cantidad.setCellStyle(e.entero);

            Cell unitario = row.createCell(5);
            if (g.getValorUnitario() != null) {
                unitario.setCellValue(g.getValorUnitario().doubleValue());
            }
            unitario.setCellStyle(e.moneda);

            Cell total = row.createCell(6);
            if (g.getValorTotal() != null) {
                total.setCellValue(g.getValorTotal().doubleValue());
            }
            total.setCellStyle(e.moneda);

            texto(row, 7, g.getUsuario() == null ? "" : g.getUsuario().getUsername(), e.texto);
            texto(row, 8, textoPersona(g), e.texto);
        }

        int ultimaDatos = fila; // número de fila (base 1) de la última fila con datos

        // Total al final: SUBTOTAL respeta los filtros que se apliquen dentro de Excel
        Row cierre = hoja.createRow(fila);
        Cell etiqueta = cierre.createCell(5);
        etiqueta.setCellValue("TOTAL");
        etiqueta.setCellStyle(e.cierreTexto);
        Cell suma = cierre.createCell(6);
        if (!gastos.isEmpty()) {
            suma.setCellFormula("SUBTOTAL(109,G2:G" + ultimaDatos + ")");
        } else {
            suma.setCellValue(0);
        }
        suma.setCellStyle(e.cierreMoneda);

        hoja.createFreezePane(0, 1);
        if (!gastos.isEmpty()) {
            hoja.setAutoFilter(new CellRangeAddress(0, gastos.size(), 0, titulos.length - 1));
        }
    }

    // ==================== AUXILIARES ====================

    private static String textoPersona(Gasto g) {
        boolean hayNombre = g.getNombrePersona() != null && !g.getNombrePersona().isBlank();
        boolean hayCedula = g.getCedula() != null && !g.getCedula().isBlank();
        if (hayNombre && hayCedula) {
            return g.getNombrePersona() + " (" + g.getCedula() + ")";
        }
        if (hayNombre) {
            return g.getNombrePersona();
        }
        return hayCedula ? g.getCedula() : "";
    }

    private static void celda(Sheet hoja, int fila, int columna, String texto, CellStyle estilo) {
        Row row = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell c = row.createCell(columna);
        c.setCellValue(texto);
        c.setCellStyle(estilo);
    }

    private static void numero(Sheet hoja, int fila, int columna, BigDecimal valor, CellStyle estilo) {
        Row row = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell c = row.createCell(columna);
        c.setCellValue(valor == null ? 0 : valor.doubleValue());
        c.setCellStyle(estilo);
    }

    private static void entero(Sheet hoja, int fila, int columna, long valor, CellStyle estilo) {
        Row row = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell c = row.createCell(columna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static void texto(Row row, int columna, String valor, CellStyle estilo) {
        Cell c = row.createCell(columna);
        c.setCellValue(valor == null ? "" : valor);
        c.setCellStyle(estilo);
    }

    /** Todos los estilos del libro, creados una sola vez (Excel limita la cantidad de estilos). */
    private static class Estilos {
        final CellStyle titulo, subtitulo, texto, nota, etiqueta, encabezado;
        final CellStyle moneda, monedaNegrita, entero, porcentaje, fecha;
        final CellStyle cierreTexto, cierreMoneda, cierreEntero, cierrePorcentaje;

        Estilos(XSSFWorkbook libro) {
            DataFormat formato = libro.createDataFormat();
            XSSFColor morado = new XSSFColor(new byte[]{(byte) 0x6f, (byte) 0x6d, (byte) 0xd9}, null);
            XSSFColor claro = new XSSFColor(new byte[]{(byte) 0xee, (byte) 0xf2, (byte) 0xff}, null);

            XSSFFont negrita = libro.createFont();
            negrita.setBold(true);

            XSSFFont grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 16);

            XSSFFont mediana = libro.createFont();
            mediana.setBold(true);
            mediana.setFontHeightInPoints((short) 12);

            XSSFFont blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());

            XSSFFont gris = libro.createFont();
            gris.setItalic(true);
            gris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            titulo = estilo(libro, grande, null, null, null);
            subtitulo = estilo(libro, mediana, null, null, null);
            texto = estilo(libro, null, null, null, null);
            nota = estilo(libro, gris, null, null, null);
            etiqueta = estilo(libro, negrita, null, null, null);

            encabezado = estilo(libro, blanca, morado, null, null);
            encabezado.setAlignment(HorizontalAlignment.CENTER);

            moneda = estilo(libro, null, null, formato.getFormat(FORMATO_MONEDA), null);
            monedaNegrita = estilo(libro, negrita, null, formato.getFormat(FORMATO_MONEDA), null);
            entero = estilo(libro, null, null, formato.getFormat("#,##0"), null);
            porcentaje = estilo(libro, null, null, formato.getFormat(FORMATO_PORCENTAJE), null);
            fecha = estilo(libro, null, null, formato.getFormat(FORMATO_FECHA), null);
            fecha.setAlignment(HorizontalAlignment.CENTER);

            cierreTexto = estilo(libro, negrita, claro, null, BorderStyle.THIN);
            cierreMoneda = estilo(libro, negrita, claro, formato.getFormat(FORMATO_MONEDA), BorderStyle.THIN);
            cierreEntero = estilo(libro, negrita, claro, formato.getFormat("#,##0"), BorderStyle.THIN);
            cierrePorcentaje = estilo(libro, negrita, claro, formato.getFormat(FORMATO_PORCENTAJE), BorderStyle.THIN);
        }

        private static XSSFCellStyle estilo(XSSFWorkbook libro, XSSFFont fuente, XSSFColor relleno,
                                            Short formato, BorderStyle borde) {
            XSSFCellStyle s = libro.createCellStyle();
            if (fuente != null) {
                s.setFont(fuente);
            }
            if (relleno != null) {
                s.setFillForegroundColor(relleno);
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (formato != null) {
                s.setDataFormat(formato);
            }
            if (borde != null) {
                s.setBorderTop(borde);
                s.setBorderBottom(borde);
            }
            return s;
        }
    }
}
