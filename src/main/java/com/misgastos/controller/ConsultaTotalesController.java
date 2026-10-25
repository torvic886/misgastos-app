package com.misgastos.controller;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.Subcategoria;
import com.misgastos.model.Usuario;
import com.misgastos.service.ConsultaTotalesService;
import com.misgastos.service.ConsultaTotalesService.Fila;
import com.misgastos.service.ConsultaTotalesService.Filtro;
import com.misgastos.service.ConsultaTotalesService.Resultado;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.misgastos.service.ConsultaExcelService;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;

/**
 * Pantalla "Consulta de totales": el usuario elige filtros (periodo, categoría,
 * subcategoría, producto, persona, quién registró) y ve al instante cuánto suman,
 * con el desglose y la lista exacta de gastos que forman ese total.
 */

@Slf4j
@Component
public class ConsultaTotalesController {

    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String PERIODO_HOY = "Hoy";
    private static final String PERIODO_SEMANA = "Esta semana";
    private static final String PERIODO_MES = "Este mes";
    private static final String PERIODO_ANIO = "Este año";
    private static final String PERIODO_TODO = "Todo el tiempo";
    private static final String PERIODO_PERSONALIZADO = "Personalizado";

    // ----- Filtros -----
    @FXML private ComboBox<String> cmbPeriodo;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private ComboBox<Subcategoria> cmbSubcategoria;
    @FXML private TextField txtProducto;
    @FXML private CheckBox chkExacto;
    @FXML private TextField txtPersona;
    @FXML private ComboBox<Usuario> cmbUsuario;
    @FXML private ComboBox<String> cmbAgrupar;

    // ----- Resultado -----
    @FXML private Label lblFiltros;
    @FXML private Label lblTotal;
    @FXML private Label lblCantidad;
    @FXML private Label lblPromedio;
    @FXML private Label lblTituloDesglose;
    @FXML private Label lblTituloDetalle;

    @FXML private TableView<Fila> tblDesglose;
    @FXML private TableColumn<Fila, String> colGrupoNombre;
    @FXML private TableColumn<Fila, Long> colGrupoCantidad;
    @FXML private TableColumn<Fila, BigDecimal> colGrupoTotal;
    @FXML private TableColumn<Fila, Double> colGrupoPorcentaje;

    @FXML private TableView<Gasto> tblDetalle;
    @FXML private TableColumn<Gasto, LocalDate> colFecha;
    @FXML private TableColumn<Gasto, String> colCategoria;
    @FXML private TableColumn<Gasto, String> colSubcategoria;
    @FXML private TableColumn<Gasto, String> colProducto;
    @FXML private TableColumn<Gasto, Integer> colCantidad;
    @FXML private TableColumn<Gasto, BigDecimal> colValorUnitario;
    @FXML private TableColumn<Gasto, BigDecimal> colValorTotal;
    @FXML private TableColumn<Gasto, String> colUsuario;
    @FXML private TableColumn<Gasto, String> colPersona;

    @Autowired
    private ConsultaTotalesService servicio;
    
    @Autowired
    private ConsultaExcelService excelService;

    // Lo último que se muestra en pantalla: es lo que se exporta
    private Resultado ultimoResultado;
    private String ultimaDescripcion = "";

    // Opciones "sin filtro" de las listas desplegables
    private final Categoria todasCategorias = nuevaCategoria("Todas las categorías");
    private final Subcategoria todasSubcategorias = nuevaSubcategoria("Todas las subcategorías");
    private final Usuario todosUsuarios = nuevoUsuario("Todos los usuarios");

    private PauseTransition espera;
    private boolean sincronizando = false;
    private String agrupadoActual = "";

    private NumberFormat monedaEntera;
    private NumberFormat monedaDecimal;

    // ==================== INICIO ====================

