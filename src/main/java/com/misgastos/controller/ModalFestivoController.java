package com.misgastos.controller;

import com.misgastos.model.Festivo;
import com.misgastos.service.FestivoService;
import com.misgastos.util.SelectorFecha;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Modal para crear o editar un festivo (fecha y nombre). */
@Slf4j
@Component
public class ModalFestivoController {

    @FXML private Label lblTitulo;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtNombre;
    @FXML private Label lblError;

    @Autowired
    private FestivoService festivoService;

    private Festivo festivo;
    private Runnable onGuardarCallback;

    @FXML
    public void initialize() {
        log.debug("Inicializando modal de festivo");
        SelectorFecha.configurar(dpFecha);
    }

    public void setFestivo(Festivo festivo) {
        this.festivo = festivo;
        if (festivo != null) {
            lblTitulo.setText("Editar festivo");
            dpFecha.setValue(festivo.getFecha());
            txtNombre.setText(festivo.getNombre());
        } else {
            lblTitulo.setText("Nuevo festivo");
            dpFecha.setValue(LocalDate.now());
        }
        log.debug("Modal de festivo preparado: {}", festivo == null ? "nuevo" : "edición");
    }

    public void setOnGuardarCallback(Runnable callback) {
        this.onGuardarCallback = callback;
    }

    @FXML
    private void handleGuardar() {
        ocultarError();
        try {
            LocalDate fecha = dpFecha.getValue();
            if (fecha == null) {
                log.warn("No se guardó el festivo: falta la fecha");
                throw new RuntimeException("Elija la fecha del festivo");
            }

            if (festivo == null) {
                festivoService.crear(fecha, txtNombre.getText());
            } else {
                festivoService.actualizar(festivo.getId(), fecha, txtNombre.getText());
            }

            log.info("Festivo guardado correctamente");
            if (onGuardarCallback != null) {
                onGuardarCallback.run();
            }
            cerrar();
        } catch (Exception e) {
            mostrarError(e.getMessage());
            log.error("No se pudo guardar el festivo", e);
        }
    }

    @FXML
    private void handleCancelar() {
        cerrar();
    }

    private void mostrarError(String mensaje) {
        lblError.setText("⚠ " + mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void cerrar() {
        Stage stage = (Stage) txtNombre.getScene().getWindow();
        stage.close();
    }
}