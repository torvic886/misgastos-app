package com.misgastos.controller;

import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.GastoService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class RegistroGastoController {
    
    @FXML
    private ComboBox<Categoria> cmbCategoria;
    
    @FXML
    private ComboBox<Subcategoria> cmbSubcategoria;
    
    @FXML
    private TextField txtProducto;
    
    @FXML
    private TextField txtCantidad;
    
    @FXML
    private TextField txtValorUnitario;
    
    @FXML
    private TextField txtValorTotal;
    
    @FXML
    private TextArea txtNotas;
    
    @Autowired
    private CategoriaService categoriaService;
    
    @Autowired
    private GastoService gastoService;
    
    private Long usuarioId = 1L; // Temporal, luego vendrá del login
    
    @FXML
    public void initialize() {
        cargarCategorias();
        configurarCalculoAutomatico();
    }
    
    private void cargarCategorias() {
        List<Categoria> categorias = categoriaService.listarCategorias();
        cmbCategoria.getItems().addAll(categorias);
        
        cmbCategoria.setOnAction(e -> {
            Categoria selected = cmbCategoria.getValue();
            if (selected != null) {
                cargarSubcategorias(selected.getId());
            }
        });
    }
    
    private void cargarSubcategorias(Long categoriaId) {
        cmbSubcategoria.getItems().clear();
        List<Subcategoria> subcategorias = categoriaService.listarSubcategoriasPorCategoria(categoriaId);
        cmbSubcategoria.getItems().addAll(subcategorias);
    }
    
    private void configurarCalculoAutomatico() {
        txtCantidad.textProperty().addListener((obs, old, newVal) -> calcularTotal());
        txtValorUnitario.textProperty().addListener((obs, old, newVal) -> calcularTotal());
    }
    
    private void calcularTotal() {
        try {
            int cantidad = Integer.parseInt(txtCantidad.getText());
            BigDecimal valorUnit = new BigDecimal(txtValorUnitario.getText());
            BigDecimal total = valorUnit.multiply(BigDecimal.valueOf(cantidad));
            txtValorTotal.setText(total.toString());
        } catch (Exception e) {
            txtValorTotal.setText("0.00");
        }
    }
    
    @FXML
    public void handleGuardar() {
        if (!validarCampos()) return;
        
        try {
            gastoService.registrarGasto(
                usuarioId,
                cmbCategoria.getValue().getId(),
                cmbSubcategoria.getValue().getId(),
                txtProducto.getText(),
                Integer.parseInt(txtCantidad.getText()),
                new BigDecimal(txtValorUnitario.getText()),
                txtNotas.getText()
            );
            
            mostrarAlerta("Éxito", "Gasto registrado correctamente", Alert.AlertType.INFORMATION);
            limpiarFormulario();
            
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo guardar el gasto", Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    public void handleLimpiar() {
        limpiarFormulario();
    }
    
    private boolean validarCampos() {
        if (cmbCategoria.getValue() == null) {
            mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
            return false;
        }
        if (cmbSubcategoria.getValue() == null) {
            mostrarAlerta("Validación", "Seleccione una subcategoría", Alert.AlertType.WARNING);
            return false;
        }
        if (txtProducto.getText().isEmpty()) {
            mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
            return false;
        }
        return true;
    }
    
    private void limpiarFormulario() {
        cmbCategoria.setValue(null);
        cmbSubcategoria.getItems().clear();
        txtProducto.clear();
        txtCantidad.setText("1");
        txtValorUnitario.clear();
        txtValorTotal.setText("0.00");
        txtNotas.clear();
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}