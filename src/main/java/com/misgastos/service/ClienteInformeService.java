package com.misgastos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.misgastos.model.Cliente;
import com.misgastos.model.Gasto;
import com.misgastos.repository.ClienteRepository;
import com.misgastos.service.diseno.Diseno;
import com.misgastos.service.diseno.ExcelEstilo;
import com.misgastos.service.diseno.ExcelEstilo.Rol;
import com.misgastos.service.diseno.HojaExcel;
import com.misgastos.service.diseno.PdfEstilo;
import com.misgastos.service.diseno.PdfEstilo.Fila;

/**
 * Informe "Total gastos (parqueaderos y bonos)": por cada cliente (cédula) suma sus
 * bonos y parqueaderos. Es de solo lectura: no cambia ningún gasto.
 */
@Service
public class ClienteInformeService {

    public static final String TITULO = "Total gastos (parqueaderos y bonos)";

    public enum Tipo { TODOS, BONOS, PARQUEADEROS }

    public enum Orden { MAYOR_TOTAL, NOMBRE }

    public record FilaCliente(String cedula, String nombre, BigDecimal bonos, BigDecimal parqueaderos) {
        public BigDecimal total() {
            return bonos.add(parqueaderos);
        }
    }

    public record Resultado(List<FilaCliente> filas, BigDecimal totalBonos, BigDecimal totalParqueaderos) {
        public BigDecimal total() {
            return totalBonos.add(totalParqueaderos);
        }
    }

    @Autowired
    private GastoService gastoService;

    @Autowired
    private ClienteRepository clienteRepository;

    // ==================== DATOS ====================

