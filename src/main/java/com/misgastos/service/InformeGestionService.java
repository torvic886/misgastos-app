package com.misgastos.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.misgastos.model.Gasto;
import com.misgastos.model.Maquina;
import com.misgastos.service.ContadoresInformeService.FilaCasino;
import com.misgastos.service.ContadoresInformeService.ResultadoCasino;
import com.misgastos.service.PromedioService.FilaPromedio;
import com.misgastos.service.diseno.Diseno;
import com.misgastos.service.diseno.ExcelEstilo;
import com.misgastos.service.diseno.ExcelEstilo.Rol;
import com.misgastos.service.diseno.HojaExcel;

/**
 * Informe Mensual de Gestión: un Excel con 4 hojas (Contadores, Categoría y producto,
 * Utilidad y Promedio). Siempre cubre el mes completo, del día 1 al último día.
 * Solo lectura: no cambia ningún dato.
 */
@Service
public class InformeGestionService {

    private static final Logger log = LoggerFactory.getLogger(InformeGestionService.class);

    private static final String[] MESES = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO",
        "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};

    private static final String HOJA_CONTADORES = "Contadores";
    private static final String HOJA_CATEGORIAS = "Categoría y producto";
    private static final String HOJA_UTILIDAD = "Utilidad";
    private static final String HOJA_PROMEDIO = "Promedio";

    /** SUMA y RESTA cambian la utilidad; INFORMATIVO solo se muestra. */
    public enum Tipo {
        SUMA, RESTA, INFORMATIVO
    }

    /** Fila extraordinaria de la hoja Utilidad (retenciones, datáfonos, Nequi, otros). */
    public record Extra(String concepto, BigDecimal valor, Tipo tipo) {
    }

    @Autowired
    private GastoService gastoService;

    @Autowired
    private ContadoresInformeService contadoresInformeService;

    @Autowired
    private PromedioService promedioService;

    // ==================== SUGERIDOS ====================

    /**
     * Datáfonos y Nequi del mes, sumados de los gastos cuyo producto o categoría los nombre.
     * El diálogo los muestra para que el usuario los revise antes de generar.
     */
    public List<Extra> sugeridos(int anio, int mes) {
        YearMonth ym = YearMonth.of(anio, mes);
        List<Gasto> gastos = gastoService.listarPorPeriodo(ym.atDay(1), ym.atEndOfMonth());
        BigDecimal datafonos = BigDecimal.ZERO;
        BigDecimal nequi = BigDecimal.ZERO;
        for (Gasto g : gastos) {
            String texto = norm(g.getProducto()) + " " + norm(g.getCategoria().getNombre());
            if (texto.contains("datafono")) {
                datafonos = datafonos.add(g.getValorTotal());
            } else if (texto.contains("nequi")) {
                nequi = nequi.add(g.getValorTotal());
            }
        }
        log.debug("Informe de gestión: sugeridos {}-{}: datáfonos={} nequi={}", anio, mes, datafonos, nequi);
        List<Extra> lista = new ArrayList<>();
        lista.add(new Extra("Ingreso de plata por retenciones", BigDecimal.ZERO, Tipo.SUMA));
        lista.add(new Extra("Valor total datáfonos", datafonos, Tipo.SUMA));
        lista.add(new Extra("Nequi", nequi, Tipo.SUMA));
        return lista;
    }
    
    public record Resumen(BigDecimal venta, BigDecimal gastos, int transacciones, int categorias) {
    }

    /** Cifras del mes con la misma lista de gastos que usa la hoja Categoría y producto. */
    public Resumen resumen(int anio, int mes) {
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();
        BigDecimal venta = contadoresInformeService.generarCasino(desde, hasta).total();
        List<Gasto> gastos = gastoService.listarPorPeriodo(desde, hasta);
        BigDecimal total = BigDecimal.ZERO;
        for (Gasto g : gastos) {
            if (g.getValorTotal() != null) {
                total = total.add(g.getValorTotal());
            }
        }
        int categorias = (int) gastos.stream().map(g -> g.getCategoria().getNombre()).distinct().count();
        log.debug("Informe de gestión: resumen {}-{} venta={} gastos={} transacciones={}", anio, mes, venta, total,
            gastos.size());
        return new Resumen(venta, total, gastos.size(), categorias);
    }