    @FXML
    public void initialize() {
        monedaEntera = NumberFormat.getCurrencyInstance(CO);
        monedaEntera.setMaximumFractionDigits(0);
        monedaDecimal = NumberFormat.getCurrencyInstance(CO);
        monedaDecimal.setMinimumFractionDigits(2);
        monedaDecimal.setMaximumFractionDigits(2);

        // Espera corta para no consultar en cada tecla mientras se escribe
        espera = new PauseTransition(Duration.millis(250));
        espera.setOnFinished(e -> consultar());

        cmbPeriodo.getItems().setAll(PERIODO_HOY, PERIODO_SEMANA, PERIODO_MES, PERIODO_ANIO,
                PERIODO_TODO, PERIODO_PERSONALIZADO);

        cmbAgrupar.getItems().setAll(ConsultaTotalesService.AGRUPAR_AUTOMATICO,
                ConsultaTotalesService.AGRUPAR_CATEGORIA,
                ConsultaTotalesService.AGRUPAR_SUBCATEGORIA,
                ConsultaTotalesService.AGRUPAR_PRODUCTO,
                ConsultaTotalesService.AGRUPAR_MES,
                ConsultaTotalesService.AGRUPAR_USUARIO);
        cmbAgrupar.setValue(ConsultaTotalesService.AGRUPAR_AUTOMATICO);

        cargarListasDeFiltros();
        configurarTablas();
        configurarListeners();

        cmbPeriodo.setValue(PERIODO_MES);
        consultar();
        espera.stop(); // la consulta inicial ya se hizo
    }

    private void cargarListasDeFiltros() {
        cmbCategoria.getItems().setAll(todasCategorias);
        cmbCategoria.getItems().addAll(servicio.categorias());
        cmbCategoria.setValue(todasCategorias);

        cmbSubcategoria.getItems().setAll(todasSubcategorias);
        cmbSubcategoria.setValue(todasSubcategorias);
        cmbSubcategoria.setDisable(true);

        cmbUsuario.setConverter(new StringConverter<Usuario>() {
            @Override
            public String toString(Usuario u) {
                return u == null ? "" : u.getUsername();
            }

            @Override
            public Usuario fromString(String s) {
                return null;
            }
        });
        cmbUsuario.getItems().setAll(todosUsuarios);
        cmbUsuario.getItems().addAll(servicio.usuarios());
        cmbUsuario.setValue(todosUsuarios);
    }

    private void configurarListeners() {
        cmbPeriodo.valueProperty().addListener((o, antes, ahora) -> {
            aplicarPeriodo();
            programarConsulta();
        });

        // Si el usuario cambia una fecha a mano, el periodo pasa a "Personalizado"
        ChangeListener<LocalDate> alCambiarFecha = (o, antes, ahora) -> {
            if (!sincronizando) {
                sincronizando = true;
                cmbPeriodo.setValue(PERIODO_PERSONALIZADO);
                sincronizando = false;
            }
            programarConsulta();
        };
        dpDesde.valueProperty().addListener(alCambiarFecha);
        dpHasta.valueProperty().addListener(alCambiarFecha);

        cmbCategoria.valueProperty().addListener((o, antes, ahora) -> {
            cargarSubcategorias();
            programarConsulta();
        });
        cmbSubcategoria.valueProperty().addListener((o, antes, ahora) -> programarConsulta());
        txtProducto.textProperty().addListener((o, antes, ahora) -> programarConsulta());
        chkExacto.selectedProperty().addListener((o, antes, ahora) -> programarConsulta());
        txtPersona.textProperty().addListener((o, antes, ahora) -> programarConsulta());
        cmbUsuario.valueProperty().addListener((o, antes, ahora) -> programarConsulta());
        cmbAgrupar.valueProperty().addListener((o, antes, ahora) -> programarConsulta());
    }

    private void programarConsulta() {
        if (espera != null) {
            espera.playFromStart();
        }
    }

    // ==================== FILTROS ====================

