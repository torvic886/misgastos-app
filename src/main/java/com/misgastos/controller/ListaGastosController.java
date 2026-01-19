package com.misgastos.controller;

import com.misgastos.model.Gasto;
import com.misgastos.service.GastoService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
public class ListaGastosController {
    
    @FXML
    private TableView<Gasto> tableGastos;
    
    @FXML
    private TableColumn<Gasto, Long> colId;
    
    @FXML
    private TableColumn<Gasto, LocalDate> colFecha;
    
    @FXML
    private TableColumn<Gasto, String> colCategoria;
    
    @FXML
    private TableColumn<Gasto, String> colProducto;
    
    @FXML
    private TableColumn<Gasto, Integer> colCantidad;
    
    @FXML
    private TableColumn<Gasto, BigDecimal> colValorUnitario;
    
    @FXML
    private TableColumn<Gasto, BigDecimal> colValorTotal;
    
    @FXML
    private TextField txtBuscar;
    
    @FXML
    private DatePicker dpFechaInicio;
    
    @FXML
    private DatePicker dpFechaFin;
    
    @Autowired
    private GastoService gastoService;
    
    private ObservableList<Gasto> listaGastos;
    
    // Formato de pesos colombianos
    private static final NumberFormat formatoPesos = NumberFormat.getInstance(new Locale("es", "CO"));
    
    static {
        formatoPesos.setMinimumFractionDigits(0);
        formatoPesos.setMaximumFractionDigits(0);
    }
    
    @FXML
    public void initialize() {
        configurarTabla();
        configurarFechasPorDefecto();
        cargarGastos();
    }
    
