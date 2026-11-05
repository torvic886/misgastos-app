package com.misgastos.service.diseno;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
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
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.misgastos.model.Gasto;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** PDF del "Informe de gastos": encabezado, tarjetas, detalle por categoría, anillo y resumen. */
public final class InformeGastosPdf {

    private static final DeviceRgb NAVY = new DeviceRgb(30, 58, 138);
    private static final DeviceRgb AZUL = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb GRIS = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb TINTA = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb LINEA = new DeviceRgb(203, 213, 225);
    private static final DeviceRgb FONDO = new DeviceRgb(241, 245, 249);
    private static final DeviceRgb FONDO_AZUL = new DeviceRgb(219, 234, 254);
    private static final DeviceRgb BLANCO = new DeviceRgb(255, 255, 255);

    private static final String[] PALETA = {
        "#2563eb", "#7c3aed", "#0891b2", "#16a34a", "#f59e0b", "#ec4899", "#94a3b8"};

    private final PdfFont normal;
    private final PdfFont negrita;

    private InformeGastosPdf() throws IOException {
        normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        negrita = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
    }

    public static void generar(String ruta, LocalDate inicio, LocalDate fin, List<Gasto> gastos) throws Exception {
        new InformeGastosPdf().crear(ruta, inicio, fin, gastos);
    }

    private void crear(String ruta, LocalDate inicio, LocalDate fin, List<Gasto> gastos) throws Exception {
        PdfDocument pdf = new PdfDocument(new PdfWriter(ruta));
        pdf.getDocumentInfo().setTitle("Informe de gastos");
        pdf.getDocumentInfo().setCreator(Diseno.APP);

        Document doc = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 36, 50, 36);
        doc.setFont(normal);
        doc.setFontSize(9);
        doc.setFontColor(TINTA);