    private void aplicarPeriodo() {
        String periodo = cmbPeriodo.getValue();
        if (periodo == null || PERIODO_PERSONALIZADO.equals(periodo)) {
            return;
        }
        LocalDate hoy = LocalDate.now();
        sincronizando = true;
        switch (periodo) {
            case PERIODO_HOY -> {
                dpDesde.setValue(hoy);
                dpHasta.setValue(hoy);
            }
            case PERIODO_SEMANA -> {
                LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
                dpDesde.setValue(lunes);
                dpHasta.setValue(lunes.plusDays(6));
            }
            case PERIODO_MES -> {
                dpDesde.setValue(hoy.withDayOfMonth(1));
                dpHasta.setValue(hoy.withDayOfMonth(hoy.lengthOfMonth()));
            }
            case PERIODO_ANIO -> {
                dpDesde.setValue(LocalDate.of(hoy.getYear(), 1, 1));
                dpHasta.setValue(LocalDate.of(hoy.getYear(), 12, 31));
            }
            case PERIODO_TODO -> {
                dpDesde.setValue(null);
                dpHasta.setValue(null);
            }
            default -> {
            }
        }
        sincronizando = false;
    }

    private void cargarSubcategorias() {
        Long categoriaId = idCategoria(cmbCategoria.getValue());
        cmbSubcategoria.getItems().setAll(todasSubcategorias);
        if (categoriaId != null) {
            cmbSubcategoria.getItems().addAll(servicio.subcategorias(categoriaId));
        }
        cmbSubcategoria.setValue(todasSubcategorias);
        cmbSubcategoria.setDisable(categoriaId == null);
    }

    @FXML
    public void handleLimpiar() {
        cmbPeriodo.setValue(PERIODO_MES);
        aplicarPeriodo();
        cmbCategoria.setValue(todasCategorias);
        cmbUsuario.setValue(todosUsuarios);
        txtProducto.clear();
        chkExacto.setSelected(false);
        txtPersona.clear();
        cmbAgrupar.setValue(ConsultaTotalesService.AGRUPAR_AUTOMATICO);
        consultar();
        espera.stop();
    }

    // ==================== CONSULTA ====================

