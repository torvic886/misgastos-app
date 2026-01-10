package com.misgastos.controller;

import javafx.application.Platform;

import javafx.collections.FXCollections;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.Subcategoria;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.GastoService;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;
import java.text.NumberFormat;
import java.util.Locale;


@Component
public class RegistroGastoController {
    
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private ComboBox<Subcategoria> cmbSubcategoria;
    @FXML private ComboBox<String> cmbProducto;
    @FXML private TextField txtCantidad;
    @FXML private TextField txtValorUnitario;
    @FXML private TextField txtValorTotal;
    @FXML private TextArea txtNotas;
    @FXML private TableView<Gasto> tblUltimosGastos;
    @FXML private TableColumn<Gasto, LocalDate> colFecha;
    @FXML private TableColumn<Gasto, String> colCategoria;
    @FXML private TableColumn<Gasto, String> colProducto;
    @FXML private TableColumn<Gasto, Integer> colCantidad;
    @FXML private TableColumn<Gasto, BigDecimal> colValorUnitario;
    @FXML private TableColumn<Gasto, BigDecimal> colTotal;
    @FXML private Label lblCedula;
    @FXML private TextField txtCedula;
    
    @Autowired private CategoriaService categoriaService;
    @Autowired private GastoService gastoService;
    
    private Long usuarioId = 1L;
    
    // ✅ NUEVO: Flag para evitar abrir el popup múltiples veces
    private boolean productoNuevoYaConfigurado = false;
    
