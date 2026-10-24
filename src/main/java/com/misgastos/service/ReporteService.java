package com.misgastos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.misgastos.model.Gasto;
import com.misgastos.service.diseno.Diseno;
import com.misgastos.service.diseno.ExcelEstilo;
import com.misgastos.service.diseno.ExcelEstilo.Rol;
import com.misgastos.service.diseno.HojaExcel;
import com.misgastos.service.diseno.PdfEstilo;
import com.misgastos.service.diseno.PdfEstilo.Fila;

/**
 * Exportación de listados de gastos a Excel y PDF con el diseño común de la aplicación.
 * Incluye quién registró el gasto y, para los bonos, la cédula y el nombre de la persona.
 */
@Service
public class ReporteService {

    private static final DateTimeFormatter HORA_MIN = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private GastoService gastoService;

    // ==================== EXCEL ====================

    public void exportarGastosExcel(String rutaArchivo) throws Exception {
        generarExcel(gastoService.listarTodos(), rutaArchivo, "Todos los gastos", null);
    }

    public void exportarGastosPorPeriodoExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        generarExcel(gastoService.listarPorPeriodo(inicio, fin), rutaArchivo,
            "Gastos por período", Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));
    }

    private void generarExcel(List<Gasto> datos, String rutaArchivo, String titulo, String periodo) throws Exception {
        List<Gasto> gastos = ordenar(datos);

        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Gastos", 13, true);

        hoja.titulo(titulo, subtitulo(periodo, gastos.size()));

        HorizontalAlignment c = HorizontalAlignment.CENTER;
        HorizontalAlignment l = HorizontalAlignment.LEFT;
        HorizontalAlignment r = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"ID", "Fecha", "Hora", "Registrado por", "Categoría", "Subcategoría", "Producto",
                "Cédula", "Nombre", "Cantidad", "Valor unit.", "Total", "Notas"},
            new HorizontalAlignment[] {c, c, c, l, l, l, l, l, l, r, r, r, l});

        int primera = hoja.siguienteFila();
        for (Gasto g : gastos) {
            hoja.fila(Rol.DATO)
                .id(g.getId())
                .fecha(g.getFecha())
                .hora(g.getHora())
                .texto(usuarioDe(g))
                .texto(g.getCategoria().getNombre())
                .texto(g.getSubcategoria().getNombre())
                .texto(g.getProducto())
                .texto(g.getCedula())
                .texto(g.getNombrePersona())
                .entero(g.getCantidad())
                .moneda(g.getValorUnitario())
                .moneda(g.getValorTotal())
                .texto(g.getNotas());
        }
        int ultima = hoja.siguienteFila() - 1;

        // El total usa SUBTOTAL: al filtrar en Excel, suma solo las filas visibles
        HojaExcel.FilaExcel total = hoja.fila(Rol.TOTAL).texto("TOTAL");
        for (int i = 0; i < 10; i++) {
            total.vacio();
        }
        if (gastos.isEmpty()) {
            total.moneda(BigDecimal.ZERO);
        } else {
            total.formulaMoneda("SUBTOTAL(109," + HojaExcel.ref(11, primera) + ":" + HojaExcel.ref(11, ultima) + ")");
        }

        hoja.terminar(true, true);
        estilos.guardar(rutaArchivo);
    }

    // ==================== PDF ====================

    public void exportarGastosPDF(String rutaArchivo) throws Exception {
        generarPDF(gastoService.listarTodos(), rutaArchivo, "Reporte de todos los gastos", null);
    }

    public void exportarGastosPorPeriodoPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        generarPDF(gastoService.listarPorPeriodo(inicio, fin), rutaArchivo,
            "Reporte de gastos", Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));
    }

    private void generarPDF(List<Gasto> datos, String rutaArchivo, String titulo, String periodo) throws Exception {
        List<Gasto> gastos = ordenar(datos);

        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (Gasto g : gastos) {
            if (g.getValorTotal() != null) {
                totalGeneral = totalGeneral.add(g.getValorTotal());
            }
        }

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Reporte de gastos", true)) {
            pdf.tamTabla(7.5f);
            pdf.titulo(titulo, periodo);
            pdf.indicadores(
                new String[] {"Registros", Diseno.entero(gastos.size())},
                new String[] {"Total gastado", Diseno.moneda(totalGeneral)});

            TextAlignment izq = TextAlignment.LEFT;
            TextAlignment cen = TextAlignment.CENTER;
            TextAlignment der = TextAlignment.RIGHT;

            Table tabla = pdf.tabla(8f, 6f, 10f, 12f, 12f, 17f, 9f, 13f, 5f, 9f);
            pdf.encabezado(tabla,
                new String[] {"Fecha", "Hora", "Registrado por", "Categoría", "Subcategoría", "Producto",
                    "Cédula", "Nombre", "Cant.", "Total"},
                new TextAlignment[] {cen, cen, izq, izq, izq, izq, izq, izq, cen, der});

            boolean zebra = false;
            for (Gasto g : gastos) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tabla.addCell(pdf.celda(Diseno.fecha(g.getFecha()), cen, tipo));
                tabla.addCell(pdf.celda(hora(g.getHora()), cen, tipo));
                tabla.addCell(pdf.celda(usuarioDe(g), izq, tipo));
                tabla.addCell(pdf.celda(g.getCategoria().getNombre(), izq, tipo));
                tabla.addCell(pdf.celda(g.getSubcategoria().getNombre(), izq, tipo));
                tabla.addCell(pdf.celda(g.getProducto(), izq, tipo));
                tabla.addCell(pdf.celda(g.getCedula(), izq, tipo));
                tabla.addCell(pdf.celda(g.getNombrePersona(), izq, tipo));
                tabla.addCell(pdf.celda(String.valueOf(g.getCantidad()), cen, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(g.getValorTotal()), der, tipo));
                zebra = !zebra;
            }

            // Fila de total
            tabla.addCell(pdf.celda("TOTAL", izq, Fila.TOTAL));
            for (int i = 0; i < 8; i++) {
                tabla.addCell(pdf.celda("", izq, Fila.TOTAL));
            }
            tabla.addCell(pdf.celda(Diseno.moneda(totalGeneral), der, Fila.TOTAL));

            pdf.agregar(tabla);
        }
    }

    // ==================== AUXILIARES ====================

    /** Orden cronológico: fecha y hora ascendente. */
    private List<Gasto> ordenar(List<Gasto> gastos) {
        return gastos.stream()
            .sorted(Comparator.comparing(Gasto::getFecha).thenComparing(Gasto::getHora))
            .toList();
    }

    private String usuarioDe(Gasto g) {
        return g.getUsuario() == null ? "" : g.getUsuario().getUsername();
    }

    private String hora(LocalTime hora) {
        return hora == null ? "" : hora.format(HORA_MIN);
    }

    private String subtitulo(String periodo, int registros) {
        String base = Diseno.entero(registros) + " registros  |  Generado el " + Diseno.fechaHoraActual();
        return periodo == null ? base : periodo + "  |  " + base;
    }
}