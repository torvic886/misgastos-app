package com.misgastos.controller;

import com.misgastos.model.HorarioEspecial;
import com.misgastos.model.TipoDiaHorario;
import com.misgastos.model.Turno;
import com.misgastos.service.HorarioEspecialService;
import com.misgastos.service.TurnoService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Modal para crear o editar un turno. Pide solo el tipo, el nombre, las horas y las personas;
 * la etiqueta y el orden se calculan solos. En turnos completos permite además un horario
 * especial para sábado y para domingo o festivo.
 */
@Slf4j
@Component
public class ModalTurnoController {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    /** Etiqueta del medio turno (½). Así se reconoce si un turno guardado es medio turno. */
    private static final String ETIQUETA_MEDIO = "\u00BD";
    private static final int CUPOS_MIN = 1;
    private static final int CUPOS_MAX = 5;

    @FXML private Label lblTitulo;
    @FXML private RadioButton rbCompleto;
    @FXML private RadioButton rbMedio;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<String> cmbHoraInicio;
    @FXML private ComboBox<String> cmbHoraFin;
    @FXML private Label lblCupos;
    @FXML private Label lblResumen;
    @FXML private VBox boxEspeciales;
    @FXML private CheckBox chkSabado;
    @FXML private ComboBox<String> cmbSabadoInicio;
    @FXML private ComboBox<String> cmbSabadoFin;
    @FXML private Label lblResumenSabado;
    @FXML private CheckBox chkDomingo;
    @FXML private ComboBox<String> cmbDomingoInicio;
    @FXML private ComboBox<String> cmbDomingoFin;
    @FXML private Label lblResumenDomingo;
    @FXML private CheckBox chkActivo;
    @FXML private Label lblError;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private HorarioEspecialService horarioEspecialService;

    private Turno turno;
    private Runnable onGuardarCallback;
    private int cupos = 1;
    private boolean cargando = false;
    private String nombreAutomatico = "";

    @FXML
    public void initialize() {
        log.debug("Inicializando modal de turno");
        List<String> horas = new ArrayList<>();
        for (int minutos = 0; minutos < 24 * 60; minutos += 30) {
            horas.add(String.format("%02d:%02d", minutos / 60, minutos % 60));
        }
        cmbHoraInicio.setItems(FXCollections.observableArrayList(horas));
        cmbHoraFin.setItems(FXCollections.observableArrayList(horas));
        for (ComboBox<String> combo : List.of(cmbSabadoInicio, cmbSabadoFin, cmbDomingoInicio, cmbDomingoFin)) {
            combo.setItems(FXCollections.observableArrayList(horas));
        }

        cmbHoraInicio.getEditor().textProperty().addListener((o, a, b) -> actualizarResumen());
        cmbHoraFin.getEditor().textProperty().addListener((o, a, b) -> actualizarResumen());

        configurarEspecial(chkSabado, cmbSabadoInicio, cmbSabadoFin, lblResumenSabado, TipoDiaHorario.SABADO);
        configurarEspecial(chkDomingo, cmbDomingoInicio, cmbDomingoFin, lblResumenDomingo,
                TipoDiaHorario.DOMINGO_FESTIVO);

        // Al cambiar el tipo en un turno nuevo, se rellenan nombre, horas y personas.
        // El medio turno no tiene horarios especiales: queda igual todos los días
        rbMedio.selectedProperty().addListener((o, a, medio) -> {
            mostrarEspeciales(!medio);
            if (!cargando && turno == null) {
                aplicarSugerencia(medio);
            }
        });
    }

