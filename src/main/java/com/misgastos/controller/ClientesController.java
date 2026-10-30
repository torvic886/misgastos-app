package com.misgastos.controller;

import java.io.File;
import java.util.List;
import java.util.Optional;

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

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ClienteImportService importService;
    @Autowired private ClienteService clienteService;

    private final ObservableList<Cliente> maestro = FXCollections.observableArrayList();
    private FilteredList<Cliente> filtrados;

    @FXML
    public void initialize() {
        tablaClientes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        colCedula.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCedula()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));

        filtrados = new FilteredList<>(maestro, c -> true);
        SortedList<Cliente> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tablaClientes.comparatorProperty());
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

        cargar();
    }

    private void cargar() {
        maestro.setAll(clienteRepository.findAll());
        actualizarResumen();
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
        int fila = 2;
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
                if (editando) {
                    ClienteService.ResultadoEdicion resultado = clienteService.actualizarNombre(
                        existente.getCedula(), txtNombreForm.getText(), chkCorregirGastos.isSelected());
                    gastosCorregidos[0] = resultado.gastosCorregidos();
                } else {
                    clienteService.crear(txtCedulaForm.getText(), txtNombreForm.getText());
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