        Pie pie = new Pie();
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, pie);

        encabezado(doc, inicio, fin);

        if (gastos.isEmpty()) {
            doc.add(new Paragraph("No hay gastos registrados en este período.").setFontColor(GRIS));
            pie.escribirTotal(pdf);
            doc.close();
            return;
        }

        // ---------- Datos ----------
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal mayor = BigDecimal.ZERO;
        Map<String, BigDecimal> porCat = new HashMap<>();
        Map<YearMonth, BigDecimal> porMes = new TreeMap<>();
        for (Gasto g : gastos) {
            BigDecimal v = g.getValorTotal();
            total = total.add(v);
            if (v.compareTo(mayor) > 0) {
                mayor = v;
            }
            porCat.merge(g.getCategoria().getNombre(), v, BigDecimal::add);
            porMes.merge(YearMonth.from(g.getFecha()), v, BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> cats = new ArrayList<>(porCat.entrySet());
        cats.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        BigDecimal promedio = total.divide(BigDecimal.valueOf(gastos.size()), 0, RoundingMode.HALF_UP);
        long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
        BigDecimal promedioDiario = total.divide(BigDecimal.valueOf(Math.max(1, dias)), 0, RoundingMode.HALF_UP);
        YearMonth mesMayor = porMes.entrySet().stream()
            .max(Map.Entry.comparingByValue()).get().getKey();

        // ---------- Tarjetas ----------
        tarjetas(doc, new String[][] {
            {"Total gastado", Diseno.moneda(total)},
            {"Promedio por gasto", Diseno.moneda(promedio)},
            {"Categorías", Diseno.entero(cats.size())},
            {"Transacciones", Diseno.entero(gastos.size())}});

        // ---------- Detalle por categoría + distribución ----------
        Table fila = new Table(UnitValue.createPercentArray(new float[] {11f, 0.6f, 8f})).useAllAvailableWidth();
        fila.setMarginBottom(10);
        fila.addCell(base().add(tablaDetalle(cats, total)));
        fila.addCell(base());
        fila.addCell(base().add(panelDistribucion(cats, total)));
        doc.add(fila);

        // ---------- Gasto por mes (solo si hay más de un mes) ----------
        if (porMes.size() > 1) {
            doc.add(barraTitulo("Gasto por mes"));
            Table tm = new Table(UnitValue.createPercentArray(new float[] {4f, 3f, 2f})).useAllAvailableWidth();
            tm.setMarginBottom(10);
            tm.addCell(celdaEnc("Mes", TextAlignment.LEFT));
            tm.addCell(celdaEnc("Monto", TextAlignment.RIGHT));
            tm.addCell(celdaEnc("Porcentaje", TextAlignment.RIGHT));
            boolean zebra = false;
            for (Map.Entry<YearMonth, BigDecimal> e : porMes.entrySet()) {
                DeviceRgb bg = zebra ? FONDO : BLANCO;
                tm.addCell(celda(Diseno.etiquetaMes(e.getKey()), TextAlignment.LEFT, false, bg));
                tm.addCell(celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, false, bg));
                tm.addCell(celda(Diseno.porcentaje(pct(e.getValue(), total)), TextAlignment.RIGHT, false, bg));
                zebra = !zebra;
            }
            doc.add(tm);
        }

        // ---------- Resumen estadístico ----------
        doc.add(barraTitulo("Resumen estadístico"));
        tarjetas(doc, new String[][] {
            {"Mayor gasto individual", Diseno.moneda(mayor)},
            {"Promedio diario", Diseno.moneda(promedioDiario)},
            {"Categoría principal", corto(cats.get(0).getKey(), 18)},
            {"Mes de mayor gasto", Diseno.etiquetaMes(mesMayor)}});

        pie.escribirTotal(pdf);
        doc.close();
    }

    // ==================== BLOQUES ====================

    private void encabezado(Document doc, LocalDate inicio, LocalDate fin) {
        Table t = new Table(UnitValue.createPercentArray(new float[] {3f, 4.5f, 2.5f})).useAllAvailableWidth();

        Cell marca = base();
        marca.add(new Paragraph(Diseno.APP).setFont(negrita).setFontSize(20).setFontColor(AZUL).setMargin(0));
        marca.add(new Paragraph("Controla · Analiza · Ahorra").setFontSize(8).setFontColor(GRIS).setMargin(0));
        t.addCell(marca);

        Cell titulo = base().setBorderLeft(new SolidBorder(AZUL, 1.5f)).setPaddingLeft(12);
        titulo.add(new Paragraph("INFORME DE GASTOS").setFont(negrita).setFontSize(18).setFontColor(NAVY).setMargin(0));
        titulo.add(new Paragraph("Período: " + Diseno.fecha(inicio) + " - " + Diseno.fecha(fin))
            .setFontSize(10).setFontColor(TINTA).setMargin(0).setMarginTop(2));
        t.addCell(titulo);

        Cell fecha = base().setTextAlignment(TextAlignment.RIGHT);
        fecha.add(new Paragraph("Fecha de generación").setFontSize(8).setFontColor(GRIS).setMargin(0));
        fecha.add(new Paragraph(Diseno.fechaHoraActual()).setFont(negrita).setFontSize(9).setMargin(0));
        t.addCell(fecha);
        doc.add(t);

        SolidLine linea = new SolidLine(1f);
        linea.setColor(AZUL);
        doc.add(new LineSeparator(linea).setMarginTop(6).setMarginBottom(10));
    }

    private void tarjetas(Document doc, String[][] items) {
        DeviceRgb[] acentos = {AZUL, new DeviceRgb(22, 163, 74), new DeviceRgb(124, 58, 237), new DeviceRgb(8, 145, 178)};
        int n = items.length;
        float[] w = new float[2 * n - 1];
        for (int i = 0; i < w.length; i++) {
            w[i] = i % 2 == 0 ? 10f : 0.6f;
        }
        Table t = new Table(UnitValue.createPercentArray(w)).useAllAvailableWidth();
        t.setMarginBottom(10);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                t.addCell(base());
            }
            Cell c = new Cell()
                .setBorder(new SolidBorder(LINEA, 0.8f))
                .setBorderLeft(new SolidBorder(acentos[i % acentos.length], 3f))
                .setBackgroundColor(FONDO)
                .setPadding(7);
            c.add(new Paragraph(items[i][0]).setFontSize(7.5f).setFontColor(GRIS).setMargin(0));
            c.add(new Paragraph(items[i][1]).setFont(negrita).setFontSize(13).setFontColor(NAVY).setMargin(0).setMarginTop(2));
            t.addCell(c);
        }
        doc.add(t);
    }

    private Table tablaDetalle(List<Map.Entry<String, BigDecimal>> cats, BigDecimal total) {
        Table t = new Table(UnitValue.createPercentArray(new float[] {5f, 3f, 2f})).useAllAvailableWidth();
        t.addCell(celdaTitulo("Detalle por categoría", 3));
        t.addCell(celdaEnc("Categoría", TextAlignment.LEFT));
        t.addCell(celdaEnc("Monto", TextAlignment.RIGHT));
        t.addCell(celdaEnc("Porcentaje", TextAlignment.RIGHT));
        boolean zebra = false;
        for (Map.Entry<String, BigDecimal> e : cats) {
            DeviceRgb bg = zebra ? FONDO : BLANCO;
            t.addCell(celda(e.getKey(), TextAlignment.LEFT, false, bg));
            t.addCell(celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, false, bg));
            t.addCell(celda(Diseno.porcentaje(pct(e.getValue(), total)), TextAlignment.RIGHT, false, bg));
            zebra = !zebra;
        }
        t.addCell(celda("Total", TextAlignment.LEFT, true, FONDO_AZUL).setBorderTop(new SolidBorder(NAVY, 0.9f)));
        t.addCell(celda(Diseno.moneda(total), TextAlignment.RIGHT, true, FONDO_AZUL).setBorderTop(new SolidBorder(NAVY, 0.9f)));
        t.addCell(celda("100,0%", TextAlignment.RIGHT, true, FONDO_AZUL).setBorderTop(new SolidBorder(NAVY, 0.9f)));
        return t;
    }

    private Table panelDistribucion(List<Map.Entry<String, BigDecimal>> cats, BigDecimal total) throws IOException {
        // Hasta 6 categorías; el resto se agrupa en "Otras"
        List<String> nombres = new ArrayList<>();
        List<BigDecimal> montos = new ArrayList<>();
        BigDecimal otras = BigDecimal.ZERO;
        for (int i = 0; i < cats.size(); i++) {
            if (i < 6) {
                nombres.add(cats.get(i).getKey());
                montos.add(cats.get(i).getValue());
            } else {
                otras = otras.add(cats.get(i).getValue());
            }
        }
        if (otras.signum() > 0) {
            nombres.add("Otras");
            montos.add(otras);
        }

        Table panel = new Table(1).useAllAvailableWidth();
        panel.addCell(celdaTitulo("Distribución del gasto", 1));

        Cell cuerpo = new Cell().setBorder(new SolidBorder(LINEA, 0.8f)).setPadding(8);
        byte[] png = donutPng(montos, total);
        cuerpo.add(new Image(ImageDataFactory.create(png)).setWidth(130).setHeight(130)
            .setHorizontalAlignment(HorizontalAlignment.CENTER));

        Table leyenda = new Table(UnitValue.createPercentArray(new float[] {0.6f, 4f, 1.6f})).useAllAvailableWidth();
        leyenda.setMarginTop(6);
        for (int i = 0; i < nombres.size(); i++) {
            leyenda.addCell(new Cell().setBorder(new SolidBorder(BLANCO, 2f))
                .setBackgroundColor(colorPaleta(i)).setHeight(8).setPadding(0));
            leyenda.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1).setPaddingLeft(4)
                .add(new Paragraph(corto(nombres.get(i), 22)).setFontSize(8).setMargin(0)));
            leyenda.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1).setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(Diseno.porcentaje(pct(montos.get(i), total))).setFontSize(8).setMargin(0)));
        }
        cuerpo.add(leyenda);
        panel.addCell(cuerpo);
        return panel;
    }

    private Table barraTitulo(String texto) {
        Table t = new Table(1).useAllAvailableWidth();
        t.setMarginTop(2);
        t.addCell(celdaTitulo(texto, 1));
        return t;
    }

    // ==================== CELDAS ====================

    private Cell base() {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(0);
    }

    private Cell celdaTitulo(String texto, int colspan) {
        return new Cell(1, colspan).setBorder(Border.NO_BORDER).setBackgroundColor(NAVY)
            .setPaddingTop(6).setPaddingBottom(6).setPaddingLeft(8)
            .add(new Paragraph(texto).setFont(negrita).setFontSize(10).setFontColor(BLANCO).setMargin(0));
    }

    private Cell celdaEnc(String texto, TextAlignment al) {
        return new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(FONDO_AZUL)
            .setBorderBottom(new SolidBorder(NAVY, 0.8f))
            .setPadding(5).setTextAlignment(al).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .add(new Paragraph(texto).setFont(negrita).setFontSize(8.5f).setFontColor(NAVY).setMargin(0));
    }

    private Cell celda(String texto, TextAlignment al, boolean fuerte, DeviceRgb fondo) {
        return new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(fondo)
            .setBorderBottom(new SolidBorder(LINEA, 0.4f))
            .setPadding(5).setTextAlignment(al).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .add(new Paragraph(texto == null ? "" : texto).setFont(fuerte ? negrita : normal)
                .setFontSize(8.5f).setMargin(0));
    }

    // ==================== AUXILIARES ====================

    private static double pct(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? 0 : parte.doubleValue() / total.doubleValue() * 100;
    }

    private static String corto(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static Color awt(int i) {
        return Color.decode(PALETA[i % PALETA.length]);
    }

    private static DeviceRgb colorPaleta(int i) {
        Color c = awt(i);
        return new DeviceRgb(c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Anillo dibujado con Java2D (PNG con fondo transparente) y el total al centro. */
    private static byte[] donutPng(List<BigDecimal> montos, BigDecimal total) throws IOException {
        final int s = 600;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double suma = total.doubleValue() <= 0 ? 1 : total.doubleValue();
        double angulo = 90;
        for (int i = 0; i < montos.size(); i++) {
            double extension = -360.0 * montos.get(i).doubleValue() / suma;
            Arc2D.Double arco = new Arc2D.Double(20, 20, s - 40, s - 40, angulo, extension, Arc2D.PIE);
            g.setColor(awt(i));
            g.fill(arco);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(5f));
            g.draw(arco);
            angulo += extension;
        }

        double d = (s - 40) * 0.60;
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double((s - d) / 2, (s - d) / 2, d, d));

        String monto = Diseno.moneda(total);
        int tam = 52;
        FontMetrics fm;
        do {
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, tam));
            fm = g.getFontMetrics();
            tam -= 2;
        } while (fm.stringWidth(monto) > d * 0.86 && tam > 14);
        g.setColor(new Color(30, 58, 138));
        g.drawString(monto, (s - fm.stringWidth(monto)) / 2, s / 2 + fm.getAscent() / 3);

        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 30));
        fm = g.getFontMetrics();
        g.setColor(new Color(100, 116, 139));
        g.drawString("Total gastado", (s - fm.stringWidth("Total gastado")) / 2, s / 2 - 40);

        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ==================== PIE DE PÁGINA ====================

    private class Pie implements IEventHandler {
        private final PdfFormXObject total = new PdfFormXObject(new Rectangle(0, 0, 24, 12));

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent ev = (PdfDocumentEvent) event;
            PdfPage pagina = ev.getPage();
            Rectangle tam = pagina.getPageSize();
            int numero = ev.getDocument().getPageNumber(pagina);
            float izq = 36;
            float der = tam.getWidth() - 36;
            float y = 24;

            PdfCanvas c = new PdfCanvas(pagina.newContentStreamAfter(), pagina.getResources(), ev.getDocument());
            c.setStrokeColor(AZUL).setLineWidth(0.8f).moveTo(izq, y + 12).lineTo(der, y + 12).stroke();

            c.beginText().setFontAndSize(negrita, 7.5f).setColor(AZUL, true).moveText(izq, y)
                .showText(Diseno.APP + " - Tu aliado en el control de tus finanzas").endText();

            String texto = "Página " + numero + " de ";
            float ancho = normal.getWidth(texto, 8);
            c.beginText().setFontAndSize(normal, 8).setColor(GRIS, true)
                .moveText(der - 24 - ancho, y).showText(texto).endText();
            c.addXObjectAt(total, der - 24, y);
            c.release();
        }

        void escribirTotal(PdfDocument pdf) {
            Canvas canvas = new Canvas(total, pdf);
            canvas.showTextAligned(new Paragraph(String.valueOf(pdf.getNumberOfPages()))
                .setFont(normal).setFontSize(8).setFontColor(GRIS), 0, 0, TextAlignment.LEFT);
            canvas.close();
        }
    }
}