    public void setTurno(Turno turno) {
        this.turno = turno;
        cargando = true;
        try {
            if (turno != null) {
                lblTitulo.setText("Editar turno");
                boolean medio = ETIQUETA_MEDIO.equals(turno.getEtiqueta());
                rbMedio.setSelected(medio);
                rbCompleto.setSelected(!medio);
                // El tipo no se cambia al editar: la etiqueta se conserva
                rbCompleto.setDisable(true);
                rbMedio.setDisable(true);
                txtNombre.setText(turno.getNombre());
                cmbHoraInicio.setValue(turno.getHoraInicio().format(FORMATO_HORA));
                cmbHoraFin.setValue(turno.getHoraFin().format(FORMATO_HORA));
                cupos = turno.getCupos();
                chkActivo.setSelected(turno.isActivo());
                chkActivo.setVisible(true);
                chkActivo.setManaged(true);
                mostrarEspeciales(!medio);
                cargarEspeciales(turno.getId());
            } else {
                lblTitulo.setText("Nuevo turno");
                rbCompleto.setSelected(true);
                mostrarEspeciales(true);
                aplicarSugerencia(false);
            }
        } finally {
            cargando = false;
        }
        mostrarCupos();
        actualizarResumen();
        log.debug("Modal de turno preparado: {}", turno == null ? "nuevo" : "edición");
    }

    public void setOnGuardarCallback(Runnable callback) {
        this.onGuardarCallback = callback;
    }

    /** Rellena nombre, horas y personas según el tipo elegido (solo en turno nuevo). */
    private void aplicarSugerencia(boolean medio) {
        String nombreNuevo;
        if (medio) {
            nombreNuevo = "Medio turno";
            cmbHoraInicio.setValue("15:00");
            cmbHoraFin.setValue("22:00");
            cupos = 1;
        } else {
            nombreNuevo = "Turno " + siguienteEtiquetaNumerica();
            long completos = turnoService.listarTodos().stream()
                    .filter(t -> !ETIQUETA_MEDIO.equals(t.getEtiqueta()))
                    .count();
            if (completos == 0) {
                cmbHoraInicio.setValue("08:30");
                cmbHoraFin.setValue("16:00");
            } else {
                cmbHoraInicio.setValue("16:00");
                cmbHoraFin.setValue("23:30");
            }
            cupos = 2;
        }
        // El nombre solo se reemplaza si está vacío o si todavía es el que puso el sistema
        String actual = txtNombre.getText() == null ? "" : txtNombre.getText().trim();
        if (actual.isEmpty() || actual.equals(nombreAutomatico)) {
            txtNombre.setText(nombreNuevo);
        }
        nombreAutomatico = nombreNuevo;
        mostrarCupos();
        actualizarResumen();
    }

    // ====================== HORARIOS ESPECIALES ======================

    /** Prepara una fila (casilla, hora de inicio, hora de fin y resumen) de horario especial. */
    private void configurarEspecial(CheckBox chk, ComboBox<String> inicio, ComboBox<String> fin,
                                    Label resumen, TipoDiaHorario tipo) {
        inicio.disableProperty().bind(chk.selectedProperty().not());
        fin.disableProperty().bind(chk.selectedProperty().not());

        Runnable refrescar = () -> resumen.setText(chk.isSelected()
                ? textoHorario(inicio.getEditor().getText(), fin.getEditor().getText())
                : "");
        inicio.getEditor().textProperty().addListener((o, a, b) -> refrescar.run());
        fin.getEditor().textProperty().addListener((o, a, b) -> refrescar.run());
        chk.selectedProperty().addListener((o, a, marcada) -> {
            // Al marcar una fila vacía se sugiere un horario (editable), salvo al cargar un turno
            if (marcada && !cargando && estaVacio(inicio) && estaVacio(fin)) {
                sugerirEspecial(tipo, inicio, fin);
            }
            refrescar.run();
        });
    }

    private boolean estaVacio(ComboBox<String> combo) {
        String texto = combo.getEditor().getText();
        return texto == null || texto.trim().isEmpty();
    }

    /** Sugerencia según sea un turno de la mañana o de la tarde (se puede cambiar). */
    private void sugerirEspecial(TipoDiaHorario tipo, ComboBox<String> inicio, ComboBox<String> fin) {
        boolean manana = turnoDeManana();
        if (tipo == TipoDiaHorario.SABADO) {
            inicio.setValue(manana ? "08:30" : "17:00");
            fin.setValue(manana ? "17:00" : "00:30");
        } else {
            inicio.setValue(manana ? "08:30" : "15:30");
            fin.setValue(manana ? "15:30" : "22:30");
        }
    }

    /** Un turno es "de la mañana" si su horario base empieza antes del mediodía. */
    private boolean turnoDeManana() {
        try {
            LocalTime inicio = LocalTime.parse(cmbHoraInicio.getEditor().getText().trim(), FORMATO_HORA);
            return inicio.getHour() < 12;
        } catch (Exception e) {
            return true;
        }
    }

