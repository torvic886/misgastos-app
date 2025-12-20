package com.misgastos.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TextField;


import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.Subcategoria;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.GastoService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;


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
    
    @FXML private TableView<Gasto> tblUltimosGastos;

    @FXML private TableColumn<Gasto, LocalDate> colFecha;
    @FXML private TableColumn<Gasto, String> colCategoria;
    @FXML private TableColumn<Gasto, String> colProducto;
    @FXML private TableColumn<Gasto, Integer> colCantidad;
    @FXML private TableColumn<Gasto, BigDecimal> colValorUnitario;
    @FXML private TableColumn<Gasto, BigDecimal> colTotal;


    
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
        configurarTabla();       // 👈
        cargarUltimosGastos();   // 👈
        
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

        // 🔽 Flecha abajo → mostrar lista
        editor.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                cmbProducto.show();
            }
        });

        // ✏️ Autocomplete al escribir
        editor.textProperty().addListener((obs, oldText, newText) -> {

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

        // ✅ ÚNICO punto de autocompletado REAL
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
        System.out.println("🟢 handleGuardar() ejecutado");
        
        // ========== PASO 1: VALIDAR PRODUCTO ==========
        String producto = cmbProducto.getEditor().getText();
        if (producto == null || producto.trim().isBlank()) {
            mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
            cmbProducto.requestFocus();
            return;
        }
        producto = producto.trim();
        
        try {
            // ========== PASO 2: VERIFICAR EXISTENCIA (UNA SOLA VEZ) ==========
            boolean productoExiste = gastoService.existeProducto(producto);
            
            System.out.println("📦 Producto: " + producto);
            System.out.println("   Existe en BD: " + (productoExiste ? "SÍ" : "NO"));
            
            // ========== PASO 3: MANEJAR PRODUCTO NUEVO ==========
            if (!productoExiste) {
                System.out.println("🆕 Producto nuevo detectado, abriendo popup...");
                
                // Abrir popup para definir categoría/subcategoría
                SeleccionProductoNueva resultado = mostrarPopupProductoNuevo(producto);
                
                // Si canceló, salir sin guardar
                if (resultado == null) {
                    System.out.println("❌ Usuario canceló el popup");
                    return;
                }
                
                // Aplicar la selección del popup al formulario
                cmbCategoria.setValue(resultado.categoria);
                cargarSubcategorias(resultado.categoria.getId());
                cmbSubcategoria.setValue(resultado.subcategoria);
                
                System.out.println("✅ Popup completado:");
                System.out.println("   → Categoría: " + resultado.categoria.getNombre());
                System.out.println("   → Subcategoría: " + resultado.subcategoria.getNombre());
            } else {
                System.out.println("♻️ Producto existente, usando categoría actual del formulario");
                // No hacer nada, usar lo que ya está en el formulario
                // El usuario pudo haber cambiado categoría, subcategoría o precio
            }
            
            // ========== PASO 4: VALIDAR TODOS LOS CAMPOS ==========
            
            // Validar categoría
            if (cmbCategoria.getValue() == null) {
                mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
                cmbCategoria.requestFocus();
                return;
            }
            
            // Validar subcategoría
            if (cmbSubcategoria.getValue() == null) {
                mostrarAlerta("Validación", "Seleccione una subcategoría", Alert.AlertType.WARNING);
                cmbSubcategoria.requestFocus();
                return;
            }
            
            // Validar cantidad
            if (txtCantidad.getText() == null || txtCantidad.getText().trim().isEmpty()) {
                mostrarAlerta("Validación", "Ingrese la cantidad", Alert.AlertType.WARNING);
                txtCantidad.requestFocus();
                return;
            }
            
            // Validar valor unitario
            if (txtValorUnitario.getText() == null || txtValorUnitario.getText().trim().isEmpty()) {
                mostrarAlerta("Validación", "Ingrese el valor unitario", Alert.AlertType.WARNING);
                txtValorUnitario.requestFocus();
                return;
            }
            
            // Validar que cantidad y valor sean números válidos
            int cantidad;
            BigDecimal valorUnitario;
            
            try {
                cantidad = Integer.parseInt(txtCantidad.getText().trim());
                if (cantidad <= 0) {
                    mostrarAlerta("Validación", "La cantidad debe ser mayor a 0", Alert.AlertType.WARNING);
                    txtCantidad.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                mostrarAlerta("Validación", "La cantidad debe ser un número válido", Alert.AlertType.WARNING);
                txtCantidad.requestFocus();
                return;
            }
            
            try {
                valorUnitario = new BigDecimal(txtValorUnitario.getText().trim());
                if (valorUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    mostrarAlerta("Validación", "El valor debe ser mayor a 0", Alert.AlertType.WARNING);
                    txtValorUnitario.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                mostrarAlerta("Validación", "El valor unitario debe ser un número válido", Alert.AlertType.WARNING);
                txtValorUnitario.requestFocus();
                return;
            }
            
            // ========== PASO 5: GUARDAR EL GASTO ==========
            System.out.println("💾 Guardando gasto:");
            System.out.println("   → Producto: " + producto);
            System.out.println("   → Categoría: " + cmbCategoria.getValue().getNombre());
            System.out.println("   → Subcategoría: " + cmbSubcategoria.getValue().getNombre());
            System.out.println("   → Cantidad: " + cantidad);
            System.out.println("   → Valor unitario: $" + valorUnitario);
            System.out.println("   → Total: $" + valorUnitario.multiply(BigDecimal.valueOf(cantidad)));
            
            gastoService.registrarGasto(
                usuarioId,
                cmbCategoria.getValue().getId(),
                cmbSubcategoria.getValue().getId(),
                producto,
                cantidad,
                valorUnitario,
                txtNotas.getText()
            );
            
            System.out.println("✅ Gasto guardado exitosamente en la base de datos");
            
            // ========== PASO 6: ACTUALIZAR UI ==========
            mostrarAlerta("Éxito", "Gasto registrado correctamente", Alert.AlertType.INFORMATION);
            cargarUltimosGastos();
            limpiarFormulario();
            
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo guardar el gasto: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    
    @FXML
    public void handleLimpiar() {
        // ✅ Limpiar TODO (incluyendo categorías)
        // Este método es para resetear completamente el formulario
        
        cmbCategoria.setValue(null);
        cmbSubcategoria.getItems().clear();
        cmbSubcategoria.setValue(null);
        
        cmbProducto.getEditor().clear();
        cmbProducto.setValue(null);
        cmbProducto.getItems().clear();
        
        txtCantidad.setText("1");
        txtValorUnitario.clear();
        txtValorTotal.setText("0.00");
        txtNotas.clear();
        
        // ✅ Recargar categorías para empezar de cero
        cargarCategorias();
        
        // ✅ Foco en categoría
        Platform.runLater(() -> {
            cmbCategoria.requestFocus();
        });
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
        // ✅ MANTENER categoría y subcategoría seleccionadas
        // El usuario probablemente seguirá registrando gastos en la misma categoría
        
        // ❌ NO tocar cmbCategoria
        // ❌ NO tocar cmbSubcategoria
        
        // ✅ Limpiar solo producto y valores
        cmbProducto.getEditor().clear();
        cmbProducto.setValue(null);
        cmbProducto.getItems().clear();
        
        txtCantidad.setText("1"); // ✅ Valor por defecto
        txtValorUnitario.clear();
        txtValorTotal.setText("0.00");
        txtNotas.clear();
        
        // ✅ Foco en producto para siguiente gasto rápido
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
    
    private void configurarTabla() {
    	
    	tblUltimosGastos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        if (colValorUnitario == null) {
            throw new IllegalStateException(
                "ERROR FXML: colValorUnitario no está definido o fx:id incorrecto"
            );
        }
    	
        colFecha.setCellValueFactory(data ->
            new SimpleObjectProperty<>(data.getValue().getFecha())
        );

        colCategoria.setCellValueFactory(data ->
            new SimpleStringProperty(
                data.getValue().getCategoria().getNombre()
            )
        );

        colProducto.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getProducto())
        );

        colCantidad.setCellValueFactory(
            new PropertyValueFactory<>("cantidad")
        );

        colValorUnitario.setCellValueFactory(
            new PropertyValueFactory<>("valorUnitario")
        );

        colTotal.setCellValueFactory(
            new PropertyValueFactory<>("valorTotal")
        );
    }

    
    private void cargarUltimosGastos() {
        List<Gasto> gastos =
            gastoService.obtenerUltimosGastos(usuarioId, 10);

        tblUltimosGastos.setItems(
            FXCollections.observableArrayList(gastos)
        );
    }


    private void guardarGasto() {
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

            mostrarAlerta(
                "Éxito",
                "Gasto registrado correctamente",
                Alert.AlertType.INFORMATION
            );

            cargarUltimosGastos(); // 🔁 refresca tabla
            limpiarFormulario();

        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta(
                "Error",
                "No se pudo guardar el gasto",
                Alert.AlertType.ERROR
            );
        }
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
        
        // ========== UI del popup ==========
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new javafx.geometry.Insets(20));
        
        // Categoría
        Label lblCategoria = new Label("Categoría:");
        ComboBox<Categoria> cmbCat = new ComboBox<>();
        cmbCat.setPromptText("Selecciona una categoría");
        cmbCat.setMaxWidth(Double.MAX_VALUE);
        cmbCat.getItems().setAll(categoriaService.listarCategorias());
        
        // 🔑 Campo de texto separado para NUEVA categoría
        TextField txtNuevaCategoria = new TextField();
        txtNuevaCategoria.setPromptText("Escribe nueva categoría");
        txtNuevaCategoria.setMaxWidth(Double.MAX_VALUE);
        txtNuevaCategoria.setVisible(false);
        txtNuevaCategoria.setManaged(false);
        
        CheckBox chkNuevaCat = new CheckBox("Crear nueva categoría");
        
        // Subcategoría
        Label lblSubcategoria = new Label("Subcategoría:");
        ComboBox<Subcategoria> cmbSub = new ComboBox<>();
        cmbSub.setPromptText("Selecciona una subcategoría");
        cmbSub.setMaxWidth(Double.MAX_VALUE);
        cmbSub.setDisable(true);
        
        // 🔑 Campo de texto separado para NUEVA subcategoría
        TextField txtNuevaSubcategoria = new TextField();
        txtNuevaSubcategoria.setPromptText("Escribe nueva subcategoría");
        txtNuevaSubcategoria.setMaxWidth(Double.MAX_VALUE);
        txtNuevaSubcategoria.setVisible(false);
        txtNuevaSubcategoria.setManaged(false);
        txtNuevaSubcategoria.setDisable(true);
        
        CheckBox chkNuevaSub = new CheckBox("Crear nueva subcategoría");
        chkNuevaSub.setDisable(true);
        
        // Layout
        grid.add(lblCategoria, 0, 0);
        grid.add(cmbCat, 1, 0);
        grid.add(txtNuevaCategoria, 1, 0); // Mismo espacio que cmbCat
        grid.add(chkNuevaCat, 1, 1);
        
        grid.add(lblSubcategoria, 0, 2);
        grid.add(cmbSub, 1, 2);
        grid.add(txtNuevaSubcategoria, 1, 2); // Mismo espacio que cmbSub
        grid.add(chkNuevaSub, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        // ========== Lógica de interacción ==========
        
        // ✅ Checkbox categoría → alternar entre ComboBox y TextField
        chkNuevaCat.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                // Ocultar ComboBox, mostrar TextField
                cmbCat.setVisible(false);
                cmbCat.setManaged(false);
                txtNuevaCategoria.setVisible(true);
                txtNuevaCategoria.setManaged(true);
                txtNuevaCategoria.requestFocus();
                
                // Limpiar subcategorías
                cmbSub.getItems().clear();
                cmbSub.setDisable(true);
                chkNuevaSub.setDisable(true);
            } else {
                // Mostrar ComboBox, ocultar TextField
                txtNuevaCategoria.setVisible(false);
                txtNuevaCategoria.setManaged(false);
                cmbCat.setVisible(true);
                cmbCat.setManaged(true);
                txtNuevaCategoria.clear();
            }
        });
        
        // ✅ Checkbox subcategoría → alternar entre ComboBox y TextField
        chkNuevaSub.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                cmbSub.setVisible(false);
                cmbSub.setManaged(false);
                txtNuevaSubcategoria.setVisible(true);
                txtNuevaSubcategoria.setManaged(true);
                txtNuevaSubcategoria.setDisable(false);
                txtNuevaSubcategoria.requestFocus();
            } else {
                txtNuevaSubcategoria.setVisible(false);
                txtNuevaSubcategoria.setManaged(false);
                cmbSub.setVisible(true);
                cmbSub.setManaged(true);
                txtNuevaSubcategoria.clear();
            }
        });
        
        // ✅ Al seleccionar categoría existente → cargar subcategorías
        cmbCat.setOnAction(e -> {
            Categoria cat = cmbCat.getValue();
            if (cat != null) {
                List<Subcategoria> subs = categoriaService.listarSubcategoriasPorCategoria(cat.getId());
                cmbSub.getItems().setAll(subs);
                cmbSub.setDisable(false);
                chkNuevaSub.setDisable(false);
                
                if (!subs.isEmpty()) {
                    cmbSub.getSelectionModel().selectFirst();
                }
            }
        });
        
        // ✅ Al escribir nueva categoría → habilitar subcategoría
        txtNuevaCategoria.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean tieneTexto = newVal != null && !newVal.trim().isEmpty();
            cmbSub.setDisable(!tieneTexto);
            txtNuevaSubcategoria.setDisable(!tieneTexto);
            chkNuevaSub.setDisable(!tieneTexto);
            
            // Limpiar subcategorías porque la categoría cambió
            if (tieneTexto) {
                cmbSub.getItems().clear();
            }
        });
        
        // ========== Validación del botón Guardar ==========
        Node btnGuardarNode = dialog.getDialogPane().lookupButton(btnGuardar);
        btnGuardarNode.setDisable(true);
        
        Runnable validar = () -> {
            boolean categoriaOk;
            boolean subcategoriaOk;
            
            // Validar categoría
            if (chkNuevaCat.isSelected()) {
                categoriaOk = txtNuevaCategoria.getText() != null && 
                             !txtNuevaCategoria.getText().trim().isEmpty();
            } else {
                categoriaOk = cmbCat.getValue() != null;
            }
            
            // Validar subcategoría
            if (chkNuevaSub.isSelected()) {
                subcategoriaOk = txtNuevaSubcategoria.getText() != null && 
                                !txtNuevaSubcategoria.getText().trim().isEmpty();
            } else {
                subcategoriaOk = cmbSub.getValue() != null;
            }
            
            btnGuardarNode.setDisable(!(categoriaOk && subcategoriaOk));
        };
        
        // Listeners para validación
        cmbCat.valueProperty().addListener((o, old, nue) -> validar.run());
        cmbSub.valueProperty().addListener((o, old, nue) -> validar.run());
        txtNuevaCategoria.textProperty().addListener((o, old, nue) -> validar.run());
        txtNuevaSubcategoria.textProperty().addListener((o, old, nue) -> validar.run());
        chkNuevaCat.selectedProperty().addListener((o, old, nue) -> validar.run());
        chkNuevaSub.selectedProperty().addListener((o, old, nue) -> validar.run());
        
        // ========== Convertir resultado ==========
        dialog.setResultConverter(buttonType -> {
            if (buttonType != btnGuardar) {
                return null;
            }
            
            try {
                Categoria categoria;
                Subcategoria subcategoria;
                
                // ✅ Obtener o crear categoría
                if (chkNuevaCat.isSelected()) {
                    String nombreCat = txtNuevaCategoria.getText().trim();
                    categoria = categoriaService.crearSiNoExiste(nombreCat);
                    System.out.println("✅ Categoría creada: " + nombreCat);
                } else {
                    categoria = cmbCat.getValue();
                    if (categoria == null) {
                        mostrarAlerta("Error", "Debe seleccionar una categoría", Alert.AlertType.ERROR);
                        return null;
                    }
                    System.out.println("✅ Categoría seleccionada: " + categoria.getNombre());
                }
                
                // ✅ Obtener o crear subcategoría
                if (chkNuevaSub.isSelected()) {
                    String nombreSub = txtNuevaSubcategoria.getText().trim();
                    subcategoria = categoriaService.crearSubcategoriaSiNoExiste(
                        categoria.getId(), 
                        nombreSub
                    );
                    System.out.println("✅ Subcategoría creada: " + nombreSub);
                } else {
                    subcategoria = cmbSub.getValue();
                    if (subcategoria == null) {
                        mostrarAlerta("Error", "Debe seleccionar una subcategoría", Alert.AlertType.ERROR);
                        return null;
                    }
                    System.out.println("✅ Subcategoría seleccionada: " + subcategoria.getNombre());
                }
                
                return new SeleccionProductoNueva(categoria, subcategoria);
                
            } catch (Exception e) {
                e.printStackTrace();
                mostrarAlerta("Error", "No se pudo procesar la selección: " + e.getMessage(), Alert.AlertType.ERROR);
                return null;
            }
        });
        
        // Mostrar y esperar
        return dialog.showAndWait().orElse(null);
    }

    private static class SeleccionProductoNueva {
        Categoria categoria;
        Subcategoria subcategoria;

        SeleccionProductoNueva(Categoria c, Subcategoria s) {
            this.categoria = c;
            this.subcategoria = s;
        }
    }
    

}