    @FXML
    public void initialize() {
        configurarCalculoAutomatico();
        configurarProductoAutocomplete();
        configurarGuardarConEnter();
        configurarTabla();
        cargarUltimosGastos();
        configurarValidacionCedula();
        configurarCampoCedula();
        
        Platform.runLater(() -> cmbProducto.requestFocus());
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
    
    private void configurarProductoAutocomplete() {
        cmbProducto.setEditable(true);
        TextField editor = cmbProducto.getEditor();
        
        editor.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                cmbProducto.show();
            }
        });
        
        editor.textProperty().addListener((obs, oldText, newText) -> {
            // ✅ NUEVO: Resetear flag cuando se cambia el producto
            productoNuevoYaConfigurado = false;
            
            if (newText == null || newText.isBlank()) {
                cmbProducto.hide();
                return;
            }
            
            List<String> productos = gastoService.buscarProductos(newText);
            if (productos.isEmpty()) {
                cmbProducto.hide();
                return;
            }
            
            cmbProducto.getItems().setAll(productos);
            cmbProducto.show();
        });
        
        cmbProducto.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                System.out.println("✅ Producto seleccionado del autocomplete: '" + newVal + "'");
                autocompletarDatosPorProducto(newVal);
                // ✅ El focus ya se maneja dentro de autocompletarDatosPorProducto
            }
        });
    }
    
    private void autocompletarDatosPorProducto(String producto) {
        if (producto == null || producto.isBlank()) return;
        
        String textoProducto = producto.trim();
        
        System.out.println("🔄 Autocompletando datos para: '" + textoProducto + "'");
        
        gastoService.buscarUltimoGastoPorProducto(textoProducto).ifPresentOrElse(
            gasto -> {
                System.out.println("   ✅ Gasto anterior encontrado");
                System.out.println("      → Categoría: " + gasto.getCategoria().getNombre());
                System.out.println("      → Subcategoría: " + gasto.getSubcategoria().getNombre());
                System.out.println("      → Valor: " + gasto.getValorUnitario());
                
                cargarCategoriasIniciales();
                cmbCategoria.setValue(gasto.getCategoria());
                cargarSubcategorias(gasto.getCategoria().getId());
                cmbSubcategoria.setValue(gasto.getSubcategoria());
                txtValorUnitario.setText(gasto.getValorUnitario().toString());
                txtCantidad.setText("");
                
                calcularTotal();

                Platform.runLater(() -> {
                    cmbProducto.setValue(textoProducto);
                    cmbProducto.getEditor().setText(textoProducto);
                    cmbProducto.getEditor().positionCaret(textoProducto.length());
                    
                    // ✅ NUEVO: Ir a Cédula si está visible, sino a Cantidad
                    if (txtCedula.isVisible()) {
                        txtCedula.requestFocus();
                        System.out.println("   → Foco en Cédula");
                    } else {
                        txtCantidad.requestFocus();
                        System.out.println("   → Foco en Cantidad");
                    }
                });

                productoNuevoYaConfigurado = true;
            },
            () -> {
                System.out.println("   ⚠️ No se encontró gasto anterior para este producto");
            }
        );
    }
    
    private void configurarGuardarConEnter() {
        // ✅ NUEVO: Enter en Cédula va a Cantidad
        txtCedula.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtCantidad.requestFocus();
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
    
    @FXML
    public void handleGuardar() {
        System.out.println("🟢 handleGuardar() ejecutado");
        
        String producto = obtenerProductoSeleccionado();
        
        if (producto == null || producto.trim().isBlank()) {
            mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
            cmbProducto.requestFocus();
            return;
        }
        producto = producto.trim();
        
        try {
            if (!productoNuevoYaConfigurado) {
                boolean productoExiste = gastoService.existeProducto(producto);
                System.out.println("📦 Producto: " + producto);
                System.out.println("   Existe: " + (productoExiste ? "SÍ" : "NO"));
                
                if (!productoExiste) {
                    System.out.println("🆕 Producto nuevo, abriendo popup...");
                    
                    SeleccionProductoNueva resultado = mostrarPopupProductoNuevo(producto);
                    
                    if (resultado == null) {
                        System.out.println("❌ Popup cancelado");
                        return;
                    }
                    
                    cargarCategoriasIniciales();
                    cmbCategoria.setValue(resultado.categoria);
                    cargarSubcategorias(resultado.categoria.getId());
                    cmbSubcategoria.setValue(resultado.subcategoria);
                    
                    productoNuevoYaConfigurado = true;
                    
                    System.out.println("✅ Popup completado:");
                    System.out.println("   → Categoría: " + resultado.categoria.getNombre());
                    System.out.println("   → Subcategoría: " + resultado.subcategoria.getNombre());
                    
                    txtCantidad.setText("");
                    
                    if (txtCantidad.getText() == null || txtCantidad.getText().trim().isEmpty()) {
                        mostrarAlerta("Validación", "Ingrese la cantidad", Alert.AlertType.WARNING);
                        txtCantidad.requestFocus();
                        return;
                    }
                    
                    if (txtValorUnitario.getText() == null || txtValorUnitario.getText().trim().isEmpty()) {
                        mostrarAlerta("Validación", "Ingrese el valor unitario", Alert.AlertType.WARNING);
                        txtValorUnitario.requestFocus();
                        return;
                    }
                }
            } else {
                System.out.println("✅ Producto ya configurado anteriormente, omitiendo popup");
            }
            
            if (cmbCategoria.getValue() == null) {
                mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
                cmbCategoria.requestFocus();
                return;
            }
            
            if (cmbSubcategoria.getValue() == null) {
                mostrarAlerta("Validación", "Seleccione una subcategoría", Alert.AlertType.WARNING);
                cmbSubcategoria.requestFocus();
                return;
            }
            
            if (txtCantidad.getText() == null || txtCantidad.getText().trim().isEmpty()) {
                mostrarAlerta("Validación", "Ingrese la cantidad", Alert.AlertType.WARNING);
                txtCantidad.requestFocus();
                return;
            }
            
            if (txtValorUnitario.getText() == null || txtValorUnitario.getText().trim().isEmpty()) {
                mostrarAlerta("Validación", "Ingrese el valor unitario", Alert.AlertType.WARNING);
                txtValorUnitario.requestFocus();
                return;
            }
            
            // ============================================================
            // ✅ AGREGAR ESTAS LÍNEAS AQUÍ (después de las validaciones)
            // ============================================================
            String cedula = null;
            if (txtCedula.isVisible() && txtCedula.getText() != null && !txtCedula.getText().trim().isEmpty()) {
                cedula = txtCedula.getText().trim();
                System.out.println("📋 Cédula ingresada: " + cedula);
            }
            
            // ✅ MODIFICAR ESTA LLAMADA (agregar cedula al final)
            gastoService.registrarGasto(
                usuarioId,
                cmbCategoria.getValue().getId(),
                cmbSubcategoria.getValue().getId(),
                producto,
                Integer.parseInt(txtCantidad.getText().trim()),
                new BigDecimal(txtValorUnitario.getText().trim()),
                txtNotas.getText(),
                cedula  // ✅ Este es el nuevo parámetro
            );
            
            System.out.println("✅ Gasto guardado exitosamente");
            
            mostrarAlerta("Éxito", "Gasto registrado correctamente", Alert.AlertType.INFORMATION);
            cargarUltimosGastos();
            limpiarFormulario();
            
        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Cantidad o valor inválido", Alert.AlertType.ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo guardar: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    public void handleLimpiar() {
        cmbCategoria.setValue(null);
        cmbSubcategoria.getItems().clear();
        cmbSubcategoria.setValue(null);
        limpiarFormulario();
    }
    
    private void limpiarFormulario() {
        // ✅ Resetear el flag
        productoNuevoYaConfigurado = false;
        
        cmbProducto.getEditor().clear();
        cmbProducto.setValue(null);
        cmbProducto.getItems().clear();
        
        cmbCategoria.setValue(null);
        cmbSubcategoria.setValue(null);
        cmbSubcategoria.getItems().clear();
        
        txtCantidad.clear(); // ✅ CAMBIADO: Dejar vacío en lugar de "1"
        txtValorUnitario.clear();
        txtValorTotal.setText("0.00");
        txtNotas.clear();
        
        Platform.runLater(() -> cmbProducto.requestFocus());
    }
    
    private void cargarCategoriasIniciales() {
        List<Categoria> categorias = categoriaService.listarCategorias();
        cmbCategoria.getItems().setAll(categorias);
        
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
    
    private void configurarTabla() {
        NumberFormat formatoPeso = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        
        tblUltimosGastos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        colFecha.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getFecha()));
        colCategoria.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategoria().getNombre()));
        colProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProducto()));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        
        // ✅ FORMATO PESO COLOMBIANO
        colValorUnitario.setCellValueFactory(new PropertyValueFactory<>("valorUnitario"));
        colValorUnitario.setCellFactory(col -> new TableCell<Gasto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatoPeso.format(item));
            }
        });
        
        colTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        colTotal.setCellFactory(col -> new TableCell<Gasto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatoPeso.format(item));
            }
        });
    }
    
    private void cargarUltimosGastos() {
        List<Gasto> gastos = gastoService.obtenerUltimosGastos(usuarioId, 10);
        tblUltimosGastos.setItems(FXCollections.observableArrayList(gastos));
    }
    
    private SeleccionProductoNueva mostrarPopupProductoNuevo(String producto) {
        Dialog<SeleccionProductoNueva> dialog = new Dialog<>();
        dialog.setTitle("🆕 Producto Nuevo");
        dialog.setHeaderText(
            "El producto \"" + producto + "\" no existe.\n" +
            "Selecciona o crea su categoría y subcategoría:"
        );
        
        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new javafx.geometry.Insets(20));
        
        Label lblCategoria = new Label("Categoría:");
        ComboBox<Categoria> cmbCat = new ComboBox<>();
        cmbCat.setPromptText("Selecciona categoría");
        cmbCat.setMaxWidth(Double.MAX_VALUE);
        cmbCat.getItems().setAll(categoriaService.listarCategorias());
        
        TextField txtNuevaCat = new TextField();
        txtNuevaCat.setPromptText("Nueva categoría");
        txtNuevaCat.setMaxWidth(Double.MAX_VALUE);
        txtNuevaCat.setVisible(false);
        txtNuevaCat.setManaged(false);
        
        CheckBox chkNuevaCat = new CheckBox("Crear nueva categoría");
        
        Label lblSubcat = new Label("Subcategoría:");
        ComboBox<Subcategoria> cmbSub = new ComboBox<>();
        cmbSub.setPromptText("Selecciona subcategoría");
        cmbSub.setMaxWidth(Double.MAX_VALUE);
        cmbSub.setDisable(true);
        
        TextField txtNuevaSub = new TextField();
        txtNuevaSub.setPromptText("Nueva subcategoría");
        txtNuevaSub.setMaxWidth(Double.MAX_VALUE);
        txtNuevaSub.setVisible(false);
        txtNuevaSub.setManaged(false);
        txtNuevaSub.setDisable(true);
        
        CheckBox chkNuevaSub = new CheckBox("Crear nueva subcategoría");
        chkNuevaSub.setDisable(true);
        
        grid.add(lblCategoria, 0, 0);
        grid.add(cmbCat, 1, 0);
        grid.add(txtNuevaCat, 1, 0);
        grid.add(chkNuevaCat, 1, 1);
        grid.add(lblSubcat, 0, 2);
        grid.add(cmbSub, 1, 2);
        grid.add(txtNuevaSub, 1, 2);
        grid.add(chkNuevaSub, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        chkNuevaCat.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                cmbCat.setVisible(false);
                cmbCat.setManaged(false);
                txtNuevaCat.setVisible(true);
                txtNuevaCat.setManaged(true);
                txtNuevaCat.requestFocus();
                cmbSub.getItems().clear();
                cmbSub.setDisable(true);
                chkNuevaSub.setDisable(true);
            } else {
                txtNuevaCat.setVisible(false);
                txtNuevaCat.setManaged(false);
                cmbCat.setVisible(true);
                cmbCat.setManaged(true);
                txtNuevaCat.clear();
            }
        });
        
        chkNuevaSub.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                cmbSub.setVisible(false);
                cmbSub.setManaged(false);
                txtNuevaSub.setVisible(true);
                txtNuevaSub.setManaged(true);
                txtNuevaSub.setDisable(false);
                txtNuevaSub.requestFocus();
            } else {
                txtNuevaSub.setVisible(false);
                txtNuevaSub.setManaged(false);
                cmbSub.setVisible(true);
                cmbSub.setManaged(true);
                txtNuevaSub.clear();
            }
        });
        
        cmbCat.setOnAction(e -> {
            Categoria cat = cmbCat.getValue();
            if (cat != null) {
                List<Subcategoria> subs = categoriaService.listarSubcategoriasPorCategoria(cat.getId());
                cmbSub.getItems().setAll(subs);
                cmbSub.setDisable(false);
                chkNuevaSub.setDisable(false);
                if (!subs.isEmpty()) cmbSub.getSelectionModel().selectFirst();
            }
        });
        
        txtNuevaCat.textProperty().addListener((obs, old, nue) -> {
            boolean ok = nue != null && !nue.trim().isEmpty();
            cmbSub.setDisable(!ok);
            txtNuevaSub.setDisable(!ok);
            chkNuevaSub.setDisable(!ok);
            if (ok) cmbSub.getItems().clear();
        });
        
        Node btnOk = dialog.getDialogPane().lookupButton(btnGuardar);
        btnOk.setDisable(true);
        
        Runnable validar = () -> {
            boolean catOk = chkNuevaCat.isSelected() 
                ? (txtNuevaCat.getText() != null && !txtNuevaCat.getText().trim().isEmpty())
                : cmbCat.getValue() != null;
            
            boolean subOk = chkNuevaSub.isSelected()
                ? (txtNuevaSub.getText() != null && !txtNuevaSub.getText().trim().isEmpty())
                : cmbSub.getValue() != null;
            
            btnOk.setDisable(!(catOk && subOk));
        };
        
        cmbCat.valueProperty().addListener((o,a,b) -> validar.run());
        cmbSub.valueProperty().addListener((o,a,b) -> validar.run());
        txtNuevaCat.textProperty().addListener((o,a,b) -> validar.run());
        txtNuevaSub.textProperty().addListener((o,a,b) -> validar.run());
        chkNuevaCat.selectedProperty().addListener((o,a,b) -> validar.run());
        chkNuevaSub.selectedProperty().addListener((o,a,b) -> validar.run());
        
        dialog.setResultConverter(bt -> {
            if (bt != btnGuardar) return null;
            
            try {
                Categoria cat = chkNuevaCat.isSelected()
                    ? categoriaService.crearSiNoExiste(txtNuevaCat.getText().trim())
                    : cmbCat.getValue();
                
                Subcategoria sub = chkNuevaSub.isSelected()
                    ? categoriaService.crearSubcategoriaSiNoExiste(cat.getId(), txtNuevaSub.getText().trim())
                    : cmbSub.getValue();
                
                return new SeleccionProductoNueva(cat, sub);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
        
        return dialog.showAndWait().orElse(null);
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
    
    private String obtenerProductoSeleccionado() {
        String producto = null;
        
        if (cmbProducto.getValue() != null && !cmbProducto.getValue().isBlank()) {
            producto = cmbProducto.getValue();
            System.out.println("📦 Producto obtenido de ComboBox.getValue(): '" + producto + "'");
        }
        else if (cmbProducto.getEditor().getText() != null && !cmbProducto.getEditor().getText().isBlank()) {
            producto = cmbProducto.getEditor().getText();
            System.out.println("📦 Producto obtenido de Editor.getText(): '" + producto + "'");
        }
        
        if (producto != null) {
            producto = producto.trim().replaceAll("\\s+", " ");
            System.out.println("   → Normalizado: '" + producto + "'");
        }
        
        return producto;
    }
    
    private static class SeleccionProductoNueva {
        final Categoria categoria;
        final Subcategoria subcategoria;
        
        SeleccionProductoNueva(Categoria c, Subcategoria s) {
            this.categoria = c;
            this.subcategoria = s;
        }
    }
    
 // ✅ NUEVO: Validar que solo se ingresen números en cédula
    private void configurarValidacionCedula() {
        txtCedula.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.matches("\\d*")) {
                txtCedula.setText(oldVal);
            }
        });
    }

    // ✅ NUEVO: Mostrar/ocultar campo de cédula según categoría y subcategoría
    private void configurarCampoCedula() {
        // Listener cuando cambia la categoría
        cmbCategoria.valueProperty().addListener((obs, oldVal, newVal) -> {
            verificarMostrarCampoCedula();
        });
        
        // Listener cuando cambia la subcategoría
        cmbSubcategoria.valueProperty().addListener((obs, oldVal, newVal) -> {
            verificarMostrarCampoCedula();
        });
    }

    // ✅ NUEVO: Verificar si se debe mostrar el campo de cédula
    private void verificarMostrarCampoCedula() {
        boolean mostrar = false;
        
        Categoria categoria = cmbCategoria.getValue();
        Subcategoria subcategoria = cmbSubcategoria.getValue();
        
        if (categoria != null && subcategoria != null) {
            // Verificar si es "Beneficios Clientes" Y la subcategoría contiene "Bono"
            String nombreCategoria = categoria.getNombre();
            String nombreSubcategoria = subcategoria.getNombre();
            
            mostrar = nombreCategoria.equalsIgnoreCase("Beneficios Clientes") 
                      && nombreSubcategoria.toLowerCase().contains("bono");
        }
        
        // Mostrar u ocultar el campo
        lblCedula.setVisible(mostrar);
        lblCedula.setManaged(mostrar);
        txtCedula.setVisible(mostrar);
        txtCedula.setManaged(mostrar);
        
        // Limpiar el campo si se oculta
        if (!mostrar) {
            txtCedula.clear();
        }
        
        System.out.println("🔍 Campo cedula: " + (mostrar ? "VISIBLE" : "OCULTO"));
    }
    
}