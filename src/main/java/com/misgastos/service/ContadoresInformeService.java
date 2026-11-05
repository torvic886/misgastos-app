package com.misgastos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.properties.TextAlignment;
import com.misgastos.model.FilaContador;
import com.misgastos.model.Lectura;
import com.misgastos.model.Maquina;
import com.misgastos.service.diseno.Diseno;
import com.misgastos.service.diseno.ExcelEstilo;
import com.misgastos.service.diseno.ExcelEstilo.Rol;
import com.misgastos.service.diseno.HojaExcel;
import com.misgastos.service.diseno.PdfEstilo;
import com.misgastos.service.diseno.PdfEstilo.Fila;

/**
 * Informes de máquinas: "Contadores por rango" y "Casino Victory Armenia".
 * Solo lectura: no cambia ninguna lectura ni máquina.
 */
@Service
public class ContadoresInformeService {

    public static final String TITULO_CONTADORES = "Informe de contadores";
    public static final String TITULO_CASINO = "Informe Casino Victory Armenia";

    private static final Locale ES = new Locale("es");
    private static final DateTimeFormatter CORTA = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter LARGA = DateTimeFormatter.ofPattern("dd-MMM-yyyy", ES);

    public record GrupoMaquina(Maquina maquina, List<FilaContador> filas, BigDecimal utilidad) {
    }

    public record ResultadoContadores(List<GrupoMaquina> grupos, BigDecimal total, int lecturas) {
    }

    public record FilaCasino(Maquina maquina, BigDecimal utilidad, boolean baja) {
        public String codigoTexto() {
            return maquina.getCodigo() + (baja ? " (baja)" : "");
        }
    }

    public record ResultadoCasino(List<FilaCasino> filas, BigDecimal total) {
    }

    @Autowired
    private LecturaService lecturaService;

    @Autowired
    private MaquinaService maquinaService;

    // ==================== DATOS ====================

    public ResultadoContadores generarContadores(LocalDate desde, LocalDate hasta) {
        // filas(null, ...) devuelve las máquinas por código y las lecturas por fecha
        Map<Long, List<FilaContador>> porMaquina = new LinkedHashMap<>();
        for (FilaContador f : lecturaService.filas(null, desde, hasta)) {
            // La COPIA repite los contadores del periodo 3 y no tiene total: no se muestra
            if (Lectura.COPIA.equals(f.lectura().getTipo())) {
                continue;
            }
            porMaquina.computeIfAbsent(f.lectura().getMaquina().getId(), k -> new ArrayList<>()).add(f);
        }

        List<GrupoMaquina> grupos = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int lecturas = 0;
        for (List<FilaContador> filas : porMaquina.values()) {
            BigDecimal utilidad = BigDecimal.ZERO;
            for (FilaContador f : filas) {
                if (f.total() != null) {
                    utilidad = utilidad.add(f.total());
                }
            }
            grupos.add(new GrupoMaquina(filas.get(0).lectura().getMaquina(), filas, utilidad));
            total = total.add(utilidad);
            lecturas += filas.size();
        }
        return new ResultadoContadores(grupos, total, lecturas);
    }

