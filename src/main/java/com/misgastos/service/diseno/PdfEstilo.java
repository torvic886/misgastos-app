package com.misgastos.service.diseno;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Estilo único de los informes PDF: márgenes, encabezado y pie de página,
 * títulos, indicadores y tablas. La lógica de cada informe solo decide qué datos mostrar.
 */
public class PdfEstilo implements AutoCloseable {

    /** Tipos de fila de una tabla. */
    public enum Fila { DATO, ZEBRA, GRUPO, TOTAL }

    private static final float MARGEN = 40f;
    private static final Locale ES = new Locale("es");

    private final PdfDocument pdf;
    private final Document doc;
    private final PdfFont normal;
    private final PdfFont negrita;
    private final String nombreInforme;
    private float tamTabla = 8f;

    public PdfEstilo(String ruta, String nombreInforme, boolean horizontal) throws IOException {
        this.nombreInforme = nombreInforme;
        PageSize tamano = horizontal ? PageSize.A4.rotate() : PageSize.A4;

        pdf = new PdfDocument(new PdfWriter(ruta));
        pdf.setDefaultPageSize(tamano);
        pdf.getDocumentInfo().setTitle(nombreInforme);
        pdf.getDocumentInfo().setCreator(Diseno.APP);

        normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        negrita = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        doc = new Document(pdf, tamano);
        doc.setMargins(54, MARGEN, 46, MARGEN);
        doc.setFont(normal);
        doc.setFontSize(9);
        doc.setFontColor(c(Diseno.TINTA));

        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new EncabezadoPie());
    }

    public static Color c(int[] rgb) {
        return new DeviceRgb(rgb[0], rgb[1], rgb[2]);
    }

    public Document documento() {
        return doc;
    }

    public void tamTabla(float tamano) {
        this.tamTabla = tamano;
    }

    // ==================== BLOQUES DE CONTENIDO ====================

    public void titulo(String titulo, String subtitulo) {
        doc.add(new Paragraph(titulo)
            .setFont(negrita).setFontSize(16).setFontColor(c(Diseno.ACENTO))
            .setMarginTop(0).setMarginBottom(2));
        if (subtitulo != null && !subtitulo.isEmpty()) {
            doc.add(new Paragraph(subtitulo)
                .setFontSize(9).setFontColor(c(Diseno.TEXTO_SUAVE))
                .setMarginTop(0).setMarginBottom(8));
        }
    }

    public void seccion(String texto) {
        doc.add(new Paragraph(texto.toUpperCase(ES))
            .setFont(negrita).setFontSize(8).setFontColor(c(Diseno.ACENTO))
            .setMarginTop(14).setMarginBottom(4).setPaddingBottom(2)
            .setBorderBottom(new SolidBorder(c(Diseno.LINEA), 0.6f))
            .setKeepWithNext(true));
    }

    public void nota(String texto) {
        doc.add(new Paragraph(texto)
            .setFontSize(8.5f).setFontColor(c(Diseno.TEXTO_SUAVE))
            .setMarginTop(4).setMarginBottom(4));
    }

    /** Línea "Etiqueta: valor" con la etiqueta en gris. */
    public void propiedad(String etiqueta, String valor) {
        Paragraph p = new Paragraph().setMarginTop(0).setMarginBottom(2).setFontSize(9);
        p.add(new Text(etiqueta + ": ").setFont(normal).setFontColor(c(Diseno.TEXTO_SUAVE)));
        p.add(new Text(valor).setFont(negrita).setFontColor(c(Diseno.TINTA)));
        doc.add(p);
    }

    /** Fila de indicadores compactos. Cada elemento es {etiqueta, valor}. */
    public void indicadores(String[]... items) {
        Table t = new Table(UnitValue.createPercentArray(items.length)).useAllAvailableWidth();
        t.setMarginTop(4).setMarginBottom(2);
        for (int i = 0; i < items.length; i++) {
            Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(c(Diseno.ACENTO), 1.5f))
                .setBackgroundColor(c(Diseno.FONDO_ZEBRA))
                .setPaddingTop(5).setPaddingBottom(5).setPaddingLeft(8).setPaddingRight(6)
                .setKeepTogether(true);
            if (i < items.length - 1) {
                cell.setBorderRight(new SolidBorder(c(Diseno.BLANCO), 6));
            }
            cell.add(new Paragraph(items[i][0].toUpperCase(ES))
                .setFont(normal).setFontSize(6.5f).setFontColor(c(Diseno.TEXTO_SUAVE)).setMargin(0));
            cell.add(new Paragraph(items[i][1])
                .setFont(negrita).setFontSize(11).setFontColor(c(Diseno.TINTA)).setMargin(0).setMarginTop(2));
            t.addCell(cell);
        }
        doc.add(t);
    }

    // ==================== TABLAS ====================

    /** Crea una tabla a todo el ancho; los anchos son proporciones relativas. */
    public Table tabla(float... anchos) {
        Table t = new Table(UnitValue.createPercentArray(anchos)).useAllAvailableWidth();
        t.setMarginTop(2).setMarginBottom(6);
        return t;
    }

    public void encabezado(Table t, String[] columnas, TextAlignment[] alineaciones) {
        for (int i = 0; i < columnas.length; i++) {
            t.addHeaderCell(celdaEncabezado(columnas[i], alineaciones[i]));
        }
    }

    public void agregar(Table t) {
        doc.add(t);
    }

    private Cell celdaEncabezado(String texto, TextAlignment alineacion) {
        Paragraph p = new Paragraph(texto.toUpperCase(ES))
            .setFont(negrita).setFontSize(Math.max(6.5f, tamTabla - 1f))
            .setFontColor(c(Diseno.TEXTO_SUAVE)).setMargin(0);
        return new Cell().add(p)
            .setTextAlignment(alineacion)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBackgroundColor(c(Diseno.FONDO_ENCABEZADO))
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(c(Diseno.LINEA_FUERTE), 0.8f))
            .setPaddingTop(4).setPaddingBottom(4).setPaddingLeft(6).setPaddingRight(6);
    }

    public Cell celda(String texto, TextAlignment alineacion, Fila tipo) {
        return celda(texto, alineacion, tipo, null, 0);
    }

    public Cell celda(String texto, TextAlignment alineacion, Fila tipo, int[] colorTexto) {
        return celda(texto, alineacion, tipo, colorTexto, 0);
    }

    /**
     * @param colorTexto color del texto (null = el de la fila)
     * @param sangria    sangría izquierda adicional, en puntos
     */
    public Cell celda(String texto, TextAlignment alineacion, Fila tipo, int[] colorTexto, float sangria) {
        boolean fuerte = tipo == Fila.GRUPO || tipo == Fila.TOTAL;
        int[] color = colorTexto != null ? colorTexto
            : (tipo == Fila.GRUPO ? Diseno.ACENTO : Diseno.TINTA);
        Paragraph p = new Paragraph(texto == null ? "" : texto)
            .setFont(fuerte ? negrita : normal).setFontSize(tamTabla)
            .setFontColor(c(color)).setMargin(0).setMultipliedLeading(1.1f);
        return formatear(new Cell().add(p), alineacion, tipo, sangria);
    }

    /** Celda con una barra horizontal proporcional (0 a 1). */
    public Cell celdaBarra(double proporcion, PdfFont fuenteBloques, Fila tipo) {
        int n = (int) Math.round(Math.max(0, Math.min(1, proporcion)) * 24);
        String barras = (fuenteBloques == null || n == 0) ? "" : "\u2588".repeat(n);
        Paragraph p = new Paragraph(barras).setFontSize(6).setFontColor(c(Diseno.ACENTO)).setMargin(0);
        if (fuenteBloques != null) {
            p.setFont(fuenteBloques);
        }
        return formatear(new Cell().add(p), TextAlignment.LEFT, tipo, 0);
    }

    private Cell formatear(Cell cell, TextAlignment alineacion, Fila tipo, float sangria) {
        cell.setTextAlignment(alineacion)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBorder(Border.NO_BORDER)
            .setPaddingTop(3.5f).setPaddingBottom(3.5f)
            .setPaddingLeft(6 + sangria).setPaddingRight(6)
            .setKeepTogether(true);
        switch (tipo) {
            case ZEBRA:
                cell.setBackgroundColor(c(Diseno.FONDO_ZEBRA));
                cell.setBorderBottom(new SolidBorder(c(Diseno.LINEA), 0.4f));
                break;
            case GRUPO:
                cell.setBackgroundColor(c(Diseno.ACENTO_SUAVE));
                cell.setBorderBottom(new SolidBorder(c(Diseno.LINEA), 0.4f));
                break;
            case TOTAL:
                cell.setBackgroundColor(c(Diseno.FONDO_ENCABEZADO));
                cell.setBorderTop(new SolidBorder(c(Diseno.TINTA), 0.9f));
                break;
            default:
                cell.setBorderBottom(new SolidBorder(c(Diseno.LINEA), 0.4f));
                break;
        }
        return cell;
    }

    /**
     * Fuente con bloques (para las barras del informe anual), cargada desde el classpath
     * (/fonts/DejaVuSans.ttf). Devuelve null si no está disponible.
     */
    public PdfFont fuenteBloques() {
        try (InputStream in = PdfEstilo.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (in == null) {
                return null;
            }
            return PdfFontFactory.createFont(in.readAllBytes(), PdfEncodings.IDENTITY_H);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        doc.close();
    }

    // ==================== ENCABEZADO Y PIE DE PÁGINA ====================

    private class EncabezadoPie implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent ev = (PdfDocumentEvent) event;
            PdfPage pagina = ev.getPage();
            Rectangle tam = pagina.getPageSize();
            int numero = ev.getDocument().getPageNumber(pagina);

            PdfCanvas canvas = new PdfCanvas(pagina.newContentStreamAfter(), pagina.getResources(), ev.getDocument());
            float izq = MARGEN;
            float der = tam.getWidth() - MARGEN;

            // Encabezado
            float yEnc = tam.getHeight() - 30;
            texto(canvas, negrita, 8, Diseno.ACENTO, Diseno.APP, izq, yEnc, false);
            texto(canvas, normal, 8, Diseno.TEXTO_SUAVE, nombreInforme, der, yEnc, true);
            linea(canvas, izq, der, yEnc - 6);

            // Pie
            float yPie = 24;
            linea(canvas, izq, der, yPie + 12);
            texto(canvas, normal, 7.5f, Diseno.TEXTO_SUAVE, "Generado el " + Diseno.fechaHoraActual(), izq, yPie, false);
            texto(canvas, normal, 7.5f, Diseno.TEXTO_SUAVE, "P\u00e1gina " + numero, der, yPie, true);

            canvas.release();
        }

        private void texto(PdfCanvas canvas, PdfFont fuente, float tamano, int[] color, String texto,
                           float x, float y, boolean alinearDerecha) {
            float posicion = alinearDerecha ? x - fuente.getWidth(texto, tamano) : x;
            canvas.beginText()
                .setFontAndSize(fuente, tamano)
                .setColor(c(color), true)
                .moveText(posicion, y)
                .showText(texto)
                .endText();
        }

        private void linea(PdfCanvas canvas, float x1, float x2, float y) {
            canvas.setStrokeColor(c(Diseno.LINEA))
                .setLineWidth(0.5f)
                .moveTo(x1, y)
                .lineTo(x2, y)
                .stroke();
        }
    }
}
