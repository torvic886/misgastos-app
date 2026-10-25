package com.misgastos.controller;

import com.misgastos.service.PresupuestoService;
import com.misgastos.service.PresupuestoService.EstadoPresupuesto;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javafx.scene.control.TextField;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Pantalla "Presupuestos": fija el tope mensual de cada categoría. Solo administradores. */
@Slf4j
@Component
public class PresupuestosController {

    private static final Locale CO = new Locale("es", "CO");

    @FXML private TableView<EstadoPresupuesto> tablaPresupuestos;
    @FXML private TableColumn<EstadoPresupuesto, String> colCategoria;
    @FXML private TableColumn<EstadoPresupuesto, String> colTope;
    @FXML private TableColumn<EstadoPresupuesto, String> colGastado;
    @FXML private TableColumn<EstadoPresupuesto, String> colPorcentaje;
    @FXML private Label lblMes;

    @Autowired
    private PresupuestoService presupuestoService;

    @FXML
    public void initialize() {
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categoria()));
        colTope.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().tieneTope() ? dinero(c.getValue().tope()) : "Sin tope"));
        colGastado.setCellValueFactory(c -> new SimpleStringProperty(dinero(c.getValue().gastado())));
        colPorcentaje.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().tieneTope() ? Math.round(c.getValue().porcentaje()) + " %" : "—"));

        lblMes.setText("Mes en curso: " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", CO)));
        recargar();
    }

    private void recargar() {
        tablaPresupuestos.getItems().setAll(presupuestoService.estadoDelMes(LocalDate.now()));
    }

    @FXML
    public void handleFijarTope() {
        EstadoPresupuesto fila = tablaPresupuestos.getSelectionModel().getSelectedItem();
        if (fila == null) {
            alerta(Alert.AlertType.WARNING, "Elige una categoría", "Selecciona una categoría de la tabla.");
            return;
        }

        NumberFormat miles = NumberFormat.getInstance(CO);
        miles.setMaximumFractionDigits(0);

        String inicial = fila.tieneTope() ? "$ " + miles.format(fila.tope().toBigInteger()) : "";
        TextInputDialog dialogo = new TextInputDialog(inicial);
        dialogo.setTitle("Fijar tope mensual");
        dialogo.setHeaderText("Categoría: " + fila.categoria());
        dialogo.setContentText("Tope mensual (pesos colombianos):");

        // Formato de pesos mientras se escribe: 500000 -> $ 500.000
        TextField editor = dialogo.getEditor();
        editor.setPromptText("$ 500.000");
        boolean[] ocupado = {false};
        editor.textProperty().addListener((obs, antes, ahora) -> {
            if (ocupado[0] || ahora == null) {
                return;
            }
            String digitos = ahora.replaceAll("[^0-9]", "");
            if (digitos.length() > 12) {
                digitos = digitos.substring(0, 12);
            }
            String formateado = digitos.isEmpty() ? "" : "$ " + miles.format(new BigInteger(digitos));
            if (!formateado.equals(ahora)) {
                ocupado[0] = true;
                int desdeElFinal = ahora.length() - editor.getCaretPosition();
                editor.setText(formateado);
                int minimo = formateado.isEmpty() ? 0 : 2; // después del "$ "
                int posicion = Math.max(minimo, Math.min(formateado.length() - desdeElFinal, formateado.length()));
                editor.positionCaret(posicion);
                ocupado[0] = false;
            }
        });

        Optional<String> respuesta = dialogo.showAndWait();
        if (respuesta.isEmpty()) {
            return;
        }
        String soloNumeros = respuesta.get().replaceAll("[^0-9]", "");
        if (soloNumeros.isEmpty() || new BigDecimal(soloNumeros).signum() <= 0) {
            alerta(Alert.AlertType.WARNING, "Valor no válido", "Escribe un valor mayor que cero, por ejemplo 500000.");
            return;
        }
        try {
            presupuestoService.fijarTope(fila.categoriaId(), new BigDecimal(soloNumeros));
            recargar();
        } catch (Exception e) {
        	log.error("No se pudo fijar el tope de la categoría {}", fila.categoriaId(), e);
            alerta(Alert.AlertType.ERROR, "No se pudo guardar", "Motivo: " + e.getMessage());
        }
    }

    @FXML
    public void handleQuitarTope() {
        EstadoPresupuesto fila = tablaPresupuestos.getSelectionModel().getSelectedItem();
        if (fila == null) {
            alerta(Alert.AlertType.WARNING, "Elige una categoría", "Selecciona una categoría de la tabla.");
            return;
        }
        if (!fila.tieneTope()) {
            alerta(Alert.AlertType.INFORMATION, "Sin tope", "Esa categoría no tiene tope fijado.");
            return;
        }
        try {
            presupuestoService.quitarTope(fila.categoriaId());
            recargar();
        } catch (Exception e) {
        	log.error("No se pudo quitar el tope de la categoría {}", fila.categoriaId(), e);
            alerta(Alert.AlertType.ERROR, "No se pudo quitar", "Motivo: " + e.getMessage());
        }
    }

    private String dinero(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(CO);
        f.setMaximumFractionDigits(0);
        return f.format(v);
    }

    private void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        try {
            a.getDialogPane().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignorada) {
            // sin estilos, el diálogo funciona igual
        }
        a.showAndWait();
    }
}