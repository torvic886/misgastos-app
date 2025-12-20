package com.misgastos.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TextField;


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
    private ComboBox<String> cmbProducto;
    
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
    
    private ObservableList<String> productosObservable;
    private FilteredList<String> productosFiltrados;

    
    @FXML
    public void initialize() {
        cargarCategorias();
        configurarCalculoAutomatico();
        configurarProductoAutocomplete();
        
        cmbProducto.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                autocompletarDatosPorProducto(newVal);
            }
        });
        
        configurarGuardarConEnter();
        
        Platform.runLater(() -> {
            if (!cmbCategoria.getItems().isEmpty()) {
                cmbCategoria.setValue(cmbCategoria.getItems().get(0));
                cargarSubcategorias(cmbCategoria.getValue().getId());

                if (!cmbSubcategoria.getItems().isEmpty()) {
                    cmbSubcategoria.setValue(cmbSubcategoria.getItems().get(0));
                }
            }
        });
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
    
    private void configurarProductoAutocomplete() {

        cmbProducto.setEditable(true);
        TextField editor = cmbProducto.getEditor();

        editor.setOnKeyPressed(event -> {

            // 🔽 Flecha abajo → mostrar lista
            if (event.getCode() == KeyCode.DOWN) {
                cmbProducto.show();
                return;
            }

            // ⏎ ENTER → autocompletar + mover foco
            if (event.getCode() == KeyCode.ENTER) {
                String producto = editor.getText();

                if (producto != null && !producto.isBlank()) {
                    autocompletarDatosPorProducto(producto);
                    moverFoco(txtCantidad);
                }

                event.consume();
            }
        });

        editor.textProperty().addListener((obs, oldText, newText) -> {

            if (newText == null || newText.isBlank()) {
                Platform.runLater(cmbProducto::hide);
                return;
            }

            Platform.runLater(() -> {
                List<String> productos = gastoService.buscarProductos(newText);

                if (productos.isEmpty()) {
                    cmbProducto.hide();
                    return;
                }

                cmbProducto.getItems().setAll(productos);
                cmbProducto.show();
            });
        });

        // 👉 Selección con mouse
        cmbProducto.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                autocompletarDatosPorProducto(newVal);
                moverFoco(txtCantidad);
            }
        });
    }


    
    private void configurarGuardarConEnter() {

        cmbProducto.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                autocompletarDatosPorProducto(
                    cmbProducto.getEditor().getText()
                );
                txtCantidad.requestFocus(); // UX rápida
                event.consume();
            }
        });

        txtCantidad.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtValorUnitario.requestFocus();
                event.consume();
            }
        });

        txtValorUnitario.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleGuardar();
                event.consume();
            }
        });
    }

    
    private void autocompletarDatosPorProducto(String producto) {

        if (producto == null || producto.isBlank()) return;

        // 🔐 Guardar texto actual
        String textoProducto = producto;

        gastoService.buscarUltimoGastoPorProducto(producto)
            .ifPresent(gasto -> {

                // 🟣 Categoría
                cmbCategoria.setValue(gasto.getCategoria());

                // 🟣 Subcategorías
                cargarSubcategorias(gasto.getCategoria().getId());
                cmbSubcategoria.setValue(gasto.getSubcategoria());

                // 🟣 Precio
                txtValorUnitario.setText(
                    gasto.getValorUnitario().toString()
                );

                // 🟣 Cantidad por defecto
                txtCantidad.setText("");

                // 🟣 Total
                calcularTotal();

                // 🔑 RESTAURAR TEXTO DEL PRODUCTO (en el siguiente frame)
                Platform.runLater(() -> {
                    cmbProducto.getEditor().setText(textoProducto);
                    cmbProducto.getEditor().positionCaret(textoProducto.length());
                });
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

    private void moverFoco(Control control) {
        Platform.runLater(control::requestFocus);
    }

    
    @FXML
    public void handleGuardar() {
        if (!validarCampos()) return;
        
        try {
            gastoService.registrarGasto(
                usuarioId,
                cmbCategoria.getValue().getId(),
                cmbSubcategoria.getValue().getId(),
                cmbProducto.getEditor().getText(),
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
        cmbCategoria.setValue(null);
        cmbSubcategoria.getItems().clear();
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
        String producto = cmbProducto.getEditor().getText();

        if (producto == null || producto.isBlank()) {
            mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
            return false;
        }
        return true;
    }
    
    private void limpiarFormulario() {

        // 🔑 NO limpiar categoría ni subcategoría
        // Se asume que el usuario seguirá en la misma

        cmbProducto.getEditor().clear();
        cmbProducto.setValue(null);
        cmbProducto.getItems().clear();
        
        cmbCategoria.getEditor().clear();
        cmbCategoria.setValue(null);
        cmbCategoria.getItems().clear();
        
        cmbSubcategoria.getEditor().clear();
        cmbSubcategoria.setValue(null);
        cmbSubcategoria.getItems().clear();

        txtCantidad.setText("");
        txtValorUnitario.clear();
        txtValorTotal.setText("0.00");
        txtNotas.clear();

        // 🔥 Foco inmediato al producto para el siguiente gasto
        Platform.runLater(() -> {
            cmbProducto.getEditor().requestFocus();
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