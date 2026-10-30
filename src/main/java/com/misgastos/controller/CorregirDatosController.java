package com.misgastos.controller;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.misgastos.service.CorreccionDatosService;
import com.misgastos.service.CorreccionDatosService.Campo;
import com.misgastos.service.CorreccionDatosService.Resultado;
import com.misgastos.service.CorreccionDatosService.ValorConteo;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Pantalla "Corregir datos": lista los valores distintos de un campo de los
 * gastos (producto o nombre de la persona) con su cantidad, y permite
 * reemplazar uno o varios mal escritos por el texto correcto.
 * Antes de aplicar muestra cuántos gastos cambiarán y pide confirmación.
 */
@Component
public class CorregirDatosController {

    private static final Logger log = LoggerFactory.getLogger(CorregirDatosController.class);

    @FXML private ComboBox<Campo> cmbCampo;
    @FXML private TextField txtBuscar;
    @FXML private Label lblResumen;
    @FXML private TableView<ValorConteo> tablaValores;
    @FXML private TableColumn<ValorConteo, String> colValor;
    @FXML private TableColumn<ValorConteo, Number> colCantidad;
    @FXML private TextField txtCorrecto;
    @FXML private CheckBox chkClientes;
    @FXML private Label lblAviso;
    @FXML private Button btnAplicar;

    @Autowired private CorreccionDatosService servicio;

    private final ObservableList<ValorConteo> maestro = FXCollections.observableArrayList();
    private FilteredList<ValorConteo> filtrados;
    private String ultimoAutorelleno = "";

