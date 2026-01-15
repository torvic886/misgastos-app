package com.misgastos.controller;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.Subcategoria;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.GastoService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class BuscarEditarGastosController {
    
    @FXML private DatePicker dpFechaInicio;
    @FXML private DatePicker dpFechaFin;
    @FXML private TextField txtBuscarProducto;
    @FXML private Button btnBuscar;
    @FXML private Button btnLimpiar;
    @FXML private TableView<Gasto> tableGastos;
    @FXML private TableColumn<Gasto, Long> colId;
    @FXML private TableColumn<Gasto, LocalDate> colFecha;
    @FXML private TableColumn<Gasto, LocalTime> colHora;
    @FXML private TableColumn<Gasto, String> colCategoria;
    @FXML private TableColumn<Gasto, String> colSubcategoria;
    @FXML private TableColumn<Gasto, String> colProducto;
    @FXML private TableColumn<Gasto, Integer> colCantidad;
    @FXML private TableColumn<Gasto, BigDecimal> colValorUnitario;
    @FXML private TableColumn<Gasto, BigDecimal> colTotal;
    @FXML private TableColumn<Gasto, Void> colAcciones;
    @FXML private Label lblResultados;
    
    @Autowired private GastoService gastoService;
    @Autowired private CategoriaService categoriaService;
    
    private Long usuarioId = 1L;
    private List<Gasto> gastosActuales;
    
    // Formato para peso colombiano sin decimales
    private static final NumberFormat formatoCOP;
    
    static {
        formatoCOP = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        formatoCOP.setMaximumFractionDigits(0);
        formatoCOP.setMinimumFractionDigits(0);
    }
    
    @FXML
    public void initialize() {
        configurarTabla();
        configurarFechas();
        habilitarCopiaDeCeldas();
        cargarGastosRecientes();
    }
    
    private void configurarFechas() {
        dpFechaFin.setValue(LocalDate.now());
        dpFechaInicio.setValue(LocalDate.now().minusMonths(1));
    }
    
    private void habilitarCopiaDeCeldas() {
        tableGastos.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.C) {
                copiarCeldaSeleccionada();
            }
        });
        
        // Permitir doble clic para copiar
        tableGastos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                copiarCeldaSeleccionada();
            }
        });
    }
    
    private void copiarCeldaSeleccionada() {
        TablePosition<Gasto, ?> pos = tableGastos.getFocusModel().getFocusedCell();
        if (pos != null) {
            Object cell = pos.getTableColumn().getCellData(pos.getRow());
            if (cell != null) {
                String texto = cell.toString();
                
                // Si es un BigDecimal, aplicar formato
                if (cell instanceof BigDecimal) {
                    texto = formatoCOP.format((BigDecimal) cell);
                }
                
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(texto);
                clipboard.setContent(content);
                
                // Feedback visual
                System.out.println("✓ Copiado: " + texto);
            }
        }
    }
    
    private void configurarTabla() {
        // Habilitar selección por celda en lugar de por fila
        tableGastos.getSelectionModel().setCellSelectionEnabled(true);
        tableGastos.setEditable(false);
        
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        
        colHora.setCellValueFactory(new PropertyValueFactory<>("hora"));
        colHora.setCellFactory(column -> new TableCell<Gasto, LocalTime>() {
            @Override
            protected void updateItem(LocalTime time, boolean empty) {
                super.updateItem(time, empty);
                if (empty || time == null) {
                    setText(null);
                } else {
                    setText(time.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
            }
        });
        
        colCategoria.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getCategoria().getNombre()
            )
        );
        
        colSubcategoria.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getSubcategoria().getNombre()
            )
        );
        
        colProducto.setCellValueFactory(new PropertyValueFactory<>("producto"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        
        // FORMATO PESO COLOMBIANO PARA VALOR UNITARIO
        colValorUnitario.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));
        colValorUnitario.setCellFactory(column -> new TableCell<Gasto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean empty) {
                super.updateItem(valor, empty);
                if (empty || valor == null) {
                    setText(null);
                } else {
                    setText(formatoCOP.format(valor));
                }
            }
        });
        
        // FORMATO PESO COLOMBIANO PARA TOTAL
        colTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        colTotal.setCellFactory(column -> new TableCell<Gasto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean empty) {
                super.updateItem(valor, empty);
                if (empty || valor == null) {
                    setText(null);
                } else {
                    setText(formatoCOP.format(valor));
                }
            }
        });
        
        agregarColumnaAcciones();
    }
    
    private void agregarColumnaAcciones() {
        colAcciones.setCellFactory(param -> new TableCell<>() {
            private final Button btnEditar = new Button("✏ Editar");
            private final Button btnEliminar = new Button("🗑");
            private final HBox contenedor = new HBox(5, btnEditar, btnEliminar);
            
            {
                btnEditar.getStyleClass().add("secondary-button");
                btnEditar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");
                
                btnEliminar.getStyleClass().add("danger-button");
                btnEliminar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; -fx-background-color: #ef4444; -fx-text-fill: white;");
                
                btnEditar.setOnAction(event -> {
                    Gasto gasto = getTableView().getItems().get(getIndex());
                    abrirDialogoEditar(gasto);
                });
                
                btnEliminar.setOnAction(event -> {
                    Gasto gasto = getTableView().getItems().get(getIndex());
                    confirmarEliminar(gasto);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : contenedor);
            }
        });
    }
    
    @FXML
    public void handleBuscar() {
        LocalDate inicio = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();
        
        if (inicio == null || fin == null) {
            mostrarAlerta("Validación", "Seleccione ambas fechas", Alert.AlertType.WARNING);
            return;
        }
        
        if (inicio.isAfter(fin)) {
            mostrarAlerta("Validación", "La fecha de inicio no puede ser posterior a la fecha fin", Alert.AlertType.WARNING);
            return;
        }
        
        gastosActuales = gastoService.buscarPorUsuarioYPeriodo(usuarioId, inicio, fin);
        tableGastos.setItems(FXCollections.observableArrayList(gastosActuales));
        
        lblResultados.setText(gastosActuales.size() + " gasto(s) encontrado(s)");
        txtBuscarProducto.clear();
    }
    
    @FXML
    public void handleBuscarProducto() {
        String textoBusqueda = txtBuscarProducto.getText();
        
        if (textoBusqueda == null || textoBusqueda.trim().isEmpty()) {
            mostrarAlerta("Validación", "Ingrese un texto para buscar", Alert.AlertType.WARNING);
            return;
        }
        
        LocalDate inicio = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();
        
        if (inicio == null || fin == null) {
            mostrarAlerta("Validación", "Seleccione ambas fechas", Alert.AlertType.WARNING);
            return;
        }
        
        if (inicio.isAfter(fin)) {
            mostrarAlerta("Validación", "La fecha de inicio no puede ser posterior a la fecha fin", Alert.AlertType.WARNING);
            return;
        }
        
        List<Gasto> todosLosGastos = gastoService.buscarPorUsuarioYPeriodo(usuarioId, inicio, fin);
        gastosActuales = todosLosGastos.stream()
            .filter(gasto -> gasto.getProducto().toLowerCase().contains(textoBusqueda.toLowerCase()))
            .collect(Collectors.toList());
        
        tableGastos.setItems(FXCollections.observableArrayList(gastosActuales));
        lblResultados.setText(gastosActuales.size() + " gasto(s) encontrado(s) con '" + textoBusqueda + "'");
    }
    
    @FXML
    public void handleLimpiarBusqueda() {
        txtBuscarProducto.clear();
        handleBuscar();
    }
    
    @FXML
    public void handleLimpiar() {
        dpFechaInicio.setValue(LocalDate.now().minusMonths(1));
        dpFechaFin.setValue(LocalDate.now());
        txtBuscarProducto.clear();
        cargarGastosRecientes();
    }
    
    private void cargarGastosRecientes() {
        LocalDate inicio = LocalDate.now().minusMonths(1);
        LocalDate fin = LocalDate.now();
        gastosActuales = gastoService.buscarPorUsuarioYPeriodo(usuarioId, inicio, fin);
        tableGastos.setItems(FXCollections.observableArrayList(gastosActuales));
        lblResultados.setText(gastosActuales.size() + " gasto(s) en el último mes");
    }
    
    private void abrirDialogoEditar(Gasto gasto) {
        Dialog<Gasto> dialog = new Dialog<>();
        dialog.setTitle("✏️ Editar Gasto");
        dialog.setHeaderText("Editar gasto #" + gasto.getId());
        
        ButtonType btnGuardar = new ButtonType("💾 Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        DatePicker dpFecha = new DatePicker(gasto.getFecha());
        TextField txtHora = new TextField(gasto.getHora().format(DateTimeFormatter.ofPattern("HH:mm")));
        
        ComboBox<Categoria> cmbCategoria = new ComboBox<>();
        cmbCategoria.getItems().setAll(categoriaService.listarCategorias());
        cmbCategoria.setValue(gasto.getCategoria());
        
        ComboBox<Subcategoria> cmbSubcategoria = new ComboBox<>();
        cmbSubcategoria.getItems().setAll(
            categoriaService.listarSubcategoriasPorCategoria(gasto.getCategoria().getId())
        );
        cmbSubcategoria.setValue(gasto.getSubcategoria());
        
        TextField txtProducto = new TextField(gasto.getProducto());
        TextField txtCantidad = new TextField(String.valueOf(gasto.getCantidad()));
        TextField txtValorUnitario = new TextField(gasto.getValorUnitario().toString());
        TextField txtValorTotal = new TextField(formatoCOP.format(gasto.getValorTotal()));
        txtValorTotal.setEditable(false);
        txtValorTotal.setStyle("-fx-background-color: #f3f4f6;");
        
        TextArea txtNotas = new TextArea(gasto.getNotas());
        txtNotas.setPrefRowCount(3);
        
        cmbCategoria.setOnAction(e -> {
            Categoria selected = cmbCategoria.getValue();
            if (selected != null) {
                cmbSubcategoria.getItems().setAll(
                    categoriaService.listarSubcategoriasPorCategoria(selected.getId())
                );
            }
        });
        
        Runnable calcularTotal = () -> {
            try {
                int cantidad = Integer.parseInt(txtCantidad.getText());
                BigDecimal valorUnit = new BigDecimal(txtValorUnitario.getText());
                BigDecimal total = valorUnit.multiply(BigDecimal.valueOf(cantidad));
                txtValorTotal.setText(formatoCOP.format(total));
            } catch (Exception e) {
                txtValorTotal.setText(formatoCOP.format(0));
            }
        };
        
        txtCantidad.textProperty().addListener((obs, old, newVal) -> calcularTotal.run());
        txtValorUnitario.textProperty().addListener((obs, old, newVal) -> calcularTotal.run());
        
        int row = 0;
        grid.add(new Label("Fecha:"), 0, row);
        grid.add(dpFecha, 1, row++);
        
        grid.add(new Label("Hora (HH:mm):"), 0, row);
        grid.add(txtHora, 1, row++);
        
        grid.add(new Label("Categoría:"), 0, row);
        grid.add(cmbCategoria, 1, row++);
        
        grid.add(new Label("Subcategoría:"), 0, row);
        grid.add(cmbSubcategoria, 1, row++);
        
        grid.add(new Label("Producto:"), 0, row);
        grid.add(txtProducto, 1, row++);
        
        grid.add(new Label("Cantidad:"), 0, row);
        grid.add(txtCantidad, 1, row++);
        
        grid.add(new Label("Valor Unitario:"), 0, row);
        grid.add(txtValorUnitario, 1, row++);
        
        grid.add(new Label("Valor Total:"), 0, row);
        grid.add(txtValorTotal, 1, row++);
        
        grid.add(new Label("Notas:"), 0, row);
        grid.add(txtNotas, 1, row++);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                try {
                    LocalTime hora = LocalTime.parse(txtHora.getText(), DateTimeFormatter.ofPattern("HH:mm"));
                    
                    gastoService.actualizarGasto(
                        gasto.getId(),
                        cmbCategoria.getValue().getId(),
                        cmbSubcategoria.getValue().getId(),
                        txtProducto.getText(),
                        Integer.parseInt(txtCantidad.getText()),
                        new BigDecimal(txtValorUnitario.getText()),
                        txtNotas.getText(),
                        dpFecha.getValue(),
                        hora
                    );
                    
                    return gasto;
                } catch (Exception e) {
                    mostrarAlerta("Error", "Error al guardar: " + e.getMessage(), Alert.AlertType.ERROR);
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(gastoActualizado -> {
            mostrarAlerta("Éxito", "Gasto actualizado correctamente", Alert.AlertType.INFORMATION);
            handleBuscar();
        });
    }
    
    private void confirmarEliminar(Gasto gasto) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText("¿Eliminar este gasto?");
        confirmacion.setContentText(
            "Producto: " + gasto.getProducto() + "\n" +
            "Valor: " + formatoCOP.format(gasto.getValorTotal()) + "\n" +
            "Fecha: " + gasto.getFecha()
        );
        
        confirmacion.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                gastoService.eliminarGasto(gasto.getId());
                mostrarAlerta("Éxito", "Gasto eliminado correctamente", Alert.AlertType.INFORMATION);
                handleBuscar();
            }
        });
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}