    /** Texto de la vista previa: resumen del mes, sin generar el Excel. */
    public String textoVistaPrevia(int anio, int mes, Resumen r) {
        YearMonth ym = YearMonth.of(anio, mes);
        StringBuilder sb = new StringBuilder();
        sb.append("INFORME MENSUAL DE GESTIÓN\n");
        sb.append(MESES[mes - 1]).append(" ").append(anio).append("  (del ").append(Diseno.fecha(ym.atDay(1)))
            .append(" al ").append(Diseno.fecha(ym.atEndOfMonth())).append(")\n");
        sb.append("=".repeat(70)).append("\n\n");
        sb.append("Hojas del Excel: Contadores, Categoría y producto, Utilidad y Promedio.\n\n");
        sb.append(String.format("%-28s %20s%n", "VENTA (contadores)", Diseno.moneda(r.venta())));
        sb.append(String.format("%-28s %20s%n", "GASTOS del mes", Diseno.moneda(r.gastos())));
        sb.append(String.format("%-28s %20s%n", "Venta - Gastos", Diseno.moneda(r.venta().subtract(r.gastos()))));
        sb.append("\nAl exportar, el programa te preguntará si hay ingresos o gastos extraordinarios\n");
        sb.append("(retenciones, datáfonos, Nequi u otros) antes de calcular la utilidad.\n");
        sb.append("El informe siempre cubre el mes completo de la Fecha inicio. Solo se exporta a Excel.\n");
        return sb.toString();
    }

    // ==================== EXCEL ====================

    public void exportarExcel(int anio, int mes, List<Extra> extras, String ruta) throws Exception {
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();
        String periodo = "Del " + Diseno.fecha(desde) + " al " + Diseno.fecha(hasta);
        log.debug("Informe de gestión: generando {} ({} extras)", ym, extras == null ? 0 : extras.size());

        ExcelEstilo estilos = new ExcelEstilo();

        // 1) Contadores: fila del total (columna F) para la Venta
        int filaVenta = hojaContadores(estilos, desde, hasta);

        // 2) Categoría y producto: filas del total general, inversiones y coljuegos
        int[] filasGastos = hojaCategorias(estilos, desde, hasta, periodo);

        // 3) Utilidad
        hojaUtilidad(estilos, ym, extras, filaVenta, filasGastos);

        // 4) Promedio
        hojaPromedio(estilos, anio, mes);

        estilos.guardar(ruta);
        log.info("Informe mensual de gestión exportado ({})", ym);
    }

    // ---------- Hoja 1: Contadores ----------

    /** Devuelve la fila de Excel del total (para referenciarla), o -1 si no hay máquinas. */
    private int hojaContadores(ExcelEstilo estilos, LocalDate desde, LocalDate hasta) {
        ResultadoCasino r = contadoresInformeService.generarCasino(desde, hasta);
        String periodo = "Fecha inicio: " + Diseno.fecha(desde) + "   |   Fecha fin: " + Diseno.fecha(hasta);
        HojaExcel hoja = new HojaExcel(estilos, HOJA_CONTADORES, 6, false);
        hoja.titulo("Informe Casino Victory Armenia", periodo);

        if (r.filas().isEmpty()) {
            log.warn("Informe de gestión: no hay máquinas ni lecturas en el mes");
            hoja.nota("No hay máquinas para mostrar en este rango de fechas.");
            hoja.terminar(false, false);
            return -1;
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
            HojaExcel.FilaExcel fila = hoja.fila(Rol.DATO);
            // Código: número, salvo en las máquinas de baja que llevan el texto "(baja)"
            if (f.baja()) {
                fila.centro(f.codigoTexto());
            } else {
                fila.id(m.getCodigo());
            }
            // Denominación: número cuando es entera (1, 5, 10...); texto si tiene decimales
            BigDecimal den = m.getDenominacion() == null ? null : m.getDenominacion().stripTrailingZeros();
            if (den == null) {
                fila.centro("");
            } else if (den.scale() <= 0) {
                fila.id(den.toBigInteger());
            } else {
                fila.centro(den.toPlainString());
            }
            fila.texto(m.getNombre())
                .texto(m.getSerial())
                .texto(m.getJuego())
                .moneda(f.utilidad());
        }
        int ultima = hoja.siguienteFila() - 1;

        int filaTotal = hoja.siguienteFila();
        HojaExcel.FilaExcel total = hoja.fila(Rol.TOTAL).texto("Total");
        for (int i = 0; i < 4; i++) {
            total.vacio();
        }
        total.formulaMoneda("SUBTOTAL(109," + HojaExcel.ref(5, primera) + ":" + HojaExcel.ref(5, ultima) + ")");

        hoja.terminar(true, true);
        log.debug("Informe de gestión: hoja Contadores con {} máquinas, total en fila {}", r.filas().size(), filaTotal);
        return filaTotal;
    }

