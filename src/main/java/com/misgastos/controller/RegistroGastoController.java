package com.misgastos.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;
import java.text.NumberFormat;
import java.util.Locale;

import javafx.scene.control.SelectionMode;
import javafx.collections.ObservableList;

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
    @FXML private TableColumn<Gasto, String> colHora;
    @FXML private Label lblTotalGastos;
    
    @Autowired private CategoriaService categoriaService;
    @Autowired private GastoService gastoService;
    
    private Long usuarioId = 1L;
    
    // ✅ Flags de control
    private boolean productoNuevoYaConfigurado = false;
    private String ultimoProductoAutocompletado = null;
    private boolean formateandoMoneda = false;
    private boolean ignorarCambioTexto = false;
    private Set<String> productosYaUsados = new HashSet<>();
    
    @FXML
    public void initialize() {
        configurarCalculoAutomatico();
        configurarProductoAutocomplete();
        configurarGuardarConEnter();
        configurarTabla();
        cargarUltimosGastos();
        configurarCopiaPegadoTabla();
        //cargarUltimosGastos();
        configurarValidacionCedula();
        configurarCampoCedula();
        configurarFormatoMoneda(); // ✅ NUEVO
        
        Platform.runLater(() -> cmbProducto.requestFocus());
    }
    
    private void configurarCalculoAutomatico() {
        txtCantidad.textProperty().addListener((obs, old, newVal) -> calcularTotal());
        txtValorUnitario.textProperty().addListener((obs, old, newVal) -> calcularTotal());
    }
    
    private void calcularTotal() {
        try {
            // Obtener cantidad
            int cantidad = Integer.parseInt(txtCantidad.getText());
            
            // Obtener valor unitario (quitar formato si existe)
            String valorTexto = txtValorUnitario.getText().replaceAll("[^0-9]", "");
            if (valorTexto.isEmpty()) {
                txtValorTotal.setText("0.00");
                return;
            }
            
            BigDecimal valorUnit = new BigDecimal(valorTexto);
            BigDecimal total = valorUnit.multiply(BigDecimal.valueOf(cantidad));
            
            // Formatear el total
            NumberFormat formatoPeso = NumberFormat.getInstance(new Locale("es", "CO"));
            txtValorTotal.setText("$ " + formatoPeso.format(total));
        } catch (Exception e) {
            txtValorTotal.setText("$ 0");
        }
    }
    
    private void configurarProductoAutocomplete() {
        cmbProducto.setEditable(true);
        TextField editor = cmbProducto.getEditor();
        
        // ✅ EventFilter para capturar Enter ANTES que el ComboBox lo procese
        cmbProducto.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String textoActual = editor.getText();
                // Si hay texto, avanzar al siguiente campo
                if (textoActual != null && !textoActual.trim().isEmpty()) {
                    event.consume(); // Consumir el evento para que no lo procese el ComboBox
                    Platform.runLater(() -> {
                        if (txtCedula.isVisible()) {
                            txtCedula.requestFocus();
                        } else {
                            txtCantidad.requestFocus();
                        }
                    });
                }
            }
        });
        
        editor.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                cmbProducto.show();
            }
        });
        
        // ✅ Este listener se dispara cuando el usuario ESCRIBE
        editor.textProperty().addListener((obs, oldText, newText) -> {
            // ✅ Ignorar cambios programáticos
            if (ignorarCambioTexto) {
                return;
            }
            
            System.out.println("🔍 textProperty cambió: '" + oldText + "' -> '" + newText + "'");
            
            // ✅ Si el texto cambió y es diferente al último autocompletado, resetear
            if (newText == null || !newText.equals(ultimoProductoAutocompletado)) {
                productoNuevoYaConfigurado = false;
                System.out.println("   🔄 Flag reseteado (producto diferente)");
            }
            
            if (newText == null || newText.isBlank()) {
                // ✅ Cerrar dropdown, limpiar selección Y LUEGO limpiar items
                Platform.runLater(() -> {
                    cmbProducto.hide();
                    cmbProducto.getSelectionModel().clearSelection();
                    cmbProducto.getItems().clear();
                });
                return;
            }
            
            List<String> productos = gastoService.buscarProductos(newText);
            if (productos.isEmpty()) {
                // ✅ NO limpiar el ComboBox aquí, solo cerrar el dropdown
                cmbProducto.hide();
                return;
            }
            
            cmbProducto.getItems().setAll(productos);
            cmbProducto.show();
        });
        
        // ✅ Este listener se dispara cuando se SELECCIONA del dropdown
        cmbProducto.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                System.out.println("✅ Producto seleccionado del autocomplete: '" + newVal + "'");
                
                // ✅ SIEMPRE autocompletar, NUNCA saltar automáticamente
                autocompletarDatosPorProducto(newVal);
                productosYaUsados.add(newVal.trim());
                ultimoProductoAutocompletado = newVal;
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
                    ignorarCambioTexto = true;
                    cmbProducto.setValue(textoProducto);
                    cmbProducto.getEditor().setText(textoProducto);
                    cmbProducto.getEditor().positionCaret(textoProducto.length());
                    ignorarCambioTexto = false;
                    
                    // ✅ SIEMPRE mantener foco en producto
                    cmbProducto.requestFocus();
                    System.out.println("   → Foco mantenido en Producto");
                });

                productoNuevoYaConfigurado = true;
            },
            () -> {
                System.out.println("   ⚠️ No se encontró gasto anterior para este producto");
                productoNuevoYaConfigurado = false;
            }
        );
    }
    
    private void configurarGuardarConEnter() {
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
                    ultimoProductoAutocompletado = producto;
                    productosYaUsados.add(producto); // ✅ Agregar al historial
                    
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
            
            String cedula = null;
            if (txtCedula.isVisible() && txtCedula.getText() != null && !txtCedula.getText().trim().isEmpty()) {
                cedula = txtCedula.getText().trim();
                System.out.println("📋 Cédula ingresada: " + cedula);
            }
            
            // ✅ Limpiar formato de moneda antes de guardar
            String valorUnitarioLimpio = txtValorUnitario.getText().replaceAll("[^0-9]", "");
            
            gastoService.registrarGasto(
                usuarioId,
                cmbCategoria.getValue().getId(),
                cmbSubcategoria.getValue().getId(),
                producto,
                Integer.parseInt(txtCantidad.getText().trim()),
                new BigDecimal(valorUnitarioLimpio), // ✅ Usar valor limpio
                txtNotas.getText(),
                cedula
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
        productoNuevoYaConfigurado = false;
        // ✅ NO resetear productosYaUsados ni ultimoProductoAutocompletado
        // Esto permite que el historial se mantenga durante toda la sesión
        
        cmbProducto.getEditor().clear();
        cmbProducto.setValue(null);
        cmbProducto.getItems().clear();
        
        cmbCategoria.setValue(null);
        cmbSubcategoria.setValue(null);
        cmbSubcategoria.getItems().clear();
        
        txtCantidad.clear();
        txtValorUnitario.clear();
        txtValorTotal.setText("$ 0");
        txtNotas.clear();
        txtCedula.clear();
        
        Platform.runLater(() -> cmbProducto.requestFocus());
        
        System.out.println("🧹 Formulario limpiado (historial de productos preservado)");
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
        colHora.setCellValueFactory(data -> {
            Object h = data.getValue().getHora();
            String texto = h == null ? "" : String.valueOf(h);
            return new SimpleStringProperty(texto.length() > 8 ? texto.substring(0, 8) : texto);
        });
        colCategoria.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategoria().getNombre()));
        colProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProducto()));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        
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
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("🔍 CARGANDO ÚLTIMOS GASTOS");
        System.out.println("═══════════════════════════════════════");
        System.out.println("👤 Usuario ID: " + usuarioId);
        
        try {
            List<Gasto> gastos = gastoService.obtenerUltimosGastos(usuarioId, 100000);
            
            System.out.println("📊 Total de gastos obtenidos: " + gastos.size());
            System.out.println("───────────────────────────────────────");
            
            if (gastos.isEmpty()) {
                System.out.println("⚠️  NO HAY GASTOS REGISTRADOS");
                System.out.println("    Verifica que:");
                System.out.println("    1. Existen registros en la tabla 'gastos'");
                System.out.println("    2. El usuario_id = " + usuarioId + " tiene gastos");
                System.out.println("    3. La consulta SQL está funcionando");
            } else {
                System.out.println("✅ GASTOS ENCONTRADOS:");
                int contador = 1;
                for (Gasto g : gastos) {
                    System.out.println(String.format(
                        "  %d. ID: %-3d | %s | %-20s | Cant: %-2d | $%s",
                        contador++,
                        g.getId(),
                        g.getFecha(),
                        g.getProducto(),
                        g.getCantidad(),
                        g.getValorTotal()
                    ));
                }
            }
            
            System.out.println("───────────────────────────────────────");
            System.out.println("🔄 Actualizando tabla en UI...");
            
            tblUltimosGastos.setItems(FXCollections.observableArrayList(gastos));
            actualizarTotal();
            
            System.out.println("✅ Tabla actualizada correctamente");
            System.out.println("   Items en tabla: " + tblUltimosGastos.getItems().size());
            
        } catch (Exception e) {
            System.err.println("❌ ERROR AL CARGAR GASTOS:");
            System.err.println("   Mensaje: " + e.getMessage());
            System.err.println("   Tipo: " + e.getClass().getName());
            e.printStackTrace();
        }
        
        System.out.println("═══════════════════════════════════════\n");
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
    
    private void configurarValidacionCedula() {
        txtCedula.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.matches("\\d*")) {
                txtCedula.setText(oldVal);
            }
        });
    }
    
    // ✅ NUEVO: Configurar formato de moneda en tiempo real
    private void configurarFormatoMoneda() {
        NumberFormat formatoPeso = NumberFormat.getInstance(new Locale("es", "CO"));
        formatoPeso.setGroupingUsed(true);
        
        // Formateador en tiempo real para Valor Unitario
        txtValorUnitario.textProperty().addListener((obs, oldVal, newVal) -> {
            // Evitar loop infinito
            if (formateandoMoneda) {
                return;
            }
            
            if (newVal == null || newVal.isEmpty()) {
                return;
            }
            
            // Quitar todo excepto números
            String soloNumeros = newVal.replaceAll("[^0-9]", "");
            
            if (soloNumeros.isEmpty()) {
                formateandoMoneda = true;
                txtValorUnitario.setText("");
                formateandoMoneda = false;
                return;
            }
            
            // Formatear el número
            try {
                long numero = Long.parseLong(soloNumeros);
                String formateado = "$ " + formatoPeso.format(numero);
                
                // Solo actualizar si el formato es diferente
                if (!newVal.equals(formateado)) {
                    formateandoMoneda = true;
                    
                    // Guardar posición del cursor relativa al final
                    int posicionCursor = txtValorUnitario.getCaretPosition();
                    int distanciaDelFinal = newVal.length() - posicionCursor;
                    
                    // Actualizar texto
                    txtValorUnitario.setText(formateado);
                    
                    // Restaurar cursor
                    int nuevaPosicion = formateado.length() - distanciaDelFinal;
                    nuevaPosicion = Math.max(2, Math.min(nuevaPosicion, formateado.length())); // Mínimo después de "$ "
                    
                    txtValorUnitario.positionCaret(nuevaPosicion);
                    
                    formateandoMoneda = false;
                }
                
            } catch (NumberFormatException e) {
                // Si hay error, no hacer nada
                System.err.println("Error formateando: " + e.getMessage());
            }
        });
        
        System.out.println("✅ Formato de moneda configurado");
    }
    
    // ✅ NUEVO: Contar separadores de miles en una cadena
    private int contarSeparadores(String texto, int hastaIndex) {
        if (texto == null || texto.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        int limite = Math.min(hastaIndex, texto.length());
        
        for (int i = 0; i < limite; i++) {
            char c = texto.charAt(i);
            if (c == '.' || c == ',') {
                count++;
            }
        }
        
        return count;
    }

    private void configurarCampoCedula() {
        cmbCategoria.valueProperty().addListener((obs, oldVal, newVal) -> {
            verificarMostrarCampoCedula();
        });
        
        cmbSubcategoria.valueProperty().addListener((obs, oldVal, newVal) -> {
            verificarMostrarCampoCedula();
        });
    }

    private void verificarMostrarCampoCedula() {
        boolean mostrar = false;
        
        Categoria categoria = cmbCategoria.getValue();
        Subcategoria subcategoria = cmbSubcategoria.getValue();
        
        if (categoria != null && subcategoria != null) {
            String nombreCategoria = categoria.getNombre();
            String nombreSubcategoria = subcategoria.getNombre();
            
            mostrar = nombreCategoria.equalsIgnoreCase("Beneficios Clientes") 
                      && nombreSubcategoria.toLowerCase().contains("bono");
        }
        
        lblCedula.setVisible(mostrar);
        lblCedula.setManaged(mostrar);
        txtCedula.setVisible(mostrar);
        txtCedula.setManaged(mostrar);
        
        if (!mostrar) {
            txtCedula.clear();
        }
        
        System.out.println("🔍 Campo cedula: " + (mostrar ? "VISIBLE" : "OCULTO"));
    }
    
    private static class SeleccionProductoNueva {
        final Categoria categoria;
        final Subcategoria subcategoria;
        
        SeleccionProductoNueva(Categoria c, Subcategoria s) {
            this.categoria = c;
            this.subcategoria = s;
        }
    }
    
 // ✅ AGREGAR estos métodos en RegistroGastoController.java

    private void configurarCopiaPegadoTabla() {
        // Habilitar selección múltiple
        tblUltimosGastos.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tblUltimosGastos.getSelectionModel().setCellSelectionEnabled(true);
        
        tblUltimosGastos.setOnKeyPressed(event -> {
            // Detectar Ctrl+C
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copiarSeleccionAlPortapapeles();
                event.consume();
            }
        });
    }
    
    private void actualizarTotal() {
        BigDecimal suma = tblUltimosGastos.getItems().stream()
            .map(Gasto::getValorTotal)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalGastos.setText(
            NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(suma));
    }

    private void copiarSeleccionAlPortapapeles() {
        StringBuilder clipboardString = new StringBuilder();
        
        // Obtener todas las posiciones seleccionadas
        ObservableList<TablePosition> posList = tblUltimosGastos.getSelectionModel().getSelectedCells();
        
        if (posList.isEmpty()) {
            return;
        }
        
        // Organizar por fila y columna
        int previousRow = -1;
        
        for (TablePosition pos : posList) {
            int currentRow = pos.getRow();
            
            // Si cambió de fila, agregar salto de línea
            if (previousRow != -1 && previousRow != currentRow) {
                clipboardString.append("\n");
            }
            
            // Si es la misma fila pero no es la primera celda, agregar tabulador
            if (previousRow == currentRow && clipboardString.length() > 0) {
                clipboardString.append("\t");
            }
            
            // Obtener el dato de la celda
            Object cellData = pos.getTableColumn().getCellData(currentRow);
            
            if (cellData != null) {
                clipboardString.append(cellData.toString());
            }
            
            previousRow = currentRow;
        }
        
        // Copiar al portapapeles
        final ClipboardContent content = new ClipboardContent();
        content.putString(clipboardString.toString());
        Clipboard.getSystemClipboard().setContent(content);
        
        System.out.println("📋 Copiadas " + posList.size() + " celdas");
    }

    // ✅ LLAMAR este método en initialize(), agregar esta línea:
    // configurarCopiaPegadoTabla();
}