package com.misgastos.controller;

import java.io.File;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.misgastos.model.Cliente;
import com.misgastos.repository.ClienteRepository;
import com.misgastos.service.ClienteImportService;
import com.misgastos.service.ClienteImportService.VistaPrevia;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import java.util.List;
import javafx.scene.control.SelectionMode;

/**
 * Pantalla Clientes: lista los clientes y permite importarlos desde un Excel
 * (cédula en la columna A, nombre en la B). Antes de guardar muestra un resumen
 * y pide confirmación.
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
        tablaClientes.setPlaceholder(new Label("Todavía no hay clientes. Usa «Importar clientes desde Excel»."));

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