    // ---------- Hoja 2: Categoría y producto ----------

    /** Devuelve {filaTotalGeneral, filaInversiones, filaColjuegos}; -1 donde no exista. */
    private int[] hojaCategorias(ExcelEstilo estilos, LocalDate desde, LocalDate hasta, String periodo) {
        int[] filas = {-1, -1, -1};
        List<Gasto> gastos = gastoService.listarPorPeriodo(desde, hasta);
        String titulo = "Informe de gastos por categoría y producto";
        HojaExcel hoja = new HojaExcel(estilos, HOJA_CATEGORIAS, 4, false);
        hoja.titulo(titulo, periodo);

        if (gastos.isEmpty()) {
            log.warn("Informe de gestión: no hay gastos en el mes");
            hoja.nota("No hay gastos registrados en este período.");
            hoja.terminar(false, false);
            return filas;
        }

        Map<String, Map<String, BigDecimal>> datos = agrupar(gastos);
        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.espacio();
        hoja.encabezado(
            new String[] {"Categoría", "Producto", "Valor", "Total"},
            new HorizontalAlignment[] {izq, izq, der, der});

        int primeraDatos = hoja.siguienteFila();
        for (Map.Entry<String, Map<String, BigDecimal>> cat : datos.entrySet()) {
            int filaGrupo = hoja.siguienteFila();
            int primeraProd = filaGrupo + 1;
            int ultimaProd = filaGrupo + cat.getValue().size();
            if (filas[1] < 0 && norm(cat.getKey()).contains("inversion")) {
                filas[1] = filaGrupo;
            }

            HojaExcel.FilaExcel grupo = hoja.fila(Rol.GRUPO);
            grupo.texto(cat.getKey());
            grupo.vacio();
            grupo.vacio();
            grupo.formulaMoneda("SUM(" + HojaExcel.ref(2, primeraProd) + ":" + HojaExcel.ref(2, ultimaProd) + ")");

            for (Map.Entry<String, BigDecimal> prod : cat.getValue().entrySet()) {
                if (filas[2] < 0 && norm(prod.getKey()).equals("coljuegos")) {
                    filas[2] = hoja.siguienteFila();
                }
                HojaExcel.FilaExcel fila = hoja.fila(Rol.DATO);
                fila.vacio();
                fila.texto(prod.getKey());
                fila.moneda(prod.getValue());
                fila.vacio();
            }
        }
        int ultimaDatos = hoja.siguienteFila() - 1;

        filas[0] = hoja.siguienteFila();
        HojaExcel.FilaExcel total = hoja.fila(Rol.TOTAL);
        total.texto("Total general");
        total.vacio();
        total.vacio();
        total.formulaMoneda("SUM(" + HojaExcel.ref(2, primeraDatos) + ":" + HojaExcel.ref(2, ultimaDatos) + ")");

        hoja.terminar(false, false);
        if (filas[1] < 0) {
            log.warn("Informe de gestión: no se encontró la categoría de inversiones");
        }
        if (filas[2] < 0) {
            log.warn("Informe de gestión: no se encontró el producto coljuegos");
        }
        log.debug("Informe de gestión: hoja categorías con {} categorías, filas {}", datos.size(), filas);
        return filas;
    }

    // ---------- Hoja 3: Utilidad ----------

