package com.misgastos.controller;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.GastoService;
import com.misgastos.service.InformeService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class InformesController {

    @FXML private ComboBox<String> cmbTipoInforme;
    @FXML private DatePicker dpFechaInicio;
    @FXML private DatePicker dpFechaFin;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private TextField txtProducto;
    @FXML private TextArea txtPreview;
    @FXML private Label lblTotalGastos;
    @FXML private Label lblMontoTotal;
    @FXML private Label lblCategorias;

    @Autowired private GastoService gastoService;
    @Autowired private CategoriaService categoriaService;
    @Autowired private InformeService informeService;

    private String informeActual = "";

    @FXML
    public void initialize() {
        configurarTiposInforme();
        configurarFechasPorDefecto();
        cargarCategorias();
        configurarListeners();
        
        txtPreview.setText("📊 Selecciona el tipo de informe, configura las fechas y presiona 'Generar Vista Previa'\n\n" +
                          "El informe aparecerá aquí antes de exportarlo a PDF o Excel.");
        
        System.out.println("✅ Módulo de Informes cargado correctamente");
    }

    private void configurarTiposInforme() {
        cmbTipoInforme.getItems().addAll(
            "📊 Informe General Mensual",
            "📂 Informe por Categoría",
            "📦 Informe por Producto",
            "⚖️ Informe Comparativo",
            "🎯 Dashboard Ejecutivo",
            "📋 Desglose por Subcategorías",
            "📅 Informe Anual"
        );
        cmbTipoInforme.getSelectionModel().selectFirst();
    }

    private void configurarFechasPorDefecto() {
        LocalDate hoy = LocalDate.now();
        dpFechaInicio.setValue(hoy.withDayOfMonth(1));
        dpFechaFin.setValue(hoy);
    }

    private void cargarCategorias() {
        List<Categoria> categorias = categoriaService.listarCategorias();
        cmbCategoria.getItems().clear();
        cmbCategoria.getItems().addAll(categorias);
        cmbCategoria.setPromptText("Todas las categorías");
    }

    private void configurarListeners() {
        // Mostrar/ocultar filtros según el tipo de informe
        cmbTipoInforme.setOnAction(e -> {
            String tipo = cmbTipoInforme.getValue();
            
            // Resetear visibilidad
            cmbCategoria.setVisible(false);
            cmbCategoria.setManaged(false);
            txtProducto.setVisible(false);
            txtProducto.setManaged(false);
            
            if (tipo != null) {
                if (tipo.contains("Categoría")) {
                    cmbCategoria.setVisible(true);
                    cmbCategoria.setManaged(true);
                } else if (tipo.contains("Producto")) {
                    txtProducto.setVisible(true);
                    txtProducto.setManaged(true);
                }
            }
        });
    }

    @FXML
    public void handleGenerarVista() {
        if (!validarFechas()) return;

        String tipoInforme = cmbTipoInforme.getValue();
        LocalDate inicio = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();
        
        System.out.println("✅ Fecha Inicio: " + inicio);
        System.out.println("✅ Fecha fin: " + fin);

        try {
            String informe = "";

            if (tipoInforme.contains("General Mensual")) {
                informe = informeService.generarInformeGeneralMensual(inicio, fin);
                System.out.println("✅ Fecha Inicio InformeGeneralMensual: " + inicio);
                System.out.println("✅ Fecha fin InformeGeneralMensual: " + fin);
            }
            else if (tipoInforme.contains("Categoría")) {
                Categoria categoria = cmbCategoria.getValue();
                if (categoria == null) {
                    mostrarAlerta("Validación", "Por favor seleccione una categoría", Alert.AlertType.WARNING);
                    return;
                }
                informe = informeService.generarInformePorCategoria(categoria.getNombre(), inicio, fin);
                System.out.println("✅ Fecha Inicio InformePorCategoria: " + inicio);
                System.out.println("✅ Fecha fin InformePorCategoria: " + fin);
            }
            else if (tipoInforme.contains("Producto")) {
                String producto = txtProducto.getText();
                if (producto == null || producto.trim().isEmpty()) {
                    mostrarAlerta("Validación", "Por favor ingrese el nombre del producto", Alert.AlertType.WARNING);
                    return;
                }
                
                System.out.println("✅ Fecha Inicio InformePorProducto: " + inicio);
                System.out.println("✅ Fecha fin InformePorProducto: " + fin);
                
                informe = informeService.generarInformePorProducto(producto.trim(), inicio, fin);
            }
            else if (tipoInforme.contains("Comparativo")) {
            	
                System.out.println("✅ Fecha Inicio InformeComparativo: " + inicio);
                System.out.println("✅ Fecha fin InformeComparativo: " + fin);
            	
                informe = informeService.generarInformeComparativo(inicio, fin);
            }
            else if (tipoInforme.contains("Dashboard")) {
            	
                System.out.println("✅ Fecha Inicio DashboardEjecutivo: " + inicio);
                System.out.println("✅ Fecha fin DashboardEjecutivo: " + fin);
            	
                informe = informeService.generarDashboardEjecutivo(inicio, fin);
            }
            else if (tipoInforme.contains("Subcategorías")) {
            	
                System.out.println("✅ Fecha Inicio InformeSubcategorias: " + inicio);
                System.out.println("✅ Fecha fin InformeSubcategorias: " + fin);
            	
                informe = informeService.generarInformeSubcategorias(inicio, fin);
            }
            else if (tipoInforme.contains("Anual")) {
                int anio = inicio.getYear();
                
                System.out.println("✅ Fecha Inicio InformeAnual: " + inicio);
                System.out.println("✅ Fecha fin InformeAnual: " + fin);
                
                informe = informeService.generarInformeAnual(anio);
            }

            informeActual = informe;
            txtPreview.setText(informe);
            
            // Actualizar estadísticas
            actualizarEstadisticas(inicio, fin);
            
            System.out.println("✅ Informe generado: " + tipoInforme);

        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo generar el informe:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void actualizarEstadisticas(LocalDate inicio, LocalDate fin) {
        try {
            List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);
            
            // Filtrar si hay categoría seleccionada
            Categoria categoria = cmbCategoria.getValue();
            if (categoria != null && cmbCategoria.isVisible()) {
                Long catId = categoria.getId();
                gastos = gastos.stream()
                    .filter(g -> g.getCategoria().getId().equals(catId))
                    .toList();
            }
            
            // Filtrar si hay producto ingresado
            String producto = txtProducto.getText();
            if (producto != null && !producto.trim().isEmpty() && txtProducto.isVisible()) {
                String prod = producto.toLowerCase();
                gastos = gastos.stream()
                    .filter(g -> g.getProducto().toLowerCase().contains(prod))
                    .toList();
            }
            
            // Actualizar labels
            lblTotalGastos.setText(String.valueOf(gastos.size()));
            
            BigDecimal total = gastos.stream()
                .map(Gasto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblMontoTotal.setText("$" + String.format("%,.2f", total));
            
            long categorias = gastos.stream()
                .map(g -> g.getCategoria().getId())
                .distinct()
                .count();
            lblCategorias.setText(String.valueOf(categorias));
            
        } catch (Exception e) {
            System.err.println("Error al actualizar estadísticas: " + e.getMessage());
        }
    }

    @FXML
    public void handleExportarPDF() {
        if (informeActual.isEmpty()) {
            mostrarAlerta("Advertencia", "Primero genere un informe", Alert.AlertType.WARNING);
            return;
        }

        if (!validarFechas()) return;

        try {
            File archivo = seleccionarArchivo("Exportar Informe - PDF", "pdf");
            if (archivo != null) {
                String tipoInforme = cmbTipoInforme.getValue();
                LocalDate inicio = dpFechaInicio.getValue();
                LocalDate fin = dpFechaFin.getValue();

                if (tipoInforme.contains("General Mensual")) {
                    informeService.exportarInformeGeneralPDF(inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Categoría")) {
                    Categoria categoria = cmbCategoria.getValue();
                    if (categoria == null) {
                        mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
                        return;
                    }
                    informeService.exportarInformeCategoriaPDF(categoria.getNombre(), inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Producto")) {
                    String producto = txtProducto.getText();
                    if (producto == null || producto.trim().isEmpty()) {
                        mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
                        return;
                    }
                    informeService.exportarInformeProductoPDF(producto.trim(), inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Dashboard")) {
                    informeService.exportarDashboardPDF(inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Comparativo")) {
                	informeService.exportarComparativoPDF(inicio, fin, archivo.getAbsolutePath());
                } 
                else if (tipoInforme.contains("Subcategorías")) {
                	informeService.exportarSubCategoriasPDF(inicio, fin, archivo.getAbsolutePath());
                }                  
                else {
                    // Para otros informes, usar exportación genérica de texto
                	int anio = inicio.getYear();
                	informeService.exportarAnualPDF(anio, archivo.getAbsolutePath());
                }

                mostrarAlerta("Éxito", "Informe PDF exportado correctamente a:\n" + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el PDF:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleExportarExcel() {
        if (informeActual.isEmpty()) {
            mostrarAlerta("Advertencia", "Primero genere un informe", Alert.AlertType.WARNING);
            return;
        }

        if (!validarFechas()) return;

        try {
            File archivo = seleccionarArchivo("Exportar Informe - Excel", "xlsx");
            if (archivo != null) {
                String tipoInforme = cmbTipoInforme.getValue();
                LocalDate inicio = dpFechaInicio.getValue();
                LocalDate fin = dpFechaFin.getValue();

                if (tipoInforme.contains("General Mensual")) {
                    informeService.exportarInformeGeneralExcel(inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Categoría")) {
                    Categoria categoria = cmbCategoria.getValue();
                    if (categoria == null) {
                        mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
                        return;
                    }
                    informeService.exportarInformeCategoriaExcel(categoria.getNombre(), inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Producto")) {
                    String producto = txtProducto.getText();
                    if (producto == null || producto.trim().isEmpty()) {
                        mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
                        return;
                    }
                    informeService.exportarInformeProductoExcel(producto.trim(), inicio, fin, archivo.getAbsolutePath());
                }
                else if (tipoInforme.contains("Dashboard")) {
                    informeService.exportarDashboardExcel(inicio, fin, archivo.getAbsolutePath());
                }
                else {
                    // Para otros informes, usar exportación genérica
                    informeService.exportarInformeGeneralExcel(inicio, fin, archivo.getAbsolutePath());
                }

                mostrarAlerta("Éxito", "Informe Excel exportado correctamente a:\n" + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo exportar el Excel:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleLimpiar() {
        configurarFechasPorDefecto();
        cmbCategoria.setValue(null);
        txtProducto.clear();
        informeActual = "";
        
        txtPreview.setText("📊 Selecciona el tipo de informe, configura las fechas y presiona 'Generar Vista Previa'\n\n" +
                          "El informe aparecerá aquí antes de exportarlo a PDF o Excel.");
        
        lblTotalGastos.setText("0");
        lblMontoTotal.setText("0.00");
        lblCategorias.setText("0");
        
        cmbTipoInforme.getSelectionModel().selectFirst();
    }

    private boolean validarFechas() {
        LocalDate inicio = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();

        if (inicio == null || fin == null) {
            mostrarAlerta("Validación", "Por favor seleccione ambas fechas", Alert.AlertType.WARNING);
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
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        fileChooser.setInitialFileName("informe_" + timestamp + "." + extension);

        String descripcion = extension.equals("xlsx") ? "Excel Files" : "PDF Files";
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(descripcion, "*." + extension)
        );

        return fileChooser.showSaveDialog(txtPreview.getScene().getWindow());
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}