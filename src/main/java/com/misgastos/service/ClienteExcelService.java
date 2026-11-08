package com.misgastos.service;

import com.misgastos.service.diseno.ExcelEstilo;
import com.misgastos.service.diseno.ExcelEstilo.Rol;
import com.misgastos.service.diseno.HojaExcel;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.stereotype.Service;

/**
 * Escribe el Excel de la pantalla Clientes con las columnas elegidas (de la lista
 * y propias), listo para imprimir en carta. Solo escribe el archivo.
 */
@Service
public class ClienteExcelService {

    private static final String HOJA = "Lista";

    /** Encabezado de una columna; centrada = true para números y fechas. */
    public record Columna(String titulo, boolean centrada) {
    }

    public void exportar(String titulo, String subtitulo, List<Columna> columnas,
                         List<List<Object>> filas, boolean numerar, int filasEnBlanco,
                         boolean horizontal, String ruta) throws Exception {
        if (columnas.isEmpty() && !numerar) {
            throw new IllegalArgumentException("Elige al menos una columna.");
        }

        ExcelEstilo estilos = new ExcelEstilo();
        int total = columnas.size() + (numerar ? 1 : 0);
        HojaExcel hoja = new HojaExcel(estilos, HOJA, total, horizontal);

        String sub = subtitulo == null ? "" : subtitulo;
        hoja.titulo(titulo == null || titulo.isBlank() ? "Clientes" : titulo, sub);

        List<String> titulos = new ArrayList<>();
        List<HorizontalAlignment> alin = new ArrayList<>();
        if (numerar) {
            titulos.add("#");
            alin.add(HorizontalAlignment.CENTER);
        }
        for (Columna c : columnas) {
            titulos.add(c.titulo());
            alin.add(c.centrada() ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
        }
        hoja.encabezado(titulos.toArray(new String[0]), alin.toArray(new HorizontalAlignment[0]));

        int n = 1;
        for (List<Object> valores : filas) {
            escribirFila(hoja, n++, numerar, valores, columnas.size());
        }
        for (int i = 0; i < Math.max(filasEnBlanco, 0); i++) {
            escribirFila(hoja, n++, numerar, List.of(), columnas.size());
        }

        hoja.terminar(false, true);

        // Solo para este informe: carta, anchos que llenan la hoja y título centrado
        XSSFSheet sheet = estilos.libro().getSheet(HOJA);
        sheet.getPrintSetup().setPaperSize(PrintSetup.LETTER_PAPERSIZE);
        repartirAncho(sheet, total, numerar, horizontal);
        centrarTitulo(estilos, sheet, total, !sub.isEmpty());

        estilos.guardar(ruta);
    }

    /** Reparte el ancho imprimible (carta, márgenes de 0,5") entre las columnas. */
    private void repartirAncho(XSSFSheet sheet, int total, boolean numerar, boolean horizontal) {
        double pixelesUtiles = (horizontal ? 10.0 : 7.5) * 96 * 0.97;
        double caracteres = (pixelesUtiles - 5.0 * total) / 7.0;

        double anchoNumero = numerar ? 5 : 0;
        double suma = 0;
        for (int c = numerar ? 1 : 0; c < total; c++) {
            suma += sheet.getColumnWidth(c) / 256.0;
        }
        if (suma <= 0) {
            return;
        }
        double disponible = caracteres - anchoNumero;
        for (int c = 0; c < total; c++) {
            if (numerar && c == 0) {
                sheet.setColumnWidth(0, (int) (anchoNumero * 256));
            } else {
                double parte = sheet.getColumnWidth(c) / 256.0 / suma;
                sheet.setColumnWidth(c, (int) (disponible * parte * 256));
            }
        }
    }

    /** Combina las celdas del título (y del subtítulo) a lo ancho de la tabla y las centra. */
    private void centrarTitulo(ExcelEstilo estilos, XSSFSheet sheet, int total, boolean haySubtitulo) {
        if (total < 2) {
            return;
        }
        XSSFCellStyle tit = estilos.libro().createCellStyle();
        tit.cloneStyleFrom(estilos.titulo());
        tit.setAlignment(HorizontalAlignment.CENTER);
        sheet.getRow(0).getCell(0).setCellStyle(tit);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, total - 1));

        if (haySubtitulo) {
            XSSFCellStyle sub = estilos.libro().createCellStyle();
            sub.cloneStyleFrom(estilos.subtitulo());
            sub.setAlignment(HorizontalAlignment.CENTER);
            sheet.getRow(1).getCell(0).setCellStyle(sub);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, total - 1));
        }
    }

    private void escribirFila(HojaExcel hoja, int numero, boolean numerar,
                              List<Object> valores, int cantidad) {
        HojaExcel.FilaExcel f = hoja.fila(Rol.DATO);
        if (numerar) {
            f.centro(String.valueOf(numero));
        }
        for (int i = 0; i < cantidad; i++) {
            Object v = i < valores.size() ? valores.get(i) : null;
            if (v instanceof Number num) {
                f.entero(num);
            } else {
                f.texto(v == null ? "" : v.toString());
            }
        }
    }
}