    private void hojaUtilidad(ExcelEstilo estilos, YearMonth ym, List<Extra> extras, int filaVenta,
            int[] filasGastos) {
        HojaExcel hoja = new HojaExcel(estilos, HOJA_UTILIDAD, 2, false);
        hoja.titulo("Utilidad del local", MESES[ym.getMonthValue() - 1] + " " + ym.getYear());
        hoja.espacio();
        hoja.encabezado(new String[] {"Concepto", "Valor"},
            new HorizontalAlignment[] {HorizontalAlignment.LEFT, HorizontalAlignment.RIGHT});

        int rVenta = hoja.siguienteFila();
        formulaOCero(hoja.fila(Rol.DATO).texto("VENTA"), HOJA_CONTADORES, 5, filaVenta);

        int rGastos = hoja.siguienteFila();
        formulaOCero(hoja.fila(Rol.DATO).texto("GASTOS"), HOJA_CATEGORIAS, 3, filasGastos[0]);

        StringBuilder formula = new StringBuilder(HojaExcel.ref(1, rVenta) + "-" + HojaExcel.ref(1, rGastos));
        if (extras != null) {
            for (Extra e : extras) {
                if (e == null || e.valor() == null) {
                    continue;
                }
                int r = hoja.siguienteFila();
                String nombre = e.concepto().toUpperCase(Locale.ROOT);
                if (e.tipo() == Tipo.INFORMATIVO) {
                    nombre += " (INFORMATIVO)";
                }
                hoja.fila(Rol.DATO).texto(nombre).moneda(e.valor());
                if (e.tipo() == Tipo.SUMA) {
                    formula.append("+").append(HojaExcel.ref(1, r));
                } else if (e.tipo() == Tipo.RESTA) {
                    formula.append("-").append(HojaExcel.ref(1, r));
                }
            }
        }

        formulaOCero(hoja.fila(Rol.DATO).texto("INVERSIONES"), HOJA_CATEGORIAS, 3, filasGastos[1]);

        hoja.fila(Rol.TOTAL).texto("UTILIDAD DEL LOCAL").formulaMoneda(formula.toString());

        formulaOCero(hoja.fila(Rol.DATO).texto("COLJUEGOS"), HOJA_CATEGORIAS, 2, filasGastos[2]);

        hoja.terminar(false, false);
        log.debug("Informe de gestión: hoja Utilidad con fórmula {}", formula);
    }

    /** Pone una referencia a otra hoja, o cero si esa fila no existe. */
    private void formulaOCero(HojaExcel.FilaExcel fila, String hoja, int columna, int filaExcel) {
        if (filaExcel < 0) {
            fila.moneda(BigDecimal.ZERO);
        } else {
            fila.formulaMoneda("'" + hoja + "'!" + HojaExcel.ref(columna, filaExcel));
        }
    }

    // ---------- Hoja 4: Promedio ----------

    private void hojaPromedio(ExcelEstilo estilos, int anio, int mes) {
        List<FilaPromedio> filas = promedioService.generar(anio, mes);
        HojaExcel hoja = new HojaExcel(estilos, HOJA_PROMEDIO, 4, false);
        hoja.titulo("Promedio " + anio, "Acumulado de billetes y premios por mes");
        hoja.espacio();
        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Mes", "Acumulado Bill", "Acumulado Premios", "Diferencia"},
            new HorizontalAlignment[] {izq, der, der, der});

        for (FilaPromedio f : filas) {
            int r = hoja.siguienteFila();
            hoja.fila(Rol.DATO)
                .texto(MESES[f.mes() - 1])
                .moneda(f.bill())
                .moneda(f.premios())
                .formulaMoneda(HojaExcel.ref(1, r) + "-" + HojaExcel.ref(2, r));
        }
        hoja.terminar(false, false);
        log.debug("Informe de gestión: hoja Promedio {} hasta el mes {}", anio, mes);
    }

    // ==================== AUXILIARES ====================

    /** Categoría > producto (suma por nombre normalizado), de mayor a menor, igual que el informe de categorías. */
    private Map<String, Map<String, BigDecimal>> agrupar(List<Gasto> gastos) {
        Map<String, Map<String, BigDecimal>> porCategoria = new HashMap<>();
        Map<String, Map<String, String>> nombres = new HashMap<>();

        for (Gasto g : gastos) {
            String categoria = g.getCategoria().getNombre();
            String producto = g.getProducto() != null && !g.getProducto().trim().isEmpty()
                ? g.getProducto().trim() : "Producto no especificado";
            String clave = InformeService.normalizarProducto(producto);
            String nombre = nombres.computeIfAbsent(categoria, k -> new HashMap<>())
                .computeIfAbsent(clave, k -> producto);
            porCategoria.computeIfAbsent(categoria, k -> new HashMap<>())
                .merge(nombre, g.getValorTotal(), BigDecimal::add);
        }

        Map<String, Map<String, BigDecimal>> ordenado = new LinkedHashMap<>();
        porCategoria.entrySet().stream()
            .sorted((a, b) -> suma(b.getValue()).compareTo(suma(a.getValue())))
            .forEach(e -> {
                Map<String, BigDecimal> productos = new LinkedHashMap<>();
                e.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(p -> productos.put(p.getKey(), p.getValue()));
                ordenado.put(e.getKey(), productos);
            });
        return ordenado;
    }

    private BigDecimal suma(Map<String, BigDecimal> mapa) {
        return mapa.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Minúsculas y sin tildes, para comparar nombres. */
    private static String norm(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}