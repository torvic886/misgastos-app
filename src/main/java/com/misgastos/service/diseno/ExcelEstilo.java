package com.misgastos.service.diseno;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDataFormat;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Estilos de los libros de Excel: misma paleta y jerarquía que los PDF.
 * Los estilos se crean una sola vez y se reutilizan.
 */
public class ExcelEstilo {

    /** Papel de una fila dentro de la tabla. */
    public enum Rol { DATO, GRUPO, TOTAL }

    /** Formato de una celda. */
    public enum Fmt { TEXTO, CENTRO, FECHA, HORA, ID, ENTERO, MONEDA, SIGNO, PCT, PCT_SIGNO }

    private static final String FUENTE = "Calibri";

    private final XSSFWorkbook libro = new XSSFWorkbook();
    private final XSSFDataFormat formato = libro.createDataFormat();
    private final Map<String, XSSFCellStyle> estilos = new HashMap<>();
    private final Map<String, XSSFFont> fuentes = new HashMap<>();

    public XSSFWorkbook libro() {
        return libro;
    }

    // ==================== ESTILOS ESPECIALES ====================

    public XSSFCellStyle titulo() {
        return especial("titulo", fuente(true, false, 16, Diseno.ACENTO), null, null, HorizontalAlignment.LEFT);
    }

    public XSSFCellStyle subtitulo() {
        return especial("subtitulo", fuente(false, false, 10, Diseno.TEXTO_SUAVE), null, null, HorizontalAlignment.LEFT);
    }

    public XSSFCellStyle seccion() {
        return especial("seccion", fuente(true, false, 11, Diseno.ACENTO), null, Diseno.LINEA, HorizontalAlignment.LEFT);
    }

    public XSSFCellStyle nota() {
        return especial("nota", fuente(false, true, 9, Diseno.TEXTO_SUAVE), null, null, HorizontalAlignment.LEFT);
    }

    public XSSFCellStyle encabezado(HorizontalAlignment alineacion) {
        String clave = "encabezado" + alineacion;
        XSSFCellStyle existente = estilos.get(clave);
        if (existente != null) {
            return existente;
        }
        XSSFCellStyle s = libro.createCellStyle();
        s.setFont(fuente(true, false, 10, Diseno.BLANCO));
        s.setAlignment(alineacion);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        rellenar(s, Diseno.ACENTO);
        bordes(s, Diseno.LINEA_FUERTE);
        estilos.put(clave, s);
        return s;
    }

    private XSSFCellStyle especial(String clave, XSSFFont fuente, int[] relleno, int[] bordeInferior,
                                   HorizontalAlignment alineacion) {
        XSSFCellStyle existente = estilos.get(clave);
        if (existente != null) {
            return existente;
        }
        XSSFCellStyle s = libro.createCellStyle();
        s.setFont(fuente);
        s.setAlignment(alineacion);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        if (relleno != null) {
            rellenar(s, relleno);
        }
        if (bordeInferior != null) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBottomBorderColor(color(bordeInferior));
        }
        estilos.put(clave, s);
        return s;
    }

    // ==================== ESTILOS DE DATOS ====================

    public XSSFCellStyle estilo(Rol rol, boolean zebra, Fmt fmt) {
        String clave = rol + "|" + zebra + "|" + fmt;
        XSSFCellStyle existente = estilos.get(clave);
        if (existente != null) {
            return existente;
        }
        XSSFCellStyle s = libro.createCellStyle();
        int[] colorTexto = rol == Rol.GRUPO ? Diseno.ACENTO
            : (rol == Rol.TOTAL ? Diseno.BLANCO : Diseno.TINTA);
        s.setFont(fuente(rol != Rol.DATO, false, 10, colorTexto));
        s.setAlignment(alineacion(fmt));
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setDataFormat(formato.getFormat(patron(fmt)));

        if (rol == Rol.GRUPO) {
            rellenar(s, Diseno.ACENTO_SUAVE);
        } else if (rol == Rol.TOTAL) {
            rellenar(s, Diseno.TINTA);
        } else if (zebra) {
            rellenar(s, Diseno.FONDO_ENCABEZADO);
        }
        bordes(s, Diseno.LINEA_FUERTE);
        estilos.put(clave, s);
        return s;
    }
    
    private void bordes(XSSFCellStyle s, int[] rgb) {
        XSSFColor c = color(rgb);
        s.setBorderTop(BorderStyle.THIN);
        s.setTopBorderColor(c);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(c);
        s.setBorderLeft(BorderStyle.THIN);
        s.setLeftBorderColor(c);
        s.setBorderRight(BorderStyle.THIN);
        s.setRightBorderColor(c);
    }

    private HorizontalAlignment alineacion(Fmt fmt) {
        switch (fmt) {
            case TEXTO:
                return HorizontalAlignment.LEFT;
            case CENTRO:
            case FECHA:
            case HORA:
            case ID:
                return HorizontalAlignment.CENTER;
            default:
                return HorizontalAlignment.RIGHT;
        }
    }

    private String patron(Fmt fmt) {
        switch (fmt) {
            case FECHA:
                return "dd/mm/yyyy";
            case HORA:
                return "hh:mm:ss";
            case ID:
                return "0";
            case ENTERO:
                return "#,##0";
            case MONEDA:
                return "\"$\"#,##0";
            case SIGNO:
                return "+\"$\"#,##0;-\"$\"#,##0;\"$\"0";
            case PCT:
                return "0.0%";
            case PCT_SIGNO:
                return "+0.0%;-0.0%;0.0%";
            default:
                return "General";
        }
    }

    // ==================== UTILIDADES ====================

    private void rellenar(XSSFCellStyle s, int[] rgb) {
        s.setFillForegroundColor(color(rgb));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private XSSFFont fuente(boolean negrita, boolean cursiva, double tamano, int[] rgb) {
        String clave = negrita + "|" + cursiva + "|" + tamano + "|" + rgb[0] + "," + rgb[1] + "," + rgb[2];
        XSSFFont existente = fuentes.get(clave);
        if (existente != null) {
            return existente;
        }
        XSSFFont f = libro.createFont();
        f.setFontName(FUENTE);
        f.setFontHeightInPoints((short) Math.round(tamano));
        f.setBold(negrita);
        f.setItalic(cursiva);
        f.setColor(color(rgb));
        fuentes.put(clave, f);
        return f;
    }

    private XSSFColor color(int[] rgb) {
        return new XSSFColor(new byte[]{(byte) rgb[0], (byte) rgb[1], (byte) rgb[2]}, null);
    }

    /** Calcula las fórmulas, guarda el archivo y cierra el libro. */
    public void guardar(String ruta) throws IOException {
        libro.getCreationHelper().createFormulaEvaluator().evaluateAll();
        try (FileOutputStream salida = new FileOutputStream(ruta)) {
            libro.write(salida);
        }
        libro.close();
    }
}