    public Resultado generar(LocalDate inicio, LocalDate fin, Tipo tipo, String texto, Orden orden) {
        Map<String, String> nombresLista = new HashMap<>();
        for (Cliente c : clienteRepository.findAll()) {
            nombresLista.put(c.getCedula(), c.getNombre());
        }

        Map<String, BigDecimal[]> sumas = new LinkedHashMap<>();
        Map<String, String> nombresGasto = new HashMap<>();

        for (Gasto g : gastoService.listarPorPeriodo(inicio, fin)) {
            if (g.getCategoria() == null || g.getSubcategoria() == null) continue;
            if (!"Beneficios Clientes".equalsIgnoreCase(g.getCategoria().getNombre())) continue;

            String cedula = g.getCedula() == null ? "" : g.getCedula().trim();
            if (cedula.isEmpty()) continue;

            String sub = g.getSubcategoria().getNombre().toLowerCase();
            boolean esBono = sub.contains("bono");
            boolean esParqueadero = !esBono && (sub.contains("parqueo") || sub.contains("parqueadero"));
            if (!esBono && !esParqueadero) continue;
            if (tipo == Tipo.BONOS && !esBono) continue;
            if (tipo == Tipo.PARQUEADEROS && !esParqueadero) continue;

            BigDecimal valor = g.getValorTotal() == null ? BigDecimal.ZERO : g.getValorTotal();
            BigDecimal[] par = sumas.computeIfAbsent(cedula,
                k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            if (esBono) {
                par[0] = par[0].add(valor);
            } else {
                par[1] = par[1].add(valor);
            }

            String nom = g.getNombrePersona() == null ? "" : g.getNombrePersona().trim();
            if (!nom.isEmpty()) {
                nombresGasto.put(cedula, nom);
            }
        }

        String q = texto == null ? "" : texto.trim().toLowerCase();
        List<FilaCliente> filas = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : sumas.entrySet()) {
            String cedula = e.getKey();
            String nombre = nombresLista.getOrDefault(cedula, nombresGasto.getOrDefault(cedula, ""));
            if (!q.isEmpty() && !cedula.toLowerCase().contains(q) && !nombre.toLowerCase().contains(q)) {
                continue;
            }
            filas.add(new FilaCliente(cedula, nombre, e.getValue()[0], e.getValue()[1]));
        }

        if (orden == Orden.NOMBRE) {
            filas.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));
        } else {
            filas.sort((a, b) -> {
                int c = b.total().compareTo(a.total());
                return c != 0 ? c : a.nombre().compareToIgnoreCase(b.nombre());
            });
        }

        BigDecimal totalBonos = BigDecimal.ZERO;
        BigDecimal totalParqueaderos = BigDecimal.ZERO;
        for (FilaCliente f : filas) {
            totalBonos = totalBonos.add(f.bonos());
            totalParqueaderos = totalParqueaderos.add(f.parqueaderos());
        }
        return new Resultado(filas, totalBonos, totalParqueaderos);
    }

    // ==================== EXCEL ====================

    public void exportarExcel(Resultado r, String periodo, String filtros, String rutaArchivo) throws Exception {
        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Parqueaderos y bonos", 5, true);

        hoja.titulo(TITULO, subtitulo(periodo, filtros, r.filas().size()));

        HorizontalAlignment l = HorizontalAlignment.LEFT;
        HorizontalAlignment d = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Cédula", "Nombre", "Bonos", "Parqueaderos", "Total"},
            new HorizontalAlignment[] {l, l, d, d, d});

        int primera = hoja.siguienteFila();
        for (FilaCliente f : r.filas()) {
            hoja.fila(Rol.DATO)
                .texto(f.cedula())
                .texto(f.nombre())
                .moneda(f.bonos())
                .moneda(f.parqueaderos())
                .moneda(f.total());
        }
        int ultima = hoja.siguienteFila() - 1;

        // SUBTOTAL: al filtrar en Excel, suma solo las filas visibles
        HojaExcel.FilaExcel total = hoja.fila(Rol.TOTAL).texto("TOTAL");
        total.vacio();
        if (r.filas().isEmpty()) {
            total.moneda(BigDecimal.ZERO);
            total.moneda(BigDecimal.ZERO);
            total.moneda(BigDecimal.ZERO);
        } else {
            for (int col = 2; col <= 4; col++) {
                total.formulaMoneda("SUBTOTAL(109," + HojaExcel.ref(col, primera) + ":"
                    + HojaExcel.ref(col, ultima) + ")");
            }
        }

        hoja.terminar(true, true);
        estilos.guardar(rutaArchivo);
    }

    // ==================== PDF ====================

    public void exportarPDF(Resultado r, String periodo, String filtros, String rutaArchivo) throws Exception {
        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, TITULO, true)) {
            pdf.tamTabla(8.5f);
            pdf.titulo(TITULO, periodo + (filtros == null || filtros.isBlank() ? "" : "  |  " + filtros));
            pdf.indicadores(
                new String[] {"Clientes", Diseno.entero(r.filas().size())},
                new String[] {"Bonos + parqueaderos", Diseno.moneda(r.total())});

            TextAlignment izq = TextAlignment.LEFT;
            TextAlignment der = TextAlignment.RIGHT;

            Table tabla = pdf.tabla(16f, 42f, 14f, 14f, 14f);
            pdf.encabezado(tabla,
                new String[] {"Cédula", "Nombre", "Bonos", "Parqueaderos", "Total"},
                new TextAlignment[] {izq, izq, der, der, der});

            boolean zebra = false;
            for (FilaCliente f : r.filas()) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tabla.addCell(pdf.celda(f.cedula(), izq, tipo));
                tabla.addCell(pdf.celda(f.nombre(), izq, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(f.bonos()), der, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(f.parqueaderos()), der, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(f.total()), der, tipo));
                zebra = !zebra;
            }

            tabla.addCell(pdf.celda("TOTAL", izq, Fila.TOTAL));
            tabla.addCell(pdf.celda("", izq, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(r.totalBonos()), der, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(r.totalParqueaderos()), der, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(r.total()), der, Fila.TOTAL));

            pdf.agregar(tabla);
        }
    }
    
    // ==================== VISTA PREVIA (texto) ====================

    public String textoVistaPrevia(Resultado r, String periodo, String filtros) {
        String formato = "%-14s %-36s %15s %15s %15s%n";
        String raya = "-".repeat(99);
        StringBuilder sb = new StringBuilder();
        sb.append(TITULO.toUpperCase()).append("\n");
        sb.append("Periodo: ").append(periodo);
        if (filtros != null && !filtros.isBlank()) {
            sb.append("   |   ").append(filtros);
        }
        sb.append("\n").append("=".repeat(99)).append("\n");
        sb.append(String.format(formato, "CEDULA", "NOMBRE", "BONOS", "PARQUEADEROS", "TOTAL"));
        sb.append(raya).append("\n");
        if (r.filas().isEmpty()) {
            sb.append("No hay bonos ni parqueaderos con estos filtros.\n");
        }
        for (FilaCliente f : r.filas()) {
            sb.append(String.format(formato, recortar(f.cedula(), 14), recortar(f.nombre(), 36),
                Diseno.moneda(f.bonos()), Diseno.moneda(f.parqueaderos()), Diseno.moneda(f.total())));
        }
        sb.append(raya).append("\n");
        sb.append(String.format(formato, "TOTAL", "",
            Diseno.moneda(r.totalBonos()), Diseno.moneda(r.totalParqueaderos()), Diseno.moneda(r.total())));
        return sb.toString();
    }

    private String recortar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    // ==================== AUXILIARES ====================

    private String subtitulo(String periodo, String filtros, int clientes) {
        StringBuilder sb = new StringBuilder(periodo);
        if (filtros != null && !filtros.isBlank()) {
            sb.append("  |  ").append(filtros);
        }
        sb.append("  |  ").append(Diseno.entero(clientes)).append(" clientes");
        sb.append("  |  Generado el ").append(Diseno.fechaHoraActual());
        return sb.toString();
    }
}