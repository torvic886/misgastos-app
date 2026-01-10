package com.misgastos.controller;

import com.misgastos.service.ReporteBilleteroService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;

@Component
public class ReportesBilleterosController {

    @FXML private DatePicker dpMensualInicio;
    @FXML private DatePicker dpMensualFin;
    @FXML private DatePicker dpDiarioInicio;
    @FXML private DatePicker dpDiarioFin;

    @Autowired
    private ReporteBilleteroService reporteService;

    @FXML
    public void initialize() {
        System.out.println("📊 Inicializando Reportes de Billeteros");
        
        // Configurar fechas por defecto - año actual
        LocalDate hoy = LocalDate.now();
        LocalDate inicioAnio = hoy.withDayOfYear(1);
        
        // Reporte mensual: año completo
        dpMensualInicio.setValue(inicioAnio);
        dpMensualFin.setValue(hoy);
        
        // Reporte diario: mes actual
        dpDiarioInicio.setValue(hoy.withDayOfMonth(1));
        dpDiarioFin.setValue(hoy);
    }

    // ==================== REPORTE MENSUAL ====================
    
    @FXML
    private void handleMensualExcel() {
        LocalDate inicio = dpMensualInicio.getValue();
        LocalDate fin = dpMensualFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        try {
            File archivo = seleccionarArchivo("Reporte Mensual Billeteros - Excel", "xlsx");
            if (archivo != null) {
                reporteService.exportarReporteMensualExcel(inicio, fin, archivo.getAbsolutePath());
                mostrarExito("Reporte mensual Excel generado correctamente en:\n" + archivo.getAbsolutePath());
            }
        } catch (Exception e) {
            mostrarError("No se pudo generar el reporte Excel", e);
        }
    }
    
    @FXML
    private void handleMensualPDF() {
        LocalDate inicio = dpMensualInicio.getValue();
        LocalDate fin = dpMensualFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        try {
            File archivo = seleccionarArchivo("Reporte Mensual Billeteros - PDF", "pdf");
            if (archivo != null) {
                reporteService.exportarReporteMensualPDF(inicio, fin, archivo.getAbsolutePath());
                mostrarExito("Reporte mensual PDF generado correctamente en:\n" + archivo.getAbsolutePath());
            }
        } catch (Exception e) {
            mostrarError("No se pudo generar el reporte PDF", e);
        }
    }

    // ==================== REPORTE DIARIO ====================
    
    @FXML
    private void handleDiarioExcel() {
        LocalDate inicio = dpDiarioInicio.getValue();
        LocalDate fin = dpDiarioFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        try {
            File archivo = seleccionarArchivo("Reporte Diario Billeteros - Excel", "xlsx");
            if (archivo != null) {
                reporteService.exportarReporteDiarioExcel(inicio, fin, archivo.getAbsolutePath());
                mostrarExito("Reporte diario Excel generado correctamente en:\n" + archivo.getAbsolutePath());
            }
        } catch (Exception e) {
            mostrarError("No se pudo generar el reporte Excel", e);
        }
    }
    
    @FXML
    private void handleDiarioPDF() {
        LocalDate inicio = dpDiarioInicio.getValue();
        LocalDate fin = dpDiarioFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        try {
            File archivo = seleccionarArchivo("Reporte Diario Billeteros - PDF", "pdf");
            if (archivo != null) {
                reporteService.exportarReporteDiarioPDF(inicio, fin, archivo.getAbsolutePath());
                mostrarExito("Reporte diario PDF generado correctamente en:\n" + archivo.getAbsolutePath());
            }
        } catch (Exception e) {
            mostrarError("No se pudo generar el reporte PDF", e);
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================
    
    private boolean validarFechas(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            mostrarAdvertencia("Seleccione ambas fechas (inicio y fin)");
            return false;
        }

        if (inicio.isAfter(fin)) {
            mostrarAdvertencia("La fecha de inicio debe ser anterior o igual a la fecha fin");
            return false;
        }
        
        return true;
    }
    
    private File seleccionarArchivo(String titulo, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(titulo);
        
        String nombreArchivo = "reporte_billeteros_" + 
                              LocalDate.now().toString().replace("-", "") + 
                              "." + extension;
        fileChooser.setInitialFileName(nombreArchivo);
        
        String descripcion = extension.equals("xlsx") ? "Excel Files (*.xlsx)" : "PDF Files (*.pdf)";
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(descripcion, "*." + extension)
        );

        return fileChooser.showSaveDialog(null);
    }

    private void mostrarExito(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Éxito");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void mostrarAdvertencia(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validación");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
    
    private void mostrarError(String mensaje, Exception e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(mensaje);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}