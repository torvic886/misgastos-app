package com.misgastos.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.misgastos.model.Gasto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.itextpdf.layout.borders.SolidBorder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;
import com.misgastos.service.diseno.ExcelEstilo;
import com.misgastos.service.diseno.HojaExcel;
import org.apache.poi.ss.usermodel.HorizontalAlignment;


import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.misgastos.service.diseno.Diseno;
import com.misgastos.service.diseno.PdfEstilo;
import com.misgastos.service.diseno.PdfEstilo.Fila;


import java.text.Normalizer;
import java.util.Locale;

@Slf4j
@Service
public class InformeService {

    @Autowired
    private GastoService gastoService;

    // ==================== 1. INFORME GENERAL MENSUAL ====================
    
    public String generarInformeGeneralMensual(LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("                        INFORME DE GASTOS\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        if (gastos.isEmpty()) {
            sb.append("⚠️ No hay gastos registrados en este período.\n");
            return sb.toString();
        }
        
        Map<YearMonth, Map<String, BigDecimal>> datosPorMesCategoria = new TreeMap<>();
        
        for (Gasto gasto : gastos) {
            YearMonth mes = YearMonth.from(gasto.getFecha());
            String categoria = gasto.getCategoria().getNombre();
            
            datosPorMesCategoria
                .computeIfAbsent(mes, k -> new HashMap<>())
                .merge(categoria, gasto.getValorTotal(), BigDecimal::add);
        }

        Set<String> categorias = gastos.stream()
            .map(g -> g.getCategoria().getNombre())
            .collect(Collectors.toCollection(TreeSet::new));

        List<YearMonth> meses = new ArrayList<>(datosPorMesCategoria.keySet());

        // ✅ ANCHO FIJO: 17 caracteres por columna de mes (incluye espacios y delimitador)
        final int ANCHO_CATEGORIA = 24;
        final int ANCHO_MES = 19;
        final int ANCHO_TOTAL = 19;

        // ✅ ENCABEZADO CON ALINEACIÓN PERFECTA
        sb.append(String.format("%-" + ANCHO_CATEGORIA + "s", "CATEGORÍA"));
        for (YearMonth mes : meses) {
            String nombreMes = mes.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
            sb.append(String.format("│ %" + (ANCHO_MES - 2) + "s ", nombreMes));
        }
        sb.append(String.format("│ %" + (ANCHO_TOTAL - 2) + "s", "TOTAL"));
        sb.append("\n");
        
        // Línea separadora
        sb.append("─".repeat(ANCHO_CATEGORIA));
        for (int i = 0; i < meses.size(); i++) {
            sb.append("┼").append("─".repeat(ANCHO_MES));
        }
        sb.append("┼").append("─".repeat(ANCHO_TOTAL)).append("\n");

        // ✅ DATOS CON COLUMNAS ALINEADAS
        for (String categoria : categorias) {
            sb.append(String.format("%-" + ANCHO_CATEGORIA + "s", truncar(categoria, ANCHO_CATEGORIA)));
            
            BigDecimal totalCategoria = BigDecimal.ZERO;
            
            for (YearMonth mes : meses) {
                BigDecimal monto = datosPorMesCategoria.get(mes).getOrDefault(categoria, BigDecimal.ZERO);
                String montoStr = formatearMoneda(monto);
                sb.append(String.format("│ %" + (ANCHO_MES - 2) + "s ", montoStr));
                totalCategoria = totalCategoria.add(monto);
            }
            
            String totalStr = formatearMoneda(totalCategoria);
            sb.append(String.format("│ %" + (ANCHO_TOTAL - 2) + "s", totalStr));
            sb.append("\n");
        }

        // Línea separadora antes de totales (más gruesa)
        sb.append("═".repeat(ANCHO_CATEGORIA));
        for (int i = 0; i < meses.size(); i++) {
            sb.append("╪").append("═".repeat(ANCHO_MES));
        }
        sb.append("╪").append("═".repeat(ANCHO_TOTAL)).append("\n");
        
        // ✅ FILA DE TOTALES
        sb.append(String.format("%-" + ANCHO_CATEGORIA + "s", "TOTAL MES"));
        
        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (YearMonth mes : meses) {
            BigDecimal totalMes = datosPorMesCategoria.get(mes).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            String totalMesStr = formatearMoneda(totalMes);
            sb.append(String.format("│ %" + (ANCHO_MES - 2) + "s ", totalMesStr));
            totalGeneral = totalGeneral.add(totalMes);
        }
        
        String totalGeneralStr = formatearMoneda(totalGeneral);
        sb.append(String.format("│ %" + (ANCHO_TOTAL - 2) + "s", totalGeneralStr));
        sb.append("\n\n");

        // ESTADÍSTICAS
        sb.append("📊 ESTADÍSTICAS\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("💰 Total gastado: %s\n", formatearMoneda(totalGeneral)));
        sb.append(String.format("📅 Período: %d meses\n", datosPorMesCategoria.size()));
        
        if (!datosPorMesCategoria.isEmpty()) {
            sb.append(String.format("📈 Promedio mensual: %s\n", 
                formatearMoneda(totalGeneral.divide(BigDecimal.valueOf(datosPorMesCategoria.size()), 2, RoundingMode.HALF_UP))));
        }
        
        sb.append(String.format("📂 Total de categorías: %d\n", categorias.size()));
        sb.append(String.format("🧾 Total de transacciones: %d\n", gastos.size()));
        
        return sb.toString();
    }

    // ==================== 2. INFORME POR CATEGORÍA ====================
    
    public String generarInformePorCategoria(String nombreCategoria, LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("              INFORME POR CATEGORÍA: ").append(nombreCategoria.toUpperCase()).append("\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> g.getCategoria().getNombre().equalsIgnoreCase(nombreCategoria))
            .collect(Collectors.toList());

        if (gastos.isEmpty()) {
            sb.append("⚠️ No se encontraron gastos para esta categoría en el período seleccionado.\n");
            return sb.toString();
        }

        BigDecimal totalGastado = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal promedioCompra = totalGastado.divide(
            BigDecimal.valueOf(gastos.size()), 2, RoundingMode.HALF_UP
        );

        // ═══ RESUMEN GENERAL ═══
        sb.append("📋 RESUMEN GENERAL\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("   Total gastado: %s\n", formatearMoneda(totalGastado)));
        sb.append(String.format("   Número de compras: %d\n", gastos.size()));
        sb.append(String.format("   Promedio por compra: %s\n\n", formatearMoneda(promedioCompra)));

        // ═══ POR SUBCATEGORÍA ═══
        Map<String, BigDecimal> porSubcategoria = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getSubcategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        sb.append("📂 POR SUBCATEGORÍA\n");
        sb.append("─".repeat(80)).append("\n");

        // ✅ ANCHOS FIJOS PARA LA TABLA
        final int ANCHO_SUBCAT = 32;
        final int ANCHO_MONTO = 18;
        final int ANCHO_COMPRAS = 10;
        final int ANCHO_PORC = 12;

        // Encabezado
        sb.append(String.format("%-" + ANCHO_SUBCAT + "s", "SUBCATEGORÍA"));
        sb.append(String.format("│ %" + (ANCHO_MONTO - 2) + "s ", "MONTO"));
        sb.append(String.format("│ %" + (ANCHO_COMPRAS - 2) + "s ", "COMPRAS"));
        sb.append(String.format("│ %" + (ANCHO_PORC - 2) + "s", "% CATEGORÍA"));
        sb.append("\n");

        // Línea separadora
        sb.append("─".repeat(ANCHO_SUBCAT));
        sb.append("┼").append("─".repeat(ANCHO_MONTO));
        sb.append("┼").append("─".repeat(ANCHO_COMPRAS));
        sb.append("┼").append("─".repeat(ANCHO_PORC));
        sb.append("\n");

        // Datos
        for (Map.Entry<String, BigDecimal> entry : porSubcategoria.entrySet()) {
            long compras = gastos.stream()
                .filter(g -> g.getSubcategoria().getNombre().equals(entry.getKey()))
                .count();
            
            double porcentaje = entry.getValue()
                .divide(totalGastado, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

            sb.append(String.format("%-" + ANCHO_SUBCAT + "s", truncar(entry.getKey(), ANCHO_SUBCAT)));
            sb.append(String.format("│ %" + (ANCHO_MONTO - 2) + "s ", formatearMoneda(entry.getValue())));
            sb.append(String.format("│ %" + (ANCHO_COMPRAS - 2) + "d ", compras));
            sb.append(String.format("│ %" + (ANCHO_PORC - 3) + ".1f%%", porcentaje));
            sb.append("\n");
        }
        sb.append("\n");

        // ═══ TOP 5 PRODUCTOS ═══
        Map<String, BigDecimal> porProducto = gastos.stream()
            .collect(Collectors.groupingBy(
                Gasto::getProducto,
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        List<Map.Entry<String, BigDecimal>> top5 = porProducto.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        sb.append("🏆 TOP 5 PRODUCTOS\n");
        sb.append("─".repeat(80)).append("\n");
        
        int pos = 1;
        for (Map.Entry<String, BigDecimal> entry : top5) {
            long veces = gastos.stream()
                .filter(g -> g.getProducto().equals(entry.getKey()))
                .count();
            
            String vecesTexto = veces == 1 ? "(1 vez)" : String.format("(%d veces)", veces);
            
            sb.append(String.format("   %d. %-45s %-12s %s\n", 
                pos++, 
                truncar(entry.getKey(), 45), 
                vecesTexto,
                formatearMoneda(entry.getValue())
            ));
        }

        return sb.toString();
    }

// ==================== 3. INFORME POR PRODUCTO ====================
    
    public String generarInformePorProducto(String nombreProducto, LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("                   ANÁLISIS DE PRODUCTO: ").append(nombreProducto.toUpperCase()).append("\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> mismoProducto(g.getProducto(), nombreProducto))
            .sorted(Comparator.comparing(Gasto::getFecha).reversed())
            .collect(Collectors.toList());

        if (gastos.isEmpty()) {
        	sb.append("⚠️ No se encontraron compras de este producto en el período seleccionado.\n");
        	List<String> parecidos = productosParecidos(nombreProducto, inicio, fin);
        	if (!parecidos.isEmpty()) {
        	    sb.append("\n¿Quisiste decir?\n");
        	    for (String p : parecidos) {
        	        sb.append("   • ").append(p).append("\n");
        	    }
        	}
        	
            return sb.toString();
        }

        Gasto ultimaCompra = gastos.get(0);
        
        BigDecimal totalGastado = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int cantidadTotal = gastos.stream()
            .mapToInt(Gasto::getCantidad)
            .sum();

        BigDecimal precioPromedio = gastos.stream()
            .map(Gasto::getValorUnitario)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(gastos.size()), 2, RoundingMode.HALF_UP);

        sb.append("📦 INFORMACIÓN GENERAL\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("   Categoría: %s > %s\n", 
            ultimaCompra.getCategoria().getNombre(),
            ultimaCompra.getSubcategoria().getNombre()));
        sb.append(String.format("   Total gastado: %s\n", formatearMoneda(totalGastado)));
        sb.append(String.format("   Cantidad comprada: %d unidades\n", cantidadTotal));
        sb.append(String.format("   Precio promedio: %s por unidad\n", formatearMoneda(precioPromedio)));
        sb.append(String.format("   Número de compras: %d\n\n", gastos.size()));

        BigDecimal precioMin = gastos.stream()
            .map(Gasto::getValorUnitario)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal precioMax = gastos.stream()
            .map(Gasto::getValorUnitario)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        sb.append("📊 ESTADÍSTICAS DE PRECIO\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("   Precio más bajo: %s\n", formatearMoneda(precioMin)));
        sb.append(String.format("   Precio más alto: %s\n", formatearMoneda(precioMax)));
        sb.append(String.format("   Precio actual: %s\n\n", formatearMoneda(ultimaCompra.getValorUnitario())));

        sb.append("📋 HISTORIAL DE COMPRAS (Últimas 10)\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-12s %-8s %8s %14s %14s %16s\n", 
            "FECHA", "HORA", "CANT.", "PRECIO UNIT.", "TOTAL", "NOTAS"));
        sb.append("─".repeat(80)).append("\n");

        gastos.stream().limit(10).forEach(g -> {
            sb.append(String.format("%-12s %-8s %8d %14s %14s %16s\n",
                formatearFecha(g.getFecha()),
                formatearHora(g),
                g.getCantidad(),
                formatearMoneda(g.getValorUnitario()),
                formatearMoneda(g.getValorTotal()),
                truncar((g.getNotas() != null && !g.getNotas().isEmpty()) ? g.getNotas() : "-", 16)
            ));
        });

        return sb.toString();
    }

    // ==================== 4. INFORME COMPARATIVO ====================
    
    public String generarInformeComparativo(LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("                    COMPARACIÓN DE PERÍODOS\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
        LocalDate mitad = inicio.plusDays(dias / 2);

        List<Gasto> periodo1 = gastoService.listarPorPeriodo(inicio, mitad);
        List<Gasto> periodo2 = gastoService.listarPorPeriodo(mitad.plusDays(1), fin);

        sb.append(String.format("Período 1: %s - %s\n", formatearFecha(inicio), formatearFecha(mitad)));
        sb.append(String.format("Período 2: %s - %s\n\n", formatearFecha(mitad.plusDays(1)), formatearFecha(fin)));

        Map<String, BigDecimal> totalP1 = periodo1.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        Map<String, BigDecimal> totalP2 = periodo2.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        Set<String> todasCategorias = new TreeSet<>();
        todasCategorias.addAll(totalP1.keySet());
        todasCategorias.addAll(totalP2.keySet());

        sb.append(String.format("%-20s %15s %15s %15s %10s\n", 
            "CATEGORÍA", "PERÍODO 1", "PERÍODO 2", "DIFERENCIA", "CAMBIO"));
        sb.append("─".repeat(80)).append("\n");

        BigDecimal totalGeneralP1 = BigDecimal.ZERO;
        BigDecimal totalGeneralP2 = BigDecimal.ZERO;

        for (String categoria : todasCategorias) {
            BigDecimal montoP1 = totalP1.getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal montoP2 = totalP2.getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal diferencia = montoP2.subtract(montoP1);
            
            String cambio = "";
            if (montoP1.compareTo(BigDecimal.ZERO) > 0) {
                double porcentaje = diferencia.divide(montoP1, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
                cambio = String.format("%s %.1f%%", porcentaje >= 0 ? "↗" : "↓", Math.abs(porcentaje));
            }

            sb.append(String.format("%-20s %15s %15s %15s %10s\n",
                truncar(categoria, 20),
                formatearMoneda(montoP1),
                formatearMoneda(montoP2),
                formatearMoneda(diferencia),
                cambio
            ));

            totalGeneralP1 = totalGeneralP1.add(montoP1);
            totalGeneralP2 = totalGeneralP2.add(montoP2);
        }

        sb.append("─".repeat(80)).append("\n");
        BigDecimal diferenciaTotal = totalGeneralP2.subtract(totalGeneralP1);
        
        String cambioTotal = "";
        if (totalGeneralP1.compareTo(BigDecimal.ZERO) > 0) {
            double cambioTotalPct = diferenciaTotal.divide(totalGeneralP1, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
            cambioTotal = String.format("%s %.1f%%", cambioTotalPct >= 0 ? "↗" : "↓", Math.abs(cambioTotalPct));
        }

        sb.append(String.format("%-20s %15s %15s %15s %10s\n",
            "TOTAL",
            formatearMoneda(totalGeneralP1),
            formatearMoneda(totalGeneralP2),
            formatearMoneda(diferenciaTotal),
            cambioTotal
        ));

        return sb.toString();
    }

    // ==================== 5. DASHBOARD EJECUTIVO ====================
    
    public String generarDashboardEjecutivo(LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("                      DASHBOARD EJECUTIVO\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        if (gastos.isEmpty()) {
            sb.append("⚠️ No hay gastos registrados en este período.\n");
            return sb.toString();
        }

        BigDecimal totalGastado = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        BigDecimal promedioDiario = totalGastado.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);

        BigDecimal mayorGasto = gastos.stream()
            .map(Gasto::getValorTotal)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        sb.append("💰 RESUMEN FINANCIERO\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("   Total gastado en el período: %s\n", formatearMoneda(totalGastado)));
        sb.append(String.format("   Promedio diario: %s\n", formatearMoneda(promedioDiario)));
        sb.append(String.format("   Mayor gasto individual: %s\n", formatearMoneda(mayorGasto)));
        sb.append(String.format("   Total de transacciones: %d\n", gastos.size()));
        sb.append(String.format("   Promedio por transacción: %s\n\n", 
            formatearMoneda(totalGastado.divide(BigDecimal.valueOf(gastos.size()), 2, RoundingMode.HALF_UP))));

        Map<String, BigDecimal> porCategoria = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        List<Map.Entry<String, BigDecimal>> top3Cat = porCategoria.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());

        sb.append("📊 TOP 3 CATEGORÍAS\n");
        sb.append("─".repeat(80)).append("\n");
        int pos = 1;
        for (Map.Entry<String, BigDecimal> entry : top3Cat) {
            double porcentaje = entry.getValue()
                .divide(totalGastado, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
            
            String emoji = pos == 1 ? "🥇" : pos == 2 ? "🥈" : "🥉";
            sb.append(String.format("   %s %s %s (%.1f%%)\n", 
                emoji, 
                entry.getKey(),
                formatearMoneda(entry.getValue()),
                porcentaje
            ));
            pos++;
        }
        sb.append("\n");

        Map<String, Long> frecuenciaProductos = gastos.stream()
            .collect(Collectors.groupingBy(Gasto::getProducto, Collectors.counting()));

        List<Map.Entry<String, Long>> topFrecuentes = frecuenciaProductos.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        sb.append("🔥 PRODUCTOS MÁS COMPRADOS\n");
        sb.append("─".repeat(80)).append("\n");
        pos = 1;
        for (Map.Entry<String, Long> entry : topFrecuentes) {
            sb.append(String.format("   %d. %s (%d veces)\n", pos++, entry.getKey(), entry.getValue()));
        }

        return sb.toString();
    }

    // ==================== 6. INFORME SUBCATEGORÍAS ====================
    
    public String generarInformeSubcategorias(LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("              DESGLOSE POR SUBCATEGORÍAS\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        if (gastos.isEmpty()) {
            sb.append("⚠️ No hay gastos registrados en este período.\n");
            return sb.toString();
        }
        
        BigDecimal totalGeneral = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Map<String, BigDecimal>> estructura = new TreeMap<>();
        
        for (Gasto gasto : gastos) {
            String categoria = gasto.getCategoria().getNombre();
            String subcategoria = gasto.getSubcategoria().getNombre();
            
            estructura
                .computeIfAbsent(categoria, k -> new TreeMap<>())
                .merge(subcategoria, gasto.getValorTotal(), BigDecimal::add);
        }

        for (Map.Entry<String, Map<String, BigDecimal>> catEntry : estructura.entrySet()) {
            String categoria = catEntry.getKey();
            Map<String, BigDecimal> subcategorias = catEntry.getValue();
            
            BigDecimal totalCategoria = subcategorias.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            double porcCategoria = totalCategoria
                .divide(totalGeneral, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

            sb.append(String.format("%s [%s - %.1f%%]\n", 
                categoria.toUpperCase(),
                formatearMoneda(totalCategoria),
                porcCategoria
            ));
            sb.append("─".repeat(80)).append("\n");

            for (Map.Entry<String, BigDecimal> subEntry : subcategorias.entrySet()) {
                double porcSubcat = subEntry.getValue()
                    .divide(totalCategoria, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

                int barras = (int) (porcSubcat / 5);
                String grafico = "█".repeat(Math.max(0, barras)) + "░".repeat(Math.max(0, 20 - barras));

                sb.append(String.format("├─ %-30s %15s (%5.1f%%) %s\n",
                    truncar(subEntry.getKey(), 30),
                    formatearMoneda(subEntry.getValue()),
                    porcSubcat,
                    grafico
                ));
            }
            sb.append("\n");
        }

        sb.append("═".repeat(80)).append("\n");
        sb.append(String.format("TOTAL GENERAL: %s\n", formatearMoneda(totalGeneral)));

        return sb.toString();
    }

    // ==================== 7. INFORME ANUAL ====================
    
    public String generarInformeAnual(int anio) {
        LocalDate inicio = LocalDate.of(anio, 1, 1);
        LocalDate fin = LocalDate.of(anio, 12, 31);
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("                      INFORME ANUAL ").append(anio).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        Map<Integer, BigDecimal> gastosPorMes = new TreeMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            gastosPorMes.put(mes, BigDecimal.ZERO);
        }

        for (Gasto gasto : gastos) {
            int mes = gasto.getFecha().getMonthValue();
            gastosPorMes.merge(mes, gasto.getValorTotal(), BigDecimal::add);
        }

        BigDecimal totalAnual = gastosPorMes.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal promedioMensual = totalAnual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        sb.append("📊 GASTOS POR MES\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-15s %15s %40s\n", "MES", "GASTO", "GRÁFICO"));
        sb.append("─".repeat(80)).append("\n");

        BigDecimal maxMes = gastosPorMes.values().stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
            
        for (Map.Entry<Integer, BigDecimal> entry : gastosPorMes.entrySet()) {
            String nombreMes = java.time.Month.of(entry.getKey())
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
            
            int barras = entry.getValue().multiply(BigDecimal.valueOf(30))
                .divide(maxMes, 0, RoundingMode.HALF_UP)
                .intValue();
            
            String grafico = "█".repeat(Math.max(0, barras)) + "░".repeat(Math.max(0, 30 - barras));

            sb.append(String.format("%-15s %15s %s\n",
                nombreMes,
                formatearMoneda(entry.getValue()),
                grafico
            ));
        }

        sb.append("\n💡 RESUMEN ANUAL\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("   Total año %d: %s\n", anio, formatearMoneda(totalAnual)));
        sb.append(String.format("   Promedio mensual: %s\n", formatearMoneda(promedioMensual)));
        sb.append(String.format("   Total transacciones: %d\n", gastos.size()));

        return sb.toString();
    }

    // ==================== EXPORTACIÓN PDF ====================

    public void exportarInformeGeneralPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        Map<YearMonth, Map<String, BigDecimal>> porMes = new TreeMap<>();
        for (Gasto g : gastos) {
            porMes.computeIfAbsent(YearMonth.from(g.getFecha()), k -> new HashMap<>())
                .merge(g.getCategoria().getNombre(), g.getValorTotal(), BigDecimal::add);
        }
        Set<String> categorias = gastos.stream()
            .map(g -> g.getCategoria().getNombre())
            .collect(Collectors.toCollection(TreeSet::new));
        List<YearMonth> meses = new ArrayList<>(porMes.keySet());

        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (Map<String, BigDecimal> m : porMes.values()) {
            for (BigDecimal v : m.values()) {
                totalGeneral = totalGeneral.add(v);
            }
        }

        boolean horizontal = meses.size() > 4;
        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Informe general mensual", horizontal)) {
            pdf.titulo("Informe general mensual", Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay gastos registrados en este período.");
                return;
            }

            BigDecimal promedio = totalGeneral.divide(BigDecimal.valueOf(meses.size()), 0, RoundingMode.HALF_UP);
            pdf.indicadores(
                new String[] {"Total gastado", Diseno.moneda(totalGeneral)},
                new String[] {"Promedio mensual", Diseno.moneda(promedio)},
                new String[] {"Meses", Diseno.entero(meses.size())},
                new String[] {"Categorías", Diseno.entero(categorias.size())},
                new String[] {"Transacciones", Diseno.entero(gastos.size())});

            pdf.seccion("Gasto por categoría y mes");

            int n = meses.size();
            float[] anchos = new float[n + 3];
            anchos[0] = 3.2f;
            for (int i = 1; i <= n; i++) {
                anchos[i] = 1.5f;
            }
            anchos[n + 1] = 1.7f;
            anchos[n + 2] = 1.1f;

            String[] columnas = new String[n + 3];
            TextAlignment[] alineaciones = new TextAlignment[n + 3];
            columnas[0] = "Categoría";
            alineaciones[0] = TextAlignment.LEFT;
            for (int i = 0; i < n; i++) {
                columnas[i + 1] = Diseno.etiquetaMes(meses.get(i));
                alineaciones[i + 1] = TextAlignment.RIGHT;
            }
            columnas[n + 1] = "Total";
            alineaciones[n + 1] = TextAlignment.RIGHT;
            columnas[n + 2] = "% del total";
            alineaciones[n + 2] = TextAlignment.RIGHT;

            Table tabla = pdf.tabla(anchos);
            pdf.encabezado(tabla, columnas, alineaciones);

            boolean zebra = false;
            for (String categoria : categorias) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tabla.addCell(pdf.celda(categoria, TextAlignment.LEFT, tipo));

                BigDecimal totalCategoria = BigDecimal.ZERO;
                for (YearMonth mes : meses) {
                    BigDecimal monto = porMes.get(mes).getOrDefault(categoria, BigDecimal.ZERO);
                    tabla.addCell(pdf.celda(monto.signum() == 0 ? "-" : Diseno.moneda(monto),
                        TextAlignment.RIGHT, tipo));
                    totalCategoria = totalCategoria.add(monto);
                }
                double pct = totalGeneral.signum() == 0 ? 0
                    : totalCategoria.doubleValue() / totalGeneral.doubleValue() * 100;
                tabla.addCell(pdf.celda(Diseno.moneda(totalCategoria), TextAlignment.RIGHT, tipo));
                tabla.addCell(pdf.celda(Diseno.porcentaje(pct), TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }

            tabla.addCell(pdf.celda("Total", TextAlignment.LEFT, Fila.TOTAL));
            for (YearMonth mes : meses) {
                BigDecimal totalMes = porMes.get(mes).values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                tabla.addCell(pdf.celda(Diseno.moneda(totalMes), TextAlignment.RIGHT, Fila.TOTAL));
            }
            tabla.addCell(pdf.celda(Diseno.moneda(totalGeneral), TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.porcentaje(100), TextAlignment.RIGHT, Fila.TOTAL));

            pdf.agregar(tabla);
        }
    }

    // Métodos auxiliares - AGREGA AL FINAL del archivo (antes del último })
    private void agregarCeldaHeader(com.itextpdf.layout.element.Table table, String texto, DeviceRgb colorFondo, DeviceRgb colorBorde) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell();
        cell.add(new Paragraph(texto).setBold().setFontSize(10).setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
        cell.setBackgroundColor(colorFondo);
        cell.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
        cell.setBorder(new com.itextpdf.layout.borders.SolidBorder(colorBorde, 1));
        cell.setPadding(8);
        table.addCell(cell);
    }

    private void agregarCeldaDato(com.itextpdf.layout.element.Table table, String texto, DeviceRgb colorFondo, DeviceRgb colorBorde, com.itextpdf.layout.properties.TextAlignment alineacion) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell();
        cell.add(new Paragraph(texto).setFontSize(9));
        cell.setBackgroundColor(colorFondo);
        cell.setTextAlignment(alineacion);
        cell.setBorder(new com.itextpdf.layout.borders.SolidBorder(colorBorde, 1));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void agregarCeldaTotal(com.itextpdf.layout.element.Table table, String texto, DeviceRgb colorFondo, DeviceRgb colorBorde, com.itextpdf.layout.properties.TextAlignment alineacion) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell();
        cell.add(new Paragraph(texto).setBold().setFontSize(10));
        cell.setBackgroundColor(colorFondo);
        cell.setTextAlignment(alineacion);
        cell.setBorder(new com.itextpdf.layout.borders.SolidBorder(colorBorde, 1));
        cell.setPadding(8);
        table.addCell(cell);
    }
    
 //=============================================================   

    public void exportarInformeCategoriaPDF(String categoria, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> g.getCategoria().getNombre().equals(categoria))
            .collect(Collectors.toList());

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Informe por categoría", false)) {
            pdf.titulo("Informe por categoría: " + categoria, Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay gastos registrados en esta categoría para el período seleccionado.");
                return;
            }

            BigDecimal totalGastado = gastos.stream()
                .map(Gasto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            int compras = gastos.size();
            BigDecimal promedio = totalGastado.divide(BigDecimal.valueOf(compras), 0, RoundingMode.HALF_UP);

            Map<String, BigDecimal> montoPorSubcat = new HashMap<>();
            Map<String, Long> comprasPorSubcat = new HashMap<>();
            for (Gasto g : gastos) {
                String sub = g.getSubcategoria() != null ? g.getSubcategoria().getNombre() : "Sin subcategoría";
                montoPorSubcat.merge(sub, g.getValorTotal(), BigDecimal::add);
                comprasPorSubcat.merge(sub, 1L, Long::sum);
            }

            pdf.indicadores(
                new String[] {"Total gastado", Diseno.moneda(totalGastado)},
                new String[] {"Número de compras", Diseno.entero(compras)},
                new String[] {"Promedio por compra", Diseno.moneda(promedio)},
                new String[] {"Subcategorías", Diseno.entero(montoPorSubcat.size())});

            // ---------- Por subcategoría ----------
            pdf.seccion("Por subcategoría");
            Table tablaSub = pdf.tabla(4f, 2f, 1.4f, 1.8f);
            pdf.encabezado(tablaSub,
                new String[] {"Subcategoría", "Monto", "Compras", "% de la categoría"},
                new TextAlignment[] {TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.CENTER, TextAlignment.RIGHT});

            List<Map.Entry<String, BigDecimal>> subcategorias = montoPorSubcat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());

            boolean zebra = false;
            for (Map.Entry<String, BigDecimal> e : subcategorias) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tablaSub.addCell(pdf.celda(e.getKey(), TextAlignment.LEFT, tipo));
                tablaSub.addCell(pdf.celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, tipo));
                tablaSub.addCell(pdf.celda(String.valueOf(comprasPorSubcat.get(e.getKey())), TextAlignment.CENTER, tipo));
                tablaSub.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(e.getValue(), totalGastado)), TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }
            tablaSub.addCell(pdf.celda("Total", TextAlignment.LEFT, Fila.TOTAL));
            tablaSub.addCell(pdf.celda(Diseno.moneda(totalGastado), TextAlignment.RIGHT, Fila.TOTAL));
            tablaSub.addCell(pdf.celda(String.valueOf(compras), TextAlignment.CENTER, Fila.TOTAL));
            tablaSub.addCell(pdf.celda(Diseno.porcentaje(100), TextAlignment.RIGHT, Fila.TOTAL));
            pdf.agregar(tablaSub);

            // ---------- Top 5 productos ----------
            Map<String, BigDecimal> montoPorProducto = new HashMap<>();
            Map<String, Long> vecesPorProducto = new HashMap<>();
            for (Gasto g : gastos) {
                String nombre = g.getProducto() != null && !g.getProducto().trim().isEmpty()
                    ? g.getProducto() : "Producto no especificado";
                montoPorProducto.merge(nombre, g.getValorTotal(), BigDecimal::add);
                vecesPorProducto.merge(nombre, 1L, Long::sum);
            }
            List<Map.Entry<String, BigDecimal>> top = montoPorProducto.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

            pdf.seccion("Top 5 productos");
            Table tablaTop = pdf.tabla(0.6f, 4f, 1f, 2f, 1.8f);
            pdf.encabezado(tablaTop,
                new String[] {"#", "Producto", "Veces", "Monto", "% de la categoría"},
                new TextAlignment[] {TextAlignment.CENTER, TextAlignment.LEFT, TextAlignment.CENTER,
                    TextAlignment.RIGHT, TextAlignment.RIGHT});

            int posicion = 1;
            zebra = false;
            for (Map.Entry<String, BigDecimal> e : top) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tablaTop.addCell(pdf.celda(String.valueOf(posicion++), TextAlignment.CENTER, tipo));
                tablaTop.addCell(pdf.celda(e.getKey(), TextAlignment.LEFT, tipo));
                tablaTop.addCell(pdf.celda(String.valueOf(vecesPorProducto.get(e.getKey())), TextAlignment.CENTER, tipo));
                tablaTop.addCell(pdf.celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, tipo));
                tablaTop.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(e.getValue(), totalGastado)), TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }
            pdf.agregar(tablaTop);
        }
    }

    /** Porcentaje (0 a 100) que representa una parte sobre el total. */
    private double pctDeTotal(BigDecimal parte, BigDecimal total) {
        return total == null || total.signum() == 0 ? 0 : parte.doubleValue() / total.doubleValue() * 100;
    }

 //=============================================================     
    
    public void exportarInformeProductoPDF(String producto, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> mismoProducto(g.getProducto(), producto))
            .sorted(Comparator.comparing(Gasto::getFecha).thenComparing(Gasto::getHora).reversed())
            .collect(Collectors.toList());

        List<Gasto> recientes = gastos.stream().limit(10).collect(Collectors.toList());
        boolean conPersona = recientes.stream()
            .anyMatch(g -> g.getCedula() != null && !g.getCedula().isBlank());

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Análisis de producto", conPersona)) {
            pdf.titulo("Análisis de producto: " + producto, Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay compras registradas de este producto en el período seleccionado.");
                return;
            }

            BigDecimal totalGastado = gastos.stream()
                .map(Gasto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            int unidades = gastos.stream().mapToInt(Gasto::getCantidad).sum();
            BigDecimal precioPromedio = unidades > 0
                ? totalGastado.divide(BigDecimal.valueOf(unidades), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            Gasto ultimo = gastos.get(0);
            String categoriaNombre = ultimo.getCategoria().getNombre();
            String subcategoriaNombre = ultimo.getSubcategoria() != null
                ? ultimo.getSubcategoria().getNombre() : "Sin subcategoría";
            BigDecimal precioMinimo = gastos.stream().map(Gasto::getValorUnitario)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal precioMaximo = gastos.stream().map(Gasto::getValorUnitario)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

            pdf.indicadores(
                new String[] {"Total gastado", Diseno.moneda(totalGastado)},
                new String[] {"Unidades", Diseno.entero(unidades)},
                new String[] {"Compras", Diseno.entero(gastos.size())},
                new String[] {"Precio promedio por unidad", Diseno.moneda(precioPromedio)});
            pdf.propiedad("Categoría", categoriaNombre + " > " + subcategoriaNombre);

            pdf.seccion("Evolución de precios");
            pdf.indicadores(
                new String[] {"Precio mínimo", Diseno.moneda(precioMinimo)},
                new String[] {"Precio máximo", Diseno.moneda(precioMaximo)},
                new String[] {"Precio actual", Diseno.moneda(ultimo.getValorUnitario())});

            // ---------- Historial ----------
            pdf.seccion("Historial de compras (últimas " + recientes.size() + ")");

            List<String> cols = new ArrayList<>(List.of("Fecha", "Hora", "Registrado por"));
            List<TextAlignment> als = new ArrayList<>(
                List.of(TextAlignment.CENTER, TextAlignment.CENTER, TextAlignment.LEFT));
            List<Float> anchos = new ArrayList<>(List.of(1.3f, 0.9f, 1.4f));
            if (conPersona) {
                cols.add("Cédula");
                als.add(TextAlignment.LEFT);
                anchos.add(1.3f);
                cols.add("Nombre");
                als.add(TextAlignment.LEFT);
                anchos.add(2f);
            }
            cols.addAll(List.of("Cant.", "Precio unit.", "Total", "Notas"));
            als.addAll(List.of(TextAlignment.CENTER, TextAlignment.RIGHT, TextAlignment.RIGHT, TextAlignment.LEFT));
            anchos.addAll(List.of(0.8f, 1.5f, 1.5f, 2.2f));

            float[] anchosArray = new float[anchos.size()];
            for (int i = 0; i < anchosArray.length; i++) {
                anchosArray[i] = anchos.get(i);
            }

            Table tabla = pdf.tabla(anchosArray);
            pdf.encabezado(tabla, cols.toArray(new String[0]), als.toArray(new TextAlignment[0]));

            DateTimeFormatter horaMin = DateTimeFormatter.ofPattern("HH:mm");
            boolean zebra = false;
            for (Gasto g : recientes) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tabla.addCell(pdf.celda(Diseno.fecha(g.getFecha()), TextAlignment.CENTER, tipo));
                tabla.addCell(pdf.celda(g.getHora() == null ? "" : g.getHora().format(horaMin), TextAlignment.CENTER, tipo));
                tabla.addCell(pdf.celda(g.getUsuario() == null ? "" : g.getUsuario().getUsername(), TextAlignment.LEFT, tipo));
                if (conPersona) {
                    tabla.addCell(pdf.celda(g.getCedula(), TextAlignment.LEFT, tipo));
                    tabla.addCell(pdf.celda(g.getNombrePersona(), TextAlignment.LEFT, tipo));
                }
                tabla.addCell(pdf.celda(String.valueOf(g.getCantidad()), TextAlignment.CENTER, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(g.getValorUnitario()), TextAlignment.RIGHT, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(g.getValorTotal()), TextAlignment.RIGHT, tipo));
                String notas = g.getNotas() != null && !g.getNotas().trim().isEmpty() ? g.getNotas() : "-";
                tabla.addCell(pdf.celda(notas, TextAlignment.LEFT, tipo));
                zebra = !zebra;
            }
            pdf.agregar(tabla);
        }
    }

 //=============================================================   
    
    
    public void exportarDashboardPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Dashboard ejecutivo", false)) {
            pdf.titulo("Dashboard ejecutivo", Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay gastos registrados en este período.");
                return;
            }

            BigDecimal totalGastado = gastos.stream()
                .map(Gasto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
            BigDecimal promedioDiario = totalGastado.divide(BigDecimal.valueOf(dias), 0, RoundingMode.HALF_UP);
            BigDecimal mayorGasto = gastos.stream()
                .map(Gasto::getValorTotal)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
            BigDecimal promedioTransaccion = totalGastado.divide(BigDecimal.valueOf(gastos.size()), 0, RoundingMode.HALF_UP);

            pdf.indicadores(
                new String[] {"Total gastado", Diseno.moneda(totalGastado)},
                new String[] {"Promedio diario", Diseno.moneda(promedioDiario)},
                new String[] {"Mayor gasto individual", Diseno.moneda(mayorGasto)},
                new String[] {"Transacciones", Diseno.entero(gastos.size())},
                new String[] {"Promedio por transacción", Diseno.moneda(promedioTransaccion)});

            // ---------- Top 3 categorías ----------
            Map<String, BigDecimal> porCategoria = gastos.stream()
                .collect(Collectors.groupingBy(
                    g -> g.getCategoria().getNombre(),
                    Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
            List<Map.Entry<String, BigDecimal>> top3 = porCategoria.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

            pdf.seccion("Top 3 categorías");
            Table tablaCat = pdf.tabla(0.6f, 4f, 2f, 1.4f);
            pdf.encabezado(tablaCat,
                new String[] {"#", "Categoría", "Monto", "% del total"},
                new TextAlignment[] {TextAlignment.CENTER, TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT});
            int pos = 1;
            boolean zebra = false;
            for (Map.Entry<String, BigDecimal> e : top3) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tablaCat.addCell(pdf.celda(String.valueOf(pos++), TextAlignment.CENTER, tipo));
                tablaCat.addCell(pdf.celda(e.getKey(), TextAlignment.LEFT, tipo));
                tablaCat.addCell(pdf.celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, tipo));
                tablaCat.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(e.getValue(), totalGastado)), TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }
            pdf.agregar(tablaCat);

            // ---------- Productos más comprados ----------
            Map<String, Long> vecesPorProducto = gastos.stream()
                .collect(Collectors.groupingBy(Gasto::getProducto, Collectors.counting()));
            Map<String, BigDecimal> montoPorProducto = gastos.stream()
                .collect(Collectors.groupingBy(
                    Gasto::getProducto,
                    Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
            List<Map.Entry<String, Long>> topFrecuentes = vecesPorProducto.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

            pdf.seccion("Productos más comprados");
            Table tablaProd = pdf.tabla(0.6f, 4f, 1.2f, 2f);
            pdf.encabezado(tablaProd,
                new String[] {"#", "Producto", "Veces", "Monto"},
                new TextAlignment[] {TextAlignment.CENTER, TextAlignment.LEFT, TextAlignment.CENTER, TextAlignment.RIGHT});
            pos = 1;
            zebra = false;
            for (Map.Entry<String, Long> e : topFrecuentes) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tablaProd.addCell(pdf.celda(String.valueOf(pos++), TextAlignment.CENTER, tipo));
                tablaProd.addCell(pdf.celda(e.getKey(), TextAlignment.LEFT, tipo));
                tablaProd.addCell(pdf.celda(String.valueOf(e.getValue()), TextAlignment.CENTER, tipo));
                tablaProd.addCell(pdf.celda(Diseno.moneda(montoPorProducto.get(e.getKey())), TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }
            pdf.agregar(tablaProd);

            // ---------- Gasto por usuario ----------
            Map<String, BigDecimal> montoPorUsuario = gastos.stream()
                .collect(Collectors.groupingBy(
                    g -> g.getUsuario() == null ? "Sin usuario" : g.getUsuario().getUsername(),
                    Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
            Map<String, Long> comprasPorUsuario = gastos.stream()
                .collect(Collectors.groupingBy(
                    g -> g.getUsuario() == null ? "Sin usuario" : g.getUsuario().getUsername(),
                    Collectors.counting()));
            List<Map.Entry<String, BigDecimal>> usuarios = montoPorUsuario.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());

            pdf.seccion("Gasto por usuario");
            Table tablaUsu = pdf.tabla(4f, 1.6f, 2f, 1.4f);
            pdf.encabezado(tablaUsu,
                new String[] {"Registrado por", "Transacciones", "Monto", "% del total"},
                new TextAlignment[] {TextAlignment.LEFT, TextAlignment.CENTER, TextAlignment.RIGHT, TextAlignment.RIGHT});
            zebra = false;
            for (Map.Entry<String, BigDecimal> e : usuarios) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tablaUsu.addCell(pdf.celda(e.getKey(), TextAlignment.LEFT, tipo));
                tablaUsu.addCell(pdf.celda(String.valueOf(comprasPorUsuario.get(e.getKey())), TextAlignment.CENTER, tipo));
                tablaUsu.addCell(pdf.celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, tipo));
                tablaUsu.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(e.getValue(), totalGastado)), TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }
            pdf.agregar(tablaUsu);
        }
    }
