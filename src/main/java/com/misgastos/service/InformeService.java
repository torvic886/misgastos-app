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
import java.util.stream.Collectors;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

@Service
public class InformeService {

    @Autowired
    private GastoService gastoService;

    // ==================== 1. INFORME GENERAL MENSUAL ====================
    
    public String generarInformeGeneralMensual(LocalDate inicio, LocalDate fin) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("                        INFORME DE GASTOS\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

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

        sb.append(String.format("%-20s", "CATEGORÍA"));
        for (YearMonth mes : datosPorMesCategoria.keySet()) {
            sb.append(String.format("%15s", mes.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase()));
        }
        sb.append(String.format("%15s", "TOTAL"));
        sb.append("\n");
        sb.append("─".repeat(95)).append("\n");

        for (String categoria : categorias) {
            sb.append(String.format("%-20s", truncar(categoria, 20)));
            BigDecimal totalCategoria = BigDecimal.ZERO;
            
            for (YearMonth mes : datosPorMesCategoria.keySet()) {
                BigDecimal monto = datosPorMesCategoria.get(mes).getOrDefault(categoria, BigDecimal.ZERO);
                sb.append(String.format("%15s", formatearMoneda(monto)));
                totalCategoria = totalCategoria.add(monto);
            }
            
            sb.append(String.format("%15s", formatearMoneda(totalCategoria)));
            sb.append("\n");
        }

        sb.append("─".repeat(95)).append("\n");
        sb.append(String.format("%-20s", "TOTAL MES"));
        
        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (YearMonth mes : datosPorMesCategoria.keySet()) {
            BigDecimal totalMes = datosPorMesCategoria.get(mes).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append(String.format("%15s", formatearMoneda(totalMes)));
            totalGeneral = totalGeneral.add(totalMes);
        }
        sb.append(String.format("%15s", formatearMoneda(totalGeneral)));
        sb.append("\n\n");

        sb.append("📊 ESTADÍSTICAS\n");
        sb.append("─".repeat(95)).append("\n");
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
        
        sb.append("═══════════════════════════════════════════════════════════════════════\n");
        sb.append("                  INFORME POR CATEGORÍA: ").append(nombreCategoria.toUpperCase()).append("\n");
        sb.append("                    ").append(formatearFecha(inicio)).append(" - ").append(formatearFecha(fin)).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════\n\n");

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

        sb.append("📊 RESUMEN GENERAL\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("   Total gastado: %s\n", formatearMoneda(totalGastado)));
        sb.append(String.format("   Número de compras: %d\n", gastos.size()));
        sb.append(String.format("   Promedio por compra: %s\n\n", formatearMoneda(promedioCompra)));

        Map<String, BigDecimal> porSubcategoria = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getSubcategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        sb.append("📂 POR SUBCATEGORÍA\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-30s %15s %15s %15s\n", "SUBCATEGORÍA", "MONTO", "COMPRAS", "% CATEGORÍA"));
        sb.append("─".repeat(80)).append("\n");

        for (Map.Entry<String, BigDecimal> entry : porSubcategoria.entrySet()) {
            long compras = gastos.stream()
                .filter(g -> g.getSubcategoria().getNombre().equals(entry.getKey()))
                .count();
            
            double porcentaje = entry.getValue()
                .divide(totalGastado, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

            sb.append(String.format("%-30s %15s %15d %14.1f%%\n", 
                truncar(entry.getKey(), 30), 
                formatearMoneda(entry.getValue()),
                compras,
                porcentaje
            ));
        }
        sb.append("\n");

        Map<String, BigDecimal> porProducto = gastos.stream()
            .collect(Collectors.groupingBy(
                Gasto::getProducto,
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        List<Map.Entry<String, BigDecimal>> top5 = porProducto.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        sb.append("🔝 TOP 5 PRODUCTOS\n");
        sb.append("─".repeat(80)).append("\n");
        int pos = 1;
        for (Map.Entry<String, BigDecimal> entry : top5) {
            long veces = gastos.stream()
                .filter(g -> g.getProducto().equals(entry.getKey()))
                .count();
            
            sb.append(String.format("   %d. %-40s (%d veces) %s\n", 
                pos++, 
                truncar(entry.getKey(), 40), 
                veces,
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
            .filter(g -> g.getProducto().equalsIgnoreCase(nombreProducto))
            .sorted(Comparator.comparing(Gasto::getFecha).reversed())
            .collect(Collectors.toList());

        if (gastos.isEmpty()) {
            sb.append("⚠️ No se encontraron compras de este producto en el período seleccionado.\n");
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
        sb.append(String.format("%-12s %10s %15s %15s %20s\n", 
            "FECHA", "CANTIDAD", "PRECIO UNIT.", "TOTAL", "NOTAS"));
        sb.append("─".repeat(80)).append("\n");

        gastos.stream().limit(10).forEach(g -> {
            sb.append(String.format("%-12s %10d %15s %15s %20s\n",
                formatearFecha(g.getFecha()),
                g.getCantidad(),
                formatearMoneda(g.getValorUnitario()),
                formatearMoneda(g.getValorTotal()),
                truncar((g.getNotas() != null && !g.getNotas().isEmpty()) ? g.getNotas() : "-", 20)
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

 //=============================================================     
    public void exportarInformeGeneralPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);

        // Colores
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb amarillo = new DeviceRgb(254, 249, 195);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);

        // Título
        Paragraph titulo = new Paragraph("INFORME GENERAL MENSUAL")
            .setFontSize(18)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(5);
        document.add(titulo);

        Paragraph subtitulo = new Paragraph(formatearFecha(inicio) + " - " + formatearFecha(fin))
            .setFontSize(11)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(subtitulo);

        if (gastos.isEmpty()) {
            document.add(new Paragraph("⚠️ No hay gastos registrados en este período."));
            document.close();
            return;
        }

        // Preparar datos
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

        // Crear tabla
        float[] columnWidths = new float[meses.size() + 2];
        columnWidths[0] = 120f; // Categoría
        for (int i = 1; i < columnWidths.length; i++) {
            columnWidths[i] = 80f; // Meses y Total
        }
        
        com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(columnWidths);
        table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

        // Headers
        agregarCeldaHeader(table, "CATEGORÍA", azulOscuro, grisBorde);
        for (YearMonth mes : meses) {
            agregarCeldaHeader(table, mes.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase(), azulOscuro, grisBorde);
        }
        agregarCeldaHeader(table, "TOTAL", azulOscuro, grisBorde);

        // Datos
        boolean alternar = false;
        for (String categoria : categorias) {
            DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
            
            agregarCeldaDato(table, categoria, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
            
            BigDecimal totalCategoria = BigDecimal.ZERO;
            for (YearMonth mes : meses) {
                BigDecimal monto = datosPorMesCategoria.get(mes).getOrDefault(categoria, BigDecimal.ZERO);
                agregarCeldaDato(table, formatearMoneda(monto), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
                totalCategoria = totalCategoria.add(monto);
            }
            
            agregarCeldaDato(table, formatearMoneda(totalCategoria), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            alternar = !alternar;
        }

        // Fila de totales
        agregarCeldaTotal(table, "TOTAL MES", amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        
        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (YearMonth mes : meses) {
            BigDecimal totalMes = datosPorMesCategoria.get(mes).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            agregarCeldaTotal(table, formatearMoneda(totalMes), amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            totalGeneral = totalGeneral.add(totalMes);
        }
        agregarCeldaTotal(table, formatearMoneda(totalGeneral), amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);

        document.add(table);

        // Estadísticas
        document.add(new Paragraph("\n"));
        Paragraph estadisticas = new Paragraph("📊 ESTADÍSTICAS")
            .setBold()
            .setFontSize(12)
            .setMarginTop(10);
        document.add(estadisticas);

        document.add(new Paragraph(String.format("💰 Total gastado: %s", formatearMoneda(totalGeneral))).setFontSize(10));
        document.add(new Paragraph(String.format("📅 Período: %d meses", datosPorMesCategoria.size())).setFontSize(10));
        
        if (!datosPorMesCategoria.isEmpty()) {
            document.add(new Paragraph(String.format("📈 Promedio mensual: %s", 
                formatearMoneda(totalGeneral.divide(BigDecimal.valueOf(datosPorMesCategoria.size()), 2, RoundingMode.HALF_UP)))).setFontSize(10));
        }
        
        document.add(new Paragraph(String.format("📂 Total de categorías: %d", categorias.size())).setFontSize(10));
        document.add(new Paragraph(String.format("🧾 Total de transacciones: %d", gastos.size())).setFontSize(10));

        // Footer
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(20)
            .setItalic();
        document.add(footer);

        document.close();
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
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);

        // Colores
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb amarillo = new DeviceRgb(254, 249, 195);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);

        // Título
        Paragraph titulo = new Paragraph("INFORME POR CATEGORÍA: " + categoria.toUpperCase())
            .setFontSize(18)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(5);
        document.add(titulo);

        Paragraph subtitulo = new Paragraph(formatearFecha(inicio) + " - " + formatearFecha(fin))
            .setFontSize(11)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(subtitulo);

        if (gastos.isEmpty()) {
            document.add(new Paragraph("⚠️ No hay gastos registrados en esta categoría para el período seleccionado."));
            document.close();
            return;
        }

        // === RESUMEN GENERAL ===
        Paragraph tituloResumen = new Paragraph("📋 RESUMEN GENERAL")
            .setBold()
            .setFontSize(12)
            .setMarginBottom(10);
        document.add(tituloResumen);

        BigDecimal totalGastado = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int numeroCompras = gastos.size();
        BigDecimal promedioCompra = numeroCompras > 0 
            ? totalGastado.divide(BigDecimal.valueOf(numeroCompras), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Tabla de resumen
        float[] resumenWidths = {300f, 200f};
        com.itextpdf.layout.element.Table tableResumen = new com.itextpdf.layout.element.Table(resumenWidths);
        tableResumen.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tableResumen.setMarginBottom(15);

        agregarCeldaDato(tableResumen, "Total gastado:", new DeviceRgb(255, 255, 255), grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        agregarCeldaDato(tableResumen, formatearMoneda(totalGastado), new DeviceRgb(255, 255, 255), grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
        
        agregarCeldaDato(tableResumen, "Número de compras:", grisClaro, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        agregarCeldaDato(tableResumen, String.valueOf(numeroCompras), grisClaro, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
        
        agregarCeldaDato(tableResumen, "Promedio por compra:", new DeviceRgb(255, 255, 255), grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        agregarCeldaDato(tableResumen, formatearMoneda(promedioCompra), new DeviceRgb(255, 255, 255), grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);

        document.add(tableResumen);

        // === POR SUBCATEGORÍA ===
        Paragraph tituloSubcat = new Paragraph("📂 POR SUBCATEGORÍA")
            .setBold()
            .setFontSize(12)
            .setMarginBottom(10);
        document.add(tituloSubcat);

        // Agrupar por subcategoría
        Map<String, BigDecimal> montoPorSubcat = new TreeMap<>();
        Map<String, Long> comprasPorSubcat = new TreeMap<>();
        
        for (Gasto gasto : gastos) {
            String subcat = gasto.getSubcategoria() != null 
                ? gasto.getSubcategoria().getNombre() 
                : "Sin subcategoría";
            
            montoPorSubcat.merge(subcat, gasto.getValorTotal(), BigDecimal::add);
            comprasPorSubcat.merge(subcat, 1L, Long::sum);
        }

        // Crear tabla de subcategorías
        float[] columnWidths = {200f, 120f, 80f, 100f};
        com.itextpdf.layout.element.Table tableSubcat = new com.itextpdf.layout.element.Table(columnWidths);
        tableSubcat.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tableSubcat.setMarginBottom(10);

        // Headers
        agregarCeldaHeader(tableSubcat, "SUBCATEGORÍA", azulOscuro, grisBorde);
        agregarCeldaHeader(tableSubcat, "MONTO", azulOscuro, grisBorde);
        agregarCeldaHeader(tableSubcat, "COMPRAS", azulOscuro, grisBorde);
        agregarCeldaHeader(tableSubcat, "% CATEGORÍA", azulOscuro, grisBorde);

        // Datos
        boolean alternar = false;
        for (Map.Entry<String, BigDecimal> entry : montoPorSubcat.entrySet()) {
            String subcat = entry.getKey();
            BigDecimal monto = entry.getValue();
            Long compras = comprasPorSubcat.get(subcat);
            double porcentaje = totalGastado.compareTo(BigDecimal.ZERO) > 0
                ? monto.divide(totalGastado, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0;

            DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
            
            agregarCeldaDato(tableSubcat, subcat, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
            agregarCeldaDato(tableSubcat, formatearMoneda(monto), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            agregarCeldaDato(tableSubcat, String.valueOf(compras), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            agregarCeldaDato(tableSubcat, String.format("%.1f%%", porcentaje), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            
            alternar = !alternar;
        }

        document.add(tableSubcat);
        document.add(new Paragraph("\n"));

        // === TOP 5 PRODUCTOS ===
        Paragraph tituloTop = new Paragraph("🏆 TOP 5 PRODUCTOS")
            .setBold()
            .setFontSize(12)
            .setMarginTop(10)
            .setMarginBottom(10);
        document.add(tituloTop);

        // Agrupar por producto
        Map<String, BigDecimal> montoPorProducto = new HashMap<>();
        Map<String, Long> vecesPorProducto = new HashMap<>();
        
        for (Gasto gasto : gastos) {
            String nombreProducto = gasto.getProducto() != null && !gasto.getProducto().trim().isEmpty()
                ? gasto.getProducto()
                : "Producto no especificado";
            
            montoPorProducto.merge(nombreProducto, gasto.getValorTotal(), BigDecimal::add);
            vecesPorProducto.merge(nombreProducto, 1L, Long::sum);
        }

        // Ordenar por monto descendente y tomar top 5
        List<Map.Entry<String, BigDecimal>> topProductos = montoPorProducto.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        // Crear tabla de productos
        float[] productosWidths = {50f, 300f, 150f};
        com.itextpdf.layout.element.Table tableProductos = new com.itextpdf.layout.element.Table(productosWidths);
        tableProductos.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tableProductos.setMarginBottom(10);

        int posicion = 1;
        boolean alternar2 = false;
        for (Map.Entry<String, BigDecimal> entry : topProductos) {
            String nombreProducto = entry.getKey();
            BigDecimal monto = entry.getValue();
            Long veces = vecesPorProducto.get(nombreProducto);
            
            String textoVeces = veces == 1 ? "(1 vez)" : String.format("(%d veces)", veces);
            
            DeviceRgb colorFondo = alternar2 ? grisClaro : new DeviceRgb(255, 255, 255);
            
            agregarCeldaDato(tableProductos, String.valueOf(posicion) + ".", colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            agregarCeldaDato(tableProductos, nombreProducto + " " + textoVeces, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
            agregarCeldaDato(tableProductos, formatearMoneda(monto), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            
            alternar2 = !alternar2;
            posicion++;
        }

        document.add(tableProductos);

        // Footer
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(20)
            .setItalic();
        document.add(footer);

        document.close();
    }

 //=============================================================     
    
    public void exportarInformeProductoPDF(String producto, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin).stream()
            .filter(g -> g.getProducto().equals(producto))
            .sorted(Comparator.comparing(Gasto::getFecha).reversed())
            .collect(Collectors.toList());
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);
        
        // Márgenes del documento
        document.setMargins(40, 40, 40, 40);

        // Colores mejorados
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb azulMedio = new DeviceRgb(59, 130, 246);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb grisMedio = new DeviceRgb(229, 231, 235);
        DeviceRgb amarillo = new DeviceRgb(254, 249, 195);
        DeviceRgb verde = new DeviceRgb(220, 252, 231);
        DeviceRgb azulClaro = new DeviceRgb(219, 234, 254);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);

        // === ENCABEZADO CON BANNER ===
        com.itextpdf.layout.element.Table bannerTable = new com.itextpdf.layout.element.Table(1);
        bannerTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        
        com.itextpdf.layout.element.Cell bannerCell = new com.itextpdf.layout.element.Cell();
        bannerCell.setBackgroundColor(azulOscuro);
        bannerCell.setPadding(20);
        bannerCell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        
        Paragraph titulo = new Paragraph("ANÁLISIS DE PRODUCTO")
            .setFontSize(22)
            .setBold()
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(5);
        
        Paragraph productoNombre = new Paragraph(producto.toUpperCase())
            .setFontSize(16)
            .setFontColor(amarillo)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(8);
        
        Paragraph periodo = new Paragraph(formatearFecha(inicio) + " - " + formatearFecha(fin))
            .setFontSize(11)
            .setFontColor(new DeviceRgb(226, 232, 240))
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
        
        bannerCell.add(titulo);
        bannerCell.add(productoNombre);
        bannerCell.add(periodo);
        bannerTable.addCell(bannerCell);
        document.add(bannerTable);
        document.add(new Paragraph("\n").setMarginTop(15));

        if (gastos.isEmpty()) {
            document.add(new Paragraph("⚠️ No hay compras registradas de este producto en el período seleccionado.")
                .setFontSize(12)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginTop(20));
            document.close();
            return;
        }

        // Calcular datos
        BigDecimal totalGastado = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int cantidadTotal = gastos.stream()
            .mapToInt(Gasto::getCantidad)
            .sum();
        
        BigDecimal precioPromedio = cantidadTotal > 0 
            ? totalGastado.divide(BigDecimal.valueOf(cantidadTotal), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        int numeroCompras = gastos.size();

        String categoriaNombre = gastos.get(0).getCategoria().getNombre();
        String subcategoriaNombre = gastos.get(0).getSubcategoria() != null 
            ? gastos.get(0).getSubcategoria().getNombre() 
            : "Sin subcategoría";

        BigDecimal precioMinimo = gastos.stream()
            .map(Gasto::getValorUnitario)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal precioMaximo = gastos.stream()
            .map(Gasto::getValorUnitario)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        BigDecimal precioActual = gastos.get(0).getValorUnitario();

        // === TARJETAS DE RESUMEN (3 columnas) ===
        float[] tarjetasWidths = {1, 1, 1};
        com.itextpdf.layout.element.Table tarjetasTable = new com.itextpdf.layout.element.Table(tarjetasWidths);
        tarjetasTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tarjetasTable.setMarginBottom(20);

        // Tarjeta 1: Total Gastado
        com.itextpdf.layout.element.Cell tarjeta1 = new com.itextpdf.layout.element.Cell();
        tarjeta1.setBackgroundColor(azulClaro);
        tarjeta1.setPadding(15);
        tarjeta1.setBorder(new com.itextpdf.layout.borders.SolidBorder(azulMedio, 2));
        tarjeta1.add(new Paragraph("💰 Total Gastado").setBold().setFontSize(10).setFontColor(azulOscuro).setMarginBottom(5));
        tarjeta1.add(new Paragraph(formatearMoneda(totalGastado)).setFontSize(16).setBold().setFontColor(azulOscuro));
        tarjetasTable.addCell(tarjeta1);

        // Tarjeta 2: Cantidad
        com.itextpdf.layout.element.Cell tarjeta2 = new com.itextpdf.layout.element.Cell();
        tarjeta2.setBackgroundColor(verde);
        tarjeta2.setPadding(15);
        tarjeta2.setBorder(new com.itextpdf.layout.borders.SolidBorder(new DeviceRgb(34, 197, 94), 2));
        tarjeta2.add(new Paragraph("📦 Unidades").setBold().setFontSize(10).setFontColor(new DeviceRgb(22, 101, 52)).setMarginBottom(5));
        tarjeta2.add(new Paragraph(String.valueOf(cantidadTotal)).setFontSize(16).setBold().setFontColor(new DeviceRgb(22, 101, 52)));
        tarjetasTable.addCell(tarjeta2);

        // Tarjeta 3: Compras
        com.itextpdf.layout.element.Cell tarjeta3 = new com.itextpdf.layout.element.Cell();
        tarjeta3.setBackgroundColor(amarillo);
        tarjeta3.setPadding(15);
        tarjeta3.setBorder(new com.itextpdf.layout.borders.SolidBorder(new DeviceRgb(234, 179, 8), 2));
        tarjeta3.add(new Paragraph("🛒 Compras").setBold().setFontSize(10).setFontColor(new DeviceRgb(133, 77, 14)).setMarginBottom(5));
        tarjeta3.add(new Paragraph(String.valueOf(numeroCompras)).setFontSize(16).setBold().setFontColor(new DeviceRgb(133, 77, 14)));
        tarjetasTable.addCell(tarjeta3);

        document.add(tarjetasTable);

        // === SECCIÓN: INFORMACIÓN GENERAL ===
        Paragraph seccionInfo = new Paragraph("📋 INFORMACIÓN GENERAL")
            .setFontSize(14)
            .setBold()
            .setFontColor(azulOscuro)
            .setMarginTop(5)
            .setMarginBottom(12);
        document.add(seccionInfo);

        float[] infoWidths = {200f, 300f};
        com.itextpdf.layout.element.Table tableInfo = new com.itextpdf.layout.element.Table(infoWidths);
        tableInfo.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tableInfo.setMarginBottom(20);

        // Fila 1: Categoría
        com.itextpdf.layout.element.Cell cellEtiqueta1 = new com.itextpdf.layout.element.Cell();
        cellEtiqueta1.add(new Paragraph("Categoría").setFontSize(10).setBold().setFontColor(azulOscuro));
        cellEtiqueta1.setBackgroundColor(grisMedio);
        cellEtiqueta1.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiqueta1.setPadding(10);
        tableInfo.addCell(cellEtiqueta1);
        
        com.itextpdf.layout.element.Cell cellValor1 = new com.itextpdf.layout.element.Cell();
        cellValor1.add(new Paragraph(categoriaNombre + " > " + subcategoriaNombre).setFontSize(10));
        cellValor1.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellValor1.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellValor1.setPadding(10);
        tableInfo.addCell(cellValor1);
        
        // Fila 2: Precio promedio
        com.itextpdf.layout.element.Cell cellEtiqueta2 = new com.itextpdf.layout.element.Cell();
        cellEtiqueta2.add(new Paragraph("Precio promedio por unidad").setFontSize(10).setBold().setFontColor(azulOscuro));
        cellEtiqueta2.setBackgroundColor(grisMedio);
        cellEtiqueta2.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiqueta2.setPadding(10);
        tableInfo.addCell(cellEtiqueta2);
        
        com.itextpdf.layout.element.Cell cellValor2 = new com.itextpdf.layout.element.Cell();
        cellValor2.add(new Paragraph(formatearMoneda(precioPromedio)).setFontSize(10));
        cellValor2.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellValor2.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellValor2.setPadding(10);
        tableInfo.addCell(cellValor2);

        document.add(tableInfo);

        // === SECCIÓN: ESTADÍSTICAS DE PRECIO ===
        Paragraph seccionPrecio = new Paragraph("💵 EVOLUCIÓN DE PRECIOS")
            .setFontSize(14)
            .setBold()
            .setFontColor(azulOscuro)
            .setMarginBottom(12);
        document.add(seccionPrecio);

        float[] precioWidths = {1, 1, 1};
        com.itextpdf.layout.element.Table tablePrecio = new com.itextpdf.layout.element.Table(precioWidths);
        tablePrecio.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tablePrecio.setMarginBottom(20);

        // Columna 1: Precio Mínimo
        com.itextpdf.layout.element.Cell cellMin = new com.itextpdf.layout.element.Cell();
        cellMin.setBackgroundColor(grisClaro);
        cellMin.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellMin.setPadding(12);
        cellMin.add(new Paragraph("Precio Mínimo").setFontSize(9).setFontColor(new DeviceRgb(107, 114, 128)).setMarginBottom(5));
        cellMin.add(new Paragraph(formatearMoneda(precioMinimo)).setFontSize(13).setBold().setFontColor(azulOscuro));
        tablePrecio.addCell(cellMin);
        
        // Columna 2: Precio Máximo
        com.itextpdf.layout.element.Cell cellMax = new com.itextpdf.layout.element.Cell();
        cellMax.setBackgroundColor(grisClaro);
        cellMax.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellMax.setPadding(12);
        cellMax.add(new Paragraph("Precio Máximo").setFontSize(9).setFontColor(new DeviceRgb(107, 114, 128)).setMarginBottom(5));
        cellMax.add(new Paragraph(formatearMoneda(precioMaximo)).setFontSize(13).setBold().setFontColor(azulOscuro));
        tablePrecio.addCell(cellMax);
        
        // Columna 3: Precio Actual
        com.itextpdf.layout.element.Cell cellActual = new com.itextpdf.layout.element.Cell();
        cellActual.setBackgroundColor(grisClaro);
        cellActual.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellActual.setPadding(12);
        cellActual.add(new Paragraph("Precio Actual").setFontSize(9).setFontColor(new DeviceRgb(107, 114, 128)).setMarginBottom(5));
        cellActual.add(new Paragraph(formatearMoneda(precioActual)).setFontSize(13).setBold().setFontColor(azulOscuro));
        tablePrecio.addCell(cellActual);

        document.add(tablePrecio);

        // === SECCIÓN: HISTORIAL ===
        Paragraph seccionHistorial = new Paragraph("📊 HISTORIAL DE COMPRAS (Últimas 10)")
            .setFontSize(14)
            .setBold()
            .setFontColor(azulOscuro)
            .setMarginBottom(12);
        document.add(seccionHistorial);

        float[] historialWidths = {90f, 70f, 110f, 110f, 150f};
        com.itextpdf.layout.element.Table tableHistorial = new com.itextpdf.layout.element.Table(historialWidths);
        tableHistorial.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

        // Headers mejorados
        String[] headers = {"FECHA", "CANT.", "PRECIO UNIT.", "TOTAL", "NOTAS"};
        for (String header : headers) {
            com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell();
            cell.add(new Paragraph(header).setBold().setFontSize(9).setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
            cell.setBackgroundColor(azulOscuro);
            cell.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
            cell.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
            cell.setPadding(8);
            tableHistorial.addCell(cell);
        }

        // Datos con colores alternados
        int limite = Math.min(gastos.size(), 10);
        for (int i = 0; i < limite; i++) {
            Gasto gasto = gastos.get(i);
            DeviceRgb colorFondo = (i % 2 == 0) ? new DeviceRgb(255, 255, 255) : grisClaro;
            
            agregarCeldaDato(tableHistorial, formatearFecha(gasto.getFecha()), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            agregarCeldaDato(tableHistorial, String.valueOf(gasto.getCantidad()), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            agregarCeldaDato(tableHistorial, formatearMoneda(gasto.getValorUnitario()), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            agregarCeldaDato(tableHistorial, formatearMoneda(gasto.getValorTotal()), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            
            String notas = gasto.getNotas() != null && !gasto.getNotas().trim().isEmpty() 
                ? gasto.getNotas() 
                : "-";
            agregarCeldaDato(tableHistorial, notas, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        }

        document.add(tableHistorial);

        // === FOOTER MEJORADO ===
        com.itextpdf.layout.element.Table footerTable = new com.itextpdf.layout.element.Table(1);
        footerTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        footerTable.setMarginTop(25);
        
        com.itextpdf.layout.element.Cell footerCell = new com.itextpdf.layout.element.Cell();
        footerCell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        footerCell.setBorderTop(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        footerCell.setPaddingTop(10);
        footerCell.add(new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(9)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setFontColor(new DeviceRgb(107, 114, 128))
            .setItalic());
        footerTable.addCell(footerCell);
        
        document.add(footerTable);

        document.close();
    }

 //=============================================================   
    
    
    public void exportarDashboardPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);

        // Colores
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb amarillo = new DeviceRgb(254, 249, 195);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);
        DeviceRgb grisMedio = new DeviceRgb(229, 231, 235);

        // Título
        Paragraph titulo = new Paragraph("DASHBOARD EJECUTIVO")
            .setFontSize(18)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(5);
        document.add(titulo);

        Paragraph subtitulo = new Paragraph(formatearFecha(inicio) + " - " + formatearFecha(fin))
            .setFontSize(11)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(subtitulo);

        if (gastos.isEmpty()) {
            document.add(new Paragraph("⚠️ No hay gastos registrados en este período."));
            document.close();
            return;
        }

        // Calcular datos financieros
        BigDecimal totalGastado = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        BigDecimal promedioDiario = totalGastado.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);

        BigDecimal mayorGasto = gastos.stream()
            .map(Gasto::getValorTotal)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal promedioTransaccion = totalGastado.divide(BigDecimal.valueOf(gastos.size()), 2, RoundingMode.HALF_UP);

        // === RESUMEN FINANCIERO ===
        Paragraph tituloResumen = new Paragraph("💰 RESUMEN FINANCIERO")
            .setBold()
            .setFontSize(12)
            .setMarginBottom(10);
        document.add(tituloResumen);

        float[] resumenWidths = {280f, 220f};
        com.itextpdf.layout.element.Table tableResumen = new com.itextpdf.layout.element.Table(resumenWidths);
        tableResumen.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tableResumen.setMarginBottom(20);

        // Fila 1
        com.itextpdf.layout.element.Cell cellEtiq1 = new com.itextpdf.layout.element.Cell();
        cellEtiq1.add(new Paragraph("Total gastado en el período:").setFontSize(10));
        cellEtiq1.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellEtiq1.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiq1.setPadding(8);
        tableResumen.addCell(cellEtiq1);
        
        com.itextpdf.layout.element.Cell cellVal1 = new com.itextpdf.layout.element.Cell();
        cellVal1.add(new Paragraph(formatearMoneda(totalGastado)).setFontSize(10));
        cellVal1.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellVal1.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellVal1.setPadding(8);
        cellVal1.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
        tableResumen.addCell(cellVal1);

        // Fila 2
        com.itextpdf.layout.element.Cell cellEtiq2 = new com.itextpdf.layout.element.Cell();
        cellEtiq2.add(new Paragraph("Promedio diario:").setFontSize(10));
        cellEtiq2.setBackgroundColor(grisClaro);
        cellEtiq2.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiq2.setPadding(8);
        tableResumen.addCell(cellEtiq2);
        
        com.itextpdf.layout.element.Cell cellVal2 = new com.itextpdf.layout.element.Cell();
        cellVal2.add(new Paragraph(formatearMoneda(promedioDiario)).setFontSize(10));
        cellVal2.setBackgroundColor(grisClaro);
        cellVal2.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellVal2.setPadding(8);
        cellVal2.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
        tableResumen.addCell(cellVal2);

        // Fila 3
        com.itextpdf.layout.element.Cell cellEtiq3 = new com.itextpdf.layout.element.Cell();
        cellEtiq3.add(new Paragraph("Mayor gasto individual:").setFontSize(10));
        cellEtiq3.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellEtiq3.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiq3.setPadding(8);
        tableResumen.addCell(cellEtiq3);
        
        com.itextpdf.layout.element.Cell cellVal3 = new com.itextpdf.layout.element.Cell();
        cellVal3.add(new Paragraph(formatearMoneda(mayorGasto)).setFontSize(10));
        cellVal3.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellVal3.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellVal3.setPadding(8);
        cellVal3.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
        tableResumen.addCell(cellVal3);

        // Fila 4
        com.itextpdf.layout.element.Cell cellEtiq4 = new com.itextpdf.layout.element.Cell();
        cellEtiq4.add(new Paragraph("Total de transacciones:").setFontSize(10));
        cellEtiq4.setBackgroundColor(grisClaro);
        cellEtiq4.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiq4.setPadding(8);
        tableResumen.addCell(cellEtiq4);
        
        com.itextpdf.layout.element.Cell cellVal4 = new com.itextpdf.layout.element.Cell();
        cellVal4.add(new Paragraph(String.valueOf(gastos.size())).setFontSize(10));
        cellVal4.setBackgroundColor(grisClaro);
        cellVal4.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellVal4.setPadding(8);
        cellVal4.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
        tableResumen.addCell(cellVal4);

        // Fila 5
        com.itextpdf.layout.element.Cell cellEtiq5 = new com.itextpdf.layout.element.Cell();
        cellEtiq5.add(new Paragraph("Promedio por transacción:").setFontSize(10));
        cellEtiq5.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellEtiq5.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellEtiq5.setPadding(8);
        tableResumen.addCell(cellEtiq5);
        
        com.itextpdf.layout.element.Cell cellVal5 = new com.itextpdf.layout.element.Cell();
        cellVal5.add(new Paragraph(formatearMoneda(promedioTransaccion)).setFontSize(10));
        cellVal5.setBackgroundColor(new DeviceRgb(255, 255, 255));
        cellVal5.setBorder(new com.itextpdf.layout.borders.SolidBorder(grisBorde, 1));
        cellVal5.setPadding(8);
        cellVal5.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
        tableResumen.addCell(cellVal5);

        document.add(tableResumen);

        // === TOP 3 CATEGORÍAS ===
        Paragraph tituloTop3 = new Paragraph("📊 TOP 3 CATEGORÍAS")
            .setBold()
            .setFontSize(12)
            .setMarginTop(10)
            .setMarginBottom(10);
        document.add(tituloTop3);

        Map<String, BigDecimal> porCategoria = gastos.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        List<Map.Entry<String, BigDecimal>> top3Cat = porCategoria.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());

        float[] top3Widths = {50f, 200f, 150f, 100f};
        com.itextpdf.layout.element.Table tableTop3 = new com.itextpdf.layout.element.Table(top3Widths);
        tableTop3.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        tableTop3.setMarginBottom(20);

        int pos = 1;
        boolean alternar = false;
        for (Map.Entry<String, BigDecimal> entry : top3Cat) {
            double porcentaje = entry.getValue()
                .divide(totalGastado, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
            
            String emoji = pos == 1 ? "🥇" : pos == 2 ? "🥈" : "🥉";
            
            DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
            
            agregarCeldaDato(tableTop3, emoji, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            agregarCeldaDato(tableTop3, entry.getKey(), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
            agregarCeldaDato(tableTop3, formatearMoneda(entry.getValue()), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            agregarCeldaDato(tableTop3, String.format("(%.1f%%)", porcentaje), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            
            pos++;
            alternar = !alternar;
        }

        document.add(tableTop3);

        // === PRODUCTOS MÁS COMPRADOS ===
        Paragraph tituloProductos = new Paragraph("🔥 PRODUCTOS MÁS COMPRADOS")
            .setBold()
            .setFontSize(12)
            .setMarginTop(10)
            .setMarginBottom(10);
        document.add(tituloProductos);

        Map<String, Long> frecuenciaProductos = gastos.stream()
            .collect(Collectors.groupingBy(Gasto::getProducto, Collectors.counting()));

        List<Map.Entry<String, Long>> topFrecuentes = frecuenciaProductos.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        float[] productosWidths = {40f, 360f, 100f};
        com.itextpdf.layout.element.Table tableProductos = new com.itextpdf.layout.element.Table(productosWidths);
        tableProductos.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

        pos = 1;
        alternar = false;
        for (Map.Entry<String, Long> entry : topFrecuentes) {
            DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
            
            agregarCeldaDato(tableProductos, String.valueOf(pos) + ".", colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            agregarCeldaDato(tableProductos, entry.getKey(), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
            
            String veces = entry.getValue() == 1 ? "(1 vez)" : String.format("(%d veces)", entry.getValue());
            agregarCeldaDato(tableProductos, veces, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            
            pos++;
            alternar = !alternar;
        }

        document.add(tableProductos);

        // Footer
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(20)
            .setItalic();
        document.add(footer);

        document.close();
    }
//=============================================================   
    
    public void exportarComparativoPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        // Calcular el punto medio para dividir en dos períodos
        long diasTotal = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
        LocalDate puntoMedio = inicio.plusDays(diasTotal / 2);
        
        LocalDate inicioPeriodo1 = inicio;
        LocalDate finPeriodo1 = puntoMedio;
        LocalDate inicioPeriodo2 = puntoMedio.plusDays(1);
        LocalDate finPeriodo2 = fin;
        
        // Obtener gastos de ambos períodos
        List<Gasto> gastosPeriodo1 = gastoService.listarPorPeriodo(inicioPeriodo1, finPeriodo1);
        List<Gasto> gastosPeriodo2 = gastoService.listarPorPeriodo(inicioPeriodo2, finPeriodo2);
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);

        // Colores
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb amarillo = new DeviceRgb(254, 249, 195);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);

        // Título
        Paragraph titulo = new Paragraph("COMPARACIÓN DE PERÍODOS")
            .setFontSize(18)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(20);
        document.add(titulo);

        // Información de períodos
        Paragraph periodo1Text = new Paragraph("Período 1: " + formatearFecha(inicioPeriodo1) + " - " + formatearFecha(finPeriodo1))
            .setFontSize(11)
            .setMarginBottom(5);
        document.add(periodo1Text);

        Paragraph periodo2Text = new Paragraph("Período 2: " + formatearFecha(inicioPeriodo2) + " - " + formatearFecha(finPeriodo2))
            .setFontSize(11)
            .setMarginBottom(20);
        document.add(periodo2Text);

        // === AGRUPAR DATOS POR CATEGORÍA ===
        Map<String, BigDecimal> montoPorCategoriaPeriodo1 = gastosPeriodo1.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));
        
        Map<String, BigDecimal> montoPorCategoriaPeriodo2 = gastosPeriodo2.stream()
            .collect(Collectors.groupingBy(
                g -> g.getCategoria().getNombre(),
                Collectors.reducing(BigDecimal.ZERO, Gasto::getValorTotal, BigDecimal::add)
            ));

        // Obtener todas las categorías
        Set<String> todasCategorias = new TreeSet<>();
        todasCategorias.addAll(montoPorCategoriaPeriodo1.keySet());
        todasCategorias.addAll(montoPorCategoriaPeriodo2.keySet());

        if (todasCategorias.isEmpty()) {
            document.add(new Paragraph("⚠️ No hay gastos registrados en el período seleccionado."));
            document.close();
            return;
        }

        // === TABLA DE COMPARACIÓN ===
        float[] columnWidths = {150f, 110f, 110f, 110f, 90f};
        com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(columnWidths);
        table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

        // Headers
        agregarCeldaHeader(table, "CATEGORÍA", azulOscuro, grisBorde);
        agregarCeldaHeader(table, "PERÍODO 1", azulOscuro, grisBorde);
        agregarCeldaHeader(table, "PERÍODO 2", azulOscuro, grisBorde);
        agregarCeldaHeader(table, "DIFERENCIA", azulOscuro, grisBorde);
        agregarCeldaHeader(table, "CAMBIO", azulOscuro, grisBorde);

        // Datos por categoría
        BigDecimal totalPeriodo1 = BigDecimal.ZERO;
        BigDecimal totalPeriodo2 = BigDecimal.ZERO;
        boolean alternar = false;

        for (String categoria : todasCategorias) {
            BigDecimal montoPer1 = montoPorCategoriaPeriodo1.getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal montoPer2 = montoPorCategoriaPeriodo2.getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal diferencia = montoPer2.subtract(montoPer1);
            
            totalPeriodo1 = totalPeriodo1.add(montoPer1);
            totalPeriodo2 = totalPeriodo2.add(montoPer2);

            DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
            
            agregarCeldaDato(table, categoria, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
            agregarCeldaDato(table, formatearMoneda(montoPer1), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            agregarCeldaDato(table, formatearMoneda(montoPer2), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            agregarCeldaDato(table, formatearMoneda(diferencia), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
            
            // Celda de cambio porcentual
            String cambio = "";
            if (montoPer1.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal porcentaje = diferencia.divide(montoPer1, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                cambio = String.format("%+.1f%%", porcentaje.doubleValue());
            } else if (montoPer2.compareTo(BigDecimal.ZERO) > 0) {
                cambio = "+∞%";
            } else {
                cambio = "0,0%";
            }
            
            agregarCeldaDato(table, cambio, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
            
            alternar = !alternar;
        }

        // Fila de totales
        BigDecimal diferenciaTotal = totalPeriodo2.subtract(totalPeriodo1);
        
        agregarCeldaTotal(table, "TOTAL", amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        agregarCeldaTotal(table, formatearMoneda(totalPeriodo1), amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
        agregarCeldaTotal(table, formatearMoneda(totalPeriodo2), amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
        agregarCeldaTotal(table, formatearMoneda(diferenciaTotal), amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
        
        String cambioTotal = "";
        if (totalPeriodo1.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal porcentajeTotal = diferenciaTotal.divide(totalPeriodo1, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            cambioTotal = String.format("%+.1f%%", porcentajeTotal.doubleValue());
        } else if (totalPeriodo2.compareTo(BigDecimal.ZERO) > 0) {
            cambioTotal = "+∞%";
        } else {
            cambioTotal = "0,0%";
        }
        agregarCeldaTotal(table, cambioTotal, amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);

        document.add(table);

        // Footer
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(20)
            .setItalic();
        document.add(footer);

        document.close();
    }
    
    //=============================================================      
    
    public void exportarSubCategoriasPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);

        // Colores
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb amarillo = new DeviceRgb(254, 249, 195);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);
        DeviceRgb grisMedio = new DeviceRgb(229, 231, 235);

        // Título
        Paragraph titulo = new Paragraph("DESGLOSE POR SUBCATEGORÍAS")
            .setFontSize(18)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(5);
        document.add(titulo);

        Paragraph subtitulo = new Paragraph(formatearFecha(inicio) + " - " + formatearFecha(fin))
            .setFontSize(11)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(subtitulo);

        if (gastos.isEmpty()) {
            document.add(new Paragraph("⚠️ No hay gastos registrados en este período."));
            document.close();
            return;
        }

        // Calcular total general
        BigDecimal totalGeneral = gastos.stream()
            .map(Gasto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Agrupar por categoría y subcategoría
        Map<String, Map<String, BigDecimal>> datosPorCategoriaYSubcategoria = new TreeMap<>();
        
        for (Gasto gasto : gastos) {
            String categoria = gasto.getCategoria().getNombre();
            String subcategoria = gasto.getSubcategoria() != null 
                ? gasto.getSubcategoria().getNombre() 
                : "Sin subcategoría";
            
            datosPorCategoriaYSubcategoria
                .computeIfAbsent(categoria, k -> new TreeMap<>())
                .merge(subcategoria, gasto.getValorTotal(), BigDecimal::add);
        }

        // Generar contenido por cada categoría
        for (Map.Entry<String, Map<String, BigDecimal>> categoriaEntry : datosPorCategoriaYSubcategoria.entrySet()) {
            String categoria = categoriaEntry.getKey();
            Map<String, BigDecimal> subcategorias = categoriaEntry.getValue();
            
            // Calcular total de la categoría
            BigDecimal totalCategoria = subcategorias.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            double porcentajeCategoria = totalGeneral.compareTo(BigDecimal.ZERO) > 0
                ? totalCategoria.divide(totalGeneral, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0;

            // === ENCABEZADO DE CATEGORÍA ===
            Paragraph tituloCategoria = new Paragraph(
                String.format("%s [%s - %.1f%%]", 
                    categoria.toUpperCase(), 
                    formatearMoneda(totalCategoria),
                    porcentajeCategoria)
            )
                .setBold()
                .setFontSize(12)
                .setFontColor(azulOscuro)
                .setMarginTop(10)
                .setMarginBottom(10);
            document.add(tituloCategoria);

            // === TABLA DE SUBCATEGORÍAS ===
            float[] columnWidths = {280f, 150f, 70f};
            com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(columnWidths);
            table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
            table.setMarginBottom(15);

            boolean alternar = false;
            for (Map.Entry<String, BigDecimal> subcatEntry : subcategorias.entrySet()) {
                String subcategoria = subcatEntry.getKey();
                BigDecimal montoSubcat = subcatEntry.getValue();
                
                double porcentajeSubcat = totalCategoria.compareTo(BigDecimal.ZERO) > 0
                    ? montoSubcat.divide(totalCategoria, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0;

                DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
                
                // Columna 1: Subcategoría con indentación
                agregarCeldaDato(table, "  " + subcategoria, colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
                
                // Columna 2: Monto
                agregarCeldaDato(table, formatearMoneda(montoSubcat), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
                
                // Columna 3: Porcentaje
                agregarCeldaDato(table, String.format("%.1f%%", porcentajeSubcat), colorFondo, grisBorde, com.itextpdf.layout.properties.TextAlignment.CENTER);
                
                alternar = !alternar;
            }

            document.add(table);
        }

        // === TOTAL GENERAL ===
        document.add(new Paragraph("\n"));
        
        float[] totalWidths = {400f, 100f};
        com.itextpdf.layout.element.Table tableTotal = new com.itextpdf.layout.element.Table(totalWidths);
        tableTotal.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
        
        agregarCeldaTotal(tableTotal, "TOTAL GENERAL:", amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.LEFT);
        agregarCeldaTotal(tableTotal, formatearMoneda(totalGeneral), amarillo, grisBorde, com.itextpdf.layout.properties.TextAlignment.RIGHT);
        
        document.add(tableTotal);

        // Footer
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(20)
            .setItalic();
        document.add(footer);

        document.close();
    }
    
  //=============================================================    
    public void exportarAnualPDF(int anio, String rutaArchivo) throws Exception {
        // Obtener gastos de todo el año
        LocalDate inicio = LocalDate.of(anio, 1, 1);
        LocalDate fin = LocalDate.of(anio, 12, 31);
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        pdf.setDefaultPageSize(PageSize.A4);
        Document document = new Document(pdf);
        document.setMargins(40, 40, 40, 40);
        
     // Fuente Unicode para bloques █ ▓ ▒ ░ □
        PdfFont fontUnicode = PdfFontFactory.createFont(
        	    "src/main/resources/fonts/DejaVuSans.ttf",
        	    PdfEncodings.IDENTITY_H
        	);

        // Colores
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb azulClaro = new DeviceRgb(59, 130, 246);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        DeviceRgb blanco = new DeviceRgb(255, 255, 255);
        DeviceRgb grisBorde = new DeviceRgb(203, 213, 224);
        DeviceRgb verdeClaro = new DeviceRgb(220, 252, 231);

        // Título
        Paragraph titulo = new Paragraph("INFORME ANUAL " + anio)
            .setFontSize(17)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(5);
        document.add(titulo);
        
        // Línea decorativa
        LineSeparator ls = new LineSeparator(new SolidLine(2f));
        ls.setMarginTop(0);
        ls.setMarginBottom(20);
        document.add(ls);

        // === GASTOS POR MES ===
        Paragraph tituloGastos = new Paragraph("📊 GASTOS POR MES")
            .setBold()
            .setFontSize(12)
            .setFontColor(azulOscuro)
            .setMarginBottom(10);
        document.add(tituloGastos);

        // Agrupar por mes
        Map<Integer, BigDecimal> gastosPorMes = new TreeMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            gastosPorMes.put(mes, BigDecimal.ZERO);
        }
        
        for (Gasto gasto : gastos) {
            int mes = gasto.getFecha().getMonthValue();
            gastosPorMes.merge(mes, gasto.getValorTotal(), BigDecimal::add);
        }

        // Calcular el máximo para las barras proporcionales
        BigDecimal maxGasto = gastosPorMes.values().stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);

        // Crear tabla
        float[] columnWidths = {80f, 120f, 300f};
        Table table = new Table(columnWidths);
        table.setWidth(UnitValue.createPercentValue(100));

        // Headers
        agregarCeldaHeaderMejorada(table, "MES", azulOscuro, blanco);
        agregarCeldaHeaderMejorada(table, "GASTO", azulOscuro, blanco);
        agregarCeldaHeaderMejorada(table, "VISUALIZACIÓN", azulOscuro, blanco);

        // Nombres de los meses
        String[] nombresMeses = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        };

        boolean alternar = false;
        BigDecimal totalAnual = BigDecimal.ZERO;
        
        for (int mes = 1; mes <= 12; mes++) {
            BigDecimal montoMes = gastosPorMes.get(mes);
            totalAnual = totalAnual.add(montoMes);
            
            DeviceRgb colorFondo = alternar ? grisClaro : blanco;
            
            // Columna 1: Mes
            Cell cellMes = new Cell();
            cellMes.add(new Paragraph(nombresMeses[mes - 1])
                .setFontSize(10)
                .setBold());
            cellMes.setBackgroundColor(colorFondo);
            cellMes.setBorder(new SolidBorder(grisBorde, 0.5f));
            cellMes.setPadding(5);
            cellMes.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
            table.addCell(cellMes);
            
            // Columna 2: Monto
            Cell cellMonto = new Cell();
            Paragraph pMonto = new Paragraph(formatearMoneda(montoMes)).setFontSize(10);
            if (montoMes.compareTo(BigDecimal.ZERO) > 0) {
                pMonto.setBold();
            }
            cellMonto.add(pMonto);
            cellMonto.setBackgroundColor(colorFondo);
            cellMonto.setBorder(new SolidBorder(grisBorde, 0.5f));
            cellMonto.setPadding(5);
            cellMonto.setTextAlignment(TextAlignment.RIGHT);
            cellMonto.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
            table.addCell(cellMonto);
            
            // Columna 3: Barra gráfica MEJORADA
            Cell cellBarra = new Cell();
            cellBarra.setBackgroundColor(colorFondo);
            cellBarra.setBorder(new SolidBorder(grisBorde, 0.5f));
            cellBarra.setPadding(5);
            cellBarra.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
            
            if (montoMes.compareTo(BigDecimal.ZERO) > 0) {
                double proporcion = maxGasto.compareTo(BigDecimal.ZERO) > 0 
                    ? montoMes.divide(maxGasto, 4, RoundingMode.HALF_UP).doubleValue()
                    : 0;
                
                // Crear barra visual usando caracteres de bloque
                int longitudTotal = 30;
                int bloquesSolidos = Math.max(1, (int) (longitudTotal * proporcion));
                
                // Construir la barra completa
                String barraSolida = "█".repeat(bloquesSolidos);
                String barraVacia = "░".repeat(longitudTotal - bloquesSolidos);
                String porcentaje = String.format(" %.1f%%", proporcion * 100);
                
                Paragraph pBarra = new Paragraph(barraSolida + barraVacia + porcentaje)
                	    .setFont(fontUnicode)
                	    .setFontSize(10)
                	    .setFontColor(azulClaro);
                cellBarra.add(pBarra);
            } else {
                String barraVacia = "(No aplica)";//.repeat(30);
                Paragraph pVacio = new Paragraph(barraVacia + " 0.0%")
                	    .setFont(fontUnicode)
                	    .setFontSize(10)
                	    .setFontColor(new DeviceRgb(156, 163, 175));
                cellBarra.add(pVacio);
            }
            
            table.addCell(cellBarra);
            alternar = !alternar;
        }

        document.add(table);

        // === RESUMEN ANUAL ===
        document.add(new Paragraph("\n"));
        
        Paragraph tituloResumen = new Paragraph("💡 RESUMEN ANUAL")
            .setBold()
            .setFontSize(12)
            .setFontColor(azulOscuro)
            .setMarginTop(15)
            .setMarginBottom(10);
        document.add(tituloResumen);

        int mesesConGastos = (int) gastosPorMes.values().stream()
            .filter(monto -> monto.compareTo(BigDecimal.ZERO) > 0)
            .count();
        
        BigDecimal promedioMensual = mesesConGastos > 0
            ? totalAnual.divide(BigDecimal.valueOf(mesesConGastos), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Tabla de resumen
        float[] resumenWidths = {300f, 200f};
        Table tableResumen = new Table(resumenWidths);
        tableResumen.setWidth(UnitValue.createPercentValue(100));

        // Fila 1
        Cell cellEtiq1 = new Cell();
        cellEtiq1.add(new Paragraph("Total año " + anio + ":")
            .setFontSize(11)
            .setBold());
        cellEtiq1.setBackgroundColor(verdeClaro);
        cellEtiq1.setBorder(new SolidBorder(grisBorde, 1));
        cellEtiq1.setPadding(5);
        tableResumen.addCell(cellEtiq1);
        
        Cell cellVal1 = new Cell();
        cellVal1.add(new Paragraph(formatearMoneda(totalAnual))
            .setFontSize(11)
            .setBold()
            .setFontColor(new DeviceRgb(22, 163, 74)));
        cellVal1.setBackgroundColor(verdeClaro);
        cellVal1.setBorder(new SolidBorder(grisBorde, 1));
        cellVal1.setPadding(5);
        cellVal1.setTextAlignment(TextAlignment.RIGHT);
        tableResumen.addCell(cellVal1);

        // Fila 2
        Cell cellEtiq2 = new Cell();
        cellEtiq2.add(new Paragraph("Promedio mensual:").setFontSize(10));
        cellEtiq2.setBackgroundColor(grisClaro);
        cellEtiq2.setBorder(new SolidBorder(grisBorde, 1));
        cellEtiq2.setPadding(5);
        tableResumen.addCell(cellEtiq2);
        
        Cell cellVal2 = new Cell();
        cellVal2.add(new Paragraph(formatearMoneda(promedioMensual)).setFontSize(10));
        cellVal2.setBackgroundColor(grisClaro);
        cellVal2.setBorder(new SolidBorder(grisBorde, 1));
        cellVal2.setPadding(5);
        cellVal2.setTextAlignment(TextAlignment.RIGHT);
        tableResumen.addCell(cellVal2);

        // Fila 3
        Cell cellEtiq3 = new Cell();
        cellEtiq3.add(new Paragraph("Meses con gastos:").setFontSize(10));
        cellEtiq3.setBackgroundColor(blanco);
        cellEtiq3.setBorder(new SolidBorder(grisBorde, 1));
        cellEtiq3.setPadding(5);
        tableResumen.addCell(cellEtiq3);
        
        Cell cellVal3 = new Cell();
        cellVal3.add(new Paragraph(mesesConGastos + " de 12").setFontSize(10));
        cellVal3.setBackgroundColor(blanco);
        cellVal3.setBorder(new SolidBorder(grisBorde, 1));
        cellVal3.setPadding(5);
        cellVal3.setTextAlignment(TextAlignment.RIGHT);
        tableResumen.addCell(cellVal3);

        // Fila 4
        Cell cellEtiq4 = new Cell();
        cellEtiq4.add(new Paragraph("Total de transacciones:").setFontSize(10));
        cellEtiq4.setBackgroundColor(grisClaro);
        cellEtiq4.setBorder(new SolidBorder(grisBorde, 1));
        cellEtiq4.setPadding(5);
        tableResumen.addCell(cellEtiq4);
        
        Cell cellVal4 = new Cell();
        cellVal4.add(new Paragraph(String.valueOf(gastos.size())).setFontSize(10));
        cellVal4.setBackgroundColor(grisClaro);
        cellVal4.setBorder(new SolidBorder(grisBorde, 1));
        cellVal4.setPadding(5);
        cellVal4.setTextAlignment(TextAlignment.RIGHT);
        tableResumen.addCell(cellVal4);

        document.add(tableResumen);

        // Footer
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos | Generado: " + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(25)
            .setFontColor(new DeviceRgb(107, 114, 128))
            .setItalic();
        document.add(footer);

        document.close();
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
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Informe General");

        CellStyle headerStyle = crearEstiloHeader(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);
        CellStyle titleStyle = crearEstiloTitulo(workbook);

        Row titleRow = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("INFORME GENERAL MENSUAL");
        titleCell.setCellStyle(titleStyle);

        Row subtitleRow = sheet.createRow(1);
        org.apache.poi.ss.usermodel.Cell subtitleCell = subtitleRow.createCell(0);
        subtitleCell.setCellValue(formatearFecha(inicio) + " - " + formatearFecha(fin));

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

        Row headerRow = sheet.createRow(3);
        int col = 0;
        
        org.apache.poi.ss.usermodel.Cell catHeader = headerRow.createCell(col++);
        catHeader.setCellValue("CATEGORÍA");
        catHeader.setCellStyle(headerStyle);

        for (YearMonth mes : meses) {
            org.apache.poi.ss.usermodel.Cell mesHeader = headerRow.createCell(col++);
            mesHeader.setCellValue(mes.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            mesHeader.setCellStyle(headerStyle);
        }

        org.apache.poi.ss.usermodel.Cell totalHeader = headerRow.createCell(col);
        totalHeader.setCellValue("TOTAL");
        totalHeader.setCellStyle(headerStyle);

        int rowNum = 4;
        for (String categoria : categorias) {
            Row row = sheet.createRow(rowNum++);
            col = 0;
            
            row.createCell(col++).setCellValue(categoria);
            
            BigDecimal totalCategoria = BigDecimal.ZERO;
            for (YearMonth mes : meses) {
                BigDecimal monto = datosPorMesCategoria.get(mes).getOrDefault(categoria, BigDecimal.ZERO);
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(col++);
                cell.setCellValue(monto.doubleValue());
                cell.setCellStyle(moneyStyle);
                totalCategoria = totalCategoria.add(monto);
            }
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(col);
            totalCell.setCellValue(totalCategoria.doubleValue());
            totalCell.setCellStyle(moneyStyle);
        }

        Row totalRow = sheet.createRow(rowNum);
        col = 0;
        
        org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(col++);
        totalLabelCell.setCellValue("TOTAL MES");
        totalLabelCell.setCellStyle(headerStyle);

        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (YearMonth mes : meses) {
            BigDecimal totalMes = datosPorMesCategoria.get(mes).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            org.apache.poi.ss.usermodel.Cell cell = totalRow.createCell(col++);
            cell.setCellValue(totalMes.doubleValue());
            cell.setCellStyle(moneyStyle);
            totalGeneral = totalGeneral.add(totalMes);
        }

        org.apache.poi.ss.usermodel.Cell totalGeneralCell = totalRow.createCell(col);
        totalGeneralCell.setCellValue(totalGeneral.doubleValue());
        totalGeneralCell.setCellStyle(moneyStyle);

        for (int i = 0; i <= col; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fileOut = new FileOutputStream(rutaArchivo)) {
            workbook.write(fileOut);
        }
        
        workbook.close();
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
        String[] columnas = {"Fecha", "Subcategoría", "Producto", "Cantidad", "Valor Unit.", "Total", "Notas"};
        
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
            
            row.createCell(1).setCellValue(gasto.getSubcategoria().getNombre());
            row.createCell(2).setCellValue(gasto.getProducto());
            row.createCell(3).setCellValue(gasto.getCantidad());
            
            org.apache.poi.ss.usermodel.Cell valorUnitCell = row.createCell(4);
            valorUnitCell.setCellValue(gasto.getValorUnitario().doubleValue());
            valorUnitCell.setCellStyle(moneyStyle);
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(5);
            totalCell.setCellValue(gasto.getValorTotal().doubleValue());
            totalCell.setCellStyle(moneyStyle);
            
            row.createCell(6).setCellValue(gasto.getNotas() != null ? gasto.getNotas() : "");
            
            totalGastado = totalGastado.add(gasto.getValorTotal());
        }

        Row totalRow = sheet.createRow(rowNum + 1);
        org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(4);
        totalLabelCell.setCellValue("TOTAL:");
        totalLabelCell.setCellStyle(headerStyle);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(5);
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
            .filter(g -> g.getProducto().equalsIgnoreCase(producto))
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
        String[] columnas = {"Fecha", "Cantidad", "Precio Unitario", "Total", "Notas"};
        
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
            
            row.createCell(1).setCellValue(gasto.getCantidad());
            
            org.apache.poi.ss.usermodel.Cell precioCell = row.createCell(2);
            precioCell.setCellValue(gasto.getValorUnitario().doubleValue());
            precioCell.setCellStyle(moneyStyle);
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(3);
            totalCell.setCellValue(gasto.getValorTotal().doubleValue());
            totalCell.setCellStyle(moneyStyle);
            
            row.createCell(4).setCellValue(gasto.getNotas() != null ? gasto.getNotas() : "");
            
            totalGastado = totalGastado.add(gasto.getValorTotal());
            cantidadTotal += gasto.getCantidad();
        }

        Row totalRow = sheet.createRow(rowNum + 1);
        totalRow.createCell(0).setCellValue("TOTALES:");
        totalRow.createCell(1).setCellValue(cantidadTotal);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(3);
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

    public void exportarDashboardExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        exportarInformeGeneralExcel(inicio, fin, rutaArchivo);
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

    private String truncar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() > max ? texto.substring(0, max - 3) + "..." : texto;
    }
}