package com.misgastos.service;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.misgastos.model.Gasto;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Table;



@Service
public class ReporteService {
    
    @Autowired
    private GastoService gastoService;
    
    // ==================== MÉTODOS EXCEL ====================
    
    public void exportarGastosExcel(String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarTodos();
        generarExcel(gastos, rutaArchivo, "Todos los Gastos");
    }
    
    public void exportarGastosPorPeriodoExcel(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        String titulo = "Gastos del " + inicio + " al " + fin;
        generarExcel(gastos, rutaArchivo, titulo);
    }
    
    private void generarExcel(List<Gasto> gastos, String rutaArchivo, String titulo) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Gastos");
        
        // Estilos
        CellStyle headerStyle = crearEstiloHeader(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);
        CellStyle dateStyle = crearEstiloFecha(workbook);
        CellStyle totalStyle = crearEstiloTotal(workbook);
        
        // Título
        Row titleRow = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(titulo);
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);
        
        // Encabezados
        Row headerRow = sheet.createRow(2);
        String[] columnas = {"ID", "Fecha", "Hora", "Categoría", "Subcategoría", "Producto", "Cantidad", "Valor Unit.", "Total", "Notas"};
        
        for (int i = 0; i < columnas.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnas[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Datos
        int rowNum = 3;
        BigDecimal totalGeneral = BigDecimal.ZERO;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        
        for (Gasto gasto : gastos) {
            Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(gasto.getId());
            
            org.apache.poi.ss.usermodel.Cell dateCell = row.createCell(1);
            dateCell.setCellValue(gasto.getFecha().format(dateFormatter));
            dateCell.setCellStyle(dateStyle);
            
            row.createCell(2).setCellValue(gasto.getHora().format(timeFormatter));
            row.createCell(3).setCellValue(gasto.getCategoria().getNombre());
            row.createCell(4).setCellValue(gasto.getSubcategoria().getNombre());
            row.createCell(5).setCellValue(gasto.getProducto());
            row.createCell(6).setCellValue(gasto.getCantidad());
            
            org.apache.poi.ss.usermodel.Cell valorUnitCell = row.createCell(7);
            valorUnitCell.setCellValue(gasto.getValorUnitario().doubleValue());
            valorUnitCell.setCellStyle(moneyStyle);
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(8);
            totalCell.setCellValue(gasto.getValorTotal().doubleValue());
            totalCell.setCellStyle(moneyStyle);
            
            row.createCell(9).setCellValue(gasto.getNotas() != null ? gasto.getNotas() : "");
            
            totalGeneral = totalGeneral.add(gasto.getValorTotal());
        }
        
        // Fila de total
        Row totalRow = sheet.createRow(rowNum + 1);
        org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(7);
        totalLabelCell.setCellValue("TOTAL:");
        totalLabelCell.setCellStyle(totalStyle);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(8);
        totalValueCell.setCellValue(totalGeneral.doubleValue());
        totalValueCell.setCellStyle(totalStyle);
        
        // Ajustar ancho de columnas
        for (int i = 0; i < columnas.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // Filtros automáticos
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(2, rowNum - 1, 0, columnas.length - 1));
        
        // Guardar archivo
        try (FileOutputStream fileOut = new FileOutputStream(rutaArchivo)) {
            workbook.write(fileOut);
        }
        
        workbook.close();
    }
    
    // ==================== MÉTODOS PDF ====================
    
    public void exportarGastosPDF(String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarTodos();
        generarPDF(gastos, rutaArchivo, "Reporte de Todos los Gastos");
    }
    
    public void exportarGastosPorPeriodoPDF(LocalDate inicio, LocalDate fin, String rutaArchivo) throws Exception {
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String titulo = String.format("Reporte de Gastos\n%s - %s", 
            inicio.format(formatter), 
            fin.format(formatter));
        generarPDF(gastos, rutaArchivo, titulo);
    }
    
    private void generarPDF(List<Gasto> gastos, String rutaArchivo, String titulo) throws Exception {
        PdfWriter writer = new PdfWriter(rutaArchivo);
        PdfDocument pdf = new PdfDocument(writer);
        com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf);
        
        // Colores personalizados
        DeviceRgb azulOscuro = new DeviceRgb(30, 58, 138);
        DeviceRgb grisClaro = new DeviceRgb(243, 244, 246);
        
        // Título
        Paragraph tituloParrafo = new Paragraph(titulo)
            .setFontSize(18)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(10);
        document.add(tituloParrafo);
        
        // Fecha de generación
        Paragraph fechaGen = new Paragraph("Generado: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .setFontSize(10)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20);
        document.add(fechaGen);
        
        // Tabla con 8 columnas
        float[] columnWidths = {50f, 70f, 50f, 90f, 90f, 120f, 60f, 80f};
        com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(UnitValue.createPointArray(columnWidths));
        table.setWidth(UnitValue.createPercentValue(100));
        
        // Encabezados
        String[] headers = {"ID", "Fecha", "Hora", "Categoría", "Subcategoría", "Producto", "Cantidad", "Total"};
        for (String header : headers) {
            com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(header).setBold().setFontSize(10))
                .setBackgroundColor(azulOscuro)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8);
            table.addHeaderCell(cell);
        }
        
        // Datos
        BigDecimal totalGeneral = BigDecimal.ZERO;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        boolean alternar = false;
        
        
        for (Gasto gasto : gastos) {
        	Color bgColor = alternar ? grisClaro : ColorConstants.WHITE;
            
            agregarCeldaPDF(table, String.valueOf(gasto.getId()), bgColor, TextAlignment.CENTER);
            agregarCeldaPDF(table, gasto.getFecha().format(dateFormatter), bgColor, TextAlignment.CENTER);
            agregarCeldaPDF(table, gasto.getHora().format(timeFormatter), bgColor, TextAlignment.CENTER);
            agregarCeldaPDF(table, gasto.getCategoria().getNombre(), bgColor, TextAlignment.LEFT);
            agregarCeldaPDF(table, gasto.getSubcategoria().getNombre(), bgColor, TextAlignment.LEFT);
            agregarCeldaPDF(table, gasto.getProducto(), bgColor, TextAlignment.LEFT);
            agregarCeldaPDF(table, String.valueOf(gasto.getCantidad()), bgColor, TextAlignment.CENTER);
            agregarCeldaPDF(table, String.format("$%,.2f", gasto.getValorTotal()), bgColor, TextAlignment.RIGHT);
            
            totalGeneral = totalGeneral.add(gasto.getValorTotal());
            alternar = !alternar;
        }
        
        document.add(table);
        
        // Total general
        Paragraph totalParrafo = new Paragraph(String.format("TOTAL GENERAL: $%,.2f", totalGeneral))
            .setFontSize(14)
            .setBold()
            .setTextAlignment(TextAlignment.RIGHT)
            .setMarginTop(15)
            .setBackgroundColor(new DeviceRgb(254, 252, 232))
            .setPadding(10);
        document.add(totalParrafo);
        
        // Resumen
        Paragraph resumen = new Paragraph(String.format("Total de registros: %d", gastos.size()))
            .setFontSize(10)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(20);
        document.add(resumen);
        
        document.close();
    }
    
    private void agregarCeldaPDF(
            Table table,
            String texto,
            Color backgroundColor,
            TextAlignment alineacion
    ) {
        Cell cell = new Cell()
                .add(new Paragraph(texto).setFontSize(9))
                .setBackgroundColor(backgroundColor)
                .setTextAlignment(alineacion)
                .setPadding(6);

        table.addCell(cell);
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
}