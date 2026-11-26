package com.misgastos.controller;

import com.misgastos.model.Empleado;
import com.misgastos.model.Novedad;
import com.misgastos.model.TipoNovedad;
import com.misgastos.service.EmpleadoService;
import com.misgastos.service.NovedadService;
import com.misgastos.util.SelectorFecha;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Modal para crear o editar una novedad. Solo informa: no cambia la programación. */
@Slf4j
@Component
public class ModalNovedadController {

    @FXML private Label lblTitulo;
    @FXML private ComboBox<TipoNovedad> cmbTipo;
    @FXML private ComboBox<Empleado> cmbEmpleado;
    @FXML private VBox boxSegunda;
    @FXML private ComboBox<Empleado> cmbEmpleado2;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Label lblDias;
    @FXML private TextArea txtMotivo;
    @FXML private Label lblError;

    @Autowired
    private NovedadService novedadService;

    @Autowired
    private EmpleadoService empleadoService;

    private Novedad novedad;
    private Runnable onGuardarCallback;

    @FXML
    public void initialize() {
        log.debug("Inicializando modal de novedad");
        SelectorFecha.configurar(dpDesde);
        SelectorFecha.configurar(dpHasta);

        cmbTipo.setItems(FXCollections.observableArrayList(TipoNovedad.values()));

        StringConverter<Empleado> conversor = new StringConverter<>() {
            @Override
            public String toString(Empleado e) {
                return e == null ? "" : e.getNombre();
            }

            @Override
            public Empleado fromString(String texto) {
                return null;
            }
        };
        cmbEmpleado.setConverter(conversor);
        cmbEmpleado2.setConverter(conversor);

        // La segunda persona solo existe en los cambios de turno
        cmbTipo.valueProperty().addListener((o, a, b) -> {
            boolean cambio = b == TipoNovedad.CAMBIO_TURNO;
            boxSegunda.setVisible(cambio);
            boxSegunda.setManaged(cambio);
            if (!cambio) {
                cmbEmpleado2.setValue(null);
            }
        });

        // Hasta acompaña a Desde mientras no se haya separado a propósito
        dpDesde.valueProperty().addListener((o, anterior, nuevo) -> {
            LocalDate hasta = dpHasta.getValue();
            if (nuevo != null && (hasta == null || hasta.isBefore(nuevo) || hasta.equals(anterior))) {
                dpHasta.setValue(nuevo);
            }
            cargarEmpleados();
            actualizarDias();
        });
        dpHasta.valueProperty().addListener((o, a, b) -> actualizarDias());
    }

    public void setNovedad(Novedad novedad) {
        this.novedad = novedad;
        ocultarError();
        cmbEmpleado.setValue(null);
        cmbEmpleado2.setValue(null);
        dpHasta.setValue(null);
        if (novedad != null) {
            lblTitulo.setText("Editar novedad");
            cmbTipo.setValue(TipoNovedad.deNombre(novedad.getTipo()));
            // El empleado ya guardado siempre aparece, aunque hoy no esté vigente
            cmbEmpleado.setValue(novedad.getEmpleado());
            cmbEmpleado2.setValue(novedad.getEmpleado2());
            dpDesde.setValue(novedad.getFechaDesde());
            dpHasta.setValue(novedad.getFechaHasta());
            txtMotivo.setText(novedad.getMotivo() == null ? "" : novedad.getMotivo());
        } else {
            lblTitulo.setText("Nueva novedad");
            cmbTipo.setValue(null);
            txtMotivo.clear();
            dpDesde.setValue(LocalDate.now());
        }
        cargarEmpleados();
        actualizarDias();
        log.debug("Modal de novedad preparado: {}", novedad == null ? "nueva" : "edición");
    }

    public void setOnGuardarCallback(Runnable callback) {
        this.onGuardarCallback = callback;
    }

