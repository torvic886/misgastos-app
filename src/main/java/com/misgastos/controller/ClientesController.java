package com.misgastos.controller;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import javafx.scene.layout.VBox;
import com.misgastos.service.ClienteExcelService;
import javafx.beans.property.SimpleObjectProperty;
import com.misgastos.service.ClienteMovimientoService;
import javafx.scene.control.DatePicker;
import com.misgastos.util.SelectorFecha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.misgastos.model.Cliente;
import com.misgastos.repository.ClienteRepository;
import com.misgastos.service.ClienteImportService;
import com.misgastos.service.ClienteImportService.VistaPrevia;
import com.misgastos.service.ClienteService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

/**
 * Pantalla Clientes: lista los clientes, permite agregar y editar uno a mano,
 * e importarlos desde un Excel (cédula en la columna A, nombre en la B).
 * Antes de guardar una importación muestra un resumen y pide confirmación.
 */
@Component
public class ClientesController {

    private static final Logger log = LoggerFactory.getLogger(ClientesController.class);

    @FXML private TableView<Cliente> tablaClientes;
    @FXML private TableColumn<Cliente, String> colCedula;
    @FXML private TableColumn<Cliente, String> colNombre;
    @FXML private TextField txtBuscar;
    @FXML private Label lblResumen;
    @FXML private ComboBox<String> cmbOrden;
    @FXML private TableColumn<Cliente, String> colTelefono;
    @FXML private TableColumn<Cliente, String> colEtiqueta;
    @FXML private TableColumn<Cliente, String> colCumple;
    @FXML private TableColumn<Cliente, Integer> colBonos;
    @FXML private TableColumn<Cliente, Integer> colParqueaderos;
    @FXML private TableColumn<Cliente, Integer> colBingos;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;

    @Autowired private ClienteMovimientoService movimientoService;

    @Autowired private ClienteExcelService excelService;

    private String ultimoTituloExport = "Lista de clientes";
    private String ultimasColumnasPropias = "";
    
    private static final ClienteMovimientoService.Conteo SIN_MOVIMIENTO =
        new ClienteMovimientoService.Conteo(0, 0, 0);
    private Map<String, ClienteMovimientoService.Conteo> conteos = new HashMap<>();
    
    
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ClienteImportService importService;
    @Autowired private ClienteService clienteService;

    private final ObservableList<Cliente> maestro = FXCollections.observableArrayList();
    private FilteredList<Cliente> filtrados;