//=============================================================   
    
    public void exportarComparativoPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        long diasTotal = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
        LocalDate puntoMedio = inicio.plusDays(diasTotal / 2);
        LocalDate inicio1 = inicio;
        LocalDate fin1 = puntoMedio;
        LocalDate inicio2 = puntoMedio.plusDays(1);
        LocalDate fin2 = fin;

        List<Gasto> gastos1 = gastoService.listarPorPeriodo(inicio1, fin1);
        List<Gasto> gastos2 = gastoService.listarPorPeriodo(inicio2, fin2);

        Map<String, BigDecimal> monto1 = gastos1.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
        Map<String, BigDecimal> monto2 = gastos2.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));

        Set<String> categoriasSet = new TreeSet<>();
        categoriasSet.addAll(monto1.keySet());
        categoriasSet.addAll(monto2.keySet());

        // De mayor a menor gasto combinado
        List<String> categorias = new ArrayList<>(categoriasSet);
        categorias.sort((a, b) -> monto1.getOrDefault(b, BigDecimal.ZERO).add(monto2.getOrDefault(b, BigDecimal.ZERO))
            .compareTo(monto1.getOrDefault(a, BigDecimal.ZERO).add(monto2.getOrDefault(a, BigDecimal.ZERO))));

        BigDecimal total1 = monto1.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total2 = monto2.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferenciaTotal = total2.subtract(total1);

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Comparación de períodos", false)) {
            pdf.titulo("Comparación de períodos", Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));
            pdf.propiedad("Período 1", Diseno.fecha(inicio1) + " al " + Diseno.fecha(fin1));
            pdf.propiedad("Período 2", Diseno.fecha(inicio2) + " al " + Diseno.fecha(fin2));

            if (categorias.isEmpty()) {
                pdf.nota("No hay gastos registrados en ninguno de los dos períodos.");
                return;
            }

            pdf.indicadores(
                new String[] {"Total período 1", Diseno.moneda(total1)},
                new String[] {"Total período 2", Diseno.moneda(total2)},
                new String[] {"Diferencia", Diseno.monedaConSigno(diferenciaTotal)},
                new String[] {"Cambio", textoCambio(total1, total2)});

            pdf.seccion("Detalle por categoría");
            Table tabla = pdf.tabla(3.4f, 2f, 2f, 2f, 1.4f);
            pdf.encabezado(tabla,
                new String[] {"Categoría", "Período 1", "Período 2", "Diferencia", "Cambio"},
                new TextAlignment[] {TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT,
                    TextAlignment.RIGHT, TextAlignment.RIGHT});

            boolean zebra = false;
            for (String categoria : categorias) {
                BigDecimal m1 = monto1.getOrDefault(categoria, BigDecimal.ZERO);
                BigDecimal m2 = monto2.getOrDefault(categoria, BigDecimal.ZERO);
                BigDecimal diferencia = m2.subtract(m1);
                int[] color = colorVariacion(diferencia);

                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tabla.addCell(pdf.celda(categoria, TextAlignment.LEFT, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(m1), TextAlignment.RIGHT, tipo));
                tabla.addCell(pdf.celda(Diseno.moneda(m2), TextAlignment.RIGHT, tipo));
                tabla.addCell(pdf.celda(Diseno.monedaConSigno(diferencia), TextAlignment.RIGHT, tipo, color));
                tabla.addCell(pdf.celda(textoCambio(m1, m2), TextAlignment.RIGHT, tipo, color));
                zebra = !zebra;
            }

            tabla.addCell(pdf.celda("Total", TextAlignment.LEFT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(total1), TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(total2), TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.monedaConSigno(diferenciaTotal), TextAlignment.RIGHT, Fila.TOTAL,
                colorVariacion(diferenciaTotal)));
            tabla.addCell(pdf.celda(textoCambio(total1, total2), TextAlignment.RIGHT, Fila.TOTAL,
                colorVariacion(diferenciaTotal)));

            pdf.agregar(tabla);
            pdf.nota("Rojo: el gasto aumentó respecto al período 1. Verde: el gasto disminuyó.");
        }
    }

    /** Variación porcentual entre dos montos: "+12,5%", "Nuevo" o "0,0%". */
    private String textoCambio(BigDecimal anterior, BigDecimal actual) {
        if (anterior.signum() > 0) {
            double pct = actual.subtract(anterior).doubleValue() / anterior.doubleValue() * 100;
            return Diseno.cambio(pct);
        }
        return actual.signum() > 0 ? "Nuevo" : "0,0%";
    }

    /** Rojo si el gasto aumentó, verde si disminuyó, null si no cambió. */
    private int[] colorVariacion(BigDecimal diferencia) {
        if (diferencia.signum() > 0) {
            return Diseno.NEGATIVO;
        }
        if (diferencia.signum() < 0) {
            return Diseno.POSITIVO;
        }
        return null;
    }
    
    //=============================================================      
    
    public void exportarSubCategoriasPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Desglose por subcategorías", false)) {
            pdf.titulo("Desglose por subcategorías", Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay gastos registrados en este período.");
                return;
            }

            BigDecimal totalGeneral = gastos.stream()
                .map(Gasto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Map<String, BigDecimal>> datos = new HashMap<>();
            for (Gasto gasto : gastos) {
                String categoria = gasto.getCategoria().getNombre();
                String subcategoria = gasto.getSubcategoria() != null
                    ? gasto.getSubcategoria().getNombre() : "Sin subcategoría";
                datos.computeIfAbsent(categoria, k -> new HashMap<>())
                    .merge(subcategoria, gasto.getValorTotal(), BigDecimal::add);
            }

            List<Map.Entry<String, Map<String, BigDecimal>>> categorias = datos.entrySet().stream()
                .sorted((a, b) -> sumarMontos(b.getValue()).compareTo(sumarMontos(a.getValue())))
                .collect(Collectors.toList());
            int totalSubcategorias = datos.values().stream().mapToInt(Map::size).sum();

            pdf.indicadores(
                new String[] {"Total gastado", Diseno.moneda(totalGeneral)},
                new String[] {"Categorías", Diseno.entero(datos.size())},
                new String[] {"Subcategorías", Diseno.entero(totalSubcategorias)},
                new String[] {"Transacciones", Diseno.entero(gastos.size())});

            pdf.seccion("Gasto por categoría y subcategoría");
            Table tabla = pdf.tabla(4f, 2f, 1.6f, 1.4f);
            pdf.encabezado(tabla,
                new String[] {"Categoría / subcategoría", "Monto", "% de la categoría", "% del total"},
                new TextAlignment[] {TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT, TextAlignment.RIGHT});

            for (Map.Entry<String, Map<String, BigDecimal>> categoriaEntry : categorias) {
                BigDecimal totalCategoria = sumarMontos(categoriaEntry.getValue());

                // Fila de la categoría
                tabla.addCell(pdf.celda(categoriaEntry.getKey(), TextAlignment.LEFT, Fila.GRUPO));
                tabla.addCell(pdf.celda(Diseno.moneda(totalCategoria), TextAlignment.RIGHT, Fila.GRUPO));
                tabla.addCell(pdf.celda("", TextAlignment.RIGHT, Fila.GRUPO));
                tabla.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(totalCategoria, totalGeneral)),
                    TextAlignment.RIGHT, Fila.GRUPO));

                // Subcategorías de mayor a menor
                List<Map.Entry<String, BigDecimal>> subcategorias = categoriaEntry.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .collect(Collectors.toList());

                boolean zebra = false;
                for (Map.Entry<String, BigDecimal> sub : subcategorias) {
                    Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                    tabla.addCell(pdf.celda(sub.getKey(), TextAlignment.LEFT, tipo, null, 12f));
                    tabla.addCell(pdf.celda(Diseno.moneda(sub.getValue()), TextAlignment.RIGHT, tipo));
                    tabla.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(sub.getValue(), totalCategoria)),
                        TextAlignment.RIGHT, tipo));
                    tabla.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(sub.getValue(), totalGeneral)),
                        TextAlignment.RIGHT, tipo));
                    zebra = !zebra;
                }
            }

            tabla.addCell(pdf.celda("Total general", TextAlignment.LEFT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(totalGeneral), TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda("", TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.porcentaje(100), TextAlignment.RIGHT, Fila.TOTAL));

            pdf.agregar(tabla);
        }
    }

    /** Suma los montos de un mapa de subcategorías. */
    private BigDecimal sumarMontos(Map<String, BigDecimal> montos) {
        return montos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
  //=============================================================    
    public void exportarAnualPDF(int anio, String rutaArchivo) throws Exception {
        LocalDate inicio = LocalDate.of(anio, 1, 1);
        LocalDate fin = LocalDate.of(anio, 12, 31);
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Informe anual " + anio, false)) {
            pdf.titulo("Informe anual " + anio, Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay gastos registrados en el año " + anio + ".");
                return;
            }

            Map<Integer, BigDecimal> gastosPorMes = new TreeMap<>();
            for (int mes = 1; mes <= 12; mes++) {
                gastosPorMes.put(mes, BigDecimal.ZERO);
            }
            for (Gasto g : gastos) {
                gastosPorMes.merge(g.getFecha().getMonthValue(), g.getValorTotal(), BigDecimal::add);
            }

            BigDecimal totalAnual = gastosPorMes.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal maxGasto = gastosPorMes.values().stream()
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            int mesMayor = gastosPorMes.entrySet().stream()
                .max(Map.Entry.<Integer, BigDecimal>comparingByValue()).get().getKey();
            int mesesConGastos = (int) gastosPorMes.values().stream().filter(m -> m.signum() > 0).count();
            BigDecimal promedioMensual = mesesConGastos > 0
                ? totalAnual.divide(BigDecimal.valueOf(mesesConGastos), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            pdf.indicadores(
                new String[] {"Total del año", Diseno.moneda(totalAnual)},
                new String[] {"Promedio mensual", Diseno.moneda(promedioMensual)},
                new String[] {"Meses con gastos", mesesConGastos + " de 12"},
                new String[] {"Transacciones", Diseno.entero(gastos.size())},
                new String[] {"Mes de mayor gasto", Diseno.nombreMes(mesMayor)});

            // ---------- Gasto por mes ----------
            PdfFont fuente = pdf.fuenteBloques();
            boolean conBarras = fuente != null;

            pdf.seccion("Gasto por mes");
            Table tabla = conBarras ? pdf.tabla(2.2f, 2.2f, 1.4f, 4f) : pdf.tabla(3f, 3f, 2f);
            pdf.encabezado(tabla,
                conBarras
                    ? new String[] {"Mes", "Gasto", "% del año", "Distribución"}
                    : new String[] {"Mes", "Gasto", "% del año"},
                conBarras
                    ? new TextAlignment[] {TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT, TextAlignment.LEFT}
                    : new TextAlignment[] {TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT});

            boolean zebra = false;
            for (int mes = 1; mes <= 12; mes++) {
                BigDecimal monto = gastosPorMes.get(mes);
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                boolean hayGasto = monto.signum() > 0;

                tabla.addCell(pdf.celda(Diseno.nombreMes(mes), TextAlignment.LEFT, tipo));
                tabla.addCell(pdf.celda(hayGasto ? Diseno.moneda(monto) : "-", TextAlignment.RIGHT, tipo));
                tabla.addCell(pdf.celda(hayGasto ? Diseno.porcentaje(pctDeTotal(monto, totalAnual)) : "-",
                    TextAlignment.RIGHT, tipo));
                if (conBarras) {
                    double proporcion = maxGasto.signum() > 0 ? monto.doubleValue() / maxGasto.doubleValue() : 0;
                    tabla.addCell(pdf.celdaBarra(proporcion, fuente, tipo));
                }
                zebra = !zebra;
            }

            tabla.addCell(pdf.celda("Total", TextAlignment.LEFT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(totalAnual), TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.porcentaje(100), TextAlignment.RIGHT, Fila.TOTAL));
            if (conBarras) {
                tabla.addCell(pdf.celda("", TextAlignment.LEFT, Fila.TOTAL));
            }
            pdf.agregar(tabla);

            // ---------- Principales categorías ----------
            Map<String, BigDecimal> porCategoria = gastos.stream()
                .collect(Collectors.groupingBy(
                    g -> g.getCategoria().getNombre(),
                    Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
            List<Map.Entry<String, BigDecimal>> top = porCategoria.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

            pdf.seccion("Principales categorías del año");
            Table tablaCat = pdf.tabla(0.6f, 4f, 2f, 1.4f);
            pdf.encabezado(tablaCat,
                new String[] {"#", "Categoría", "Monto", "% del año"},
                new TextAlignment[] {TextAlignment.CENTER, TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT});
            int pos = 1;
            zebra = false;
            for (Map.Entry<String, BigDecimal> e : top) {
                Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                tablaCat.addCell(pdf.celda(String.valueOf(pos++), TextAlignment.CENTER, tipo));
                tablaCat.addCell(pdf.celda(e.getKey(), TextAlignment.LEFT, tipo));
                tablaCat.addCell(pdf.celda(Diseno.moneda(e.getValue()), TextAlignment.RIGHT, tipo));
                tablaCat.addCell(pdf.celda(Diseno.porcentaje(pctDeTotal(e.getValue(), totalAnual)),
                    TextAlignment.RIGHT, tipo));
                zebra = !zebra;
            }
            pdf.agregar(tablaCat);
        }
    }

    // Método auxiliar para headers mejorados
    private void agregarCeldaHeaderMejorada(Table table, String texto, DeviceRgb colorFondo, DeviceRgb colorTexto) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell();
        cell.add(new Paragraph(texto)
            .setFontSize(11)
            .setBold()
            .setFontColor(colorTexto));
        cell.setBackgroundColor(colorFondo);
        cell.setBorder(new SolidBorder(colorFondo, 1));
        cell.setPadding(5);
        cell.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
        cell.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
        table.addCell(cell);
    }
    
  //=============================================================      
    
    
    private void exportarTextoPDF(String contenido, String rutaArchivo, String titulo) throws Exception {
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);

        // Título con mejor estilo
        Paragraph tituloParrafo = new Paragraph(titulo)
            .setFontSize(18)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(8)
            .setFontColor(azulOscuro);
        document.add(tituloParrafo);

        // Fecha de generación más pequeña
        Paragraph fechaGen = new Paragraph("Generado: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .setFontSize(9)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(25)
            .setItalic()
            .setFontColor(new DeviceRgb(107, 114, 128));
        document.add(fechaGen);

        // Contenido con mejor fuente monoespaciada
        Paragraph contenidoParrafo = new Paragraph(contenido)
            .setFontSize(9)
            .setFixedLeading(14)
            .setMarginLeft(20)
            .setMarginRight(20);
        document.add(contenidoParrafo);

        // Footer mejorado
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(30)
            .setItalic()
            .setFontColor(new DeviceRgb(107, 114, 128));
        document.add(footer);

        document.close();
    }

    // ==================== EXPORTACIÓN EXCEL ====================

    public void exportarInformeGeneralExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        String periodo = Diseno.fecha(inicio) + " - " + Diseno.fecha(fin);
        ExcelEstilo estilos = new ExcelEstilo();

        if (gastos.isEmpty()) {
            HojaExcel vacia = new HojaExcel(estilos, "Informe general", 3, false);
            vacia.titulo("Informe de gastos", periodo);
            vacia.nota("No hay gastos registrados en este período.");
            vacia.terminar(false, false);
            estilos.guardar(rutaArchivo);
            return;
        }

        Map<YearMonth, Map<String, BigDecimal>> datos = new TreeMap<>();
        Map<String, BigDecimal> totalPorCategoria = new HashMap<>();
        for (Gasto g : gastos) {
            String categoria = g.getCategoria().getNombre();
            datos.computeIfAbsent(YearMonth.from(g.getFecha()), k -> new HashMap<>())
                .merge(categoria, g.getValorTotal(), BigDecimal::add);
            totalPorCategoria.merge(categoria, g.getValorTotal(), BigDecimal::add);
        }
        List<String> categorias = new ArrayList<>(totalPorCategoria.keySet());
        categorias.sort((a, b) -> totalPorCategoria.get(b).compareTo(totalPorCategoria.get(a)));
        List<YearMonth> meses = new ArrayList<>(datos.keySet());

        int nMeses = meses.size();
        int colTotal = nMeses + 1;
        BigDecimal totalGeneral = sumarGastos(gastos);
        BigDecimal promedio = totalGeneral.divide(BigDecimal.valueOf(nMeses), 0, RoundingMode.HALF_UP);

        HojaExcel hoja = new HojaExcel(estilos, "Informe general", nMeses + 2, nMeses > 5);
        hoja.titulo("Informe de gastos", periodo);

        String[] titulos = new String[nMeses + 2];
        HorizontalAlignment[] alineaciones = new HorizontalAlignment[nMeses + 2];
        titulos[0] = "Categoría";
        alineaciones[0] = HorizontalAlignment.LEFT;
        for (int i = 0; i < nMeses; i++) {
            titulos[i + 1] = Diseno.etiquetaMes(meses.get(i));
            alineaciones[i + 1] = HorizontalAlignment.RIGHT;
        }
        titulos[colTotal] = "Total";
        alineaciones[colTotal] = HorizontalAlignment.RIGHT;
        hoja.encabezado(titulos, alineaciones);

        int primera = hoja.siguienteFila();
        for (String categoria : categorias) {
            int filaActual = hoja.siguienteFila();
            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO).texto(categoria);
            for (YearMonth mes : meses) {
                fila.moneda(datos.get(mes).getOrDefault(categoria, BigDecimal.ZERO));
            }
            fila.formulaMoneda("SUM(" + HojaExcel.ref(1, filaActual) + ":" + HojaExcel.ref(nMeses, filaActual) + ")");
        }
        int ultima = hoja.siguienteFila() - 1;

        // Total con SUBTOTAL: al filtrar en Excel suma solo las filas visibles
        HojaExcel.FilaExcel total = hoja.fila(ExcelEstilo.Rol.TOTAL).texto("Total mes");
        for (int c = 1; c <= colTotal; c++) {
            total.formulaMoneda("SUBTOTAL(109," + HojaExcel.ref(c, primera) + ":" + HojaExcel.ref(c, ultima) + ")");
        }

        hoja.espacio();
        hoja.seccion("Estadísticas");
        hoja.estadistica("Total gastado", Diseno.moneda(totalGeneral));
        hoja.estadistica("Período", nMeses + (nMeses == 1 ? " mes" : " meses"));
        hoja.estadistica("Promedio mensual", Diseno.moneda(promedio));
        hoja.estadistica("Total de categorías", Diseno.entero(categorias.size()));
        hoja.estadistica("Total de transacciones", Diseno.entero(gastos.size()));

        hoja.terminar(true, true);
        estilos.guardar(rutaArchivo);
    }

    /** Suma el valor de un gasto en la posición del período (mes o día) de su categoría y subcategoría. */
    private void acumularMatriz(Map<String, Map<String, BigDecimal[]>> datos, Gasto g, int indice, int periodos) {
        String categoria = g.getCategoria().getNombre();
        String sub = g.getSubcategoria() != null ? g.getSubcategoria().getNombre() : "Sin subcategoría";
        BigDecimal[] fila = datos.computeIfAbsent(categoria, k -> new HashMap<>())
            .computeIfAbsent(sub, k -> {
                BigDecimal[] valores = new BigDecimal[periodos];
                java.util.Arrays.fill(valores, BigDecimal.ZERO);
                return valores;
            });
        fila[indice] = fila[indice].add(g.getValorTotal());
    }

    /**
     * Escribe la matriz categoría > subcategoría contra períodos (meses o días):
     * fila de categoría con su total, subcategorías debajo, total por fila y total general, todo con fórmulas.
     */
    private void escribirMatrizGastos(HojaExcel hoja, Map<String, Map<String, BigDecimal[]>> datos,
                                      int periodos, boolean conPorcentaje) {
        Map<String, BigDecimal> totalPorCategoria = new HashMap<>();
        for (Map.Entry<String, Map<String, BigDecimal[]>> e : datos.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal[] fila : e.getValue().values()) {
                total = total.add(sumarArreglo(fila));
            }
            totalPorCategoria.put(e.getKey(), total);
        }
        List<String> categorias = new ArrayList<>(datos.keySet());
        categorias.sort((a, b) -> totalPorCategoria.get(b).compareTo(totalPorCategoria.get(a)));

        int colTotal = periodos + 1;
        int filasEnTabla = 0;
        for (String categoria : categorias) {
            filasEnTabla += 1 + datos.get(categoria).size();
        }
        int filaTotal = hoja.siguienteFila() + filasEnTabla;

        List<Integer> filasGrupo = new ArrayList<>();
        for (String categoria : categorias) {
            Map<String, BigDecimal[]> subs = datos.get(categoria);
            int filaGrupo = hoja.siguienteFila();
            int primeraSub = filaGrupo + 1;
            int ultimaSub = filaGrupo + subs.size();
            filasGrupo.add(filaGrupo);

            HojaExcel.FilaExcel grupo = hoja.fila(ExcelEstilo.Rol.GRUPO).texto(categoria);
            for (int c = 1; c <= periodos; c++) {
                grupo.formulaMoneda("SUM(" + HojaExcel.ref(c, primeraSub) + ":" + HojaExcel.ref(c, ultimaSub) + ")");
            }
            grupo.formulaMoneda("SUM(" + HojaExcel.ref(1, filaGrupo) + ":" + HojaExcel.ref(periodos, filaGrupo) + ")");
            if (conPorcentaje) {
                grupo.formulaPct(formulaPorcentaje(colTotal, filaGrupo, filaTotal));
            }

            List<Map.Entry<String, BigDecimal[]>> filasSub = new ArrayList<>(subs.entrySet());
            filasSub.sort((a, b) -> sumarArreglo(b.getValue()).compareTo(sumarArreglo(a.getValue())));
            for (Map.Entry<String, BigDecimal[]> entrada : filasSub) {
                int filaActual = hoja.siguienteFila();
                HojaExcel.FilaExcel sub = hoja.fila(ExcelEstilo.Rol.DATO).texto("    " + entrada.getKey());
                for (BigDecimal valor : entrada.getValue()) {
                    if (valor.signum() == 0) {
                        sub.vacio();
                    } else {
                        sub.moneda(valor);
                    }
                }
                sub.formulaMoneda("SUM(" + HojaExcel.ref(1, filaActual) + ":" + HojaExcel.ref(periodos, filaActual) + ")");
                if (conPorcentaje) {
                    sub.formulaPct(formulaPorcentaje(colTotal, filaActual, filaTotal));
                }
            }
        }

        HojaExcel.FilaExcel total = hoja.fila(ExcelEstilo.Rol.TOTAL).texto("Total");
        for (int c = 1; c <= colTotal; c++) {
            StringBuilder suma = new StringBuilder();
            for (int filaGrupo : filasGrupo) {
                if (suma.length() > 0) {
                    suma.append("+");
                }
                suma.append(HojaExcel.ref(c, filaGrupo));
            }
            total.formulaMoneda(suma.toString());
        }
        if (conPorcentaje) {
            total.pct(1.0);
        }
    }

    private String formulaPorcentaje(int colTotal, int fila, int filaTotal) {
        String celda = HojaExcel.ref(colTotal, fila);
        String granTotal = HojaExcel.ref(colTotal, filaTotal);
        return "IF(" + granTotal + "=0,0," + celda + "/" + granTotal + ")";
    }

    private BigDecimal sumarArreglo(BigDecimal[] valores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : valores) {
            total = total.add(v);
        }
        return total;
    }

    private BigDecimal sumarGastos(List<Gasto> gastos) {
        return gastos.stream().map(Gasto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void exportarInformeCategoriaExcel(String categoria, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> g.getCategoria().getNombre().equalsIgnoreCase(categoria))
            .collect(Collectors.toList());

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Informe Categoría");

        CellStyle headerStyle = crearEstiloHeader(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);
        CellStyle dateStyle = crearEstiloFecha(workbook);
        CellStyle titleStyle = crearEstiloTitulo(workbook);

        Row titleRow = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("INFORME POR CATEGORÍA: " + categoria.toUpperCase());
        titleCell.setCellStyle(titleStyle);

        Row headerRow = sheet.createRow(2);
        String[] columnas = {"Fecha", "Hora", "Subcategoría", "Producto", "Cantidad", "Valor Unit.", "Total", "Notas"};
        
        for (int i = 0; i < columnas.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnas[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 3;
        BigDecimal totalGastado = BigDecimal.ZERO;

        for (Gasto gasto : gastos) {
            Row row = sheet.createRow(rowNum++);
            
            org.apache.poi.ss.usermodel.Cell dateCell = row.createCell(0);
            dateCell.setCellValue(formatearFecha(gasto.getFecha()));
            dateCell.setCellStyle(dateStyle);
            
            row.createCell(1).setCellValue(formatearHora(gasto));
            row.createCell(2).setCellValue(gasto.getSubcategoria().getNombre());
            row.createCell(3).setCellValue(gasto.getProducto());
            row.createCell(4).setCellValue(gasto.getCantidad());
            
            org.apache.poi.ss.usermodel.Cell valorUnitCell = row.createCell(5);
            valorUnitCell.setCellValue(gasto.getValorUnitario().doubleValue());
            valorUnitCell.setCellStyle(moneyStyle);
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(6);
            totalCell.setCellValue(gasto.getValorTotal().doubleValue());
            totalCell.setCellStyle(moneyStyle);
            
            row.createCell(7).setCellValue(gasto.getNotas() != null ? gasto.getNotas() : "");
            
            totalGastado = totalGastado.add(gasto.getValorTotal());
        }

        Row totalRow = sheet.createRow(rowNum + 1);
        org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(5);
        totalLabelCell.setCellValue("TOTAL:");
        totalLabelCell.setCellStyle(headerStyle);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(6);
        totalValueCell.setCellValue(totalGastado.doubleValue());
        totalValueCell.setCellStyle(crearEstiloTotal(workbook));

        for (int i = 0; i < columnas.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fileOut = new FileOutputStream(rutaArchivo)) {
            workbook.write(fileOut);
        }
        
        workbook.close();
    }

    public void exportarInformeProductoExcel(String producto, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> mismoProducto(g.getProducto(), producto))
            .sorted(Comparator.comparing(Gasto::getFecha).reversed())
            .collect(Collectors.toList());

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Análisis Producto");

        CellStyle headerStyle = crearEstiloHeader(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);
        CellStyle dateStyle = crearEstiloFecha(workbook);
        CellStyle titleStyle = crearEstiloTitulo(workbook);

        Row titleRow = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS DE PRODUCTO: " + producto.toUpperCase());
        titleCell.setCellStyle(titleStyle);

        Row headerRow = sheet.createRow(2);
        String[] columnas = {"Fecha", "Hora", "Cantidad", "Precio Unitario", "Total", "Notas"};
        
        for (int i = 0; i < columnas.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnas[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 3;
        BigDecimal totalGastado = BigDecimal.ZERO;
        int cantidadTotal = 0;

        for (Gasto gasto : gastos) {
            Row row = sheet.createRow(rowNum++);
            
            org.apache.poi.ss.usermodel.Cell dateCell = row.createCell(0);
            dateCell.setCellValue(formatearFecha(gasto.getFecha()));
            dateCell.setCellStyle(dateStyle);
            
            row.createCell(1).setCellValue(formatearHora(gasto));
            row.createCell(2).setCellValue(gasto.getCantidad());
            
            org.apache.poi.ss.usermodel.Cell precioCell = row.createCell(3);
            precioCell.setCellValue(gasto.getValorUnitario().doubleValue());
            precioCell.setCellStyle(moneyStyle);
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(4);
            totalCell.setCellValue(gasto.getValorTotal().doubleValue());
            totalCell.setCellStyle(moneyStyle);
            
            row.createCell(5).setCellValue(gasto.getNotas() != null ? gasto.getNotas() : "");
            
            totalGastado = totalGastado.add(gasto.getValorTotal());
            cantidadTotal += gasto.getCantidad();
        }

        Row totalRow = sheet.createRow(rowNum + 1);
        totalRow.createCell(0).setCellValue("TOTALES:");
        totalRow.createCell(2).setCellValue(cantidadTotal);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(4);
        totalValueCell.setCellValue(totalGastado.doubleValue());
        totalValueCell.setCellStyle(crearEstiloTotal(workbook));

        for (int i = 0; i < columnas.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fileOut = new FileOutputStream(rutaArchivo)) {
            workbook.write(fileOut);
        }
        
        workbook.close();
    }

 // =====================================================================
    // EXCEL: COMPARATIVO, DASHBOARD, SUBCATEGORÍAS E INFORME ANUAL
    // Cada Excel replica su PDF: mismas secciones, columnas, orden y cifras.
    // =====================================================================

    /** Excel con un solo mensaje, para cuando no hay gastos en el período. */
    private void guardarExcelVacio(String nombreHoja, String titulo, String periodo,
                                   String mensaje, String rutaArchivo) throws Exception {
        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel vacia = new HojaExcel(estilos, nombreHoja, 3, false);
        vacia.titulo(titulo, periodo);
        vacia.nota(mensaje);
        vacia.terminar(false, false);
        estilos.guardar(rutaArchivo);
    }

    // ---------- COMPARATIVO ----------

    public void exportarComparativoExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        long diasTotal = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
        LocalDate puntoMedio = inicio.plusDays(diasTotal / 2);
        LocalDate inicio2 = puntoMedio.plusDays(1);

        List<Gasto> gastos1 = gastoService.listarPorPeriodo(inicio, puntoMedio);
        List<Gasto> gastos2 = gastoService.listarPorPeriodo(inicio2, fin);

        Map<String, BigDecimal> monto1 = gastos1.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
        Map<String, BigDecimal> monto2 = gastos2.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));

        Set<String> categoriasSet = new TreeSet<>();
        categoriasSet.addAll(monto1.keySet());
        categoriasSet.addAll(monto2.keySet());

        // Igual que el PDF: de mayor a menor gasto combinado
        List<String> categorias = new ArrayList<>(categoriasSet);
        categorias.sort((a, b) -> monto1.getOrDefault(b, BigDecimal.ZERO).add(monto2.getOrDefault(b, BigDecimal.ZERO))
            .compareTo(monto1.getOrDefault(a, BigDecimal.ZERO).add(monto2.getOrDefault(a, BigDecimal.ZERO))));

        String periodo = Diseno.fecha(inicio) + " al " + Diseno.fecha(fin);

        if (categorias.isEmpty()) {
            guardarExcelVacio("Comparativo", "Comparación de períodos", periodo,
                "No hay gastos registrados en ninguno de los dos períodos.", rutaArchivo);
            return;
        }

        BigDecimal total1 = sumarGastos(gastos1);
        BigDecimal total2 = sumarGastos(gastos2);
        BigDecimal diferenciaTotal = total2.subtract(total1);

        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Comparativo", 5, false);
        hoja.titulo("Comparación de períodos", periodo);

        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Categoría", "Período 1", "Período 2", "Diferencia", "Cambio"},
            new HorizontalAlignment[] {izq, der, der, der, der});

        int primera = hoja.siguienteFila();
        for (String categoria : categorias) {
            BigDecimal m1 = monto1.getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal m2 = monto2.getOrDefault(categoria, BigDecimal.ZERO);

            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
            fila.texto(categoria);
            fila.moneda(m1);
            fila.moneda(m2);
            fila.moneda(m2.subtract(m1));
            fila.texto(textoCambio(m1, m2));
        }
        int ultima = hoja.siguienteFila() - 1;

        // Total con SUBTOTAL: al filtrar en Excel suma solo las filas visibles
        HojaExcel.FilaExcel total = hoja.fila(ExcelEstilo.Rol.TOTAL);
        total.texto("Total");
        for (int c = 1; c <= 3; c++) {
            total.formulaMoneda("SUBTOTAL(109," + HojaExcel.ref(c, primera) + ":" + HojaExcel.ref(c, ultima) + ")");
        }
        total.texto(textoCambio(total1, total2));

        hoja.espacio();
        hoja.seccion("Resumen");
        hoja.estadistica("Período 1", Diseno.fecha(inicio) + " al " + Diseno.fecha(puntoMedio));
        hoja.estadistica("Período 2", Diseno.fecha(inicio2) + " al " + Diseno.fecha(fin));
        hoja.estadistica("Total período 1", Diseno.moneda(total1));
        hoja.estadistica("Total período 2", Diseno.moneda(total2));
        hoja.estadistica("Diferencia", Diseno.monedaConSigno(diferenciaTotal));
        hoja.estadistica("Cambio", textoCambio(total1, total2));

        hoja.terminar(true, true);
        estilos.guardar(rutaArchivo);
    }

    // ---------- DASHBOARD EJECUTIVO ----------

    public void exportarDashboardExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        String periodo = Diseno.fecha(inicio) + " al " + Diseno.fecha(fin);

        if (gastos.isEmpty()) {
            guardarExcelVacio("Dashboard", "Dashboard ejecutivo", periodo,
                "No hay gastos registrados en este período.", rutaArchivo);
            return;
        }

        BigDecimal totalGastado = sumarGastos(gastos);
        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        BigDecimal promedioDiario = totalGastado.divide(BigDecimal.valueOf(dias), 0, RoundingMode.HALF_UP);
        BigDecimal mayorGasto = gastos.stream()
            .map(Gasto::getValorTotal)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        BigDecimal promedioTransaccion = totalGastado.divide(BigDecimal.valueOf(gastos.size()), 0, RoundingMode.HALF_UP);

        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Dashboard", 4, false);
        hoja.titulo("Dashboard ejecutivo", periodo);

        hoja.estadistica("Total gastado", Diseno.moneda(totalGastado));
        hoja.estadistica("Promedio diario", Diseno.moneda(promedioDiario));
        hoja.estadistica("Mayor gasto individual", Diseno.moneda(mayorGasto));
        hoja.estadistica("Transacciones", Diseno.entero(gastos.size()));
        hoja.estadistica("Promedio por transacción", Diseno.moneda(promedioTransaccion));

        HorizontalAlignment cen = HorizontalAlignment.CENTER;
        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;

        // ---------- Top 3 categorías ----------
        Map<String, BigDecimal> porCategoria = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
        List<Map.Entry<String, BigDecimal>> top3 = porCategoria.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());

        hoja.espacio();
        hoja.seccion("Top 3 categorías");
        hoja.encabezado(
            new String[] {"#", "Categoría", "Monto", "% del total"},
            new HorizontalAlignment[] {cen, izq, der, der});
        int pos = 1;
        for (Map.Entry<String, BigDecimal> e : top3) {
            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
            fila.entero(pos++);
            fila.texto(e.getKey());
            fila.moneda(e.getValue());
            fila.pct(pctDeTotal(e.getValue(), totalGastado) / 100.0);
        }

        // ---------- Productos más comprados ----------
        Map<String, Long> vecesPorProducto = gastos.stream()
            .collect(Collectors.groupingBy(Gasto::getProducto, Collectors.counting()));
        Map<String, BigDecimal> montoPorProducto = gastos.stream()
            .collect(Collectors.groupingBy(
                Gasto::getProducto,
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
        List<Map.Entry<String, Long>> topFrecuentes = vecesPorProducto.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        hoja.espacio();
        hoja.seccion("Productos más comprados");
        hoja.encabezado(
            new String[] {"#", "Producto", "Veces", "Monto"},
            new HorizontalAlignment[] {cen, izq, cen, der});
        pos = 1;
        for (Map.Entry<String, Long> e : topFrecuentes) {
            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
            fila.entero(pos++);
            fila.texto(e.getKey());
            fila.entero(e.getValue().intValue());
            fila.moneda(montoPorProducto.get(e.getKey()));
        }

        // ---------- Gasto por usuario ----------
        Map<String, BigDecimal> montoPorUsuario = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getUsuario() == null ? "Sin usuario" : g.getUsuario().getUsername(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
        Map<String, Long> comprasPorUsuario = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getUsuario() == null ? "Sin usuario" : g.getUsuario().getUsername(),
                Collectors.counting()));
        List<Map.Entry<String, BigDecimal>> usuarios = montoPorUsuario.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .collect(Collectors.toList());

        hoja.espacio();
        hoja.seccion("Gasto por usuario");
        hoja.encabezado(
            new String[] {"Registrado por", "Transacciones", "Monto", "% del total"},
            new HorizontalAlignment[] {izq, cen, der, der});
        for (Map.Entry<String, BigDecimal> e : usuarios) {
            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
            fila.texto(e.getKey());
            fila.entero(comprasPorUsuario.get(e.getKey()).intValue());
            fila.moneda(e.getValue());
            fila.pct(pctDeTotal(e.getValue(), totalGastado) / 100.0);
        }

        hoja.terminar(false, false);
        estilos.guardar(rutaArchivo);
    }

    // ---------- DESGLOSE POR SUBCATEGORÍAS ----------

    public void exportarSubCategoriasExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        String periodo = Diseno.fecha(inicio) + " al " + Diseno.fecha(fin);

        if (gastos.isEmpty()) {
            guardarExcelVacio("Subcategorías", "Desglose por subcategorías", periodo,
                "No hay gastos registrados en este período.", rutaArchivo);
            return;
        }

        BigDecimal totalGeneral = sumarGastos(gastos);

        Map<String, Map<String, BigDecimal>> datos = new HashMap<>();
        for (Gasto gasto : gastos) {
            String categoria = gasto.getCategoria().getNombre();
            String subcategoria = gasto.getSubcategoria() != null
                ? gasto.getSubcategoria().getNombre() : "Sin subcategoría";
            datos.computeIfAbsent(categoria, k -> new HashMap<>())
                .merge(subcategoria, gasto.getValorTotal(), BigDecimal::add);
        }

        // Igual que el PDF: categorías y subcategorías de mayor a menor
        List<Map.Entry<String, Map<String, BigDecimal>>> categorias = datos.entrySet().stream()
            .sorted((a, b) -> sumarMontos(b.getValue()).compareTo(sumarMontos(a.getValue())))
            .collect(Collectors.toList());
        int totalSubcategorias = datos.values().stream().mapToInt(Map::size).sum();

        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Subcategorías", 4, false);
        hoja.titulo("Desglose por subcategorías", periodo);

        hoja.estadistica("Total gastado", Diseno.moneda(totalGeneral));
        hoja.estadistica("Categorías", Diseno.entero(datos.size()));
        hoja.estadistica("Subcategorías", Diseno.entero(totalSubcategorias));
        hoja.estadistica("Transacciones", Diseno.entero(gastos.size()));

        hoja.espacio();
        hoja.seccion("Gasto por categoría y subcategoría");
        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Categoría / subcategoría", "Monto", "% de la categoría", "% del total"},
            new HorizontalAlignment[] {izq, der, der, der});

        for (Map.Entry<String, Map<String, BigDecimal>> categoriaEntry : categorias) {
            BigDecimal totalCategoria = sumarMontos(categoriaEntry.getValue());

            HojaExcel.FilaExcel grupo = hoja.fila(ExcelEstilo.Rol.GRUPO);
            grupo.texto(categoriaEntry.getKey());
            grupo.moneda(totalCategoria);
            grupo.vacio();
            grupo.pct(pctDeTotal(totalCategoria, totalGeneral) / 100.0);

            List<Map.Entry<String, BigDecimal>> subcategorias = categoriaEntry.getValue().entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());

            for (Map.Entry<String, BigDecimal> sub : subcategorias) {
                HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
                fila.texto("    " + sub.getKey());
                fila.moneda(sub.getValue());
                fila.pct(pctDeTotal(sub.getValue(), totalCategoria) / 100.0);
                fila.pct(pctDeTotal(sub.getValue(), totalGeneral) / 100.0);
            }
        }

        HojaExcel.FilaExcel total = hoja.fila(ExcelEstilo.Rol.TOTAL);
        total.texto("Total general");
        total.moneda(totalGeneral);
        total.vacio();
        total.pct(1.0);

        hoja.terminar(false, false);
        estilos.guardar(rutaArchivo);
    }

    // ---------- INFORME ANUAL ----------

    public void exportarAnualExcel(int anio, String rutaArchivo) throws Exception {
        LocalDate inicio = LocalDate.of(anio, 1, 1);
        LocalDate fin = LocalDate.of(anio, 12, 31);
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        String titulo = "Informe anual " + anio;
        String periodo = Diseno.fecha(inicio) + " al " + Diseno.fecha(fin);

        if (gastos.isEmpty()) {
            guardarExcelVacio("Anual " + anio, titulo, periodo,
                "No hay gastos registrados en el año " + anio + ".", rutaArchivo);
            return;
        }

        Map<Integer, BigDecimal> gastosPorMes = new TreeMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            gastosPorMes.put(mes, BigDecimal.ZERO);
        }
        for (Gasto g : gastos) {
            gastosPorMes.merge(g.getFecha().getMonthValue(), g.getValorTotal(), BigDecimal::add);
        }

        BigDecimal totalAnual = gastosPorMes.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        int mesMayor = gastosPorMes.entrySet().stream()
            .max(Map.Entry.<Integer, BigDecimal>comparingByValue()).get().getKey();
        int mesesConGastos = (int) gastosPorMes.values().stream().filter(m -> m.signum() > 0).count();
        BigDecimal promedioMensual = mesesConGastos > 0
            ? totalAnual.divide(BigDecimal.valueOf(mesesConGastos), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Anual " + anio, 4, false);
        hoja.titulo(titulo, periodo);

        hoja.estadistica("Total del año", Diseno.moneda(totalAnual));
        hoja.estadistica("Promedio mensual", Diseno.moneda(promedioMensual));
        hoja.estadistica("Meses con gastos", mesesConGastos + " de 12");
        hoja.estadistica("Transacciones", Diseno.entero(gastos.size()));
        hoja.estadistica("Mes de mayor gasto", Diseno.nombreMes(mesMayor));

        HorizontalAlignment cen = HorizontalAlignment.CENTER;
        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;

        // ---------- Gasto por mes ----------
        hoja.espacio();
        hoja.seccion("Gasto por mes");
        hoja.encabezado(
            new String[] {"Mes", "Gasto", "% del año"},
            new HorizontalAlignment[] {izq, der, der});

        int primera = hoja.siguienteFila();
        for (int mes = 1; mes <= 12; mes++) {
            BigDecimal monto = gastosPorMes.get(mes);
            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
            fila.texto(Diseno.nombreMes(mes));
            if (monto.signum() > 0) {
                fila.moneda(monto);
                fila.pct(pctDeTotal(monto, totalAnual) / 100.0);
            } else {
                fila.vacio();
                fila.vacio();
            }
        }
        int ultima = hoja.siguienteFila() - 1;

        HojaExcel.FilaExcel total = hoja.fila(ExcelEstilo.Rol.TOTAL);
        total.texto("Total");
        total.formulaMoneda("SUM(" + HojaExcel.ref(1, primera) + ":" + HojaExcel.ref(1, ultima) + ")");
        total.pct(1.0);

        // ---------- Principales categorías ----------
        Map<String, BigDecimal> porCategoria = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)));
        List<Map.Entry<String, BigDecimal>> top = porCategoria.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        hoja.espacio();
        hoja.seccion("Principales categorías del año");
        hoja.encabezado(
            new String[] {"#", "Categoría", "Monto", "% del año"},
            new HorizontalAlignment[] {cen, izq, der, der});
        int pos = 1;
        for (Map.Entry<String, BigDecimal> e : top) {
            HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
            fila.entero(pos++);
            fila.texto(e.getKey());
            fila.moneda(e.getValue());
            fila.pct(pctDeTotal(e.getValue(), totalAnual) / 100.0);
        }

        hoja.terminar(false, false);
        estilos.guardar(rutaArchivo);
    }
    
    /** Texto comparable: sin mayúsculas, sin tildes y con espacios simples. */
    public static String normalizarProducto(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Regla única de coincidencia de producto para vista previa, pie, PDF y Excel. */
    public static boolean mismoProducto(String productoGasto, String buscado) {
        return normalizarProducto(productoGasto).equals(normalizarProducto(buscado));
    }

    /** Hasta 5 productos del período cuyo nombre contiene el texto buscado. */
    private List<String> productosParecidos(String buscado, LocalDate inicio, LocalDate fin) {
        String clave = normalizarProducto(buscado);
        return gastoService.listarPorPeriodo(inicio, fin).stream()
            .map(Gasto::getProducto)
            .filter(p -> p != null && normalizarProducto(p).contains(clave))
            .map(String::trim)
            .distinct()
            .limit(5)
            .collect(Collectors.toList());
    }

    // ==================== ESTILOS EXCEL ====================

 // ==================== ESTILOS EXCEL ====================

    private CellStyle crearEstiloHeader(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        // Bordes
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle crearEstiloMoneda(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        // Bordes
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private CellStyle crearEstiloFecha(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        // Bordes
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private CellStyle crearEstiloTotal(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.RIGHT);
        // Bordes más gruesos
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle crearEstiloTitulo(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }


    // ==================== MÉTODOS DE UTILIDAD ====================

    private String formatearMoneda(BigDecimal valor) {
        return String.format("$%,.2f", valor);
    }

    private String formatearFecha(LocalDate fecha) {
        return fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
    
    private String formatearHora(Gasto gasto) {
        Object hora = gasto.getHora();
        if (hora == null) return "-";
        if (hora instanceof java.time.LocalTime) {
            return ((java.time.LocalTime) hora).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        return hora.toString();
    }   

    private String truncar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() > max ? texto.substring(0, max - 3) + "..." : texto;
    }
    
    // ==================== INFORME CATEGORÍA Y PRODUCTO ====================

    /** Agrupa categoría > producto (suma por nombre normalizado), ordenado de mayor a menor. */
    private Map<String, Map<String, BigDecimal>> agruparCategoriaProducto(List<Gasto> gastos) {
        log.debug("Agrupando {} gastos por categoría y producto", gastos.size());
        int sinProducto = 0;
    	Map<String, Map<String, BigDecimal>> porCategoria = new HashMap<>();
        Map<String, Map<String, String>> nombres = new HashMap<>();

        for (Gasto g : gastos) {
            String categoria = g.getCategoria().getNombre();
            String producto = g.getProducto() != null && !g.getProducto().trim().isEmpty()
                ? g.getProducto().trim() : "Producto no especificado";
            String clave = normalizarProducto(producto);
            String nombre = nombres.computeIfAbsent(categoria, k -> new HashMap<>())
                .computeIfAbsent(clave, k -> producto);
            porCategoria.computeIfAbsent(categoria, k -> new HashMap<>())
                .merge(nombre, g.getValorTotal(), BigDecimal::add);
        }

        Map<String, Map<String, BigDecimal>> ordenado = new LinkedHashMap<>();
        porCategoria.entrySet().stream()
            .sorted((a, b) -> sumarMontos(b.getValue()).compareTo(sumarMontos(a.getValue())))
            .forEach(e -> {
                Map<String, BigDecimal> productos = new LinkedHashMap<>();
                e.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(p -> productos.put(p.getKey(), p.getValue()));
                ordenado.put(e.getKey(), productos);
            });
        return ordenado;
    }

    // ---------- Vista previa ----------

    public String generarInformeCategoriaProducto(LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        sb.append("INFORME DE GASTOS POR CATEGORÍA Y PRODUCTO\n");
        sb.append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("=".repeat(90)).append("\n\n");

        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        if (gastos.isEmpty()) {
            sb.append("No hay gastos registrados en este período.\n");
            return sb.toString();
        }

        Map<String, Map<String, BigDecimal>> datos = agruparCategoriaProducto(gastos);
        BigDecimal totalGeneral = sumarGastos(gastos);

        sb.append(String.format("%-26s %-30s %16s %16s%n", "CATEGORÍA", "PRODUCTO", "VALOR", "TOTAL"));
        sb.append("-".repeat(90)).append("\n");

        for (Map.Entry<String, Map<String, BigDecimal>> cat : datos.entrySet()) {
            sb.append(String.format("%-26s %-30s %16s %16s%n",
                truncar(cat.getKey().toUpperCase(), 26), "", "", formatearMoneda(sumarMontos(cat.getValue()))));
            for (Map.Entry<String, BigDecimal> prod : cat.getValue().entrySet()) {
                sb.append(String.format("%-26s %-30s %16s %16s%n",
                    "", truncar(prod.getKey(), 30), formatearMoneda(prod.getValue()), ""));
            }
        }

        sb.append("=".repeat(90)).append("\n");
        sb.append(String.format("%-26s %-30s %16s %16s%n", "TOTAL GENERAL", "", "", formatearMoneda(totalGeneral)));
        return sb.toString();
    }

    // ---------- PDF ----------

    public void exportarCategoriaProductoPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

        try (PdfEstilo pdf = new PdfEstilo(rutaArchivo, "Informe por categoría y producto", false)) {
            pdf.titulo("Informe de gastos por categoría y producto",
                Diseno.fecha(inicio) + " al " + Diseno.fecha(fin));

            if (gastos.isEmpty()) {
                pdf.nota("No hay gastos registrados en este período.");
                return;
            }

            Map<String, Map<String, BigDecimal>> datos = agruparCategoriaProducto(gastos);
            BigDecimal totalGeneral = sumarGastos(gastos);
            int totalProductos = datos.values().stream().mapToInt(Map::size).sum();

            pdf.indicadores(
                new String[] {"Total gastado", Diseno.moneda(totalGeneral)},
                new String[] {"Categorías", Diseno.entero(datos.size())},
                new String[] {"Productos", Diseno.entero(totalProductos)},
                new String[] {"Transacciones", Diseno.entero(gastos.size())});

            pdf.seccion("Gasto por categoría y producto");
            Table tabla = pdf.tabla(3.2f, 3.8f, 2f, 2f);
            pdf.encabezado(tabla,
                new String[] {"Categoría", "Producto", "Valor", "Total"},
                new TextAlignment[] {TextAlignment.LEFT, TextAlignment.LEFT, TextAlignment.RIGHT, TextAlignment.RIGHT});

            for (Map.Entry<String, Map<String, BigDecimal>> cat : datos.entrySet()) {
                tabla.addCell(pdf.celda(cat.getKey(), TextAlignment.LEFT, Fila.GRUPO));
                tabla.addCell(pdf.celda("", TextAlignment.LEFT, Fila.GRUPO));
                tabla.addCell(pdf.celda("", TextAlignment.RIGHT, Fila.GRUPO));
                tabla.addCell(pdf.celda(Diseno.moneda(sumarMontos(cat.getValue())), TextAlignment.RIGHT, Fila.GRUPO));

                boolean zebra = false;
                for (Map.Entry<String, BigDecimal> prod : cat.getValue().entrySet()) {
                    Fila tipo = zebra ? Fila.ZEBRA : Fila.DATO;
                    tabla.addCell(pdf.celda("", TextAlignment.LEFT, tipo));
                    tabla.addCell(pdf.celda(prod.getKey(), TextAlignment.LEFT, tipo));
                    tabla.addCell(pdf.celda(Diseno.moneda(prod.getValue()), TextAlignment.RIGHT, tipo));
                    tabla.addCell(pdf.celda("", TextAlignment.RIGHT, tipo));
                    zebra = !zebra;
                }
            }

            tabla.addCell(pdf.celda("Total general", TextAlignment.LEFT, Fila.TOTAL));
            tabla.addCell(pdf.celda("", TextAlignment.LEFT, Fila.TOTAL));
            tabla.addCell(pdf.celda("", TextAlignment.RIGHT, Fila.TOTAL));
            tabla.addCell(pdf.celda(Diseno.moneda(totalGeneral), TextAlignment.RIGHT, Fila.TOTAL));

            pdf.agregar(tabla);
        }
    }

    // ---------- Excel ----------

    public void exportarCategoriaProductoExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        String periodo = Diseno.fecha(inicio) + " al " + Diseno.fecha(fin);
        String titulo = "Informe de gastos por categoría y producto";

        if (gastos.isEmpty()) {
            guardarExcelVacio("Categoría y producto", titulo, periodo,
                "No hay gastos registrados en este período.", rutaArchivo);
            return;
        }

        Map<String, Map<String, BigDecimal>> datos = agruparCategoriaProducto(gastos);
        BigDecimal totalGeneral = sumarGastos(gastos);
        int totalProductos = datos.values().stream().mapToInt(Map::size).sum();

        ExcelEstilo estilos = new ExcelEstilo();
        HojaExcel hoja = new HojaExcel(estilos, "Categoría y producto", 4, false);
        hoja.titulo(titulo, periodo);

        hoja.estadistica("Total gastado", Diseno.moneda(totalGeneral));
        hoja.estadistica("Categorías", Diseno.entero(datos.size()));
        hoja.estadistica("Productos", Diseno.entero(totalProductos));
        hoja.estadistica("Transacciones", Diseno.entero(gastos.size()));

        hoja.espacio();
        hoja.seccion("Gasto por categoría y producto");
        HorizontalAlignment izq = HorizontalAlignment.LEFT;
        HorizontalAlignment der = HorizontalAlignment.RIGHT;
        hoja.encabezado(
            new String[] {"Categoría", "Producto", "Valor", "Total"},
            new HorizontalAlignment[] {izq, izq, der, der});

        int primeraDatos = hoja.siguienteFila();
        for (Map.Entry<String, Map<String, BigDecimal>> cat : datos.entrySet()) {
            int filaGrupo = hoja.siguienteFila();
            int primeraProd = filaGrupo + 1;
            int ultimaProd = filaGrupo + cat.getValue().size();

            HojaExcel.FilaExcel grupo = hoja.fila(ExcelEstilo.Rol.GRUPO);
            grupo.texto(cat.getKey());
            grupo.vacio();
            grupo.vacio();
            grupo.formulaMoneda("SUM(" + HojaExcel.ref(2, primeraProd) + ":" + HojaExcel.ref(2, ultimaProd) + ")");

            for (Map.Entry<String, BigDecimal> prod : cat.getValue().entrySet()) {
                HojaExcel.FilaExcel fila = hoja.fila(ExcelEstilo.Rol.DATO);
                fila.vacio();
                fila.texto(prod.getKey());
                fila.moneda(prod.getValue());
                fila.vacio();
            }
        }
        int ultimaDatos = hoja.siguienteFila() - 1;

        HojaExcel.FilaExcel total = hoja.fila(ExcelEstilo.Rol.TOTAL);
        total.texto("Total general");
        total.vacio();
        total.vacio();
        total.formulaMoneda("SUM(" + HojaExcel.ref(2, primeraDatos) + ":" + HojaExcel.ref(2, ultimaDatos) + ")");

        hoja.terminar(false, false);
        estilos.guardar(rutaArchivo);
    }
}