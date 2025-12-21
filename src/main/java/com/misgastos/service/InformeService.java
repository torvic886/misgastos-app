package com.misgastos.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.misgastos.model.Gasto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

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

    public void exportarInformeGeneralPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        String contenido = generarInformeGeneralMensual(inicio, fin);
        exportarTextoPDF(contenido, rutaArchivo, "Informe General Mensual");
    }

    public void exportarInformeCategoriaPDF(String categoria, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        String contenido = generarInformePorCategoria(categoria, inicio, fin);
        exportarTextoPDF(contenido, rutaArchivo, "Informe por Categoría: " + categoria);
    }

    public void exportarInformeProductoPDF(String producto, LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        String contenido = generarInformePorProducto(producto, inicio, fin);
        exportarTextoPDF(contenido, rutaArchivo, "Análisis de Producto: " + producto);
    }

    public void exportarDashboardPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        String contenido = generarDashboardEjecutivo(inicio, fin);
        exportarTextoPDF(contenido, rutaArchivo, "Dashboard Ejecutivo");
    }

    private void exportarTextoPDF(String contenido, String rutaArchivo, String titulo) throws Exception {
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);

        Paragraph tituloParrafo = new Paragraph(titulo)
            .setFontSize(16)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(10)
            .setFontColor(azulOscuro);
        document.add(tituloParrafo);

        Paragraph fechaGen = new Paragraph("Generado: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .setFontSize(9)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(fechaGen);

        Paragraph contenidoParrafo = new Paragraph(contenido)
            .setFontSize(8)
            .setFixedLeading(12);
        document.add(contenidoParrafo);

        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(8)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginTop(20)
            .setItalic();
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

    private CellStyle crearEstiloHeader(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloMoneda(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    private CellStyle crearEstiloFecha(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloTotal(Workbook workbook) {
        CellStyle style = crearEstiloMoneda(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle crearEstiloTitulo(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
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