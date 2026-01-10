package com.misgastos.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.misgastos.model.Billetero;
import com.misgastos.service.BilleteroService;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class GestionBilleterosController {

    @FXML private TableView<Billetero> tablaBilleteros;
    @FXML private TableColumn<Billetero, String> colFecha;
    @FXML private TableColumn<Billetero, String> colBilletero;
    @FXML private TableColumn<Billetero, String> colPremios;
    @FXML private TableColumn<Billetero, String> colDiferencia;
    @FXML private TableColumn<Billetero, Void> colAcciones;
    
    @FXML private TextField txtBuscar;
    @FXML private DatePicker dpFechaDesde;
    @FXML private DatePicker dpFechaHasta;
    
    @FXML private Label lblTotalRegistros;
    @FXML private Label lblTotalBilletero;
    @FXML private Label lblTotalPremios;
    @FXML private Label lblDiferenciaNeta;
    
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Label lblPagina;
    
    @Autowired
    private BilleteroService billeteroService;
    
    @Autowired
    private ApplicationContext springContext;
    
    private ObservableList<Billetero> listaBilleteros;
    private ObservableList<Billetero> listaFiltrada;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NumberFormat currencyFormat;
    
    public GestionBilleterosController() {
        // Formato de moneda SIN decimales
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        DecimalFormat format = new DecimalFormat("$ #,##0", symbols);
        this.currencyFormat = format;
    }
    
    @FXML
    public void initialize() {
        System.out.println("💼 Inicializando Gestión de Billeteros");
        configurarTabla();
        configurarBusqueda();
        cargarDatos();
        actualizarResumen();
    }
    
    private void configurarTabla() {
        // Columna Fecha
        colFecha.setCellValueFactory(cellData -> {
            LocalDate fecha = cellData.getValue().getFecha();
            return new SimpleStringProperty(fecha.format(dateFormatter));
        });
        
        // Columna Billetero
        colBilletero.setCellValueFactory(cellData -> {
            BigDecimal valor = cellData.getValue().getBilletero();
            return new SimpleStringProperty(formatearMoneda(valor));
        });
        colBilletero.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // Columna Premios
        colPremios.setCellValueFactory(cellData -> {
            BigDecimal valor = cellData.getValue().getPremios();
            return new SimpleStringProperty(formatearMoneda(valor));
        });
        colPremios.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        // Columna Diferencia con color
        colDiferencia.setCellValueFactory(cellData -> {
            BigDecimal valor = cellData.getValue().getDiferencia();
            return new SimpleStringProperty(formatearMoneda(valor));
        });
        colDiferencia.setCellFactory(column -> new TableCell<Billetero, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    
                    Billetero billetero = getTableView().getItems().get(getIndex());
                    BigDecimal diferencia = billetero.getDiferencia();
                    
                    if (diferencia.compareTo(BigDecimal.ZERO) > 0) {
                        setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                    } else if (diferencia.compareTo(BigDecimal.ZERO) < 0) {
                        setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #6b7280; -fx-font-weight: normal;");
                    }
                }
            }
        });
        
        // Columna Acciones
        colAcciones.setCellFactory(column -> new TableCell<Billetero, Void>() {
            private final Button btnEditar = new Button("✎");
            private final Button btnEliminar = new Button("✖");
            private final HBox container = new HBox(10, btnEditar, btnEliminar);
            
            {
                btnEditar.getStyleClass().add("action-button-edit");
                btnEliminar.getStyleClass().add("action-button-delete");
                container.setAlignment(Pos.CENTER);
                
                btnEditar.setOnAction(event -> {
                    Billetero billetero = getTableView().getItems().get(getIndex());
                    handleEditar(billetero);
                });
                
                btnEliminar.setOnAction(event -> {
                    Billetero billetero = getTableView().getItems().get(getIndex());
                    handleEliminar(billetero);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });
    }
    
    private void configurarBusqueda() {
        txtBuscar.textProperty().addListener((observable, oldValue, newValue) -> {
            filtrarDatos();
        });
    }
    
    private void cargarDatos() {
        try {
            List<Billetero> billeteros = billeteroService.listarTodos();
            listaBilleteros = FXCollections.observableArrayList(billeteros);
            listaFiltrada = FXCollections.observableArrayList(billeteros);
            tablaBilleteros.setItems(listaFiltrada);
            
            lblTotalRegistros.setText("Total registros: " + listaFiltrada.size());
            
            System.out.println("✅ Datos cargados: " + billeteros.size() + " registros");
        } catch (Exception e) {
            mostrarError("Error al cargar datos", e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void filtrarDatos() {
        String busqueda = txtBuscar.getText().toLowerCase().trim();
        
        if (busqueda.isEmpty()) {
            listaFiltrada.setAll(listaBilleteros);
        } else {
            listaFiltrada.clear();
            for (Billetero b : listaBilleteros) {
                String fecha = b.getFecha().format(dateFormatter);
                String billetero = formatearMoneda(b.getBilletero());
                String premios = formatearMoneda(b.getPremios());
                String diferencia = formatearMoneda(b.getDiferencia());
                
                if (fecha.contains(busqueda) || 
                    billetero.toLowerCase().contains(busqueda) ||
                    premios.toLowerCase().contains(busqueda) ||
                    diferencia.toLowerCase().contains(busqueda)) {
                    listaFiltrada.add(b);
                }
            }
        }
        
        lblTotalRegistros.setText("Total registros: " + listaFiltrada.size());
    }
    
    @FXML
    private void handleNuevo() {
        abrirModal(null);
    }
    
    @FXML
    private void handleRefresh() {
        cargarDatos();
        actualizarResumen();
        dpFechaDesde.setValue(null);
        dpFechaHasta.setValue(null);
        txtBuscar.clear();
    }
    
    @FXML
    private void handleBuscar() {
        LocalDate fechaInicio = dpFechaDesde.getValue();
        LocalDate fechaFin = dpFechaHasta.getValue();
        
        if (fechaInicio == null && fechaFin == null) {
            cargarDatos();
            actualizarResumen();
            return;
        }
        
        if (fechaInicio == null) fechaInicio = LocalDate.of(2000, 1, 1);
        if (fechaFin == null) fechaFin = LocalDate.now();
        
        if (fechaInicio.isAfter(fechaFin)) {
            mostrarError("Error de fechas", "La fecha inicial no puede ser mayor a la fecha final");
            return;
        }
        
        try {
            List<Billetero> resultados = billeteroService.buscarPorRangoFechas(fechaInicio, fechaFin);
            listaBilleteros.setAll(resultados);
            listaFiltrada.setAll(resultados);
            tablaBilleteros.setItems(listaFiltrada);
            
            lblTotalRegistros.setText("Total registros: " + listaFiltrada.size());
            actualizarResumenPorFechas(fechaInicio, fechaFin);
            
        } catch (Exception e) {
            mostrarError("Error al buscar", e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleLimpiarFiltros() {
        dpFechaDesde.setValue(null);
        dpFechaHasta.setValue(null);
        txtBuscar.clear();
        handleRefresh();
    }
    
    private void handleEditar(Billetero billetero) {
        abrirModal(billetero);
    }
    
    private void handleEliminar(Billetero billetero) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText("¿Está seguro de eliminar este registro?");
        confirmacion.setContentText("Fecha: " + billetero.getFecha().format(dateFormatter) +
                                   "\nBilletero: " + formatearMoneda(billetero.getBilletero()));
        
        Optional<ButtonType> resultado = confirmacion.showAndWait();
        
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            try {
                billeteroService.eliminar(billetero.getId());
                cargarDatos();
                actualizarResumen();
                
                mostrarInfo("Éxito", "Registro eliminado correctamente");
            } catch (Exception e) {
                mostrarError("Error al eliminar", e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void abrirModal(Billetero billetero) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-billetero.fxml"));
            loader.setControllerFactory(springContext::getBean);
            
            Parent root = loader.load();
            
            ModalBilleteroController controller = loader.getController();
            controller.setBilletero(billetero);
            controller.setOnGuardarCallback(() -> {
                cargarDatos();
                actualizarResumen();
            });
            
            Stage stage = new Stage();
            stage.setTitle(billetero == null ? "Nuevo Billetero" : "Editar Billetero");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            
            // Cargar CSS
            String css = getClass().getResource("/css/styles.css").toExternalForm();
            stage.getScene().getStylesheets().add(css);
            
            stage.showAndWait();
            
        } catch (Exception e) {
            mostrarError("Error al abrir modal", e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void actualizarResumen() {
        try {
            Map<String, BigDecimal> totales = billeteroService.obtenerTotales();
            
            lblTotalBilletero.setText(formatearMoneda(totales.get("billetero")));
            lblTotalPremios.setText(formatearMoneda(totales.get("premios")));
            
            BigDecimal diferencia = totales.get("diferencia");
            lblDiferenciaNeta.setText(formatearMoneda(diferencia));
            
            aplicarEstiloDiferencia(lblDiferenciaNeta, diferencia);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void actualizarResumenPorFechas(LocalDate inicio, LocalDate fin) {
        try {
            Map<String, BigDecimal> totales = billeteroService.obtenerTotalesPorFechas(inicio, fin);
            
            lblTotalBilletero.setText(formatearMoneda(totales.get("billetero")));
            lblTotalPremios.setText(formatearMoneda(totales.get("premios")));
            
            BigDecimal diferencia = totales.get("diferencia");
            lblDiferenciaNeta.setText(formatearMoneda(diferencia));
            
            aplicarEstiloDiferencia(lblDiferenciaNeta, diferencia);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void aplicarEstiloDiferencia(Label label, BigDecimal valor) {
        if (valor.compareTo(BigDecimal.ZERO) > 0) {
            label.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 18px;");
        } else if (valor.compareTo(BigDecimal.ZERO) < 0) {
            label.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 18px;");
        } else {
            label.setStyle("-fx-text-fill: #6b7280; -fx-font-weight: normal; -fx-font-size: 18px;");
        }
    }
    
    private String formatearMoneda(BigDecimal valor) {
        if (valor == null) return "$ 0";
        return currencyFormat.format(valor.longValue());
    }
    
    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
    
    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}