    public ResultadoCasino generarCasino(LocalDate desde, LocalDate hasta) {
        List<Maquina> todas = new ArrayList<>(maquinaService.listarActivas());
        todas.addAll(maquinaService.listarBajas());
        todas.sort((a, b) -> a.getCodigo().compareTo(b.getCodigo()));

        List<FilaCasino> filas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Maquina m : todas) {
            boolean baja = !m.isActiva();
            // Las máquinas de baja solo salen si tuvieron lecturas en el rango
            if (baja && !lecturaService.tieneLecturas(m, desde, hasta)) {
                continue;
            }
            BigDecimal utilidad = lecturaService.utilidad(m, desde, hasta);
            filas.add(new FilaCasino(m, utilidad, baja));
            total = total.add(utilidad);
        }
        return new ResultadoCasino(filas, total);
    }

    // ==================== VISTA PREVIA ====================

    public String textoContadores(LocalDate desde, LocalDate hasta) {
        ResultadoContadores r = generarContadores(desde, hasta);
        String fmt = "%-9s %-4s %-11s %-8s %15s %15s %14s %15s %15s%n";
        String raya = "-".repeat(114);
        StringBuilder sb = new StringBuilder();
        sb.append(TITULO_CONTADORES.toUpperCase()).append("\n");
        sb.append("Del ").append(fechaLarga(desde)).append(" al ").append(fechaLarga(hasta)).append("\n");
        sb.append("=".repeat(114)).append("\n");
        sb.append(String.format(fmt, "Fecha", "Den", "Num_maquina", "Periodo", "Contador_In",
            "Contador_Out", "Prem(Jackpot)", "Diferencia", "Total"));
        sb.append(raya).append("\n");
        if (r.grupos().isEmpty()) {
            sb.append("No hay lecturas en este rango de fechas.\n");
            return sb.toString();
        }
        for (GrupoMaquina g : r.grupos()) {
            for (FilaContador f : g.filas()) {
                Lectura l = f.lectura();
                sb.append(String.format(fmt, l.getFecha().format(CORTA), den(l.getDenominacion()),
                    l.getMaquina().getCodigo(), l.getPeriodo(), num(l.getContadorIn()),
                    num(l.getContadorOut()), num(l.getContadorPrem()), peso(f.diferencia()),
                    textoTotal(f)));
            }
            sb.append(raya).append("\n");
            sb.append(String.format(fmt, "", "", "", "", "", "", "", "Total", peso(g.utilidad())));
            sb.append("\n");
        }
        return sb.toString();
    }

    public String textoCasino(LocalDate desde, LocalDate hasta) {
        ResultadoCasino r = generarCasino(desde, hasta);
        String fmt = "%-12s %-13s %-22s %-14s %-18s %16s%n";
        StringBuilder sb = new StringBuilder();
        sb.append(TITULO_CASINO.toUpperCase()).append("\n");
        sb.append("Fecha inicio: ").append(fechaLarga(desde)).append("     Fecha fin: ")
            .append(fechaLarga(hasta)).append("\n");
        sb.append("=".repeat(101)).append("\n");
        sb.append(String.format(fmt, "CODIGO", "DENOMINACION", "NOMBRE", "SERIAL", "JUEGO", "UTILIDAD"));
        sb.append("-".repeat(101)).append("\n");
        if (r.filas().isEmpty()) {
            sb.append("No hay máquinas para mostrar.\n");
            return sb.toString();
        }
        for (FilaCasino f : r.filas()) {
            Maquina m = f.maquina();
            sb.append(String.format(fmt, f.codigoTexto(), den(m.getDenominacion()), recortar(m.getNombre(), 22),
                recortar(m.getSerial(), 14), recortar(m.getJuego(), 18), Diseno.moneda(f.utilidad())));
        }
        sb.append("=".repeat(101)).append("\n");
        sb.append(String.format(fmt, "", "", "", "", "TOTAL", Diseno.moneda(r.total())));
        return sb.toString();
    }

    // ==================== PDF: CONTADORES ====================

    public void exportarContadoresPDF(LocalDate desde, LocalDate hasta, String ruta) throws Exception {
        ResultadoContadores r = generarContadores(desde, hasta);
        try (PdfEstilo pdf = new PdfEstilo(ruta, TITULO_CONTADORES, true)) {
            pdf.tamTabla(8f);
            pdf.titulo(TITULO_CONTADORES, "Del " + fechaLarga(desde) + " al " + fechaLarga(hasta));

            if (r.grupos().isEmpty()) {
                pdf.nota("No hay lecturas en este rango de fechas.");
                return;
            }

            pdf.indicadores(
                new String[] {"Máquinas", Diseno.entero(r.grupos().size())},
                new String[] {"Lecturas", Diseno.entero(r.lecturas())},
                new String[] {"Utilidad total", peso(r.total())});

            TextAlignment cen = TextAlignment.CENTER;
            TextAlignment izq = TextAlignment.LEFT;
            TextAlignment der = TextAlignment.RIGHT;

            Table tabla = pdf.tabla(1.2f, 0.7f, 1.2f, 0.9f, 1.8f, 1.8f, 1.6f, 1.8f, 1.7f);
            pdf.encabezado(tabla,
                new String[] {"Fecha", "Den", "Num_maquina", "Periodo", "Contador_In", "Contador_Out",
                    "Prem(Jackpot)", "Diferencia", "Total"},
                new TextAlignment[] {cen, cen, cen, cen, der, der, der, der, der});

            int n = 0;
            for (GrupoMaquina g : r.grupos()) {
                n++;
                boolean zebra = false;
                for (FilaContador f : g.filas()) {
                    Lectura l = f.lectura();
                    Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                    tabla.addCell(pdf.celda(l.getFecha().format(CORTA), cen, tipo));
                    tabla.addCell(pdf.celda(den(l.getDenominacion()), cen, tipo));
                    tabla.addCell(pdf.celda(String.valueOf(l.getMaquina().getCodigo()), cen, tipo));
                    tabla.addCell(pdf.celda(String.valueOf(l.getPeriodo()), cen, tipo));
                    tabla.addCell(pdf.celda(num(l.getContadorIn()), der, tipo));
                    tabla.addCell(pdf.celda(num(l.getContadorOut()), der, tipo));
                    tabla.addCell(pdf.celda(num(l.getContadorPrem()), der, tipo));
                    tabla.addCell(pdf.celda(peso(f.diferencia()), der, tipo));
                    tabla.addCell(pdf.celda(textoTotal(f), der, tipo, colorSigno(f.total())));
                    zebra = !zebra;
                }
                // Total de la máquina, bajo la columna Total
                for (int i = 0; i < 7; i++) {
                    tabla.addCell(pdf.celda("", izq, Fila.TOTAL));
                }
                tabla.addCell(pdf.celda("Total", der, Fila.TOTAL));
                tabla.addCell(pdf.celda(peso(g.utilidad()), der, Fila.TOTAL, colorSigno(g.utilidad())));

                if (n < r.grupos().size()) {
                    separador(pdf, tabla);
                }
            }

            pdf.agregar(tabla);
        }
    }

    // ==================== EXCEL: CONTADORES ====================

    public void exportarContadoresExcel(LocalDate desde, LocalDate hasta, String ruta) throws Exception {
        ResultadoContadores r = generarContadores(desde, hasta);
        String periodo = "Del " + fechaLarga(desde) + " al " + fechaLarga(hasta);
        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Contadores", 9, true);
        hoja.titulo(TITULO_CONTADORES, periodo);

        if (r.grupos().isEmpty()) {
            hoja.nota("No hay lecturas en este rango de fechas.");
            hoja.terminar(false, false);
            estilos.guardar(ruta);
            return;
        }

        HorizontalAlignment cen = HorizontalAlignment.CENTER;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Fecha", "Den", "Num_maquina", "Periodo", "Contador_In", "Contador_Out",
                "Prem(Jackpot)", "Diferencia", "Total"},
            new HorizontalAlignment[] {cen, cen, cen, cen, der, der, der, der, der});

        int n = 0;
        for (GrupoMaquina g : r.grupos()) {
            n++;
            int primera = hoja.siguienteFila();
            for (FilaContador f : g.filas()) {
                Lectura l = f.lectura();
                HojaExcel.FilaExcel fila = hoja.fila(Rol.DATO)
                    .fecha(l.getFecha())
                    .centro(den(l.getDenominacion()))
                    .id(l.getMaquina().getCodigo())
                    .id(l.getPeriodo())
                    .moneda(BigDecimal.valueOf(l.getContadorIn()))
                    .moneda(BigDecimal.valueOf(l.getContadorOut()))
                    .moneda(BigDecimal.valueOf(l.getContadorPrem()))
                    .moneda(f.diferencia());
                if (f.total() != null) {
                    fila.moneda(f.total());
                } else {
                    fila.centro(textoTotal(f));
                }
            }
            int ultima = hoja.siguienteFila() - 1;

            // Total de la máquina: suma de la columna Total de sus filas
            HojaExcel.FilaExcel total = hoja.fila(Rol.TOTAL);
            for (int i = 0; i < 7; i++) {
                total.vacio();
            }
            total.texto("Total");
            total.formulaMoneda("SUM(" + HojaExcel.ref(8, primera) + ":" + HojaExcel.ref(8, ultima) + ")");

            if (n < r.grupos().size()) {
                hoja.espacio();
            }
        }

        hoja.terminar(false, true);
        estilos.guardar(ruta);
    }

    // ==================== PDF: CASINO VICTORY ====================

    public void exportarCasinoPDF(LocalDate desde, LocalDate hasta, String ruta) throws Exception {
        ResultadoCasino r = generarCasino(desde, hasta);
        try (PdfEstilo pdf = new PdfEstilo(ruta, TITULO_CASINO, false)) {
            pdf.tamTabla(8.5f);
            pdf.titulo(TITULO_CASINO, null);
            pdf.propiedad("Fecha inicio", fechaLarga(desde));
            pdf.propiedad("Fecha fin", fechaLarga(hasta));

            if (r.filas().isEmpty()) {
                pdf.nota("No hay máquinas para mostrar en este rango de fechas.");
                return;
            }

            pdf.indicadores(
                new String[] {"Máquinas", Diseno.entero(r.filas().size())},
                new String[] {"Total neto", Diseno.moneda(r.total())});

            TextAlignment izq = TextAlignment.LEFT;
            TextAlignment cen = TextAlignment.CENTER;
            TextAlignment der = TextAlignment.RIGHT;

            Table tabla = pdf.tabla(1.3f, 1.3f, 2.2f, 2f, 2.2f, 2f);
            pdf.encabezado(tabla,
                new String[] {"Código", "Denominación", "Nombre", "Serial", "Juego", "Utilidad"},
                new TextAlignment[] {cen, cen, izq, izq, izq, der});

            boolean zebra = false;
            for (FilaCasino f : r.filas()) {
                Maquina m = f.maquina();
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tabla.addCell(pdf.celda(f.codigoTexto(), cen, tipo));
                tabla.addCell(pdf.celda(den(m.getDenominacion()), cen, tipo));
                tabla.addCell(pdf.celda(m.getNombre(), izq, tipo));
                tabla.addCell(pdf.celda(m.getSerial(), izq, tipo));
                tabla.addCell(pdf.celda(m.getJuego(), izq, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(f.utilidad()), der, tipo, colorSigno(f.utilidad())));
                zebra = !zebra;
            }

            tabla.addCell(pdf.celda("Total", izq, Fila.TOTAL));
            for (int i = 0; i < 4; i++) {
                tabla.addCell(pdf.celda("", izq, Fila.TOTAL));
            }
            tabla.addCell(pdf.celda(Diseno.moneda(r.total()), der, Fila.TOTAL, colorSigno(r.total())));

            pdf.agregar(tabla);
        }
    }

    // ==================== EXCEL: CASINO VICTORY ====================

    public void exportarCasinoExcel(LocalDate desde, LocalDate hasta, String ruta) throws Exception {
        ResultadoCasino r = generarCasino(desde, hasta);
        String periodo = "Fecha inicio: " + fechaLarga(desde) + "   |   Fecha fin: " + fechaLarga(hasta);
        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Casino Victory", 6, false);
        hoja.titulo(TITULO_CASINO, periodo);

        if (r.filas().isEmpty()) {
            hoja.nota("No hay máquinas para mostrar en este rango de fechas.");
            hoja.terminar(false, false);
            estilos.guardar(ruta);
            return;
        }

        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment cen = HorizontalAlignment.CENTER;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Código", "Denominación", "Nombre", "Serial", "Juego", "Utilidad"},
            new HorizontalAlignment[] {cen, cen, izq, izq, izq, der});

        int primera = hoja.siguienteFila();
        for (FilaCasino f : r.filas()) {
            Maquina m = f.maquina();
            hoja.fila(Rol.DATO)
                .centro(f.codigoTexto())
                .centro(den(m.getDenominacion()))
                .texto(m.getNombre())
                .texto(m.getSerial())
                .texto(m.getJuego())
                .moneda(f.utilidad());
        }
        int ultima = hoja.siguienteFila() - 1;

        // SUBTOTAL: al filtrar en Excel, suma solo las filas visibles
        HojaExcel.FilaExcel total = hoja.fila(Rol.TOTAL).texto("Total");
        for (int i = 0; i < 4; i++) {
            total.vacio();
        }
        total.formulaMoneda("SUBTOTAL(109," + HojaExcel.ref(5, primera) + ":" + HojaExcel.ref(5, ultima) + ")");

        hoja.terminar(true, true);
        estilos.guardar(ruta);
    }

    // ==================== AUXILIARES ====================

    /** "01-sept-2026" */
    private String fechaLarga(LocalDate fecha) {
        return fecha.format(LARGA).replace(".", "").toLowerCase(ES);
    }

    /** Denominación sin ceros sobrantes: 1.00 -> "1" */
    private String den(BigDecimal d) {
        return d == null ? "" : d.stripTrailingZeros().toPlainString();
    }

    /** Contador con formato de pesos: $ 92.480.000 */
    private String num(long valor) {
        return peso(BigDecimal.valueOf(valor));
    }

    /** Total de la fila: vacío en la lectura inicial y en la copia. */
    private String textoTotal(FilaContador f) {
        if (f.total() != null) {
            return peso(f.total());
        }
        return Lectura.REINICIO.equals(f.lectura().getTipo()) ? "Reinicio" : "";
    }
    
    /** Pesos con espacio después del signo: $ 92.480.000 */
    private String peso(BigDecimal valor) {
        return Diseno.moneda(valor).replace("$", "$ ");
    }
    
    /** Fila en blanco entre una máquina y la siguiente. */
    private void separador(PdfEstilo pdf, Table tabla) {
        for (int i = 0; i < 9; i++) {
            tabla.addCell(pdf.celda("", TextAlignment.LEFT, Fila.DATO)
                .setBorder(Border.NO_BORDER).setHeight(8));
        }
    }

    /** Rojo si el valor es negativo; null deja el color de la fila. */
    private int[] colorSigno(BigDecimal valor) {
        return valor != null && valor.signum() < 0 ? Diseno.NEGATIVO : null;
    }

    private String recortar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}