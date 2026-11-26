package com.misgastos.controller;

import com.misgastos.model.Empleado;
import com.misgastos.service.EmpleadoService;
import com.misgastos.util.SelectorFecha;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Modal para crear o editar un empleado. El retiro se pone o se quita aquí. */
@Slf4j
@Component
public class ModalEmpleadoController {

    @FXML private Label lblTitulo;
    @FXML private TextField txtNombre;
    @FXML private CheckBox chkEncargada;
    @FXML private DatePicker dpIngreso;
    @FXML private DatePicker dpRetiro;
    @FXML private Label lblError;

    @Autowired
    private EmpleadoService empleadoService;

    private Empleado empleado;
    private Runnable onGuardarCallback;

    @FXML
    public void initialize() {
        log.debug("Inicializando modal de empleado");
        SelectorFecha.configurar(dpIngreso);
        SelectorFecha.configurar(dpRetiro);
        dpIngreso.setValue(LocalDate.now());
    }

    public void setEmpleado(Empleado empleado) {
        this.empleado = empleado;
        if (empleado != null) {
            lblTitulo.setText("Editar empleado");
            txtNombre.setText(empleado.getNombre());
            chkEncargada.setSelected(empleado.isEsEncargada());
            dpIngreso.setValue(empleado.getFechaIngreso());
            dpRetiro.setValue(empleado.getFechaRetiro());
        } else {
            lblTitulo.setText("Nuevo empleado");
        }
    }

    public void setOnGuardarCallback(Runnable callback) {
        this.onGuardarCallback = callback;
    }

    @FXML
    private void handleGuardar() {
        ocultarError();
        try {
            String nombre = txtNombre.getText();
            LocalDate ingreso = dpIngreso.getValue();
            LocalDate retiro = dpRetiro.getValue();

            if (empleado == null) {
                if (retiro != null) {
                    throw new RuntimeException("Un empleado nuevo no puede tener fecha de retiro");
                }
                empleadoService.crear(nombre, ingreso, chkEncargada.isSelected());
            } else {
                empleadoService.actualizar(empleado.getId(), nombre, ingreso, chkEncargada.isSelected(), retiro);
            }

            log.info("Empleado guardado correctamente");
            if (onGuardarCallback != null) {
                onGuardarCallback.run();
            }
            cerrar();
        } catch (Exception e) {
            mostrarError(e.getMessage());
            log.error("No se pudo guardar el empleado", e);
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