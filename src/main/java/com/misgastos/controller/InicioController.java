package com.misgastos.controller;

import com.misgastos.service.GastoService;
import com.misgastos.service.PresupuestoService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InicioController {

    private static final Locale CO = Locale.forLanguageTag("es-CO");

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

    @FXML private HBox panelEncabezado;
    @FXML private VBox panelPresupuestos;
    @FXML private FlowPane flowPresupuestos;
    @FXML private Label lblPresupuestoResumen;

    @FXML private Button btnVerHistorial;
    @FXML private Button btnBuscarEditar;
    private Runnable alVerHistorial;
    private Runnable alBuscarEditar;

    @FXML private BarChart<String, Number> chartUltimosDias;
    @FXML private PieChart chartCategorias;
    @FXML private LineChart<String, Number> chartTendencia;
    @FXML private NumberAxis ejeY;
    @FXML private NumberAxis ejeTendencia;
    @FXML private Label lblTotalCentro;

    @Autowired
    private GastoService gastoService;

    @Autowired
    private PresupuestoService presupuestoService;

    @FXML
    public void initialize() {
        StringConverter<Number> conversorPesos = new StringConverter<>() {
            @Override public String toString(Number n) {
                return formatearMonto(BigDecimal.valueOf(n.doubleValue()));
            }
            @Override public Number fromString(String s) { return null; }
        };
        ejeY.setTickLabelFormatter(conversorPesos);
        ejeTendencia.setTickLabelFormatter(conversorPesos);

        cargarIndicadores();
        cargarTopProductos();
        cargarCategoriasResumen();
        cargarGraficoUltimosDias();
        cargarGraficoCategorias();
        cargarTendenciaMensual();
        cargarPresupuestos();
    }

    /** Lo llama el Dashboard al cargar Inicio: decide qué botones se ven y qué hacen. */
    public void configurarAcciones(boolean verHistorial, boolean buscarEditar,
                                   Runnable alHistorial, Runnable alBuscar) {
        this.alVerHistorial = alHistorial;
        this.alBuscarEditar = alBuscar;
        if (btnVerHistorial != null) {
            btnVerHistorial.setVisible(verHistorial);
            btnVerHistorial.setManaged(verHistorial);
        }
        if (btnBuscarEditar != null) {
            btnBuscarEditar.setVisible(buscarEditar);
            btnBuscarEditar.setManaged(buscarEditar);
        }
    }

    @FXML
    private void handleVerHistorial() {
        if (alVerHistorial != null) alVerHistorial.run();
    }

    @FXML
    private void handleBuscarEditar() {
        if (alBuscarEditar != null) alBuscarEditar.run();
    }

    // ==================== TARJETAS ====================

    private void cargarIndicadores() {
        LocalDate hoy = LocalDate.now();

        BigDecimal totalHoy = gastoService.calcularTotalPorPeriodo(hoy, hoy);
        lblTotalHoy.setText(formatearMonto(totalHoy));
        LocalDate ayer = hoy.minusDays(1);
        actualizarCambio(lblCambioHoy, hboxCambioHoy, totalHoy,
                gastoService.calcularTotalPorPeriodo(ayer, ayer));

        LocalDate inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate finSemana = hoy.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        BigDecimal totalSemana = gastoService.calcularTotalPorPeriodo(inicioSemana, finSemana);
        lblTotalSemana.setText(formatearMonto(totalSemana));
        actualizarCambio(lblCambioSemana, hboxCambioSemana, totalSemana,
                gastoService.calcularTotalPorPeriodo(inicioSemana.minusWeeks(1), finSemana.minusWeeks(1)));

        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate finMes = hoy.with(TemporalAdjusters.lastDayOfMonth());
        BigDecimal totalMes = gastoService.calcularTotalPorPeriodo(inicioMes, finMes);
        lblTotalMes.setText(formatearMonto(totalMes));
        actualizarCambio(lblCambioMes, hboxCambioMes, totalMes,
                gastoService.calcularTotalPorPeriodo(inicioMes.minusMonths(1), inicioMes.minusDays(1)));

        LocalDate inicioAnio = hoy.withDayOfYear(1);
        LocalDate finAnio = hoy.with(TemporalAdjusters.lastDayOfYear());
        BigDecimal totalAnio = gastoService.calcularTotalPorPeriodo(inicioAnio, finAnio);
        lblTotalAnio.setText(formatearMonto(totalAnio));
        actualizarCambio(lblCambioAnio, hboxCambioAnio, totalAnio,
                gastoService.calcularTotalPorPeriodo(inicioAnio.minusYears(1), finAnio.minusYears(1)));
    }

    private void actualizarCambio(Label label, HBox container, BigDecimal actual, BigDecimal anterior) {
        container.getStyleClass().removeAll("stat-change-positive", "stat-change-negative", "stat-change-neutral");

        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            label.setText("Sin datos previos");
            container.getStyleClass().add("stat-change-neutral");
            return;
        }

        BigDecimal diferencia = actual.subtract(anterior);
        BigDecimal porcentaje = diferencia.divide(anterior, 4, RoundingMode.HALF_UP)
                                         .multiply(BigDecimal.valueOf(100));

        String simbolo = diferencia.signum() >= 0 ? "\u2191" : "\u2193";
        label.setText(String.format(CO, "%s %.1f%% vs. anterior", simbolo, Math.abs(porcentaje.doubleValue())));

        if (diferencia.signum() > 0) {
            container.getStyleClass().add("stat-change-negative");   // más gasto = rojo
        } else if (diferencia.signum() < 0) {
            container.getStyleClass().add("stat-change-positive");   // menos gasto = verde
        } else {
            container.getStyleClass().add("stat-change-neutral");
        }
    }

    // ==================== LISTAS (PESTAÑAS) ====================

    private void cargarTopProductos() {
        List<Map<String, Object>> topProductos = gastoService.obtenerTopProductos(10);
        vboxTopProductos.getChildren().clear();

        int posicion = 1;
        for (Map<String, Object> item : topProductos) {
            String producto = (String) item.get("producto");
            BigDecimal total = (BigDecimal) item.get("total");

            Label lblPosicion = new Label(posicion + ".");
            lblPosicion.setMinWidth(22);
            lblPosicion.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;");

            Label lblProducto = new Label(producto);
            lblProducto.setMaxWidth(Double.MAX_VALUE);
            lblProducto.setStyle("-fx-font-size: 11px; -fx-text-fill: #334155;");
            HBox.setHgrow(lblProducto, Priority.ALWAYS);

            Label lblTotal = new Label(formatearMonto(total));
            lblTotal.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

            HBox fila = new HBox(8, lblPosicion, lblProducto, lblTotal);
            fila.setAlignment(Pos.CENTER_LEFT);
            vboxTopProductos.getChildren().add(fila);
            posicion++;
        }

        if (topProductos.isEmpty()) {
            Label vacio = new Label("No hay productos registrados");
            vacio.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic; -fx-font-size: 11px;");
            vboxTopProductos.getChildren().add(vacio);
        }
    }

    private void cargarCategoriasResumen() {
        Map<String, BigDecimal> categorias = gastoService.obtenerGastosPorCategoria();
        vboxCategorias.getChildren().clear();

        List<Map.Entry<String, BigDecimal>> ordenadas = categorias.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());

        int posicion = 1;
        for (Map.Entry<String, BigDecimal> entry : ordenadas) {
            Label lblPosicion = new Label(posicion + ".");
            lblPosicion.setMinWidth(22);
            lblPosicion.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;");

            Label lblCategoria = new Label(entry.getKey());
            lblCategoria.setMaxWidth(Double.MAX_VALUE);
            lblCategoria.setStyle("-fx-font-size: 11px; -fx-text-fill: #334155;");
            HBox.setHgrow(lblCategoria, Priority.ALWAYS);

            Label lblTotal = new Label(formatearMonto(entry.getValue()));
            lblTotal.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

            HBox fila = new HBox(8, lblPosicion, lblCategoria, lblTotal);
            fila.setAlignment(Pos.CENTER_LEFT);
            vboxCategorias.getChildren().add(fila);
            posicion++;
        }

        if (categorias.isEmpty()) {
            Label vacio = new Label("No hay categorías registradas");
            vacio.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic; -fx-font-size: 11px;");
            vboxCategorias.getChildren().add(vacio);
        }
    }

    // ==================== PRESUPUESTO ====================

    /** Panel de presupuesto del mes: se muestra debajo del encabezado cuando hay topes fijados. */
    private void cargarPresupuestos() {
        try {
            flowPresupuestos.getChildren().clear();
            List<PresupuestoService.EstadoPresupuesto> filas = presupuestoService.estadoDelMes(LocalDate.now()).stream()
                    .filter(PresupuestoService.EstadoPresupuesto::tieneTope)
                    .sorted((a, b) -> Double.compare(b.porcentaje(), a.porcentaje()))
                    .collect(Collectors.toList());

            boolean hay = !filas.isEmpty();
            panelPresupuestos.setVisible(hay);
            panelPresupuestos.setManaged(hay);
            if (!hay) {
                return;
            }

            BigDecimal totalGastado = filas.stream()
                    .map(PresupuestoService.EstadoPresupuesto::gastado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalTope = filas.stream()
                    .map(PresupuestoService.EstadoPresupuesto::tope)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblPresupuestoResumen.setText(formatearMonto(totalGastado) + " de " + formatearMonto(totalTope) + " este mes");

            final int maximo = 6;
            for (PresupuestoService.EstadoPresupuesto fila : filas.subList(0, Math.min(maximo, filas.size()))) {
                double pct = fila.porcentaje();
                String colorBarra = pct >= 100 ? "#ef4444" : (pct >= 80 ? "#f59e0b" : "#22c55e");
                String colorTexto = pct >= 100 ? "#dc2626" : (pct >= 80 ? "#d97706" : "#16a34a");

                Label nombre = new Label(fila.categoria());
                nombre.setMaxWidth(150);
                nombre.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");

                Label porcentaje = new Label(Math.round(pct) + " %");
                porcentaje.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + colorTexto + ";");

                Region espacio = new Region();
                HBox.setHgrow(espacio, Priority.ALWAYS);

                ProgressBar barra = new ProgressBar(Math.min(pct / 100.0, 1.0));
                barra.setMaxWidth(Double.MAX_VALUE);
                barra.setStyle("-fx-accent: " + colorBarra + ";");

                Label detalle = new Label(formatearMonto(fila.gastado()) + " de " + formatearMonto(fila.tope()));
                detalle.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

                VBox item = new VBox(4, new HBox(8, nombre, espacio, porcentaje), barra, detalle);
                item.setPrefWidth(230);
                item.setMinWidth(180);
                flowPresupuestos.getChildren().add(item);
            }

            if (filas.size() > maximo) {
                Label mas = new Label("y " + (filas.size() - maximo) + " más");
                mas.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8; -fx-font-style: italic;");
                flowPresupuestos.getChildren().add(mas);
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar el presupuesto", e);
        }
    }

    // ==================== GRÁFICOS ====================

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

        // Eje con margen arriba para que quepa el valor sobre la barra más alta
        double max = series.getData().stream().mapToDouble(d -> d.getYValue().doubleValue()).max().orElse(0);
        double tope = max <= 0 ? 100 : max * 1.25;
        double paso = Math.pow(10, Math.floor(Math.log10(tope / 4)));
        paso = Math.ceil((tope / 4) / paso) * paso;
        ejeY.setAutoRanging(false);
        ejeY.setLowerBound(0);
        ejeY.setUpperBound(paso * 4);
        ejeY.setTickUnit(paso);
        ejeY.setMinorTickVisible(false);

        chartUltimosDias.getData().add(series);
        chartUltimosDias.setLegendVisible(false);

        Platform.runLater(() -> {
            for (XYChart.Series<String, Number> serie : chartUltimosDias.getData()) {
                for (XYChart.Data<String, Number> data : serie.getData()) {
                    double valor = data.getYValue().doubleValue();
                    if (valor <= 0) continue;

                    Node node = data.getNode();
                    if (node instanceof StackPane bar) {
                        Label label = new Label(formatearMonto(BigDecimal.valueOf(valor)));
                        label.setStyle(
                            "-fx-font-size: 10px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-text-fill: #334155; " +
                            "-fx-background-color: rgba(255, 255, 255, 0.95); " +
                            "-fx-background-radius: 4; " +
                            "-fx-padding: 3 8;"
                        );
                        label.setMouseTransparent(true);
                        label.setMinWidth(Region.USE_PREF_SIZE);
                        StackPane.setAlignment(label, Pos.TOP_CENTER);
                        StackPane.setMargin(label, new javafx.geometry.Insets(-22, 0, 0, 0));
                        bar.getChildren().add(label);

                        Tooltip.install(bar, new Tooltip(
                                data.getXValue() + "\n" + formatearMonto(BigDecimal.valueOf(valor))));
                    }
                }
            }
        });
    }

    private void cargarGraficoCategorias() {
        chartCategorias.getData().clear();
        vboxLeyendaCategorias.getChildren().clear();

        Map<String, BigDecimal> categorias = gastoService.obtenerGastosPorCategoria();
        BigDecimal totalGeneral = categorias.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalCentro.setText(formatearMonto(totalGeneral));

        String[] colores = {"#2563eb", "#7c3aed", "#0891b2", "#16a34a", "#f59e0b", "#ec4899", "#94a3b8"};

        List<Map.Entry<String, BigDecimal>> ordenadas = categorias.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());

        int i = 0;
        for (Map.Entry<String, BigDecimal> entry : ordenadas) {
            final String color = colores[i % colores.length];
            double pct = totalGeneral.signum() == 0 ? 0
                    : entry.getValue().multiply(BigDecimal.valueOf(100))
                            .divide(totalGeneral, 1, RoundingMode.HALF_UP).doubleValue();
            String pctTexto = String.format(CO, "%.1f%%", pct);

            PieChart.Data slice = new PieChart.Data(entry.getKey(), entry.getValue().doubleValue());
            chartCategorias.getData().add(slice);
            if (slice.getNode() != null) {
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
                Tooltip.install(slice.getNode(), new Tooltip(
                        entry.getKey() + "\n" + formatearMonto(entry.getValue()) + "\n" + pctTexto));
            }

            Rectangle cuadro = new Rectangle(10, 10);
            cuadro.setFill(javafx.scene.paint.Color.web(color));
            cuadro.setArcWidth(3);
            cuadro.setArcHeight(3);

            Label lbl = new Label(entry.getKey() + "  (" + pctTexto + ")");
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #334155;");

            HBox fila = new HBox(8, cuadro, lbl);
            fila.setAlignment(Pos.CENTER_LEFT);
            vboxLeyendaCategorias.getChildren().add(fila);
            i++;
        }

        if (ordenadas.isEmpty()) {
            Label vacio = new Label("Sin gastos registrados");
            vacio.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic;");
            vboxLeyendaCategorias.getChildren().add(vacio);
        }
    }

    /** Total de cada uno de los últimos 12 meses. El mes actual (incompleto) va marcado con *. */
    private void cargarTendenciaMensual() {
        chartTendencia.getData().clear();

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy", CO);
        YearMonth actual = YearMonth.now();

        double max = 0;
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = actual.minusMonths(i);
            BigDecimal total = gastoService.calcularTotalPorPeriodo(ym.atDay(1), ym.atEndOfMonth());
            String etiqueta = ym.format(fmt).replace(".", "") + (i == 0 ? "*" : "");
            serie.getData().add(new XYChart.Data<>(etiqueta, total.doubleValue()));
            max = Math.max(max, total.doubleValue());
        }

        double tope = max <= 0 ? 100 : max * 1.15;
        double paso = Math.pow(10, Math.floor(Math.log10(tope / 4)));
        paso = Math.ceil((tope / 4) / paso) * paso;
        ejeTendencia.setAutoRanging(false);
        ejeTendencia.setLowerBound(0);
        ejeTendencia.setUpperBound(paso * 4);
        ejeTendencia.setTickUnit(paso);
        ejeTendencia.setMinorTickVisible(false);

        chartTendencia.getData().add(serie);

        Platform.runLater(() -> {
            int n = serie.getData().size();
            int idx = 0;
            for (XYChart.Data<String, Number> d : serie.getData()) {
                if (d.getNode() != null) {
                    String extra = (idx == n - 1) ? "\n(mes en curso)" : "";
                    Tooltip.install(d.getNode(), new Tooltip(
                            d.getXValue().replace("*", "") + "\n"
                            + formatearMonto(BigDecimal.valueOf(d.getYValue().doubleValue())) + extra));
                }
                idx++;
            }
        });
    }

    /** Pesos colombianos sin decimales: $ 1.234.567 */
    private String formatearMonto(BigDecimal monto) {
        if (monto == null) monto = BigDecimal.ZERO;
        NumberFormat f = NumberFormat.getIntegerInstance(CO);
        return "$ " + f.format(monto);
    }
}