package com.misgastos.controller;

import com.misgastos.service.ReporteBilleteroService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class ReportesBilleterosController {

    @FXML private DatePicker dpMensualInicio;
    @FXML private DatePicker dpMensualFin;
    @FXML private DatePicker dpDiarioInicio;
    @FXML private DatePicker dpDiarioFin;

    // Tabla de vista previa
    @FXML private TableView<ReporteRow> tblVistaPrevia;
    @FXML private TableColumn<ReporteRow, String> colPeriodo;
    @FXML private TableColumn<ReporteRow, String> colBilletero;
    @FXML private TableColumn<ReporteRow, String> colPremios;
    @FXML private TableColumn<ReporteRow, String> colDiferencia;
    @FXML private TableColumn<ReporteRow, String> colGastosLocal;
    @FXML private TableColumn<ReporteRow, String> colPagosManuel;
    @FXML private TableColumn<ReporteRow, String> colInversion;
    @FXML private TableColumn<ReporteRow, String> colGastoTotal;
    @FXML private TableColumn<ReporteRow, String> colUtilidadNeta;

    // Labels
    @FXML private Label lblRangoFechas;
    @FXML private Label lblTotalBilletero;
    @FXML private Label lblTotalPremios;
    @FXML private Label lblUtilidadTotal;

    @Autowired
    private ReporteBilleteroService reporteService;

    private NumberFormat formatoMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    @FXML
    public void initialize() {
        System.out.println("📊 Inicializando Reportes de Billeteros");
        
        // Configurar fechas por defecto
        LocalDate hoy = LocalDate.now();
        LocalDate inicioAnio = hoy.withDayOfYear(1);
        
        dpMensualInicio.setValue(inicioAnio);
        dpMensualFin.setValue(hoy);
        
        dpDiarioInicio.setValue(hoy.withDayOfMonth(1));
        dpDiarioFin.setValue(hoy);

        configurarTabla();
        limpiarTotales();
    }

    private void configurarTabla() {
        colPeriodo.setCellValueFactory(new PropertyValueFactory<>("periodo"));
        colBilletero.setCellValueFactory(new PropertyValueFactory<>("billetero"));
        colPremios.setCellValueFactory(new PropertyValueFactory<>("premios"));
        colDiferencia.setCellValueFactory(new PropertyValueFactory<>("diferencia"));
        colGastosLocal.setCellValueFactory(new PropertyValueFactory<>("gastosLocal"));
        colPagosManuel.setCellValueFactory(new PropertyValueFactory<>("pagosManuel"));
        colInversion.setCellValueFactory(new PropertyValueFactory<>("inversion"));
        colGastoTotal.setCellValueFactory(new PropertyValueFactory<>("gastoTotal"));
        colUtilidadNeta.setCellValueFactory(new PropertyValueFactory<>("utilidadNeta"));

        // Alineación a la derecha para columnas numéricas
        colBilletero.setStyle("-fx-alignment: CENTER-RIGHT;");
        colPremios.setStyle("-fx-alignment: CENTER-RIGHT;");
        colDiferencia.setStyle("-fx-alignment: CENTER-RIGHT;");
        colGastosLocal.setStyle("-fx-alignment: CENTER-RIGHT;");
        colPagosManuel.setStyle("-fx-alignment: CENTER-RIGHT;");
        colInversion.setStyle("-fx-alignment: CENTER-RIGHT;");
        colGastoTotal.setStyle("-fx-alignment: CENTER-RIGHT;");
        colUtilidadNeta.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    // ==================== VISTA PREVIA ====================

    @FXML
    private void handleVistaPreviaMensual() {
        LocalDate inicio = dpMensualInicio.getValue();
        LocalDate fin = dpMensualFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        // Aquí deberías cargar datos reales desde el servicio
        // Por ahora usamos datos de ejemplo
        ObservableList<ReporteRow> datos = generarDatosEjemploMensual(inicio, fin);
        tblVistaPrevia.setItems(datos);
        actualizarTotales(datos);
        
        lblRangoFechas.setText(String.format("Reporte Mensual: %s - %s", 
            inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
    }

    @FXML
    private void handleVistaPreviaDiario() {
        LocalDate inicio = dpDiarioInicio.getValue();
        LocalDate fin = dpDiarioFin.getValue();

        if (!validarFechas(inicio, fin)) {
            return;
        }

        // Aquí deberías cargar datos reales desde el servicio
        // Por ahora usamos datos de ejemplo
        ObservableList<ReporteRow> datos = generarDatosEjemploDiario(inicio, fin);
        tblVistaPrevia.setItems(datos);
        actualizarTotales(datos);
        
        lblRangoFechas.setText(String.format("Reporte Diario: %s - %s", 
            inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
    }

    @FXML
    private void handleLimpiarVista() {
        tblVistaPrevia.getItems().clear();
        limpiarTotales();
        lblRangoFechas.setText("Seleccione fechas y presione 'Vista Previa' para generar el reporte");
    }

    private ObservableList<ReporteRow> generarDatosEjemploMensual(LocalDate inicio, LocalDate fin) {
        ObservableList<ReporteRow> datos = FXCollections.observableArrayList();
        
        // Generar datos de ejemplo por mes
        LocalDate fecha = inicio.withDayOfMonth(1);
        while (!fecha.isAfter(fin)) {
            String periodo = fecha.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es")));
            datos.add(new ReporteRow(
                periodo,
                15000000 + (Math.random() * 5000000),
                8000000 + (Math.random() * 3000000),
                7000000 + (Math.random() * 2000000),
                1200000 + (Math.random() * 300000),
                500000 + (Math.random() * 200000),
                800000 + (Math.random() * 200000),
                2500000 + (Math.random() * 500000),
                4500000 + (Math.random() * 1000000)
            ));
            fecha = fecha.plusMonths(1);
        }
        
        return datos;
    }

    private ObservableList<ReporteRow> generarDatosEjemploDiario(LocalDate inicio, LocalDate fin) {
        ObservableList<ReporteRow> datos = FXCollections.observableArrayList();
        
        // Generar datos de ejemplo por día
        LocalDate fecha = inicio;
        while (!fecha.isAfter(fin)) {
            String periodo = fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            datos.add(new ReporteRow(
                periodo,
                500000 + (Math.random() * 200000),
                250000 + (Math.random() * 100000),
                250000 + (Math.random() * 100000),
                40000 + (Math.random() * 10000),
                15000 + (Math.random() * 5000),
                25000 + (Math.random() * 10000),
                80000 + (Math.random() * 20000),
                170000 + (Math.random() * 50000)
            ));
            fecha = fecha.plusDays(1);
        }
        
        return datos;
    }

    private void actualizarTotales(ObservableList<ReporteRow> datos) {
        if (datos.isEmpty()) {
            limpiarTotales();
            return;
        }

        double totalBill = datos.stream().mapToDouble(r -> r.getBilleteroNum()).sum();
        double totalPrem = datos.stream().mapToDouble(r -> r.getPremiosNum()).sum();
        double totalUtil = datos.stream().mapToDouble(r -> r.getUtilidadNetaNum()).sum();

        lblTotalBilletero.setText(formatoMoneda.format(totalBill));
        lblTotalPremios.setText(formatoMoneda.format(totalPrem));
        lblUtilidadTotal.setText(formatoMoneda.format(totalUtil));
    }

    private void limpiarTotales() {
        lblTotalBilletero.setText("$0.00");
        lblTotalPremios.setText("$0.00");
        lblUtilidadTotal.setText("$0.00");
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

    // ==================== CLASE INTERNA PARA LA TABLA ====================
    
    public static class ReporteRow {
        private final SimpleStringProperty periodo;
        private final SimpleStringProperty billetero;
        private final SimpleStringProperty premios;
        private final SimpleStringProperty diferencia;
        private final SimpleStringProperty gastosLocal;
        private final SimpleStringProperty pagosManuel;
        private final SimpleStringProperty inversion;
        private final SimpleStringProperty gastoTotal;
        private final SimpleStringProperty utilidadNeta;

        private double billeteroNum;
        private double premiosNum;
        private double utilidadNetaNum;

        private static final NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

        public ReporteRow(String periodo, double billetero, double premios, double diferencia,
                         double gastosLocal, double pagosManuel, double inversion,
                         double gastoTotal, double utilidadNeta) {
            this.periodo = new SimpleStringProperty(periodo);
            this.billetero = new SimpleStringProperty(formato.format(billetero));
            this.premios = new SimpleStringProperty(formato.format(premios));
            this.diferencia = new SimpleStringProperty(formato.format(diferencia));
            this.gastosLocal = new SimpleStringProperty(formato.format(gastosLocal));
            this.pagosManuel = new SimpleStringProperty(formato.format(pagosManuel));
            this.inversion = new SimpleStringProperty(formato.format(inversion));
            this.gastoTotal = new SimpleStringProperty(formato.format(gastoTotal));
            this.utilidadNeta = new SimpleStringProperty(formato.format(utilidadNeta));

            this.billeteroNum = billetero;
            this.premiosNum = premios;
            this.utilidadNetaNum = utilidadNeta;
        }

        // Getters para la tabla
        public String getPeriodo() { return periodo.get(); }
        public String getBilletero() { return billetero.get(); }
        public String getPremios() { return premios.get(); }
        public String getDiferencia() { return diferencia.get(); }
        public String getGastosLocal() { return gastosLocal.get(); }
        public String getPagosManuel() { return pagosManuel.get(); }
        public String getInversion() { return inversion.get(); }
        public String getGastoTotal() { return gastoTotal.get(); }
        public String getUtilidadNeta() { return utilidadNeta.get(); }

        // Getters numéricos para cálculos
        public double getBilleteroNum() { return billeteroNum; }
        public double getPremiosNum() { return premiosNum; }
        public double getUtilidadNetaNum() { return utilidadNetaNum; }
    }
}