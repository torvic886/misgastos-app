package com.misgastos.controller;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.misgastos.model.Gasto;
import com.misgastos.service.GastoService;
import com.misgastos.service.diseno.Diseno;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Papelera de gastos: lista los gastos eliminados y permite restaurarlos
 * o borrarlos de forma definitiva. Solo se muestra a administradores.
 */
@Slf4j
@Component
public class PapeleraController {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private TableView<Gasto> tablaPapelera;
    @FXML private TableColumn<Gasto, String> colEliminado;
    @FXML private TableColumn<Gasto, String> colFecha;
    @FXML private TableColumn<Gasto, String> colHora;
    @FXML private TableColumn<Gasto, String> colUsuario;
    @FXML private TableColumn<Gasto, String> colCategoria;
    @FXML private TableColumn<Gasto, String> colProducto;
    @FXML private TableColumn<Gasto, String> colCedula;
    @FXML private TableColumn<Gasto, String> colNombre;
    @FXML private TableColumn<Gasto, String> colCantidad;
    @FXML private TableColumn<Gasto, String> colTotal;
    @FXML private Label lblResumen;

    @Autowired
    private GastoService gastoService;

    @FXML
    public void initialize() {
        tablaPapelera.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        colEliminado.setCellValueFactory(c -> texto(c.getValue().getFechaEliminacion() == null
            ? "" : c.getValue().getFechaEliminacion().format(FECHA_HORA)));
        colFecha.setCellValueFactory(c -> texto(Diseno.fecha(c.getValue().getFecha())));
        colHora.setCellValueFactory(c -> texto(c.getValue().getHora() == null
            ? "" : c.getValue().getHora().format(HORA)));
        colUsuario.setCellValueFactory(c -> texto(c.getValue().getUsuario() == null
            ? "" : c.getValue().getUsuario().getUsername()));
        colCategoria.setCellValueFactory(c -> texto(c.getValue().getCategoria().getNombre()));
        colProducto.setCellValueFactory(c -> texto(c.getValue().getProducto()));
        colCedula.setCellValueFactory(c -> texto(c.getValue().getCedula()));
        colNombre.setCellValueFactory(c -> texto(c.getValue().getNombrePersona()));
        colCantidad.setCellValueFactory(c -> texto(String.valueOf(c.getValue().getCantidad())));
        colTotal.setCellValueFactory(c -> texto(Diseno.moneda(c.getValue().getValorTotal())));

        tablaPapelera.setPlaceholder(new Label("La papelera está vacía"));
        cargar();
    }

    private SimpleStringProperty texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }

    private void cargar() {
        List<Gasto> eliminados = gastoService.listarEliminados();
        tablaPapelera.setItems(FXCollections.observableArrayList(eliminados));

        if (eliminados.isEmpty()) {
            lblResumen.setText("La papelera está vacía");
        } else {
            BigDecimal total = eliminados.stream()
                .map(Gasto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblResumen.setText(eliminados.size() + " gasto(s)  |  " + Diseno.moneda(total));
        }
    }

    @FXML
    public void handleRestaurar() {
        List<Gasto> seleccion = List.copyOf(tablaPapelera.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) {
            avisar("Seleccione uno o más gastos para restaurar");
            return;
        }
        log.debug("Restaurando {} gasto(s) desde la papelera", seleccion.size());
        for (Gasto g : seleccion) {
            gastoService.restaurarGasto(g.getId());
        }
        cargar();
    }

    @FXML
    public void handleEliminarDefinitivo() {
        List<Gasto> seleccion = List.copyOf(tablaPapelera.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) {
            avisar("Seleccione uno o más gastos para eliminar");
            return;
        }
        if (!confirmar("Eliminar definitivamente",
            "Se eliminarán " + seleccion.size() + " gasto(s) para siempre. Esta acción no se puede deshacer.")) {
            return;
        }
        log.debug("Eliminando definitivamente {} gasto(s)", seleccion.size());
        for (Gasto g : seleccion) {
            gastoService.eliminarDefinitivamente(g.getId());
        }
        cargar();
    }

    @FXML
    public void handleVaciar() {
        if (tablaPapelera.getItems().isEmpty()) {
            avisar("La papelera ya está vacía");
            return;
        }
        if (!confirmar("Vaciar papelera",
            "Se eliminarán para siempre todos los gastos de la papelera. Esta acción no se puede deshacer.")) {
            return;
        }
        log.debug("Vaciando la papelera");
        gastoService.vaciarPapelera();
        cargar();
    }

    private void avisar(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Papelera");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private boolean confirmar(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        Optional<ButtonType> respuesta = alert.showAndWait();
        return respuesta.isPresent() && respuesta.get() == ButtonType.OK;
    }
}
