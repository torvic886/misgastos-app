package com.misgastos.controller;

import com.misgastos.service.GastoService;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Component
public class InicioController {
    
    @FXML private Label lblTotalHoy;
    @FXML private Label lblTotalSemana;
    @FXML private Label lblTotalMes;
    @FXML private Label lblTotalAnio;
    
    @FXML private Label lblCambioHoy;
    @FXML private Label lblCambioSemana;
    @FXML private Label lblCambioMes;
    @FXML private Label lblCambioAnio;
    
    @FXML private HBox hboxCambioHoy;
    @FXML private HBox hboxCambioSemana;
    @FXML private HBox hboxCambioMes;
    @FXML private HBox hboxCambioAnio;
    
    @FXML private VBox vboxTopProductos;
    @FXML private VBox vboxCategorias;
    
    @FXML private BarChart<String, Number> chartUltimosDias;
    @FXML private PieChart chartCategorias;
    
    @Autowired
    private GastoService gastoService;
    
    @FXML
    public void initialize() {
        cargarIndicadores();
        cargarTopProductos();
        cargarCategoriasResumen();
        cargarGraficoUltimosDias();
        cargarGraficoCategorias();
    }
    
    private void cargarIndicadores() {
        LocalDate hoy = LocalDate.now();
        
        // Hoy
        BigDecimal totalHoy = gastoService.calcularTotalPorPeriodo(hoy, hoy);
        lblTotalHoy.setText(formatearMonto(totalHoy));
        
        LocalDate ayer = hoy.minusDays(1);
        BigDecimal totalAyer = gastoService.calcularTotalPorPeriodo(ayer, ayer);
        actualizarCambio(lblCambioHoy, hboxCambioHoy, totalHoy, totalAyer);
        
        // Semana (lunes a domingo)
        LocalDate inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate finSemana = hoy.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        BigDecimal totalSemana = gastoService.calcularTotalPorPeriodo(inicioSemana, finSemana);
        lblTotalSemana.setText(formatearMonto(totalSemana));
        
        LocalDate inicioSemanaAnterior = inicioSemana.minusWeeks(1);
        LocalDate finSemanaAnterior = finSemana.minusWeeks(1);
        BigDecimal totalSemanaAnterior = gastoService.calcularTotalPorPeriodo(inicioSemanaAnterior, finSemanaAnterior);
        actualizarCambio(lblCambioSemana, hboxCambioSemana, totalSemana, totalSemanaAnterior);
        
        // Mes
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate finMes = hoy.with(TemporalAdjusters.lastDayOfMonth());
        BigDecimal totalMes = gastoService.calcularTotalPorPeriodo(inicioMes, finMes);
        lblTotalMes.setText(formatearMonto(totalMes));
        
        LocalDate inicioMesAnterior = inicioMes.minusMonths(1);
        LocalDate finMesAnterior = inicioMes.minusDays(1);
        BigDecimal totalMesAnterior = gastoService.calcularTotalPorPeriodo(inicioMesAnterior, finMesAnterior);
        actualizarCambio(lblCambioMes, hboxCambioMes, totalMes, totalMesAnterior);
        
        // Año
        LocalDate inicioAnio = hoy.withDayOfYear(1);
        LocalDate finAnio = hoy.with(TemporalAdjusters.lastDayOfYear());
        BigDecimal totalAnio = gastoService.calcularTotalPorPeriodo(inicioAnio, finAnio);
        lblTotalAnio.setText(formatearMonto(totalAnio));
        
        LocalDate inicioAnioAnterior = inicioAnio.minusYears(1);
        LocalDate finAnioAnterior = finAnio.minusYears(1);
        BigDecimal totalAnioAnterior = gastoService.calcularTotalPorPeriodo(inicioAnioAnterior, finAnioAnterior);
        actualizarCambio(lblCambioAnio, hboxCambioAnio, totalAnio, totalAnioAnterior);
    }
    
    private void actualizarCambio(Label label, HBox container, BigDecimal actual, BigDecimal anterior) {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            label.setText("Sin datos previos");
            container.getStyleClass().removeAll("stat-change-positive", "stat-change-negative");
            container.getStyleClass().add("stat-change-neutral");
            return;
        }
        
        BigDecimal diferencia = actual.subtract(anterior);
        BigDecimal porcentaje = diferencia.divide(anterior, 4, RoundingMode.HALF_UP)
                                         .multiply(BigDecimal.valueOf(100));
        
        String simbolo = diferencia.compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓";
        String texto = String.format("%s %.1f%%", simbolo, Math.abs(porcentaje.doubleValue()));
        label.setText(texto);
        
