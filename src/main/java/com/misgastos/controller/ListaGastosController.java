package com.misgastos.controller;

import com.misgastos.model.Gasto;
import com.misgastos.service.GastoService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class ListaGastosController {
    
    @FXML
    private TableView<Gasto> tableGastos;
    
    @FXML
    private TableColumn<Gasto, Long> colId;
    
    @FXML
    private TableColumn<Gasto, LocalDate> colFecha;
    
    @FXML
    private TableColumn<Gasto, String> colCategoria;
    
    @FXML
    private TableColumn<Gasto, String> colProducto;
    
    @FXML
    private TableColumn<Gasto, Integer> colCantidad;
    
    @FXML
    private TableColumn<Gasto, BigDecimal> colValorUnitario;
    
    @FXML
    private TableColumn<Gasto, BigDecimal> colValorTotal;
    
    @FXML
    private TextField txtBuscar;
    
    @Autowired
    private GastoService gastoService;
    
    private ObservableList<Gasto> listaGastos;
    
    @FXML
    public void initialize() {
        configurarTabla();
        cargarGastos();
    }
    
    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        colCategoria.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getCategoria().getNombre()
            )
        );
        colProducto.setCellValueFactory(new PropertyValueFactory<>("producto"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colValorUnitario.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));
        colValorTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
    }
    
    private void cargarGastos() {
        List<Gasto> gastos = gastoService.listarTodos();
        listaGastos = FXCollections.observableArrayList(gastos);
        tableGastos.setItems(listaGastos);
        
        System.out.println("✅ Cargados " + gastos.size() + " gastos");
    }
    
    @FXML
    public void handleBuscar() {
        String busqueda = txtBuscar.getText().toLowerCase();
        if (busqueda.isEmpty()) {
            cargarGastos();
            return;
        }
        
        List<Gasto> gastosFiltrados = listaGastos.stream()
            .filter(g -> g.getProducto().toLowerCase().contains(busqueda) ||
                        g.getCategoria().getNombre().toLowerCase().contains(busqueda))
            .toList();
        
        tableGastos.setItems(FXCollections.observableArrayList(gastosFiltrados));
    }
    
    @FXML
    public void handleEliminar() {
        Gasto seleccionado = tableGastos.getSelectionModel().getSelectedItem();
        
        if (seleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un gasto para eliminar", Alert.AlertType.WARNING);
            return;
        }
        
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("¿Está seguro de eliminar este gasto?");
        
        if (confirmacion.showAndWait().get() == ButtonType.OK) {
            gastoService.eliminarGasto(seleccionado.getId());
            mostrarAlerta("Éxito", "Gasto eliminado correctamente", Alert.AlertType.INFORMATION);
            cargarGastos();
        }
    }
    
    @FXML
    public void handleRefrescar() {
        cargarGastos();
        txtBuscar.clear();
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}