    private void configurarFechasPorDefecto() {
        LocalDate hoy = LocalDate.now();
        dpFechaInicio.setValue(hoy.withDayOfMonth(1)); // Primer día del mes actual
        dpFechaFin.setValue(hoy);
    }
    
    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        colCategoria.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getCategoria().getNombre()
            )
        );
        colProducto.setCellValueFactory(new PropertyValueFactory<>("producto"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        
        // Configurar formato de pesos colombianos para Valor Unitario
        colValorUnitario.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));
        colValorUnitario.setCellFactory(col -> new TableCell<Gasto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean empty) {
                super.updateItem(valor, empty);
                if (empty || valor == null) {
                    setText(null);
                } else {
                    setText("$" + formatoPesos.format(valor));
                }
            }
        });
        
        // Configurar formato de pesos colombianos para Valor Total
        colValorTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        colValorTotal.setCellFactory(col -> new TableCell<Gasto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean empty) {
                super.updateItem(valor, empty);
                if (empty || valor == null) {
                    setText(null);
                } else {
                    setText("$" + formatoPesos.format(valor));
                }
            }
        });
    }
    
    private void cargarGastos() {
        List<Gasto> gastos = gastoService.listarTodos();
        listaGastos = FXCollections.observableArrayList(gastos);
        tableGastos.setItems(listaGastos);
        
        System.out.println("✅ Cargados " + gastos.size() + " gastos");
    }
    
    @FXML
    public void handleBuscar() {
        String busqueda = txtBuscar.getText().toLowerCase();
        if (busqueda.isEmpty()) {
            cargarGastos();
            return;
        }
        
        List<Gasto> gastosFiltrados = listaGastos.stream()
            .filter(g -> g.getProducto().toLowerCase().contains(busqueda) ||
                        g.getCategoria().getNombre().toLowerCase().contains(busqueda))
            .toList();
        
        tableGastos.setItems(FXCollections.observableArrayList(gastosFiltrados));
    }
    
    @FXML
    public void handleEliminar() {
        Gasto seleccionado = tableGastos.getSelectionModel().getSelectedItem();
        
        if (seleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un gasto para eliminar", Alert.AlertType.WARNING);
            return;
        }
        
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("¿Está seguro de eliminar este gasto?");
        
        if (confirmacion.showAndWait().get() == ButtonType.OK) {
            gastoService.eliminarGasto(seleccionado.getId());
            mostrarAlerta("Éxito", "Gasto eliminado correctamente", Alert.AlertType.INFORMATION);
            cargarGastos();
        }
    }
    
    @FXML
    public void handleRefrescar() {
        cargarGastos();
        txtBuscar.clear();
    }
    
    @FXML
    public void handleExportarExcel() {
        LocalDate fechaInicio = dpFechaInicio.getValue();
        LocalDate fechaFin = dpFechaFin.getValue();
        
        if (fechaInicio == null || fechaFin == null) {
            mostrarAlerta("Validación", "Por favor seleccione ambas fechas", Alert.AlertType.WARNING);
            return;
        }
        
        if (fechaInicio.isAfter(fechaFin)) {
            mostrarAlerta("Validación", "La fecha de inicio debe ser anterior a la fecha fin", Alert.AlertType.WARNING);
            return;
        }
        
        try {
            File archivo = seleccionarArchivoExcel();
            if (archivo != null) {
                String busqueda = txtBuscar.getText().trim();
                exportarGastosExcel(fechaInicio, fechaFin, busqueda, archivo);
                
                String mensaje = "Gastos exportados correctamente a:\n" + archivo.getAbsolutePath();
                if (!busqueda.isEmpty()) {
                    mensaje += "\n\n🔍 Filtro aplicado: \"" + busqueda + "\"";
                }
                
                mostrarAlerta("Éxito", mensaje, Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", 
                "No se pudo exportar el archivo:\n" + e.getMessage(), 
                Alert.AlertType.ERROR);
        }
    }
    
    private File seleccionarArchivoExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportar Gastos - Excel");
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        fileChooser.setInitialFileName("gastos_" + timestamp + ".xlsx");
        
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        
        return fileChooser.showSaveDialog(tableGastos.getScene().getWindow());
    }
    
    private void exportarGastosExcel(LocalDate inicio, LocalDate fin, String filtro, File archivo) throws Exception {
        // Obtener gastos del período
        List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
        
        // Aplicar filtro de búsqueda si existe
        if (filtro != null && !filtro.isEmpty()) {
            String busqueda = filtro.toLowerCase();
            gastos = gastos.stream()
                .filter(g -> g.getProducto().toLowerCase().contains(busqueda) ||
                            g.getCategoria().getNombre().toLowerCase().contains(busqueda))
                .toList();
        }
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Gastos");
            
            // Estilos
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            
            // Título del informe con filtro (si existe)
            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            String titulo = "HISTORIAL DE GASTOS - " + inicio + " a " + fin;
            if (filtro != null && !filtro.isEmpty()) {
                titulo += " | Filtro: \"" + filtro + "\"";
            }
            titleCell.setCellValue(titulo);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            
            // Fusionar celdas del título
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));
            
            // Crear encabezados (fila 1, justo debajo del título)
            Row headerRow = sheet.createRow(1);
            String[] columnas = {"ID", "Fecha", "Categoría", "Producto", "Cantidad", "Valor Unitario", "Valor Total"};
            
            for (int i = 0; i < columnas.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Llenar datos (comienza en fila 2)
            int rowNum = 2;
            BigDecimal totalGeneral = BigDecimal.ZERO;
            
            for (Gasto gasto : gastos) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(gasto.getId());
                
                org.apache.poi.ss.usermodel.Cell fechaCell = row.createCell(1);
                fechaCell.setCellValue(gasto.getFecha().toString());
                fechaCell.setCellStyle(dateStyle);
                
                row.createCell(2).setCellValue(gasto.getCategoria().getNombre());
                row.createCell(3).setCellValue(gasto.getProducto());
                row.createCell(4).setCellValue(gasto.getCantidad());
                
                org.apache.poi.ss.usermodel.Cell valorUnitCell = row.createCell(5);
                valorUnitCell.setCellValue(gasto.getValorUnitario().doubleValue());
                valorUnitCell.setCellStyle(numberStyle);
                
                org.apache.poi.ss.usermodel.Cell valorTotalCell = row.createCell(6);
                valorTotalCell.setCellValue(gasto.getValorTotal().doubleValue());
                valorTotalCell.setCellStyle(numberStyle);
                
                totalGeneral = totalGeneral.add(gasto.getValorTotal());
            }
            
            // Fila de total
            Row totalRow = sheet.createRow(rowNum);
            org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(5);
            totalLabelCell.setCellValue("TOTAL:");
            
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);
            boldStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalLabelCell.setCellStyle(boldStyle);
            
            org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(6);
            totalValueCell.setCellValue(totalGeneral.doubleValue());
            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.cloneStyleFrom(numberStyle);
            totalStyle.setFont(boldFont);
            totalValueCell.setCellStyle(totalStyle);
            
            // Ajustar ancho de columnas
            for (int i = 0; i < columnas.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Escribir archivo
            try (FileOutputStream fos = new FileOutputStream(archivo)) {
                workbook.write(fos);
            }
            
            String filtroLog = (filtro != null && !filtro.isEmpty()) ? " [Filtro: \"" + filtro + "\"]" : "";
            System.out.println("✅ Excel exportado: " + gastos.size() + " gastos - Total: $" + 
                formatoPesos.format(totalGeneral) + filtroLog);
        }
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}