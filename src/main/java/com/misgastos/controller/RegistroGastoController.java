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

import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.misgastos.service.UsuarioService;

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
    @FXML private Label lblNombre;
    @FXML private TextField txtNombre;
    @FXML private TableColumn<Gasto, String> colHora;
    @FXML private Label lblTotalGastos;
    @Autowired private UsuarioService usuarioService;
    @FXML private TableColumn<Gasto, String> colUsuario;
    @FXML private TableColumn<Gasto, String> colCedula;
    @FXML private TableColumn<Gasto, String> colNombre;
    
    
    @Autowired private CategoriaService categoriaService;
    @Autowired private GastoService gastoService;
    
    private Long usuarioId = 1L;
    
    // ✅ Flags de control
    private boolean productoNuevoYaConfigurado = false;
    private String ultimoProductoAutocompletado = null;
    private boolean formateandoMoneda = false;
    private boolean ignorarCambioTexto = false;
    private Set<String> productosYaUsados = new HashSet<>();
    
    private final ObservableList<Gasto> gastosMaestro = FXCollections.observableArrayList();
    private FilteredList<Gasto> gastosFiltrados;
    private final Map<TableColumn<Gasto, ?>, Function<Gasto, String>> extractores = new LinkedHashMap<>();
    private final Map<TableColumn<Gasto, ?>, Set<String>> seleccionados = new HashMap<>();
    private final Map<TableColumn<Gasto, ?>, Button> botonesFiltro = new HashMap<>();
    private final NumberFormat fmtMoneda = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
    
    
    @FXML
    public void initialize() {
        configurarCalculoAutomatico();
        configurarProductoAutocomplete();
        configurarGuardarConEnter();
        configurarTabla();
        configurarFiltros();
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
            	(txtNombre.isEditable() ? txtNombre : txtCantidad).requestFocus();
                event.consume();
            }
        });
        
        txtNombre.setOnKeyPressed(event -> {
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
            String nombrePersona = null;
            if (txtCedula.isVisible()) {
                cedula = txtCedula.getText() == null ? "" : txtCedula.getText().trim();
                nombrePersona = txtNombre.getText() == null ? "" : txtNombre.getText().trim();
                if (cedula.isEmpty()) {
                    mostrarAlerta("Validación", "Ingrese la cédula de la persona", Alert.AlertType.WARNING);
                    txtCedula.requestFocus();
                    return;
                }
                if (nombrePersona.isEmpty()) {
                    mostrarAlerta("Validación", "Ingrese el nombre de la persona", Alert.AlertType.WARNING);
                    txtNombre.requestFocus();
                    return;
                }
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
                cedula,
                nombrePersona
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
        txtNombre.clear();
        txtNombre.setEditable(true);
        
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
        
        tblUltimosGastos.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        colFecha.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getFecha()));
        colHora.setCellValueFactory(data -> {
            Object h = data.getValue().getHora();
            String texto = h == null ? "" : String.valueOf(h);
            return new SimpleStringProperty(texto.length() > 8 ? texto.substring(0, 8) : texto);
        });
        colUsuario.setCellValueFactory(data -> new SimpleStringProperty(
        	    data.getValue().getUsuario() == null ? "" : data.getValue().getUsuario().getUsername()));
        colCedula.setCellValueFactory(d -> new SimpleStringProperty(
        	    d.getValue().getCedula() == null ? "" : d.getValue().getCedula()));
        	colNombre.setCellValueFactory(d -> new SimpleStringProperty(
        	    d.getValue().getNombrePersona() == null ? "" : d.getValue().getNombrePersona()));
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
        	List<Gasto> gastos = gastoService.obtenerUltimosGastosGeneral(100000);
            
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
            
            seleccionados.clear();
            gastosMaestro.setAll(gastos);
            aplicarFiltros();

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
        txtCedula.textProperty().addListener((obs, oldVal, newVal) -> autocompletarNombre(newVal));
    }
    
    public void setUsuarioActual(String username) {
        usuarioService.buscarPorUsername(username).ifPresent(u -> this.usuarioId = u.getId());
        cargarUltimosGastos();
    }
    
    private void autocompletarNombre(String cedula) {
        if (cedula != null && !cedula.trim().isEmpty()) {
            java.util.Optional<String> nombre = gastoService.buscarNombrePorCedula(cedula.trim());
            if (nombre.isPresent()) {
                txtNombre.setText(nombre.get());
                txtNombre.setEditable(false);
                return;
            }
        }
        if (!txtNombre.isEditable()) {
            txtNombre.clear();
        }
        txtNombre.setEditable(true);
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
        lblNombre.setVisible(mostrar);
        lblNombre.setManaged(mostrar);
        txtNombre.setVisible(mostrar);
        txtNombre.setManaged(mostrar);
        
        if (!mostrar) {
            txtCedula.clear();
            txtNombre.clear();
            txtNombre.setEditable(true);
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
    
    private void configurarFiltros() {
        gastosFiltrados = new FilteredList<>(gastosMaestro, g -> true);
        SortedList<Gasto> ordenados = new SortedList<>(gastosFiltrados);
        ordenados.comparatorProperty().bind(tblUltimosGastos.comparatorProperty());
        tblUltimosGastos.setItems(ordenados);

        agregarFiltro(colFecha, "Fecha", g -> String.valueOf(g.getFecha()));
        agregarFiltro(colHora, "Hora", g -> {
            Object h = g.getHora();
            String t = h == null ? "" : String.valueOf(h);
            return t.length() > 5 ? t.substring(0, 5) : t;
        });
        agregarFiltro(colUsuario, "Registrado por",
        	    g -> g.getUsuario() == null ? "" : g.getUsuario().getUsername());
        agregarFiltro(colCategoria, "Categoría", g -> g.getCategoria().getNombre());
        agregarFiltro(colProducto, "Producto", g -> g.getProducto());
        agregarFiltro(colCedula, "Cédula", g -> g.getCedula() == null ? "" : g.getCedula());
        agregarFiltro(colNombre, "Nombre", g -> g.getNombrePersona() == null ? "" : g.getNombrePersona());
        agregarFiltro(colCantidad, "Cant", g -> String.valueOf(g.getCantidad()));
        agregarFiltro(colValorUnitario, "V. Unit",
            g -> g.getValorUnitario() == null ? "" : fmtMoneda.format(g.getValorUnitario()));
        agregarFiltro(colTotal, "Total",
            g -> g.getValorTotal() == null ? "" : fmtMoneda.format(g.getValorTotal()));
    }

    private void agregarFiltro(TableColumn<Gasto, ?> col, String titulo, Function<Gasto, String> extractor) {
        Label lbl = new Label(titulo);
        lbl.setStyle("-fx-font-weight: bold;");
        Button btn = new Button("▼");
        btn.setFocusTraversable(false);
        btn.setStyle(ESTILO_FILTRO_NORMAL);
        btn.setOnAction(e -> mostrarMenuFiltro(col, btn));

        HBox caja = new HBox(4, lbl, btn);
        caja.setAlignment(Pos.CENTER);
        col.setText("");
        col.setGraphic(caja);

        extractores.put(col, extractor);
        botonesFiltro.put(col, btn);
    }

    private static final String ESTILO_FILTRO_NORMAL =
        "-fx-padding: 0 4 0 4; -fx-font-size: 10px; -fx-background-color: transparent; -fx-text-fill: #64748b; -fx-cursor: hand;";
    private static final String ESTILO_FILTRO_ACTIVO =
        "-fx-padding: 0 4 0 4; -fx-font-size: 10px; -fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand;";

    private void mostrarMenuFiltro(TableColumn<Gasto, ?> col, Button anchor) {
        Function<Gasto, String> ext = extractores.get(col);
        TreeSet<String> valores = gastosMaestro.stream()
            .map(g -> { String v = ext.apply(g); return v == null ? "" : v; })
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actuales = seleccionados.containsKey(col)
            ? new HashSet<>(seleccionados.get(col)) : new HashSet<>(valores);

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar...");
        CheckBox chkTodo = new CheckBox("(Seleccionar todo)");
        chkTodo.setSelected(actuales.containsAll(valores));
        VBox lista = new VBox(4);
        lista.setPadding(new Insets(4));
        ScrollPane sp = new ScrollPane(lista);
        sp.setFitToWidth(true);
        sp.setPrefHeight(200);
        sp.setPrefWidth(230);
        Button ok = new Button("Aceptar");
        Button limpiar = new Button("Limpiar filtro");
        HBox botones = new HBox(8, ok, limpiar);
        VBox contenido = new VBox(8, buscar, chkTodo, sp, botones);
        contenido.setPadding(new Insets(8));

        ContextMenu menu = new ContextMenu();
        CustomMenuItem item = new CustomMenuItem(contenido, false);
        menu.getItems().add(item);

        Runnable[] refrescar = new Runnable[1];
        refrescar[0] = () -> {
            String q = buscar.getText() == null ? "" : buscar.getText().trim().toLowerCase();
            lista.getChildren().clear();
            for (String v : valores) {
                String etiqueta = v.isEmpty() ? "(vacío)" : v;
                if (!q.isEmpty() && !etiqueta.toLowerCase().contains(q)) continue;
                CheckBox cb = new CheckBox(etiqueta);
                cb.setSelected(actuales.contains(v));
                cb.selectedProperty().addListener((o, a, sel) -> {
                    if (sel) actuales.add(v); else actuales.remove(v);
                });
                lista.getChildren().add(cb);
            }
        };
        buscar.textProperty().addListener((o, a, b) -> refrescar[0].run());
        chkTodo.setOnAction(e -> {
            for (Node n : lista.getChildren()) ((CheckBox) n).setSelected(chkTodo.isSelected());
        });
        ok.setOnAction(e -> {
            if (actuales.containsAll(valores)) seleccionados.remove(col);
            else seleccionados.put(col, new HashSet<>(actuales));
            menu.hide();
            aplicarFiltros();
        });
        limpiar.setOnAction(e -> {
            seleccionados.remove(col);
            menu.hide();
            aplicarFiltros();
        });

        refrescar[0].run();
        menu.show(anchor, Side.BOTTOM, 0, 0);
    }

    private void aplicarFiltros() {
        if (gastosFiltrados == null) return;
        gastosFiltrados.setPredicate(g -> {
            for (Map.Entry<TableColumn<Gasto, ?>, Set<String>> f : seleccionados.entrySet()) {
                String v = extractores.get(f.getKey()).apply(g);
                if (!f.getValue().contains(v == null ? "" : v)) return false;
            }
            return true;
        });
        botonesFiltro.forEach((c, b) ->
            b.setStyle(seleccionados.containsKey(c) ? ESTILO_FILTRO_ACTIVO : ESTILO_FILTRO_NORMAL));
        actualizarTotal();
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