    @FXML
    public void initialize() {
        cmbCampo.setItems(FXCollections.observableArrayList(Campo.values()));
        cmbCampo.getSelectionModel().select(Campo.PRODUCTO);

        tablaValores.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        colValor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().valor()));
        colCantidad.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().cantidad()));
        colCantidad.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Marca los valores con espacios de más, que a simple vista no se notan
        colValor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setText(null);
                } else if (!valor.equals(valor.replaceAll("\\s+", " ").trim())) {
                    setText(valor + "   (tiene espacios de más)");
                } else {
                    setText(valor);
                }
            }
        });

        filtrados = new FilteredList<>(maestro, v -> true);
        SortedList<ValorConteo> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tablaValores.comparatorProperty());
        tablaValores.setItems(ordenados);
        tablaValores.setPlaceholder(new Label("No hay valores para mostrar."));

        txtBuscar.textProperty().addListener((obs, a, b) -> {
            String t = b == null ? "" : b.trim().toLowerCase();
            filtrados.setPredicate(v -> t.isEmpty() || v.valor().toLowerCase().contains(t));
            actualizarResumen();
        });

        cmbCampo.valueProperty().addListener((obs, a, b) -> {
            log.debug("Campo a corregir: {}", b);
            cargar();
        });

        tablaValores.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<ValorConteo>) c -> alCambiarSeleccion());
        txtCorrecto.textProperty().addListener((obs, a, b) -> actualizarAviso());

        cargar();
    }

    private Campo campo() {
        return cmbCampo.getValue();
    }

    private void cargar() {
        maestro.setAll(servicio.listar(campo()));
        chkClientes.setVisible(campo() == Campo.NOMBRE_PERSONA);
        chkClientes.setManaged(campo() == Campo.NOMBRE_PERSONA);
        chkClientes.setSelected(true);
        txtCorrecto.clear();
        ultimoAutorelleno = "";
        actualizarResumen();
        actualizarAviso();
    }

    private void actualizarResumen() {
        lblResumen.setText(filtrados.size() + " de " + maestro.size() + " valor(es)");
    }

    private List<String> valoresSeleccionados() {
        return tablaValores.getSelectionModel().getSelectedItems().stream()
            .map(ValorConteo::valor).toList();
    }

    /** Si todavía no escribiste nada propio, sugiere el valor más usado de la selección. */
    private void alCambiarSeleccion() {
        List<ValorConteo> sel = List.copyOf(tablaValores.getSelectionModel().getSelectedItems());
        String actual = txtCorrecto.getText() == null ? "" : txtCorrecto.getText();
        if (!sel.isEmpty() && (actual.isEmpty() || actual.equals(ultimoAutorelleno))) {
            String sugerido = sel.stream()
                .max(java.util.Comparator.comparingLong(ValorConteo::cantidad))
                .get().valor().replaceAll("\\s+", " ").trim();
            ultimoAutorelleno = sugerido;
            txtCorrecto.setText(sugerido);
        }
        actualizarAviso();
    }

    private void actualizarAviso() {
        List<String> valores = valoresSeleccionados();
        String nuevo = txtCorrecto.getText() == null ? "" : txtCorrecto.getText();
        if (valores.isEmpty() || nuevo.trim().isEmpty()) {
            lblAviso.setText("Selecciona uno o más valores y escribe el texto correcto.");
            btnAplicar.setDisable(true);
            return;
        }
        long afectados = servicio.contarAfectados(campo(), valores, nuevo);
        StringBuilder sb = new StringBuilder();
        sb.append("Se cambiarán ").append(afectados).append(" gasto(s) a «")
          .append(nuevo.replaceAll("\\s+", " ").trim()).append("».");
        if (campo() == Campo.NOMBRE_PERSONA) {
            int cedulas = servicio.contarCedulas(valores);
            sb.append("\nCédulas distintas afectadas: ").append(cedulas).append(".");
            if (cedulas > 1) {
                sb.append(" Revisa que sea la misma persona.");
            }
        }
        lblAviso.setText(sb.toString());
        btnAplicar.setDisable(afectados == 0 && campo() != Campo.NOMBRE_PERSONA);
    }

    @FXML
    public void handleAplicar() {
        List<String> valores = List.copyOf(valoresSeleccionados());
        String nuevo = txtCorrecto.getText();
        if (valores.isEmpty()) {
            mostrar(Alert.AlertType.INFORMATION, "Corregir datos", "Selecciona uno o más valores de la lista.");
            return;
        }

        long afectados = servicio.contarAfectados(campo(), valores, nuevo);
        Optional<ButtonType> resp = mostrar(Alert.AlertType.CONFIRMATION, "Confirmar corrección",
            "Se cambiarán " + afectados + " gasto(s) (incluidos los de la papelera) para que "
            + campo().toString().toLowerCase() + " quede como:\n\n«"
            + nuevo.replaceAll("\\s+", " ").trim() + "»\n\n¿Continuar?");
        if (resp.isEmpty() || resp.get() != ButtonType.OK) {
            log.info("Corrección de datos cancelada por el usuario");
            return;
        }

        try {
            Resultado r = servicio.corregir(campo(), valores, nuevo, chkClientes.isSelected());
            String msg = "Se corrigieron " + r.gastos() + " gasto(s).";
            if (campo() == Campo.NOMBRE_PERSONA && chkClientes.isSelected()) {
                msg += "\nSe actualizaron " + r.clientes() + " cliente(s) en la lista de Clientes.";
            }
            cargar();
            mostrar(Alert.AlertType.INFORMATION, "Corrección lista", msg);
        } catch (IllegalArgumentException ex) {
            log.warn("Corrección de datos rechazada: {}", ex.getMessage());
            mostrar(Alert.AlertType.WARNING, "No se pudo corregir", ex.getMessage());
        } catch (Exception ex) {
            log.error("No se pudo aplicar la corrección de datos", ex);
            mostrar(Alert.AlertType.ERROR, "No se pudo corregir",
                "No se guardó ningún cambio. Revisa el registro de errores.");
        }
    }

    @FXML
    public void handleLimpiar() {
        tablaValores.getSelectionModel().clearSelection();
        txtCorrecto.clear();
        ultimoAutorelleno = "";
        txtBuscar.clear();
        actualizarAviso();
    }

    private Optional<ButtonType> mostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        return alerta.showAndWait();
    }
}