    @FXML
    public void initialize() {
        tablaClientes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        colCedula.setCellValueFactory(c -> new SimpleStringProperty(formatearCedula(c.getValue().getCedula())));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(tituloNombre(c.getValue().getNombre())));

        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTelefono() == null ? "" : c.getValue().getTelefono()));
            colEtiqueta.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEtiqueta() == null ? "" : c.getValue().getEtiqueta()));
            colCumple.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCumpleanos() == null ? ""
                    : c.getValue().getCumpleanos().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        
        filtrados = new FilteredList<>(maestro, c -> true);
        SortedList<Cliente> ordenados = new SortedList<>(filtrados);
        // Orden por defecto: nombre. El selector cambia el criterio.
        cmbOrden.getItems().addAll("Nombre", "Cédula", "Apellido");
        cmbOrden.getSelectionModel().selectFirst();
        ordenados.setComparator(comparadorPor("Nombre"));
        cmbOrden.valueProperty().addListener((obs, a, n) -> {
            tablaClientes.getSortOrder().clear();
            ordenados.comparatorProperty().unbind();
            ordenados.setComparator(comparadorPor(n));
        });
        // Clic en un encabezado: ordena por esa columna
        tablaClientes.comparatorProperty().addListener((obs, a, n) -> {
            if (n != null) {
                ordenados.comparatorProperty().unbind();
                ordenados.setComparator(n);
            }
        });
        tablaClientes.setItems(ordenados);
        tablaClientes.setPlaceholder(new Label(
            "Todavía no hay clientes. Usa «Agregar cliente» o «Importar clientes desde Excel»."));

        // Doble clic sobre un cliente abre el formulario de edición
        tablaClientes.setRowFactory(tv -> {
            TableRow<Cliente> fila = new TableRow<>();
            fila.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2 && !fila.isEmpty()) {
                    abrirFormulario(fila.getItem());
                }
            });
            return fila;
        });

        txtBuscar.textProperty().addListener((obs, a, b) -> {
            String t = b == null ? "" : b.trim().toLowerCase();
            filtrados.setPredicate(c -> t.isEmpty()
                || c.getCedula().toLowerCase().contains(t)
                || c.getNombre().toLowerCase().contains(t));
            actualizarResumen();
        });
        
        colBonos.setCellValueFactory(c -> new SimpleObjectProperty<>(
                conteos.getOrDefault(c.getValue().getCedula().trim(), SIN_MOVIMIENTO).bonos()));
            colParqueaderos.setCellValueFactory(c -> new SimpleObjectProperty<>(
                conteos.getOrDefault(c.getValue().getCedula().trim(), SIN_MOVIMIENTO).parqueaderos()));
            colBingos.setCellValueFactory(c -> new SimpleObjectProperty<>(
                conteos.getOrDefault(c.getValue().getCedula().trim(), SIN_MOVIMIENTO).bingos()));

            LocalDate hoy = LocalDate.now();
            dpDesde.setValue(hoy.withDayOfMonth(1));
            dpHasta.setValue(hoy.withDayOfMonth(hoy.lengthOfMonth()));
            SelectorFecha.configurar(dpDesde);
            SelectorFecha.configurar(dpHasta);
            dpDesde.valueProperty().addListener((obs, a, n) -> recalcularConteos());
            dpHasta.valueProperty().addListener((obs, a, n) -> recalcularConteos());
            recalcularConteos();

        cargar();
    }
    
    /** 41911884 -> 41.911.884 (solo visual; lo guardado no cambia). */
    private static String formatearCedula(String cedula) {
        if (cedula == null || !cedula.matches("\\d+")) {
            return cedula == null ? "" : cedula;
        }
        return new java.text.DecimalFormat("#,##0",
                new java.text.DecimalFormatSymbols(new java.util.Locale("es", "CO")))
                .format(Long.parseLong(cedula));
    }

    /** "daniel  hurtado" -> "Daniel Hurtado" (solo visual; lo guardado no cambia). */
    private static String tituloNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : nombre.trim().split("\\s+")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static String apellidoDe(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        String[] p = nombre.trim().split("\\s+");
        return p.length >= 4 ? p[p.length - 2] + " " + p[p.length - 1] : p[p.length - 1];
    }

    private java.util.Comparator<Cliente> comparadorPor(String criterio) {
        java.text.Collator col = java.text.Collator.getInstance(new java.util.Locale("es", "CO"));
        if ("Cédula".equals(criterio)) {
            return java.util.Comparator
                    .comparingInt((Cliente c) -> c.getCedula().length())
                    .thenComparing(Cliente::getCedula);
        }
        if ("Apellido".equals(criterio)) {
            return (a, b) -> {
                int r = col.compare(apellidoDe(a.getNombre()), apellidoDe(b.getNombre()));
                return r != 0 ? r : col.compare(a.getNombre(), b.getNombre());
            };
        }
        return (a, b) -> col.compare(a.getNombre().trim(), b.getNombre().trim());
    }

    private void cargar() {
        maestro.setAll(clienteRepository.findAll());
        actualizarResumen();
    }
    
    /** Cuenta bonos, parqueaderos y bingos por cédula en el rango Desde/Hasta. */
    private void recalcularConteos() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            return;
        }
        conteos = movimientoService.contar(desde, hasta);
        tablaClientes.refresh();
    }
    
    // ==================== Exportar a Excel ====================

    private static final String[] COLUMNAS_EXPORT = {"Cédula", "Nombre", "Apellido", "Teléfono",
        "Etiqueta", "Cumpleaños", "Bonos", "Parqueaderos", "Bingos"};

    /** Valor de la columna i de COLUMNAS_EXPORT para un cliente. */
    private Object valorExport(int i, Cliente c) {
        ClienteMovimientoService.Conteo n = conteos.getOrDefault(c.getCedula().trim(), SIN_MOVIMIENTO);
        switch (i) {
            case 0: return formatearCedula(c.getCedula());
            case 1: return tituloNombre(c.getNombre());
            case 2: return tituloNombre(apellidoDe(c.getNombre()));
            case 3: return c.getTelefono() == null ? "" : c.getTelefono();
            case 4: return c.getEtiqueta() == null ? "" : c.getEtiqueta();
            case 5: return c.getCumpleanos() == null ? ""
                    : c.getCumpleanos().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            case 6: return n.bonos();
            case 7: return n.parqueaderos();
            default: return n.bingos();
        }
    }

    /** Una línea por columna propia: «Nombre» o «Nombre = valor fijo». */
    private List<String[]> columnasPropias(String texto) {
        List<String[]> r = new ArrayList<>();
        if (texto == null) {
            return r;
        }
        for (String linea : texto.split("\\R")) {
            String l = linea.trim();
            if (l.isEmpty()) {
                continue;
            }
            int eq = l.indexOf('=');
            String nombre = (eq < 0 ? l : l.substring(0, eq)).trim();
            String valor = eq < 0 ? "" : l.substring(eq + 1).trim();
            if (!nombre.isEmpty()) {
                r.add(new String[] {nombre, valor});
            }
        }
        return r;
    }

    private String validarExport(boolean prellenar, CheckBox[] chk, String propias, String filasTxt) {
        int n;
        try {
            n = Integer.parseInt(filasTxt.trim());
        } catch (NumberFormatException e) {
            return "Las filas deben ser un número entero.";
        }
        if (n < 0 || n > 500) {
            return "Las filas deben estar entre 0 y 500.";
        }
        boolean hayLista = false;
        for (CheckBox c : chk) {
            hayLista |= c.isSelected();
        }
        if (!hayLista && columnasPropias(propias).isEmpty()) {
            return "Elige al menos una columna o escribe una propia.";
        }
        if (!prellenar && n == 0) {
            return "Escribe cuántas filas quieres.";
        }
        return null;
    }

    @FXML
    public void handleExportar() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Exportar a Excel");
        dialogo.setHeaderText(null);
        dialogo.initOwner(tablaClientes.getScene().getWindow());

        TextField txtTitulo = new TextField(ultimoTituloExport);
        txtTitulo.setPrefWidth(360);

        CheckBox chkPrellenar = new CheckBox("Prellenar con los clientes que se ven en la tabla ("
            + tablaClientes.getItems().size() + ")");
        chkPrellenar.setSelected(true);
        chkPrellenar.setWrapText(true);
        chkPrellenar.setMaxWidth(380);

        CheckBox[] chk = new CheckBox[COLUMNAS_EXPORT.length];
        GridPane cols = new GridPane();
        cols.setHgap(16);
        cols.setVgap(6);
        for (int i = 0; i < chk.length; i++) {
            chk[i] = new CheckBox(COLUMNAS_EXPORT[i]);
            chk[i].setSelected(i < 2);
            cols.add(chk[i], i % 3, i / 3);
        }

        TextArea txtPropias = new TextArea();
        txtPropias.setPromptText("Una columna por línea. Ejemplos:\nJugador\nMarcador\nValor = 10000");
        txtPropias.setPrefRowCount(5);
        txtPropias.setPrefWidth(360);

        CheckBox chkNumerar = new CheckBox("Numerar las filas (columna #)");
        chkNumerar.setSelected(true);

        Label lblFilas = new Label("Filas en blanco al final:");
        TextField txtFilas = new TextField("0");
        txtFilas.setPrefWidth(70);

        chkPrellenar.selectedProperty().addListener((obs, a, sel) -> {
            
            lblFilas.setText(sel ? "Filas en blanco al final:" : "Cantidad de filas:");
            txtFilas.setText(sel ? "0" : "30");
        });

        ComboBox<String> cmbOrientacion = new ComboBox<>();
        cmbOrientacion.getItems().addAll("Vertical", "Horizontal");
        cmbOrientacion.setValue("Vertical");

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #dc2626;");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        javafx.scene.layout.HBox filaFilas = new javafx.scene.layout.HBox(8, lblFilas, txtFilas);
        filaFilas.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox filaOrient = new javafx.scene.layout.HBox(8, new Label("Hoja:"), cmbOrientacion);
        filaOrient.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox contenido = new VBox(10,
            new Label("Título de la hoja"), txtTitulo,
            chkPrellenar,
            new Label("Columnas de la lista"), cols,
            new Label("Columnas propias (vacías, o con valor fijo: Nombre = valor)"), txtPropias,
            chkNumerar, filaFilas, filaOrient, lblError);
        contenido.setPadding(new Insets(10, 10, 0, 10));

        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button btnOk = (Button) dialogo.getDialogPane().lookupButton(ButtonType.OK);
        btnOk.setText("Exportar");
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");

        btnOk.addEventFilter(ActionEvent.ACTION, ev -> {
            String error = validarExport(chkPrellenar.isSelected(), chk, txtPropias.getText(), txtFilas.getText());
            if (error != null) {
                lblError.setText(error);
                ev.consume();
            }
        });

        Optional<ButtonType> resp = dialogo.showAndWait();
        if (resp.isEmpty() || resp.get() != ButtonType.OK) {
            return;
        }

        ultimoTituloExport = txtTitulo.getText();
        

        boolean prellenar = chkPrellenar.isSelected();
        int filasBlanco = Integer.parseInt(txtFilas.getText().trim());
        List<String[]> propias = columnasPropias(txtPropias.getText());

        List<ClienteExcelService.Columna> columnas = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < chk.length; i++) {
            if (chk[i].isSelected()) {
                indices.add(i);
                columnas.add(new ClienteExcelService.Columna(COLUMNAS_EXPORT[i], i >= 4));
            }
        }
        for (String[] p : propias) {
            columnas.add(new ClienteExcelService.Columna(p[0], false));
        }

        List<List<Object>> filas = new ArrayList<>();
        if (prellenar) {
            for (Cliente c : new ArrayList<>(tablaClientes.getItems())) {
                List<Object> f = new ArrayList<>();
                for (int idx : indices) {
                    f.add(valorExport(idx, c));
                }
                for (String[] p : propias) {
                    f.add(p[1]);
                }
                filas.add(f);
            }
        }
        for (int i = 0; i < filasBlanco; i++) {
            List<Object> f = new ArrayList<>();
            for (int idx : indices) {
                f.add("");
            }
            for (String[] p : propias) {
                f.add(p[1]);
            }
            filas.add(f);
        }

        String subtitulo = "";
        boolean usaConteos = indices.contains(6) || indices.contains(7) || indices.contains(8);
        if (usaConteos && dpDesde.getValue() != null && dpHasta.getValue() != null) {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            subtitulo = "Del " + dpDesde.getValue().format(fmt) + " al " + dpHasta.getValue().format(fmt);
        }

        String base = txtTitulo.getText().replaceAll("[\\\\/:*?\"<>|]", "").trim();
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar Excel");
        selector.setInitialFileName((base.isEmpty() ? "lista_clientes" : base) + ".xlsx");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File destino = selector.showSaveDialog(tablaClientes.getScene().getWindow());
        if (destino == null) {
            return;
        }
        String ruta = destino.getAbsolutePath();
        if (!ruta.toLowerCase().endsWith(".xlsx")) {
            ruta += ".xlsx";
        }

        try {
            excelService.exportar(txtTitulo.getText(), subtitulo, columnas, filas,
                chkNumerar.isSelected(), 0, "Horizontal".equals(cmbOrientacion.getValue()), ruta);
            mostrar(Alert.AlertType.INFORMATION, "Exportar a Excel", "Archivo guardado:\n" + ruta, null);
        } catch (Exception e) {
            log.error("No se pudo exportar el Excel de clientes", e);
            mostrar(Alert.AlertType.ERROR, "Exportar a Excel",
                "No se pudo guardar el archivo. Si lo tienes abierto en Excel, ciérralo e intenta de nuevo.", null);
        }
    }

    private void ponerRango(LocalDate desde, LocalDate hasta) {
        LocalDate actual = dpDesde.getValue();
        // Orden que nunca deja Desde > Hasta, ni un instante
        if (actual == null || desde.isAfter(actual)) {
            dpHasta.setValue(hasta);
            dpDesde.setValue(desde);
        } else {
            dpDesde.setValue(desde);
            dpHasta.setValue(hasta);
        }
    }

    private YearMonth mesVisible() {
        return YearMonth.from(dpDesde.getValue() != null ? dpDesde.getValue() : LocalDate.now());
    }

    @FXML private void handleMesAnterior() {
        YearMonth ym = mesVisible().minusMonths(1);
        ponerRango(ym.atDay(1), ym.atEndOfMonth());
    }

    @FXML private void handleMesSiguiente() {
        YearMonth ym = mesVisible().plusMonths(1);
        ponerRango(ym.atDay(1), ym.atEndOfMonth());
    }

    @FXML private void handleEsteMes() {
        YearMonth ym = YearMonth.now();
        ponerRango(ym.atDay(1), ym.atEndOfMonth());
    }

    @FXML private void handleQuincena1() {
        YearMonth ym = mesVisible();
        ponerRango(ym.atDay(1), ym.atDay(15));
    }

    @FXML private void handleQuincena2() {
        YearMonth ym = mesVisible();
        ponerRango(ym.atDay(16), ym.atEndOfMonth());
    }

    private void actualizarResumen() {
        lblResumen.setText(filtrados.size() + " de " + maestro.size() + " cliente(s)");
    }

    // ==================== Agregar y editar ====================

    @FXML
    public void handleAgregar() {
        log.debug("Abriendo formulario para agregar cliente");
        abrirFormulario(null);
    }

    @FXML
    public void handleEditar() {
        List<Cliente> seleccion = tablaClientes.getSelectionModel().getSelectedItems();
        if (seleccion.size() != 1) {
            log.warn("Editar cliente: hay que seleccionar exactamente uno (seleccionados: {})", seleccion.size());
            mostrar(Alert.AlertType.INFORMATION, "Editar cliente",
                "Selecciona un solo cliente en la tabla.", null);
            return;
        }
        log.debug("Abriendo formulario para editar cliente");
        abrirFormulario(seleccion.get(0));
    }

    /**
     * Formulario para agregar (existente == null) o editar un cliente.
     * Al editar solo se cambia el nombre: la cédula es la llave y no se modifica.
     * Si los datos no son válidos, el formulario sigue abierto mostrando el motivo.
     */
    private void abrirFormulario(Cliente existente) {
        boolean editando = existente != null;

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(editando ? "Editar cliente" : "Agregar cliente");
        dialogo.setHeaderText(null);
        dialogo.initOwner(tablaClientes.getScene().getWindow());

        TextField txtCedulaForm = new TextField(editando ? existente.getCedula() : "");
        txtCedulaForm.setPromptText("Solo números");
        txtCedulaForm.setEditable(!editando);
        txtCedulaForm.setPrefWidth(280);

        TextField txtNombreForm = new TextField(editando ? existente.getNombre() : "");
        txtNombreForm.setPromptText("Nombre completo");
        txtNombreForm.setPrefWidth(280);
        
        TextField txtTelefonoForm = new TextField(editando && existente.getTelefono() != null
                ? existente.getTelefono() : "");
            txtTelefonoForm.setPromptText("Opcional");
            txtTelefonoForm.setPrefWidth(280);

            ComboBox<String> cmbEtiquetaForm = new ComboBox<>();
            cmbEtiquetaForm.getItems().addAll("Sin etiqueta", "Tipo 1", "Tipo 2", "Tipo 3");
            cmbEtiquetaForm.setValue(editando && existente.getEtiqueta() != null
                ? existente.getEtiqueta() : "Sin etiqueta");
            cmbEtiquetaForm.setPrefWidth(280);

            DatePicker dpCumpleForm = new DatePicker();
            SelectorFecha.configurar(dpCumpleForm);
            dpCumpleForm.setValue(editando ? existente.getCumpleanos() : null);
            dpCumpleForm.setPrefWidth(280);
        

        CheckBox chkCorregirGastos = new CheckBox(
            "Corregir también el nombre en los gastos ya registrados de este cliente");
        chkCorregirGastos.setSelected(true);
        chkCorregirGastos.setWrapText(true);
        chkCorregirGastos.setMaxWidth(380);

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #dc2626;");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 0, 10));
        grid.add(new Label("Cédula"), 0, 0);
        grid.add(txtCedulaForm, 1, 0);
        grid.add(new Label("Nombre"), 0, 1);
        grid.add(txtNombreForm, 1, 1);
        grid.add(new Label("Teléfono"), 0, 2);
        grid.add(txtTelefonoForm, 1, 2);
        grid.add(new Label("Etiqueta"), 0, 3);
        grid.add(cmbEtiquetaForm, 1, 3);
        grid.add(new Label("Cumpleaños"), 0, 4);
        grid.add(dpCumpleForm, 1, 4);
        int fila = 5;
        if (editando) {
            Label nota = new Label("La cédula no se puede cambiar. Si está mal, elimina el cliente y créalo de nuevo.");
            nota.setStyle("-fx-text-fill: #64748b;");
            nota.setWrapText(true);
            nota.setMaxWidth(380);
            grid.add(nota, 0, fila++, 2, 1);
            grid.add(chkCorregirGastos, 0, fila++, 2, 1);
        }
        grid.add(lblError, 0, fila, 2, 1);

        dialogo.getDialogPane().setContent(grid);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button btnGuardar = (Button) dialogo.getDialogPane().lookupButton(ButtonType.OK);
        btnGuardar.setText("Guardar");
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");

        // Guarda antes de cerrar; si algo no es válido, el formulario queda abierto
        int[] gastosCorregidos = {0};
        btnGuardar.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String etiqueta = "Sin etiqueta".equals(cmbEtiquetaForm.getValue())
                        ? null : cmbEtiquetaForm.getValue();
                    if (editando) {
                        ClienteService.ResultadoEdicion resultado = clienteService.actualizar(
                            existente.getCedula(), txtNombreForm.getText(), txtTelefonoForm.getText(),
                            etiqueta, dpCumpleForm.getValue(), chkCorregirGastos.isSelected());
                        gastosCorregidos[0] = resultado.gastosCorregidos();
                    } else {
                        clienteService.crear(txtCedulaForm.getText(), txtNombreForm.getText(),
                            txtTelefonoForm.getText(), etiqueta, dpCumpleForm.getValue());
                    }
            } catch (IllegalArgumentException ex) {
                lblError.setText(ex.getMessage());
                ev.consume();
            } catch (Exception ex) {
                log.error("No se pudo guardar el cliente", ex);
                lblError.setText("No se pudo guardar el cliente. Revisa el registro de errores.");
                ev.consume();
            }
        });

        Platform.runLater(() -> (editando ? txtNombreForm : txtCedulaForm).requestFocus());

        Optional<ButtonType> resp = dialogo.showAndWait();
        if (resp.isPresent() && resp.get() == ButtonType.OK) {
            cargar();
            if (editando && gastosCorregidos[0] > 0) {
                mostrar(Alert.AlertType.INFORMATION, "Cliente actualizado",
                    "Se corrigió el nombre en " + gastosCorregidos[0] + " gasto(s) ya registrado(s).", null);
            }
        } else {
            log.debug("Formulario de cliente cerrado sin guardar");
        }
    }

    // ==================== Importar desde Excel ====================

    @FXML
    public void handleImportar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir Excel de clientes");
        selector.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
        File archivo = selector.showOpenDialog(tablaClientes.getScene().getWindow());
        if (archivo == null) {
            return;
        }

        VistaPrevia vista;
        try {
            vista = importService.analizar(archivo);
        } catch (Exception e) {
            log.error("No se pudo leer el Excel de clientes: {}", archivo.getName(), e);
            mostrar(Alert.AlertType.ERROR, "No se pudo leer el archivo",
                "Revisa que sea un Excel válido y que no esté abierto en otro programa.\n\n"
                + e.getMessage(), null);
            return;
        }

        String problemas = String.join("\n", vista.problemas());
        String resumen = "Archivo: " + archivo.getName() + "\n\n"
            + "Clientes nuevos: " + vista.nuevos().size() + "\n"
            + "Nombres que se actualizan: " + vista.actualizados().size() + "\n"
            + "Sin cambios: " + vista.sinCambios() + "\n"
            + "Filas con problema (no se importan): " + vista.problemas().size();

        if (vista.totalAplicables() == 0) {
            mostrar(Alert.AlertType.INFORMATION, "Nada para guardar",
                resumen + "\n\nNo hay clientes nuevos ni cambios.",
                problemas.isEmpty() ? null : problemas);
            return;
        }

        Optional<ButtonType> resp = mostrar(Alert.AlertType.CONFIRMATION, "Confirmar importación",
            resumen + "\n\nSe guardarán " + vista.totalAplicables() + " cliente(s). ¿Continuar?",
            problemas.isEmpty() ? null : problemas);
        if (resp.isEmpty() || resp.get() != ButtonType.OK) {
            log.info("Importación de clientes cancelada por el usuario");
            return;
        }

        try {
            importService.aplicar(vista);
            log.info("Clientes importados: {} nuevos, {} actualizados, {} con problema",
                vista.nuevos().size(), vista.actualizados().size(), vista.problemas().size());
            cargar();
            mostrar(Alert.AlertType.INFORMATION, "Importación lista",
                "Se guardaron " + vista.totalAplicables() + " cliente(s).", null);
        } catch (Exception e) {
            log.error("No se pudieron guardar los clientes importados", e);
            mostrar(Alert.AlertType.ERROR, "No se pudo guardar",
                "No se guardó ningún cambio.\n\n" + e.getMessage(), null);
        }
    }

    // ==================== Eliminar ====================

    @FXML
    public void handleEliminar() {
        List<Cliente> seleccion = List.copyOf(tablaClientes.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) {
            mostrar(Alert.AlertType.INFORMATION, "Eliminar clientes",
                "Selecciona uno o más clientes en la tabla.", null);
            return;
        }
        Optional<ButtonType> resp = mostrar(Alert.AlertType.CONFIRMATION, "Eliminar clientes",
            "Se eliminarán " + seleccion.size() + " cliente(s) de la lista.\n"
            + "Los gastos ya registrados no se tocan. ¿Continuar?", null);
        if (resp.isEmpty() || resp.get() != ButtonType.OK) {
            return;
        }
        try {
            clienteRepository.deleteAll(seleccion);
            log.info("Clientes eliminados: {}", seleccion.size());
            cargar();
        } catch (Exception e) {
            log.error("No se pudieron eliminar los clientes", e);
            mostrar(Alert.AlertType.ERROR, "No se pudo eliminar",
                "No se eliminó ningún cliente.\n\n" + e.getMessage(), null);
        }
    }

    private Optional<ButtonType> mostrar(Alert.AlertType tipo, String titulo, String mensaje, String detalle) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        if (detalle != null) {
            TextArea area = new TextArea(detalle);
            area.setEditable(false);
            area.setPrefRowCount(10);
            alerta.getDialogPane().setExpandableContent(area);
            alerta.getDialogPane().setExpanded(true);
        }
        return alerta.showAndWait();
    }
}