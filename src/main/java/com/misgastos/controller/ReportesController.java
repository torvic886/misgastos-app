package com.misgastos.controller;

import com.misgastos.service.ReporteService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;

@Component
public class ReportesController {

    @FXML
    private DatePicker dpFechaInicio;

    @FXML
    private DatePicker dpFechaFin;

    @Autowired
    private ReporteService reporteService;

    @FXML
    public void initialize() {
        // Configurar fechas por defecto (mes actual)
        LocalDate hoy = LocalDate.now();
        dpFechaInicio.setValue(hoy.withDayOfMonth(1));
        dpFechaFin.setValue(hoy);
    }

    // ==================== EXPORTAR TODO ====================
    
    @FXML
    public void handleExportarTodosExcel() {
        try {
            File archivo = seleccionarArchivo("Exportar Todos los Gastos - Excel", "xlsx");
            if (archivo != null) {
                reporteService.exportarGastosExcel(archivo.getAbsolutePath());
                mostrarAlerta("Éxito", "Reporte Excel exportado correctamente a:\n" + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el reporte Excel: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    public void handleExportarTodosPDF() {
        try {
            File archivo = seleccionarArchivo("Exportar Todos los Gastos - PDF", "pdf");
            if (archivo != null) {
                reporteService.exportarGastosPDF(archivo.getAbsolutePath());
                mostrarAlerta("Éxito", "Reporte PDF exportado correctamente a:\n" + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el reporte PDF: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ==================== EXPORTAR POR FECHA ====================
    
    @FXML
    public void handleExportarPorFechaExcel() {
        LocalDate inicio = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        try {
            File archivo = seleccionarArchivo("Exportar Gastos por Fecha - Excel", "xlsx");
            if (archivo != null) {
                reporteService.exportarGastosPorPeriodoExcel(inicio, fin, archivo.getAbsolutePath());
                mostrarAlerta("Éxito", "Reporte Excel exportado correctamente a:\n" + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el reporte Excel: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    public void handleExportarPorFechaPDF() {
        LocalDate inicio = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        try {
            File archivo = seleccionarArchivo("Exportar Gastos por Fecha - PDF", "pdf");
            if (archivo != null) {
                reporteService.exportarGastosPorPeriodoPDF(inicio, fin, archivo.getAbsolutePath());
                mostrarAlerta("Éxito", "Reporte PDF exportado correctamente a:\n" + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el reporte PDF: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================
    
    private boolean validarFechas(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            mostrarAlerta("Validación", "Seleccione ambas fechas", Alert.AlertType.WARNING);
            return false;
        }

        if (inicio.isAfter(fin)) {
            mostrarAlerta("Validación", "La fecha de inicio debe ser anterior a la fecha fin", Alert.AlertType.WARNING);
            return false;
        }
        
        return true;
    }
    
    private File seleccionarArchivo(String titulo, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(titulo);
        
        String nombreArchivo = "reporte_gastos." + extension;
        fileChooser.setInitialFileName(nombreArchivo);
        
        String descripcion = extension.equals("xlsx") ? "Excel Files" : "PDF Files";
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(descripcion, "*." + extension)
        );

        return fileChooser.showSaveDialog(null);
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}