    private void mostrarEspeciales(boolean visible) {
        boxEspeciales.setVisible(visible);
        boxEspeciales.setManaged(visible);
    }

    private void cargarEspeciales(Long turnoId) {
        Optional<HorarioEspecial> sabado = horarioEspecialService.buscar(turnoId, TipoDiaHorario.SABADO);
        chkSabado.setSelected(sabado.isPresent());
        sabado.ifPresent(h -> {
            cmbSabadoInicio.setValue(h.getHoraInicio().format(FORMATO_HORA));
            cmbSabadoFin.setValue(h.getHoraFin().format(FORMATO_HORA));
        });

        Optional<HorarioEspecial> domingo = horarioEspecialService.buscar(turnoId, TipoDiaHorario.DOMINGO_FESTIVO);
        chkDomingo.setSelected(domingo.isPresent());
        domingo.ifPresent(h -> {
            cmbDomingoInicio.setValue(h.getHoraInicio().format(FORMATO_HORA));
            cmbDomingoFin.setValue(h.getHoraFin().format(FORMATO_HORA));
        });
    }

    /** Lee y valida las dos horas de una fila especial antes de guardar nada. */
    private LocalTime[] leerEspecial(ComboBox<String> inicio, ComboBox<String> fin, String cual) {
        LocalTime desde = leerHora(inicio.getEditor().getText(), "inicio " + cual);
        LocalTime hasta = leerHora(fin.getEditor().getText(), "fin " + cual);
        if (desde.equals(hasta)) {
            throw new RuntimeException("La hora de inicio y la de fin " + cual + " no pueden ser iguales");
        }
        return new LocalTime[] {desde, hasta};
    }

    private void guardarEspecial(Long turnoId, TipoDiaHorario tipo, LocalTime[] horas) {
        if (horas == null) {
            horarioEspecialService.quitar(turnoId, tipo);
        } else {
            horarioEspecialService.guardar(turnoId, tipo, horas[0], horas[1]);
        }
    }

    /** "17:00 – 00:30 · 7 h 30 min (termina al día siguiente)" para una fila especial. */
    private String textoHorario(String textoInicio, String textoFin) {
        try {
            LocalTime inicio = LocalTime.parse(textoInicio.trim(), FORMATO_HORA);
            LocalTime fin = LocalTime.parse(textoFin.trim(), FORMATO_HORA);
            long minutos = Duration.between(inicio, fin).toMinutes();
            if (minutos == 0) {
                return "La hora de inicio y la de fin no pueden ser iguales";
            }
            String sufijo = "";
            if (minutos < 0) {
                minutos += 24 * 60;
                sufijo = " (termina al día siguiente)";
            }
            String duracion = (minutos / 60) + " h" + (minutos % 60 == 0 ? "" : " " + (minutos % 60) + " min");
            return inicio.format(FORMATO_HORA) + " – " + fin.format(FORMATO_HORA) + " · " + duracion + sufijo;
        } catch (Exception e) {
            return "Elige o escribe las horas en formato HH:mm";
        }
    }

    // ====================== CUPOS Y GUARDAR ======================

    @FXML
    private void handleMenosCupos() {
        if (cupos > CUPOS_MIN) {
            cupos--;
            mostrarCupos();
            actualizarResumen();
        }
    }

    @FXML
    private void handleMasCupos() {
        if (cupos < CUPOS_MAX) {
            cupos++;
            mostrarCupos();
            actualizarResumen();
        }
    }