    private void consultar() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            lblFiltros.setText("⚠ La fecha \"Desde\" es posterior a \"Hasta\". Corrígela para ver el resultado.");
            return;
        }

        Filtro filtro = new Filtro();
        filtro.desde = desde;
        filtro.hasta = hasta;
        filtro.categoriaId = idCategoria(cmbCategoria.getValue());
        filtro.subcategoriaId = idSubcategoria(cmbSubcategoria.getValue());
        filtro.usuarioId = idUsuario(cmbUsuario.getValue());
        filtro.producto = txtProducto.getText();
        filtro.productoExacto = chkExacto.isSelected();
        filtro.persona = txtPersona.getText();

        try {
            Resultado r = servicio.consultar(filtro, cmbAgrupar.getValue());

            lblTotal.setText(moneda(r.total()));
            lblCantidad.setText(String.valueOf(r.cantidad()));
            lblPromedio.setText(r.cantidad() == 0 ? "-" : moneda(r.promedio()));

            agrupadoActual = r.agrupadoPor();
            lblTituloDesglose.setText(tituloDesglose(r.agrupadoPor()));
            lblTituloDetalle.setText(r.cantidad() == 1
                    ? "Detalle: el gasto que forma el total"
                    : "Detalle: los " + r.cantidad() + " gastos que forman el total");

            tblDesglose.setItems(FXCollections.observableArrayList(r.desglose()));
            tblDetalle.setItems(FXCollections.observableArrayList(r.gastos()));

            lblFiltros.setText(describirFiltros(filtro));
            ultimoResultado = r;
            ultimaDescripcion = lblFiltros.getText();
        } catch (Exception e) {
        	log.error("No se pudo hacer la consulta de totales", e);
            mostrarAlerta("Error", "No se pudo hacer la consulta:\n" + e.getMessage());
        }
    }

    private String tituloDesglose(String agrupadoPor) {
        if (ConsultaTotalesService.AGRUPAR_USUARIO.equals(agrupadoPor)) {
            return "Desglose por quién registró el gasto";
        }
        return "Desglose por " + agrupadoPor.toLowerCase(CO);
    }

    /** Dice, en palabras, exactamente qué se está sumando. */
    private String describirFiltros(Filtro f) {
        List<String> partes = new ArrayList<>();

        if (f.desde == null && f.hasta == null) {
            partes.add("Todo el tiempo");
        } else if (f.desde != null && f.hasta != null) {
            partes.add(f.desde.equals(f.hasta)
                    ? FECHA.format(f.desde)
                    : FECHA.format(f.desde) + " al " + FECHA.format(f.hasta));
        } else if (f.desde != null) {
            partes.add("Desde " + FECHA.format(f.desde));
        } else {
            partes.add("Hasta " + FECHA.format(f.hasta));
        }

        if (f.categoriaId != null) {
            partes.add("Categoría: " + cmbCategoria.getValue().getNombre());
        }
        if (f.subcategoriaId != null) {
            partes.add("Subcategoría: " + cmbSubcategoria.getValue().getNombre());
        }
        if (f.producto != null && !f.producto.isBlank()) {
            partes.add(f.productoExacto
                    ? "Producto: \"" + f.producto.trim() + "\" (nombre exacto)"
                    : "Producto: contiene \"" + f.producto.trim() + "\"");
        }
        if (f.persona != null && !f.persona.isBlank()) {
            partes.add("Persona: contiene \"" + f.persona.trim() + "\"");
        }
        if (f.usuarioId != null) {
            partes.add("Registrado por: " + cmbUsuario.getValue().getUsername());
        }
        return "Sumando: " + String.join("  ·  ", partes);
    }

    // ==================== PROFUNDIZAR (doble clic en el desglose) ====================

    private void profundizar(Fila fila) {
        switch (agrupadoActual) {
            case ConsultaTotalesService.AGRUPAR_CATEGORIA -> seleccionarCategoria(fila.categoriaId());
            case ConsultaTotalesService.AGRUPAR_SUBCATEGORIA -> {
                seleccionarCategoria(fila.categoriaId());
                seleccionarSubcategoria(fila.subcategoriaId());
            }
            case ConsultaTotalesService.AGRUPAR_PRODUCTO -> {
                txtProducto.setText(fila.producto());
                chkExacto.setSelected(true);
            }
            default -> {
                return; // Mes y "Registrado por" no se pueden profundizar
            }
        }
        cmbAgrupar.setValue(ConsultaTotalesService.AGRUPAR_AUTOMATICO);
    }

    private void seleccionarCategoria(Long id) {
        if (id == null) {
            return;
        }
        for (Categoria c : cmbCategoria.getItems()) {
            if (c.getId() != null && c.getId().equals(id)) {
                cmbCategoria.setValue(c);
                return;
            }
        }
    }

    private void seleccionarSubcategoria(Long id) {
        if (id == null) {
            return;
        }
        for (Subcategoria s : cmbSubcategoria.getItems()) {
            if (s.getId() != null && s.getId().equals(id)) {
                cmbSubcategoria.setValue(s);
                return;
            }
        }
    }

    // ==================== TABLAS ====================

    private void configurarTablas() {
        // ----- Desglose -----
        colGrupoNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        colGrupoCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().cantidad()));
        colGrupoTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().total()));
        colGrupoTotal.setCellFactory(col -> this.<Fila>celdaMoneda());
        colGrupoPorcentaje.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().porcentaje()));
        colGrupoPorcentaje.setCellFactory(col -> new TableCell<Fila, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(CO, "%.1f %%", item));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });

        tblDesglose.setPlaceholder(new Label("No hay gastos con estos filtros"));
        tblDesglose.setRowFactory(tv -> {
            TableRow<Fila> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    profundizar(fila.getItem());
                }
            });
            return fila;
        });

        // ----- Detalle -----
        colFecha.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFecha()));
        colFecha.setCellFactory(col -> new TableCell<Gasto, LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : FECHA.format(item));
            }
        });
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategoria().getNombre()));
        colSubcategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSubcategoria().getNombre()));
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProducto()));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colValorUnitario.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getValorUnitario()));
        colValorUnitario.setCellFactory(col -> this.<Gasto>celdaMoneda());
        colValorTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getValorTotal()));
        colValorTotal.setCellFactory(col -> this.<Gasto>celdaMoneda());
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsuario().getUsername()));
        colPersona.setCellValueFactory(c -> new SimpleStringProperty(textoPersona(c.getValue())));

        tblDetalle.setPlaceholder(new Label("No hay gastos con estos filtros"));
    }

    private <S> TableCell<S, BigDecimal> celdaMoneda() {
        return new TableCell<S, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : moneda(item));
                setAlignment(Pos.CENTER_RIGHT);
            }
        };
    }

    private String textoPersona(Gasto g) {
        boolean hayNombre = g.getNombrePersona() != null && !g.getNombrePersona().isBlank();
        boolean hayCedula = g.getCedula() != null && !g.getCedula().isBlank();
        if (hayNombre && hayCedula) {
            return g.getNombrePersona() + " (" + g.getCedula() + ")";
        }
        if (hayNombre) {
            return g.getNombrePersona();
        }
        return hayCedula ? g.getCedula() : "";
    }
    
    // ==================== EXPORTAR A EXCEL ====================

    @FXML
    public void handleExportarExcel() {
        if (ultimoResultado == null || ultimoResultado.cantidad() == 0) {
            Alert aviso = new Alert(Alert.AlertType.INFORMATION);
            aviso.setTitle("Nada que exportar");
            aviso.setHeaderText(null);
            aviso.setContentText("La consulta actual no tiene gastos. Cambia los filtros y vuelve a intentarlo.");
            aviso.showAndWait();
            return;
        }

        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar consulta en Excel");
        selector.setInitialFileName("consulta_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".xlsx");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Libro de Excel (*.xlsx)", "*.xlsx"));

        File elegido = selector.showSaveDialog(lblTotal.getScene().getWindow());
        if (elegido == null) {
            return;
        }
        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase().endsWith(".xlsx")) {
            destino = destino.resolveSibling(destino.getFileName() + ".xlsx");
        }

        try {
            excelService.exportar(ultimoResultado, ultimaDescripcion,
                    tituloDesglose(ultimoResultado.agrupadoPor()), destino);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Excel guardado");
            ok.setHeaderText(null);
            ok.setContentText("El archivo quedó en:\n" + destino);
            ok.showAndWait();
        } catch (Exception e) {
        	log.error("No se pudo exportar la consulta a Excel", e);
            mostrarAlerta("No se pudo guardar",
                    "Si tienes ese archivo abierto en Excel, ciérralo e inténtalo de nuevo.\n\nMotivo: " + e.getMessage());
        }
    }

    // ==================== AUXILIARES ====================

    /** Pesos sin decimales; solo muestra los centavos cuando de verdad existen. */
    private String moneda(BigDecimal valor) {
        if (valor == null) {
            return monedaEntera.format(BigDecimal.ZERO);
        }
        return valor.stripTrailingZeros().scale() > 0
                ? monedaDecimal.format(valor)
                : monedaEntera.format(valor);
    }

    private Long idCategoria(Categoria c) {
        return c == null ? null : c.getId();
    }

    private Long idSubcategoria(Subcategoria s) {
        return s == null ? null : s.getId();
    }

    private Long idUsuario(Usuario u) {
        return u == null ? null : u.getId();
    }

    private static Categoria nuevaCategoria(String nombre) {
        Categoria c = new Categoria();
        c.setNombre(nombre);
        return c;
    }

    private static Subcategoria nuevaSubcategoria(String nombre) {
        Subcategoria s = new Subcategoria();
        s.setNombre(nombre);
        return s;
    }

    private static Usuario nuevoUsuario(String nombre) {
        Usuario u = new Usuario();
        u.setUsername(nombre);
        return u;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}