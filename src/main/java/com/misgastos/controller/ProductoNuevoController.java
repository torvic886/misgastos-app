package com.misgastos.controller;

import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;
import com.misgastos.service.CategoriaService;
import com.misgastos.util.SeleccionProductoNueva;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductoNuevoController {

    @FXML
    private Label lblMensaje;

    @FXML
    private ComboBox<Categoria> cmbCategoria;

    @FXML
    private ComboBox<Subcategoria> cmbSubcategoria;

    @FXML
    private CheckBox chkNuevaCategoria;

    @FXML
    private CheckBox chkNuevaSubcategoria;

    @Autowired
    private CategoriaService categoriaService;

    // 👉 Resultado que será leído desde RegistroGastoController
    private SeleccionProductoNueva resultado;

    @FXML
    public void initialize() {
        cargarCategorias();
        configurarNuevaCategoria();
        configurarNuevaSubcategoria();
        configurarCargaDeSubcategorias();
    }
    
    private void cargarCategorias() {
        // Cargar categorías existentes
        cmbCategoria.getItems().setAll(
            categoriaService.listarCategorias()
        );
    }

    private void configurarNuevaCategoria() {
        // Nueva categoría → Combo editable
        chkNuevaCategoria.selectedProperty().addListener((obs, oldVal, nuevo) -> {
            cmbCategoria.setEditable(nuevo);
            if (nuevo) {
                cmbCategoria.getSelectionModel().clearSelection();
            }
        });
    }

    private void configurarNuevaSubcategoria() {
        // Nueva subcategoría → Combo editable
        chkNuevaSubcategoria.selectedProperty().addListener((obs, oldVal, nuevo) -> {
            cmbSubcategoria.setEditable(nuevo);
            if (nuevo) {
                cmbSubcategoria.getSelectionModel().clearSelection();
            }
        });
    }

    private void configurarCargaDeSubcategorias() {
        // Al seleccionar categoría → cargar subcategorías
        cmbCategoria.setOnAction(e -> {
            Categoria categoria = cmbCategoria.getValue();
            if (categoria != null) {
                cmbSubcategoria.getItems().setAll(
                    categoriaService.listarSubcategoriasPorCategoria(
                        categoria.getId()
                    )
                );
            }
        });
    }

    /**
     * Se llama desde RegistroGastoController
     */
    public void setProducto(String producto) {
        lblMensaje.setText(
            "El producto \"" + producto + "\" no existe.\n" +
            "Selecciona o crea categoría y subcategoría:"
        );
    }

    @FXML
    private void handleGuardar() {

        Categoria categoria;
        Subcategoria subcategoria;

        // 🟢 Categoría
        if (chkNuevaCategoria.isSelected()) {
            String nombreCat = cmbCategoria.getEditor().getText();

            if (nombreCat == null || nombreCat.isBlank()) {
                return;
            }

            categoria = categoriaService.crearSiNoExiste(nombreCat);

        } else {
            categoria = cmbCategoria.getValue();
        }

        if (categoria == null) return;

        // 🟢 Subcategoría
        if (chkNuevaSubcategoria.isSelected()) {
            String nombreSub = cmbSubcategoria.getEditor().getText();

            if (nombreSub == null || nombreSub.isBlank()) {
                return;
            }

            subcategoria = categoriaService.crearSubcategoriaSiNoExiste(
                categoria.getId(),
                nombreSub
            );

        } else {
            subcategoria = cmbSubcategoria.getValue();
        }

        if (subcategoria == null) return;

        // 👉 Guardar resultado para el controller padre
        resultado = new SeleccionProductoNueva(categoria, subcategoria);

        cerrar();
    }

    @FXML
    private void handleCancelar() {
        resultado = null;
        cerrar();
    }

    private void cerrar() {
        Stage stage = (Stage) cmbCategoria.getScene().getWindow();
        stage.close();
    }

    public SeleccionProductoNueva getResultado() {
        return resultado;
    }
}
