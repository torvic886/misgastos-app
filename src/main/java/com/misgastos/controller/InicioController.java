package com.misgastos.controller;

import com.misgastos.service.GastoService;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

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
    @FXML private VBox vboxLeyendaCategorias;
    
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
            // ✅ AGREGAR NEGRITA AL VALOR
            lblTotal.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
            
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
        
        // ✅ AGREGAR NUMERACIÓN
        int posicion = 1;
        
        for (Map.Entry<String, BigDecimal> entry : categorias.entrySet()) {
            HBox itemBox = new HBox(10);
            itemBox.getStyleClass().add("panel-item");
            itemBox.setStyle("-fx-alignment: center-left;");
            
            // ✅ AGREGAR NÚMERO DE POSICIÓN
            Label lblPosicion = new Label(posicion + ".");
            lblPosicion.setStyle("-fx-font-weight: bold; -fx-text-fill: #9ca3af; -fx-min-width: 25;");
            
            Label lblCategoria = new Label(entry.getKey());
            lblCategoria.getStyleClass().add("item-label");
            lblCategoria.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lblCategoria, javafx.scene.layout.Priority.ALWAYS);
            
            Label lblTotal = new Label(formatearMonto(entry.getValue()));
            lblTotal.getStyleClass().add("item-value");
            // ✅ AGREGAR NEGRITA AL VALOR
            lblTotal.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
            
            // ✅ AGREGAR POSICIÓN AL INICIO
            itemBox.getChildren().addAll(lblPosicion, lblCategoria, lblTotal);
            vboxCategorias.getChildren().add(itemBox);
            
            posicion++;
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

        Platform.runLater(() -> {
            for (XYChart.Series<String, Number> serie : chartUltimosDias.getData()) {
                for (XYChart.Data<String, Number> data : serie.getData()) {

                    double valor = data.getYValue().doubleValue();
                    if (valor <= 0) continue;

                    Node node = data.getNode();
                    if (node instanceof StackPane bar) {

                        // Etiqueta compacta arriba de la barra con mejor posicionamiento
                        Label label = new Label(
                            formatearMonto(BigDecimal.valueOf(valor))
                        );
                        label.setStyle(
                            "-fx-font-size: 10px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-text-fill: #334155; " +
                            "-fx-background-color: rgba(255, 255, 255, 0.95); " +
                            "-fx-background-radius: 4; " +
                            "-fx-padding: 3 8;"
                        );
                        label.setMouseTransparent(true);
                        StackPane.setAlignment(label, Pos.TOP_CENTER);
                        // Ajustar margen para que no tape la barra
                        StackPane.setMargin(label, new Insets(-22, 0, 0, 0));
                        bar.getChildren().add(label);

                        // Tooltip con fecha + valor completo
                        String textoTooltip =
                                data.getXValue() + "\n" +
                                formatearMontoCompleto(BigDecimal.valueOf(valor));

                        Tooltip tooltip = new Tooltip(textoTooltip);
                        tooltip.setStyle("""
                            -fx-font-size: 12px;
                            -fx-font-weight: bold;
                        """);

                        Tooltip.install(bar, tooltip);
                    }
                }
            }
        });
    }

    private String formatearMontoCompleto(BigDecimal monto) {
        return String.format("%,.0f", monto.doubleValue());
    }

    
    private void cargarGraficoCategorias() {
        chartCategorias.getData().clear();
        if (vboxLeyendaCategorias != null) {
            vboxLeyendaCategorias.getChildren().clear();
        }

        Map<String, BigDecimal> categorias = gastoService.obtenerGastosPorCategoria();

        BigDecimal totalGeneral = categorias.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Colores modernos y vibrantes para cada categoría
        String[] colores = {
            "#e91e63",  // Rosa/magenta vibrante
            "#9c27b0",  // Púrpura
            "#3f51b5",  // Azul índigo
            "#00bcd4",  // Cian
            "#4caf50",  // Verde
            "#ff9800",  // Naranja
            "#f44336"   // Rojo
        };
        
        // Ordenar categorías por monto descendente
        List<Map.Entry<String, BigDecimal>> categoriasOrdenadas = categorias.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());
        
        int colorIndex = 0;

        for (Map.Entry<String, BigDecimal> entry : categoriasOrdenadas) {

            PieChart.Data slice = new PieChart.Data(
                    entry.getKey(),
                    entry.getValue().doubleValue()
            );

            chartCategorias.getData().add(slice);

            // Aplicar color personalizado a cada slice
            final int currentIndex = colorIndex;
            final String colorActual = colores[colorIndex % colores.length];
            
            Platform.runLater(() -> {
                slice.getNode().setStyle(
                    "-fx-pie-color: " + colorActual + ";"
                );
            });

            // Tooltip con valor + porcentaje (sin símbolo $)
            double porcentaje = totalGeneral.compareTo(BigDecimal.ZERO) == 0
                    ? 0
                    : entry.getValue()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalGeneral, 1, RoundingMode.HALF_UP)
                            .doubleValue();

            Tooltip tooltip = new Tooltip(
                    entry.getKey() + "\n" +
                    formatearMontoCompleto(entry.getValue()) + "\n" +
                    String.format("%.1f%%", porcentaje)
            );

            Tooltip.install(slice.getNode(), tooltip);
            
            // Crear leyenda personalizada lateral para las primeras 5 categorías
            if (colorIndex < 5 && vboxLeyendaCategorias != null) {
                HBox leyendaItem = new HBox(8);
                leyendaItem.setAlignment(Pos.CENTER_LEFT);
                
                // Cuadro de color más grande para mejor visibilidad
                Rectangle colorBox = new Rectangle(12, 12);
                colorBox.setFill(javafx.scene.paint.Color.web(colorActual));
                colorBox.setArcWidth(3);
                colorBox.setArcHeight(3);
                
                // VBox para nombre y porcentaje
                VBox infoBox = new VBox(2);
                
                // Nombre de categoría
                Label lblNombre = new Label(entry.getKey());
                lblNombre.setStyle("-fx-font-size: 11px; -fx-text-fill: #334155; -fx-font-weight: 600;");
                lblNombre.setMaxWidth(130);
                lblNombre.setWrapText(false);
                
                // Porcentaje con monto
                Label lblDetalle = new Label(String.format("%.1f%% - %s", porcentaje, formatearMonto(entry.getValue())));
                lblDetalle.setStyle("-fx-font-size: 9px; -fx-text-fill: #64748b; -fx-font-weight: 500;");
                
                infoBox.getChildren().addAll(lblNombre, lblDetalle);
                
                leyendaItem.getChildren().addAll(colorBox, infoBox);
                vboxLeyendaCategorias.getChildren().add(leyendaItem);
            }
            
            colorIndex++;
        }
        
        // Si hay más de 5 categorías, agregar indicador "Otras"
        if (categoriasOrdenadas.size() > 5 && vboxLeyendaCategorias != null) {
            HBox otrasBox = new HBox(8);
            otrasBox.setAlignment(Pos.CENTER_LEFT);
            
            Rectangle colorBox = new Rectangle(12, 12);
            colorBox.setFill(javafx.scene.paint.Color.web("#e2e8f0"));
            colorBox.setArcWidth(3);
            colorBox.setArcHeight(3);
            
            Label lblOtras = new Label("... y " + (categoriasOrdenadas.size() - 5) + " mas");
            lblOtras.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-font-style: italic;");
            
            otrasBox.getChildren().addAll(colorBox, lblOtras);
            vboxLeyendaCategorias.getChildren().add(otrasBox);
        }
    }


    
    /**
     * Formatea montos grandes de forma compacta
     * Ejemplos: 
     * - 1,234.56 -> 1.2K
     * - 1,234,567.89 -> 1.2M
     * - 999.99 -> 1.0K
     */
    private String formatearMonto(BigDecimal monto) {
        double valor = monto.doubleValue();
        
        if (valor >= 1_000_000) {
            return String.format("%.1fM", valor / 1_000_000);
        } else if (valor >= 1_000) {
            return String.format("%.1fK", valor / 1_000);
        } else {
            return String.format("%.0f", valor);
        }
    }
}