    /** Vigentes en la fecha Desde, más los ya elegidos aunque no estén vigentes ese día. */
    private void cargarEmpleados() {
        LocalDate dia = dpDesde.getValue() == null ? LocalDate.now() : dpDesde.getValue();
        Long id1 = cmbEmpleado.getValue() == null ? null : cmbEmpleado.getValue().getId();
        Long id2 = cmbEmpleado2.getValue() == null ? null : cmbEmpleado2.getValue().getId();

        List<Empleado> lista = new ArrayList<>(empleadoService.listarVigentes(dia));
        agregarSiFalta(lista, id1);
        agregarSiFalta(lista, id2);

        cmbEmpleado.setItems(FXCollections.observableArrayList(lista));
        cmbEmpleado2.setItems(FXCollections.observableArrayList(lista));
        cmbEmpleado.setValue(buscar(lista, id1));
        cmbEmpleado2.setValue(buscar(lista, id2));
        log.debug("Empleados del modal de novedad cargados: {}", lista.size());
    }

    private void agregarSiFalta(List<Empleado> lista, Long id) {
        if (id != null && buscar(lista, id) == null) {
            empleadoService.buscarPorId(id).ifPresent(lista::add);
        }
    }

    private Empleado buscar(List<Empleado> lista, Long id) {
        if (id == null) {
            return null;
        }
        for (Empleado e : lista) {
            if (id.equals(e.getId())) {
                return e;
            }
        }
        return null;
    }

    private void actualizarDias() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            lblDias.setText("");
            return;
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        lblDias.setText(dias + (dias == 1 ? " día" : " días"));
    }

    @FXML
    private void handleGuardar() {
        ocultarError();
        try {
            Empleado empleado = cmbEmpleado.getValue();
            TipoNovedad tipo = cmbTipo.getValue();
            LocalDate desde = dpDesde.getValue();
            LocalDate hasta = dpHasta.getValue() == null ? desde : dpHasta.getValue();
            Empleado segunda = tipo == TipoNovedad.CAMBIO_TURNO ? cmbEmpleado2.getValue() : null;

            // Revisión mínima antes de preguntar por cruces, para no preguntar y luego fallar
            if (empleado == null) {
                throw new RuntimeException("Elija el empleado");
            }
            if (tipo == null) {
                throw new RuntimeException("Elija el tipo de novedad");
            }
            if (desde == null) {
                throw new RuntimeException("Elija la fecha desde");
            }
            if (hasta.isBefore(desde)) {
                throw new RuntimeException("La fecha hasta no puede ser anterior a la fecha desde");
            }
            if (tipo == TipoNovedad.CAMBIO_TURNO && segunda == null) {
                throw new RuntimeException("En un cambio de turno hay que elegir la segunda persona");
            }

            // Los cruces solo avisan: si confirma, se guarda igual
            Long idExcluir = novedad == null ? null : novedad.getId();
            List<Novedad> cruces = novedadService.buscarCruces(empleado.getId(), desde, hasta, idExcluir);
            if (!cruces.isEmpty() && !confirmarCruces(cruces)) {
                log.debug("Guardado de novedad cancelado por el usuario tras el aviso de cruces");
                return;
            }

            Long segundaId = segunda == null ? null : segunda.getId();
            if (novedad == null) {
                novedadService.crear(empleado.getId(), tipo, desde, hasta, segundaId, txtMotivo.getText());
            } else {
                novedadService.actualizar(novedad.getId(), empleado.getId(), tipo, desde, hasta, segundaId,
                        txtMotivo.getText());
            }

            log.info("Novedad guardada correctamente");
            if (onGuardarCallback != null) {
                onGuardarCallback.run();
            }
            cerrar();
        } catch (Exception e) {
            mostrarError(e.getMessage());
            log.error("No se pudo guardar la novedad", e);
        }
    }

    private boolean confirmarCruces(List<Novedad> cruces) {
        log.warn("La novedad se cruza con {} novedad(es) de la misma persona", cruces.size());
        StringBuilder texto = new StringBuilder("Esta persona ya tiene novedades en esas fechas:\n\n");
        int mostradas = 0;
        for (Novedad n : cruces) {
            if (mostradas == 5) {
                texto.append("... y ").append(cruces.size() - 5).append(" más\n");
                break;
            }
            texto.append("• ").append(novedadService.texto(n)).append("\n");
            mostradas++;
        }
        texto.append("\n¿Guardar igual?");
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, texto.toString(), ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText("Las fechas se cruzan");
        confirmar.setResizable(true);
        Optional<ButtonType> r = confirmar.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
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
        Stage stage = (Stage) txtMotivo.getScene().getWindow();
        stage.close();
    }
}