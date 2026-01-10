package com.misgastos.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.colors.ColorConstants;

import com.misgastos.model.Billetero;
import com.misgastos.repository.BilleteroRepository;
import com.misgastos.repository.GastoRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class ReporteBilleteroService {

    @Autowired
    private BilleteroRepository billeteroRepository;
    
    @Autowired
    private GastoRepository gastoRepository;
    
    private final DecimalFormat moneyFormat;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    public ReporteBilleteroService() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        symbols.setCurrencySymbol("$");
        moneyFormat = new DecimalFormat("$ #,##0", symbols);
    }
    
    private long obtenerValor(BigDecimal valor) {
        return (valor != null) ? valor.longValue() : 0L;
    }
    
    // ==================== DATOS DEL REPORTE ====================
    
    private List<FilaReporte> generarDatosReporteMensual(LocalDate fechaInicio, LocalDate fechaFin) {
        List<FilaReporte> filas = new ArrayList<>();
        
        // 1. Buscar billeteros en el rango
        List<Billetero> billeteros = billeteroRepository.findByFechaBetweenOrderByFechaDesc(fechaInicio, fechaFin);
        billeteros.sort(Comparator.comparing(Billetero::getFecha));
        
        // 2. Agrupar billeteros por mes
        Map<YearMonth, List<Billetero>> billeteroPorMes = new TreeMap<>();
        for (Billetero b : billeteros) {
            YearMonth mes = YearMonth.from(b.getFecha());
            billeteroPorMes.computeIfAbsent(mes, k -> new ArrayList<>()).add(b);
        }
        
        // 🆕 3. GENERAR TODOS LOS MESES DEL RANGO (aunque no tengan billeteros)
        YearMonth mesInicio = YearMonth.from(fechaInicio);
        YearMonth mesFin = YearMonth.from(fechaFin);
        
        YearMonth mesActual = mesInicio;
        
        // 4. Iterar por TODOS los meses del rango
        while (!mesActual.isAfter(mesFin)) {
            // Obtener billeteros del mes (puede ser lista vacía si no hay)
            List<Billetero> billeterosDelMes = billeteroPorMes.getOrDefault(mesActual, new ArrayList<>());
            
            // Calcular sumas de billeteros (será 0 si no hay)
            long billMes = billeterosDelMes.stream()
                .mapToLong(b -> b.getBilletero().longValue())
                .sum();
            
            long premiosMes = billeterosDelMes.stream()
                .mapToLong(b -> b.getPremios().longValue())
                .sum();
            
            long diferencia = billMes - premiosMes;
            
            LocalDate primerDia = mesActual.atDay(1);
            LocalDate ultimoDia = mesActual.atEndOfMonth();
            
            System.out.println("\n=== DIAGNÓSTICO MES: " + mesActual + " ===");
            System.out.println("Rango: " + primerDia + " a " + ultimoDia);
            System.out.println("Billeteros encontrados: " + billeterosDelMes.size());
            
            // PAGOS MANUEL (categoría 14)
            BigDecimal pagosManuelBD = gastoRepository.sumByFechaBetweenAndCategoriaId(
                primerDia.toString(), ultimoDia.toString(), 14L);
            long pagosManuelVal = obtenerValor(pagosManuelBD);
            System.out.println("Pagos Manuel (cat 14): " + pagosManuelVal + " | BD devolvió: " + pagosManuelBD);
            
            // INVERSIÓN (categoría 10)
            BigDecimal inversionBD = gastoRepository.sumByFechaBetweenAndCategoriaId(
                primerDia.toString(), ultimoDia.toString(), 10L);
            long inversionVal = obtenerValor(inversionBD);
            System.out.println("Inversión (cat 10): " + inversionVal + " | BD devolvió: " + inversionBD);
            
            // GASTOS TOTALES
            BigDecimal gastosTotalesBD = gastoRepository.sumByFechaBetween(primerDia, ultimoDia);
            long gastosTotalesVal = obtenerValor(gastosTotalesBD);
            System.out.println("Gastos Totales: " + gastosTotalesVal + " | BD devolvió: " + gastosTotalesBD);
            
            // GASTOS LOCAL = GASTOS TOTALES - PAGOS MANUEL - INVERSIÓN
            long gastosLocal = gastosTotalesVal - pagosManuelVal - inversionVal;
            System.out.println("Gastos Local: " + gastosLocal);
            
            // GASTO TOTAL
            long gastoTotal = gastosLocal + pagosManuelVal + inversionVal;
            System.out.println("Gasto Total: " + gastoTotal);
            
            // UTILIDAD NETA = DIFERENCIA - GASTO TOTAL
            long utilidadNeta = diferencia - gastoTotal;
            System.out.println("Utilidad Neta: " + utilidadNeta);
            
            FilaReporte fila = new FilaReporte(
                mesActual.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES")).toUpperCase(),
                billMes,
                premiosMes,
                diferencia,
                gastosLocal,
                pagosManuelVal,
                inversionVal,
                gastoTotal,
                utilidadNeta
            );
            
            filas.add(fila);
            
            // 🆕 Avanzar al siguiente mes
            mesActual = mesActual.plusMonths(1);
        }
        
        return filas;
    }
    
    private List<FilaReporte> generarDatosReporteDiario(LocalDate fechaInicio, LocalDate fechaFin) {
        List<FilaReporte> filas = new ArrayList<>();
        
        // 1. Buscar billeteros en el rango
        List<Billetero> billeteros = billeteroRepository.findByFechaBetweenOrderByFechaDesc(fechaInicio, fechaFin);
        billeteros.sort(Comparator.comparing(Billetero::getFecha));
        
        // 2. Agrupar billeteros por fecha
        Map<LocalDate, List<Billetero>> billeteroPorFecha = new TreeMap<>();
        for (Billetero b : billeteros) {
            billeteroPorFecha.computeIfAbsent(b.getFecha(), k -> new ArrayList<>()).add(b);
        }
        
        // 🆕 3. GENERAR TODOS LOS DÍAS DEL RANGO (aunque no tengan billeteros)
        LocalDate fechaActual = fechaInicio;
        
        // 4. Iterar por TODOS los días del rango
        while (!fechaActual.isAfter(fechaFin)) {
            // Obtener billeteros del día (puede ser lista vacía si no hay)
            List<Billetero> billeterosDelDia = billeteroPorFecha.getOrDefault(fechaActual, new ArrayList<>());
            
            // Calcular sumas de billeteros (será 0 si no hay)
            long billDia = billeterosDelDia.stream()
                .mapToLong(b -> b.getBilletero().longValue())
                .sum();
            
            long premiosDia = billeterosDelDia.stream()
                .mapToLong(b -> b.getPremios().longValue())
                .sum();
            
            long diferencia = billDia - premiosDia;
            
            long pagosManuelVal = obtenerValor(gastoRepository.sumByFechaAndCategoriaId(fechaActual.toString(), 14L));
            long inversionVal = obtenerValor(gastoRepository.sumByFechaAndCategoriaId(fechaActual.toString(), 10L));
            long gastosTotalesVal = obtenerValor(gastoRepository.sumByFecha(fechaActual));
            
            long gastosLocal = gastosTotalesVal - pagosManuelVal - inversionVal;
            long gastoTotal = gastosLocal + pagosManuelVal + inversionVal;
            long utilidadNeta = diferencia - gastoTotal;
            
            FilaReporte fila = new FilaReporte(
                fechaActual.format(dateFormatter),
                billDia,
                premiosDia,
                diferencia,
                gastosLocal,
                pagosManuelVal,
                inversionVal,
                gastoTotal,
                utilidadNeta
            );
            
            filas.add(fila);
            
            // 🆕 Avanzar al siguiente día
            fechaActual = fechaActual.plusDays(1);
        }
        
        return filas;
    }
    
    // ==================== EXPORTAR A EXCEL ====================
    
    public void exportarReporteMensualExcel(LocalDate fechaInicio, LocalDate fechaFin, String rutaArchivo) throws Exception {
        List<FilaReporte> datos = generarDatosReporteMensual(fechaInicio, fechaFin);
        String subtitulo = "Período: " + fechaInicio.format(dateFormatter) + " al " + fechaFin.format(dateFormatter);
        exportarExcel(datos, rutaArchivo, "REPORTE MENSUAL BILLETEROS", subtitulo);
    }
    
    public void exportarReporteDiarioExcel(LocalDate fechaInicio, LocalDate fechaFin, String rutaArchivo) throws Exception {
        List<FilaReporte> datos = generarDatosReporteDiario(fechaInicio, fechaFin);
        String subtitulo = "Período: " + fechaInicio.format(dateFormatter) + " al " + fechaFin.format(dateFormatter);
        exportarExcel(datos, rutaArchivo, "REPORTE DIARIO BILLETEROS", subtitulo);
    }
    
    private void exportarExcel(List<FilaReporte> datos, String rutaArchivo, String titulo, String subtitulo) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Reporte Billeteros");
        
        XSSFCellStyle estiloTitulo = crearEstiloTitulo(workbook);
        XSSFCellStyle estiloSubtitulo = crearEstiloSubtitulo(workbook);
        XSSFCellStyle estiloEncabezado = crearEstiloEncabezado(workbook);
        XSSFCellStyle estiloMoneda = crearEstiloMoneda(workbook);
        XSSFCellStyle estiloTotal = crearEstiloTotal(workbook);
        
        int rowNum = 0;
        
        // TÍTULO
        Row rowTitulo = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell cellTitulo = rowTitulo.createCell(0);
        cellTitulo.setCellValue(titulo);
        cellTitulo.setCellStyle(estiloTitulo);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 8));
        
        // SUBTÍTULO CON FECHAS
        Row rowSubtitulo = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell cellSubtitulo = rowSubtitulo.createCell(0);
        cellSubtitulo.setCellValue(subtitulo);
        cellSubtitulo.setCellStyle(estiloSubtitulo);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 8));
        
        rowNum++; // Espacio
        
        // ENCABEZADOS
        Row rowEncabezado = sheet.createRow(rowNum++);
        String[] encabezados = {
            "PERIODO", "BILL", "PREMIOS", "DIFERENCIA",
            "GASTOS LOCAL", "PAGOS MANUEL", "INVERSIÓN", "GASTO TOTAL", "UTILIDAD NETA"
        };
        
        for (int i = 0; i < encabezados.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = rowEncabezado.createCell(i);
            cell.setCellValue(encabezados[i]);
            cell.setCellStyle(estiloEncabezado);
        }
        
        long totalBill = 0;
        long totalPremios = 0;
        long totalDiferencia = 0;
        long totalGastosLocal = 0;
        long totalPagosManuel = 0;
        long totalInversion = 0;
        long totalGastoTotal = 0;
        long totalUtilidadNeta = 0;
        
        for (FilaReporte fila : datos) {
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(fila.periodo);
            
            org.apache.poi.ss.usermodel.Cell cellBill = row.createCell(1);
            cellBill.setCellValue(fila.acumuladoBill);
            cellBill.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellPremios = row.createCell(2);
            cellPremios.setCellValue(fila.acumuladoPremios);
            cellPremios.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellDif = row.createCell(3);
            cellDif.setCellValue(fila.diferencia);
            cellDif.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellGastos = row.createCell(4);
            cellGastos.setCellValue(fila.gastosLocal);
            cellGastos.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellPagos = row.createCell(5);
            cellPagos.setCellValue(fila.pagosManuel);
            cellPagos.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellInv = row.createCell(6);
            cellInv.setCellValue(fila.inversion);
            cellInv.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellGastoTotal = row.createCell(7);
            cellGastoTotal.setCellValue(fila.gastoTotal);
            cellGastoTotal.setCellStyle(estiloMoneda);
            
            org.apache.poi.ss.usermodel.Cell cellUtilidad = row.createCell(8);
            cellUtilidad.setCellValue(fila.utilidadNeta);
            cellUtilidad.setCellStyle(fila.utilidadNeta >= 0 ? estiloMoneda : estiloTotal);
            
            totalBill += fila.acumuladoBill;
            totalPremios += fila.acumuladoPremios;
            totalDiferencia += fila.diferencia;
            totalGastosLocal += fila.gastosLocal;
            totalPagosManuel += fila.pagosManuel;
            totalInversion += fila.inversion;
            totalGastoTotal += fila.gastoTotal;
            totalUtilidadNeta += fila.utilidadNeta;
        }
        
        if (!datos.isEmpty()) {
            Row rowTotal = sheet.createRow(rowNum);
            
            org.apache.poi.ss.usermodel.Cell cellLabelTotal = rowTotal.createCell(0);
            cellLabelTotal.setCellValue("TOTALES");
            cellLabelTotal.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalBill = rowTotal.createCell(1);
            cellTotalBill.setCellValue(totalBill);
            cellTotalBill.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalPremios = rowTotal.createCell(2);
            cellTotalPremios.setCellValue(totalPremios);
            cellTotalPremios.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalDif = rowTotal.createCell(3);
            cellTotalDif.setCellValue(totalDiferencia);
            cellTotalDif.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalGastosLocal = rowTotal.createCell(4);
            cellTotalGastosLocal.setCellValue(totalGastosLocal);
            cellTotalGastosLocal.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalPagos = rowTotal.createCell(5);
            cellTotalPagos.setCellValue(totalPagosManuel);
            cellTotalPagos.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalInv = rowTotal.createCell(6);
            cellTotalInv.setCellValue(totalInversion);
            cellTotalInv.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalGastoTotal = rowTotal.createCell(7);
            cellTotalGastoTotal.setCellValue(totalGastoTotal);
            cellTotalGastoTotal.setCellStyle(estiloTotal);
            
            org.apache.poi.ss.usermodel.Cell cellTotalUtilidad = rowTotal.createCell(8);
            cellTotalUtilidad.setCellValue(totalUtilidadNeta);
            cellTotalUtilidad.setCellStyle(estiloTotal);
        }
        
        for (int i = 0; i < 9; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }
        
        try (FileOutputStream outputStream = new FileOutputStream(rutaArchivo)) {
            workbook.write(outputStream);
        }
        workbook.close();
    }
    
    // ==================== EXPORTAR A PDF ====================
    
    public void exportarReporteMensualPDF(LocalDate fechaInicio, LocalDate fechaFin, String rutaArchivo) throws Exception {
        List<FilaReporte> datos = generarDatosReporteMensual(fechaInicio, fechaFin);
        String subtitulo = "Período: " + fechaInicio.format(dateFormatter) + " al " + fechaFin.format(dateFormatter);
        exportarPDF(datos, rutaArchivo, "REPORTE MENSUAL BILLETEROS", subtitulo);
    }
    
    public void exportarReporteDiarioPDF(LocalDate fechaInicio, LocalDate fechaFin, String rutaArchivo) throws Exception {
        List<FilaReporte> datos = generarDatosReporteDiario(fechaInicio, fechaFin);
        String subtitulo = "Período: " + fechaInicio.format(dateFormatter) + " al " + fechaFin.format(dateFormatter);
        exportarPDF(datos, rutaArchivo, "REPORTE DIARIO BILLETEROS", subtitulo);
    }
    
    private void exportarPDF(List<FilaReporte> datos, String rutaArchivo, String titulo, String subtitulo) throws Exception {
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        
        // 🆕 CAMBIAR A ORIENTACIÓN HORIZONTAL (Landscape)
        pdf.setDefaultPageSize(com.itextpdf.kernel.geom.PageSize.A4.rotate());
        
        Document document = new Document(pdf);
        
        // 🆕 Reducir márgenes para aprovechar más espacio
        document.setMargins(15, 15, 15, 15);
        
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        
        Paragraph tituloParrafo = new Paragraph(titulo)
            .setFontSize(16)  // Reducido de 18 a 16
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(azulOscuro)
            .setMarginBottom(3);  // Reducido margen
        document.add(tituloParrafo);
        
        Paragraph subtituloParrafo = new Paragraph(subtitulo)
            .setFontSize(10)  // Reducido de 11 a 10
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(new DeviceRgb(107, 114, 128))
            .setMarginBottom(10);  // Reducido margen
        document.add(subtituloParrafo);
        
        // 🆕 AJUSTAR ANCHOS DE COLUMNA: más espacio para columnas numéricas
        float[] columnWidths = {
            1.5f,  // PERIODO (más estrecha)
            2.3f,  // BILL (más ancha)
            2.3f,  // PREMIOS (más ancha)
            2.3f,  // DIFERENCIA (más ancha)
            2.3f,  // GASTOS LOCAL (más ancha)
            2.3f,  // PAGOS MANUEL (más ancha)
            2.3f,  // INVERSIÓN (más ancha)
            2.3f,  // GASTO TOTAL (más ancha)
            2.3f   // UTILIDAD NETA (más ancha)
        };
        
        Table table = new Table(UnitValue.createPointArray(columnWidths));
        table.setWidth(UnitValue.createPercentValue(100));
        
        String[] encabezados = {
            "PERIODO", "BILL", "PREMIOS", "DIFERENCIA",
            "GASTOS LOCAL", "PAGOS MANUEL", "INVERSIÓN", "GASTO TOTAL", "UTILIDAD NETA"
        };
        
        for (String encabezado : encabezados) {
            Cell cell = new Cell()
                .add(new Paragraph(encabezado).setBold().setFontSize(7))  // Tamaño reducido
                .setBackgroundColor(azulOscuro)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(4);  // Reducido de 6 a 4
            table.addHeaderCell(cell);
        }
        
        long totalBill = 0;
        long totalPremios = 0;
        long totalDiferencia = 0;
        long totalGastosLocal = 0;
        long totalPagosManuel = 0;
        long totalInversion = 0;
        long totalGastoTotal = 0;
        long totalUtilidadNeta = 0;
        
        boolean alternar = false;
        for (FilaReporte fila : datos) {
            DeviceRgb colorFondo = alternar ? grisClaro : new DeviceRgb(255, 255, 255);
            
            // PERIODO - alineado a la izquierda, fuente más pequeña
            table.addCell(crearCeldaPDFAjustada(fila.periodo, colorFondo, TextAlignment.LEFT, 6.5f));
            
            // Todas las columnas numéricas - fuente más pequeña
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.acumuladoBill), colorFondo, TextAlignment.RIGHT, 6.5f));
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.acumuladoPremios), colorFondo, TextAlignment.RIGHT, 6.5f));
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.diferencia), colorFondo, TextAlignment.RIGHT, 6.5f));
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.gastosLocal), colorFondo, TextAlignment.RIGHT, 6.5f));
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.pagosManuel), colorFondo, TextAlignment.RIGHT, 6.5f));
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.inversion), colorFondo, TextAlignment.RIGHT, 6.5f));
            table.addCell(crearCeldaPDFAjustada(moneyFormat.format(fila.gastoTotal), colorFondo, TextAlignment.RIGHT, 6.5f));
            
            Cell cellUtilidad = crearCeldaPDFAjustada(moneyFormat.format(fila.utilidadNeta), colorFondo, TextAlignment.RIGHT, 6.5f);
            if (fila.utilidadNeta < 0) {
                cellUtilidad.setBackgroundColor(new DeviceRgb(254, 226, 226));
            } else if (fila.utilidadNeta > 0) {
                cellUtilidad.setBackgroundColor(new DeviceRgb(220, 252, 231));
            }
            table.addCell(cellUtilidad);
            
            totalBill += fila.acumuladoBill;
            totalPremios += fila.acumuladoPremios;
            totalDiferencia += fila.diferencia;
            totalGastosLocal += fila.gastosLocal;
            totalPagosManuel += fila.pagosManuel;
            totalInversion += fila.inversion;
            totalGastoTotal += fila.gastoTotal;
            totalUtilidadNeta += fila.utilidadNeta;
            
            alternar = !alternar;
        }
        
        DeviceRgb colorTotal = new DeviceRgb(254, 226, 226);
        table.addCell(crearCeldaPDFAjustada("TOTALES", colorTotal, TextAlignment.LEFT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalBill), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalPremios), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalDiferencia), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalGastosLocal), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalPagosManuel), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalInversion), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalGastoTotal), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        table.addCell(crearCeldaPDFAjustada(moneyFormat.format(totalUtilidadNeta), colorTotal, TextAlignment.RIGHT, 7f).setBold());
        
        document.add(table);
        
        Paragraph footer = new Paragraph("ExpenseFlow - Sistema de Control de Gastos")
            .setFontSize(7)  // Reducido de 8 a 7
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(10)  // Reducido margen
            .setItalic();
        document.add(footer);
        
        document.close();
    }

    // 🆕 NUEVO MÉTODO: crearCeldaPDFAjustada con tamaño de fuente personalizado
    private Cell crearCeldaPDFAjustada(String texto, DeviceRgb backgroundColor, TextAlignment alineacion, float fontSize) {
        Cell cell = new Cell()
            .add(new Paragraph(texto).setFontSize(fontSize))
            .setBackgroundColor(backgroundColor)
            .setTextAlignment(alineacion)
            .setPadding(3);  // Reducido de 4 a 3
        return cell;
    }
    
    private Cell crearCeldaPDF(String texto, DeviceRgb backgroundColor, TextAlignment alineacion) {
        Cell cell = new Cell()
            .add(new Paragraph(texto).setFontSize(7))
            .setBackgroundColor(backgroundColor)
            .setTextAlignment(alineacion)
            .setPadding(4);
        return cell;
    }
    
    // ==================== ESTILOS EXCEL ====================
    
    private XSSFCellStyle crearEstiloTitulo(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private XSSFCellStyle crearEstiloSubtitulo(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    private XSSFCellStyle crearEstiloEncabezado(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{59, (byte)130, (byte)246}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private XSSFCellStyle crearEstiloMoneda(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("$ #,##0"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private XSSFCellStyle crearEstiloTotal(XSSFWorkbook workbook) {
        XSSFCellStyle style = crearEstiloMoneda(workbook);
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)254, (byte)226, (byte)226}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    // ==================== CLASE INTERNA ====================
    
    private static class FilaReporte {
        String periodo;
        long acumuladoBill;
        long acumuladoPremios;
        long diferencia;
        long gastosLocal;
        long pagosManuel;
        long inversion;
        long gastoTotal;
        long utilidadNeta;
        
        FilaReporte(String periodo, long acumuladoBill, long acumuladoPremios, long diferencia,
                   long gastosLocal, long pagosManuel, long inversion, long gastoTotal, long utilidadNeta) {
            this.periodo = periodo;
            this.acumuladoBill = acumuladoBill;
            this.acumuladoPremios = acumuladoPremios;
            this.diferencia = diferencia;
            this.gastosLocal = gastosLocal;
            this.pagosManuel = pagosManuel;
            this.inversion = inversion;
            this.gastoTotal = gastoTotal;
            this.utilidadNeta = utilidadNeta;
        }
    }
}