    @FXML
    private void handleGuardar() {
        ocultarError();
        try {
            LocalTime inicio = leerHora(cmbHoraInicio.getEditor().getText(), "inicio");
            LocalTime fin = leerHora(cmbHoraFin.getEditor().getText(), "fin");
            boolean medio = rbMedio.isSelected();

            // Los horarios especiales se validan antes de guardar el turno, para no dejarlo a medias
            LocalTime[] sabado = (medio || !chkSabado.isSelected())
                    ? null : leerEspecial(cmbSabadoInicio, cmbSabadoFin, "del sábado");
            LocalTime[] domingo = (medio || !chkDomingo.isSelected())
                    ? null : leerEspecial(cmbDomingoInicio, cmbDomingoFin, "de domingo o festivo");

            Long turnoId;
            if (turno == null) {
                String etiqueta = medio
                        ? ETIQUETA_MEDIO
                        : String.valueOf(siguienteEtiquetaNumerica());
                Turno creado = turnoService.crear(txtNombre.getText(), etiqueta, inicio, fin, cupos, siguienteOrden());
                // Si algo falla después, un nuevo intento edita este turno en vez de crear otro igual
                turno = creado;
                chkActivo.setSelected(true);
                turnoId = creado.getId();
            } else {
                boolean estabaActivo = turno.isActivo();
                // Etiqueta y orden se conservan tal como están
                turnoService.actualizar(turno.getId(), txtNombre.getText(), turno.getEtiqueta(),
                        inicio, fin, cupos, turno.getOrden());
                if (chkActivo.isSelected() != estabaActivo) {
                    if (chkActivo.isSelected()) {
                        turnoService.reactivar(turno.getId());
                    } else {
                        turnoService.desactivar(turno.getId());
                    }
                }
                turnoId = turno.getId();
            }

            guardarEspecial(turnoId, TipoDiaHorario.SABADO, sabado);
            guardarEspecial(turnoId, TipoDiaHorario.DOMINGO_FESTIVO, domingo);

            log.info("Turno guardado correctamente");
            if (onGuardarCallback != null) {
                onGuardarCallback.run();
            }
            cerrar();
        } catch (Exception e) {
            mostrarError(e.getMessage());
            log.error("No se pudo guardar el turno", e);
        }
    }

    @FXML
    private void handleCancelar() {
        cerrar();
    }

    /** Menor número (1, 2, 3...) que ningún turno usa todavía como etiqueta. */
    private int siguienteEtiquetaNumerica() {
        Set<String> usadas = new HashSet<>();
        for (Turno t : turnoService.listarTodos()) {
            usadas.add(t.getEtiqueta());
        }
        int n = 1;
        while (usadas.contains(String.valueOf(n))) {
            n++;
        }
        return n;
    }

    /** Orden al final de los existentes. */
    private int siguienteOrden() {
        int max = 0;
        for (Turno t : turnoService.listarTodos()) {
            max = Math.max(max, t.getOrden());
        }
        return max + 1;
    }

    /** Línea que se actualiza sola: "08:30 – 16:00 · 7 h 30 min · 2 personas". */
    private void actualizarResumen() {
        if (lblResumen == null) {
            return;
        }
        try {
            LocalTime inicio = LocalTime.parse(cmbHoraInicio.getEditor().getText().trim(), FORMATO_HORA);
            LocalTime fin = LocalTime.parse(cmbHoraFin.getEditor().getText().trim(), FORMATO_HORA);
            long minutos = Duration.between(inicio, fin).toMinutes();
            if (minutos == 0) {
                lblResumen.setText("La hora de inicio y la de fin no pueden ser iguales");
                return;
            }
            String sufijo = "";
            if (minutos < 0) {
                minutos += 24 * 60;
                sufijo = " (termina al día siguiente)";
            }
            String duracion = (minutos / 60) + " h" + (minutos % 60 == 0 ? "" : " " + (minutos % 60) + " min");
            String personas = cupos == 1 ? "1 persona" : cupos + " personas";
            lblResumen.setText(inicio.format(FORMATO_HORA) + " – " + fin.format(FORMATO_HORA)
                    + " · " + duracion + " · " + personas + sufijo);
        } catch (Exception e) {
            lblResumen.setText("Elige o escribe las horas en formato HH:mm");
        }
    }

    private void mostrarCupos() {
        lblCupos.setText(String.valueOf(cupos));
    }

    private LocalTime leerHora(String texto, String cual) {
        String limpio = texto == null ? "" : texto.trim();
        try {
            return LocalTime.parse(limpio, FORMATO_HORA);
        } catch (DateTimeParseException e) {
            log.warn("Hora de {} con formato inválido", cual);
            throw new RuntimeException("La hora de " + cual + " debe tener el formato HH:mm, por ejemplo 08:30");
        }
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