        container.getStyleClass().removeAll("stat-change-positive", "stat-change-negative", "stat-change-neutral");
        if (diferencia.compareTo(BigDecimal.ZERO) > 0) {
            container.getStyleClass().add("stat-change-negative"); // Más gasto = negativo
        } else if (diferencia.compareTo(BigDecimal.ZERO) < 0) {
            container.getStyleClass().add("stat-change-positive"); // Menos gasto = positivo
        } else {
            container.getStyleClass().add("stat-change-neutral");
        }
    }
    
    private void cargarTopProductos() {
        List<Map<String, Object>> topProductos = gastoService.obtenerTopProductos(5);
        vboxTopProductos.getChildren().clear();
        
        int posicion = 1;
        for (Map<String, Object> item : topProductos) {
            String producto = (String) item.get("producto");
            BigDecimal total = (BigDecimal) item.get("total");
            
            HBox itemBox = new HBox(10);
            itemBox.getStyleClass().add("panel-item");
            itemBox.setStyle("-fx-alignment: center-left;");
            
            Label lblPosicion = new Label(posicion + ".");
            lblPosicion.setStyle("-fx-font-weight: bold; -fx-text-fill: #9ca3af; -fx-min-width: 25;");
            
            Label lblProducto = new Label(producto);
            lblProducto.getStyleClass().add("item-label");
            lblProducto.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lblProducto, javafx.scene.layout.Priority.ALWAYS);
            
            Label lblTotal = new Label(formatearMonto(total));
            lblTotal.getStyleClass().add("item-value");
            
            itemBox.getChildren().addAll(lblPosicion, lblProducto, lblTotal);
            vboxTopProductos.getChildren().add(itemBox);
            
            posicion++;
        }
        
        if (topProductos.isEmpty()) {
            Label lblVacio = new Label("No hay productos registrados");
            lblVacio.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic;");
            vboxTopProductos.getChildren().add(lblVacio);
        }
    }
    
    private void cargarCategoriasResumen() {
        Map<String, BigDecimal> categorias = gastoService.obtenerGastosPorCategoria();
        vboxCategorias.getChildren().clear();
        
        for (Map.Entry<String, BigDecimal> entry : categorias.entrySet()) {
            HBox itemBox = new HBox(10);
            itemBox.getStyleClass().add("panel-item");
            itemBox.setStyle("-fx-alignment: center-left;");
            
            Label lblCategoria = new Label(entry.getKey());
            lblCategoria.getStyleClass().add("item-label");
            lblCategoria.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lblCategoria, javafx.scene.layout.Priority.ALWAYS);
            
            Label lblTotal = new Label(formatearMonto(entry.getValue()));
            lblTotal.getStyleClass().add("item-value");
            
            itemBox.getChildren().addAll(lblCategoria, lblTotal);
            vboxCategorias.getChildren().add(itemBox);
        }
        
        if (categorias.isEmpty()) {
            Label lblVacio = new Label("No hay categorías registradas");
            lblVacio.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic;");
            vboxCategorias.getChildren().add(lblVacio);
        }
    }
    
    private void cargarGraficoUltimosDias() {
        chartUltimosDias.getData().clear();
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Gastos Diarios");
        
        LocalDate hoy = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        
        for (int i = 6; i >= 0; i--) {
            LocalDate fecha = hoy.minusDays(i);
            BigDecimal total = gastoService.calcularTotalPorPeriodo(fecha, fecha);
            
            String etiqueta = i == 0 ? "Hoy" : fecha.format(formatter);
            series.getData().add(new XYChart.Data<>(etiqueta, total.doubleValue()));
        }
        
        chartUltimosDias.getData().add(series);
        chartUltimosDias.setLegendVisible(false);
        
        // Personalizar colores
        chartUltimosDias.lookupAll(".default-color0.chart-bar")
            .forEach(node -> node.setStyle("-fx-bar-fill: linear-gradient(to top, #6f6dd9, #8b7ce8);"));
    }
    
    private void cargarGraficoCategorias() {
        chartCategorias.getData().clear();
        
        Map<String, BigDecimal> categorias = gastoService.obtenerGastosPorCategoria();
        
        // Colores personalizados para el gráfico
        String[] colores = {
            "#6f6dd9", "#8b5cf6", "#ec4899", 
            "#f59e0b", "#10b981", "#3b82f6"
        };
        
        int index = 0;
        for (Map.Entry<String, BigDecimal> entry : categorias.entrySet()) {
            PieChart.Data slice = new PieChart.Data(
                entry.getKey() + " (" + formatearMonto(entry.getValue()) + ")", 
                entry.getValue().doubleValue()
            );
            chartCategorias.getData().add(slice);
            
            // Aplicar color personalizado
            final String color = colores[index % colores.length];
            slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            
            index++;
        }
        
        if (categorias.isEmpty()) {
            PieChart.Data slice = new PieChart.Data("Sin datos", 1);
            chartCategorias.getData().add(slice);
            slice.getNode().setStyle("-fx-pie-color: #e5e7eb;");
        }
    }
    
    /**
     * Formatea montos grandes de forma compacta
     * Ejemplos: 
     * - 1,234.56 → $1.2K
     * - 1,234,567.89 → $1.2M
     * - 999.99 → $1.0K
     */
    private String formatearMonto(BigDecimal monto) {
        double valor = monto.doubleValue();
        
        if (valor >= 1_000_000) {
            return String.format("$%.1fM", valor / 1_000_000);
        } else if (valor >= 1_000) {
            return String.format("$%.1fK", valor / 1_000);
        } else {
            return String.format("$%.0f", valor);
        }
    }
}