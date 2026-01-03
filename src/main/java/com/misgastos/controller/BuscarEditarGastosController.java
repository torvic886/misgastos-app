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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BuscarEditarGastosController {
    
    @FXML private DatePicker dpFechaInicio;
    @FXML private DatePicker dpFechaFin;
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
    
    @FXML
    public void initialize() {
        configurarTabla();
        configurarFechas();
        cargarGastosRecientes();
    }
    
    private void configurarFechas() {
        // Establecer fechas por defecto (último mes)
        dpFechaFin.setValue(LocalDate.now());
        dpFechaInicio.setValue(LocalDate.now().minusMonths(1));
    }
    
    private void configurarTabla() {
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
        colValorUnitario.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        
        // Columna de acciones (botones Editar/Eliminar)
        agregarColumnaAcciones();
    }
    
    private void agregarColumnaAcciones() {
        colAcciones.setCellFactory(param -> new TableCell<>() {
            private final Button btnEditar = new Button("📝 Editar");
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
        
        List<Gasto> gastos = gastoService.buscarPorUsuarioYPeriodo(usuarioId, inicio, fin);
        tableGastos.setItems(FXCollections.observableArrayList(gastos));
        
        lblResultados.setText(gastos.size() + " gasto(s) encontrado(s)");
    }
    
    @FXML
    public void handleLimpiar() {
        dpFechaInicio.setValue(LocalDate.now().minusMonths(1));
        dpFechaFin.setValue(LocalDate.now());
        cargarGastosRecientes();
    }
    
    private void cargarGastosRecientes() {
        LocalDate inicio = LocalDate.now().minusMonths(1);
        LocalDate fin = LocalDate.now();
        List<Gasto> gastos = gastoService.buscarPorUsuarioYPeriodo(usuarioId, inicio, fin);
        tableGastos.setItems(FXCollections.observableArrayList(gastos));
        lblResultados.setText(gastos.size() + " gasto(s) en el último mes");
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
        
        // Campos del formulario
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
        TextField txtValorTotal = new TextField(gasto.getValorTotal().toString());
        txtValorTotal.setEditable(false);
        txtValorTotal.setStyle("-fx-background-color: #f3f4f6;");
        
        TextArea txtNotas = new TextArea(gasto.getNotas());
        txtNotas.setPrefRowCount(3);
        
        // Listener para cambiar subcategorías
        cmbCategoria.setOnAction(e -> {
            Categoria selected = cmbCategoria.getValue();
            if (selected != null) {
                cmbSubcategoria.getItems().setAll(
                    categoriaService.listarSubcategoriasPorCategoria(selected.getId())
                );
            }
        });
        
        // Calcular total automáticamente
        Runnable calcularTotal = () -> {
            try {
                int cantidad = Integer.parseInt(txtCantidad.getText());
                BigDecimal valorUnit = new BigDecimal(txtValorUnitario.getText());
                BigDecimal total = valorUnit.multiply(BigDecimal.valueOf(cantidad));
                txtValorTotal.setText(total.toString());
            } catch (Exception e) {
                txtValorTotal.setText("0.00");
            }
        };
        
        txtCantidad.textProperty().addListener((obs, old, newVal) -> calcularTotal.run());
        txtValorUnitario.textProperty().addListener((obs, old, newVal) -> calcularTotal.run());
        
        // Agregar campos al grid
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
        
        // Convertir resultado
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
            "Valor: $" + gasto.getValorTotal() + "\n" +
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
