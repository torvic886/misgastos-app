package com.misgastos.controller;

import com.misgastos.model.Maquina;
import com.misgastos.service.MaquinaService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import javafx.animation.PauseTransition;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.util.List;


@Slf4j
@Component
public class ModalMaquinaController {

    @FXML private Label lblTitulo;
    @FXML private TextField txtCodigo;
    @FXML private TextField txtDenominacion;
    @FXML private TextField txtNombre;
    @FXML private TextField txtSerial;
    @FXML private TextField txtJuego;
    @FXML private TextField txtContadorIn;
    @FXML private TextField txtContadorOut;
    @FXML private TextField txtContadorPrem;
    @FXML private Label lblNotaContadores;
    @FXML private Label lblDiferencia;
    @FXML private DatePicker dpFechaAlta;
    @FXML private Label lblError;
    @FXML private Button btnGuardar;

    @Autowired
    private MaquinaService maquinaService;

    private Maquina maquina;
    private Runnable onGuardarCallback;
    private final DecimalFormat plainFormat;

    public ModalMaquinaController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        plainFormat = new DecimalFormat("#,##0", symbols);
    }

    @FXML
    public void initialize() {
        log.debug("Inicializando modal de máquina");

        dpFechaAlta.setValue(LocalDate.now());

        // Código: solo dígitos (máximo 12)
        txtCodigo.textProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) return;
            String limpio = nuevo.replaceAll("[^0-9]", "");
            if (limpio.length() > 12) {
                limpio = limpio.substring(0, 12);
            }
            if (!limpio.equals(nuevo)) {
                txtCodigo.setText(limpio);
            }
        });

        // Denominación y contadores: dígitos con separador de miles
        aplicarFormatoMiles(txtDenominacion, 9);
        aplicarFormatoMiles(txtContadorIn, 15);
        aplicarFormatoMiles(txtContadorOut, 15);
        aplicarFormatoMiles(txtContadorPrem, 15);
        
        txtContadorIn.textProperty().addListener((obs, a, n) -> actualizarDiferencia());
        txtContadorOut.textProperty().addListener((obs, a, n) -> actualizarDiferencia());
        txtContadorPrem.textProperty().addListener((obs, a, n) -> actualizarDiferencia());
        actualizarDiferencia();
        configurarEnter();
    }

    /** Deja solo dígitos con separador de miles y limita la cantidad de dígitos. */
    private void aplicarFormatoMiles(TextField campo, int maxDigitos) {
        boolean[] formateando = {false};
        campo.textProperty().addListener((obs, anterior, nuevo) -> {
            if (formateando[0] || nuevo == null) return;

            String limpio = nuevo.replaceAll("[^0-9]", "");
            if (limpio.length() > maxDigitos) {
                limpio = limpio.substring(0, maxDigitos);
            }
            String formateado = limpio.isEmpty() ? "" : plainFormat.format(Long.parseLong(limpio));
            if (formateado.equals(nuevo)) return;

            // Dígitos que quedan a la izquierda del cursor, para recolocarlo después
            int caret = Math.min(campo.getCaretPosition(), nuevo.length());
            int digitosAntes = nuevo.substring(0, caret).replaceAll("[^0-9]", "").length();
            final String texto = formateado;

            Platform.runLater(() -> {
                formateando[0] = true;
                try {
                    campo.setText(texto);
                    int pos = 0;
                    int contados = 0;
                    while (pos < texto.length() && contados < digitosAntes) {
                        if (Character.isDigit(texto.charAt(pos))) contados++;
                        pos++;
                    }
                    campo.positionCaret(pos);
                } finally {
                    formateando[0] = false;
                }
            });
        });
    }

    private Long leerEntero(TextField campo) {
        String limpio = campo.getText() == null ? "" : campo.getText().replaceAll("[^0-9]", "");
        return limpio.isEmpty() ? null : Long.parseLong(limpio);
    }

    /** Diferencia (In - Out - Prem) en vivo; si es negativa se muestra con signo menos y en rojo. */
    private void actualizarDiferencia() {
        Long in = leerEntero(txtContadorIn);
        Long out = leerEntero(txtContadorOut);
        Long prem = leerEntero(txtContadorPrem);
        long diferencia = (in == null ? 0 : in) - (out == null ? 0 : out) - (prem == null ? 0 : prem);
        lblDiferencia.setText(plainFormat.format(diferencia));
        lblDiferencia.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;"
                + (diferencia < 0 ? " -fx-text-fill: #ef4444;" : ""));
    }
    
    /** Enter pasa al siguiente campo; en el último, presiona Guardar. */
    private void configurarEnter() {
        List<Control> orden = List.of(txtCodigo, txtDenominacion, txtNombre, txtSerial,
                txtJuego, txtContadorIn, txtContadorOut, txtContadorPrem, dpFechaAlta);

        for (int i = 0; i < orden.size(); i++) {
            final int pos = i;
            if (orden.get(i) instanceof TextField tf) {
                tf.setOnAction(e -> siguienteCampo(orden, pos));
            }
        }

        dpFechaAlta.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                try {
                    var fecha = dpFechaAlta.getConverter().fromString(dpFechaAlta.getEditor().getText());
                    if (fecha != null) {
                        dpFechaAlta.setValue(fecha);
                    }
                } catch (Exception ignorada) {
                    // si el texto no es una fecha válida, la validación de Guardar avisa
                }
                e.consume();
                siguienteCampo(orden, orden.size() - 1);
            }
        });
    }

    private void siguienteCampo(List<Control> orden, int actual) {
        for (int j = actual + 1; j < orden.size(); j++) {
            if (!orden.get(j).isDisabled()) {
                orden.get(j).requestFocus();
                return;
            }
        }
        presionarGuardar();
    }

    /** Hunde el botón Guardar un instante y lo acciona. */
    private void presionarGuardar() {
        btnGuardar.arm();
        PauseTransition pausa = new PauseTransition(Duration.millis(150));
        pausa.setOnFinished(e -> {
            btnGuardar.disarm();
            btnGuardar.fire();
        });
        pausa.play();
    } 

    public void setMaquina(Maquina maquina) {
        this.maquina = maquina;

        if (maquina != null) {
            lblTitulo.setText("Editar máquina");
            txtCodigo.setText(String.valueOf(maquina.getCodigo()));
            txtDenominacion.setText(plainFormat.format(maquina.getDenominacion().longValue()));
            txtNombre.setText(maquina.getNombre() == null ? "" : maquina.getNombre());
            txtSerial.setText(maquina.getSerial() == null ? "" : maquina.getSerial());
            txtJuego.setText(maquina.getJuego() == null ? "" : maquina.getJuego());
            txtContadorIn.setText(maquina.getContadorIn() == null ? "" : plainFormat.format(maquina.getContadorIn()));
            txtContadorOut.setText(maquina.getContadorOut() == null ? "" : plainFormat.format(maquina.getContadorOut()));
            txtContadorPrem.setText(maquina.getContadorPrem() == null ? "" : plainFormat.format(maquina.getContadorPrem()));
            dpFechaAlta.setValue(maquina.getFechaAlta());
            // La fecha de alta no se edita
            dpFechaAlta.setDisable(true);
        } else {
            lblTitulo.setText("Ingresar máquina");
        }
    }

    public void setOnGuardarCallback(Runnable callback) {
        this.onGuardarCallback = callback;
    }
    
    /** Con lecturas registradas los contadores iniciales no se editan aquí. */
    public void setContadoresBloqueados(boolean bloqueados) {
        txtContadorIn.setDisable(bloqueados);
        txtContadorOut.setDisable(bloqueados);
        txtContadorPrem.setDisable(bloqueados);
        lblNotaContadores.setVisible(bloqueados);
        lblNotaContadores.setManaged(bloqueados);
    }

    @FXML
    private void handleGuardar() {
        if (!validarCampos()) {
            return;
        }

        try {
            Long codigo = Long.parseLong(txtCodigo.getText().trim());
            BigDecimal denominacion = new BigDecimal(txtDenominacion.getText().replaceAll("[^0-9]", ""));
            Long contadorIn = leerEntero(txtContadorIn);
            Long contadorOut = leerEntero(txtContadorOut);
            Long contadorPrem = leerEntero(txtContadorPrem);

            if (maquina == null) {
                maquinaService.crear(codigo, denominacion, txtNombre.getText(), txtSerial.getText(),
                        txtJuego.getText(), contadorIn, contadorOut, contadorPrem, dpFechaAlta.getValue());
            } else {
                maquinaService.actualizar(maquina.getId(), codigo, denominacion, txtNombre.getText(),
                        txtSerial.getText(), txtJuego.getText(), contadorIn, contadorOut, contadorPrem);
            }

            log.info("Máquina guardada correctamente");

            if (onGuardarCallback != null) {
                onGuardarCallback.run();
            }

            cerrarModal();

        } catch (Exception e) {
            mostrarError(e.getMessage());
            log.error("No se pudo guardar la máquina", e);
        }
    }

    @FXML
    private void handleCancelar() {
        cerrarModal();
    }

    private boolean validarCampos() {
        ocultarError();

        if (txtCodigo.getText() == null || txtCodigo.getText().trim().isEmpty()) {
            mostrarError("Debe ingresar el código de la máquina");
            txtCodigo.requestFocus();
            return false;
        }

        if (Long.parseLong(txtCodigo.getText().trim()) <= 0) {
            mostrarError("El código debe ser mayor a cero");
            txtCodigo.requestFocus();
            return false;
        }

        Long den = leerEntero(txtDenominacion);
        if (den == null || den <= 0) {
            mostrarError("Debe ingresar una denominación mayor a cero");
            txtDenominacion.requestFocus();
            return false;
        }

        if (txtNombre.getText() == null || txtNombre.getText().isBlank()) {
            mostrarError("Debe ingresar el nombre");
            txtNombre.requestFocus();
            return false;
        }

        if (txtSerial.getText() == null || txtSerial.getText().isBlank()) {
            mostrarError("Debe ingresar el serial");
            txtSerial.requestFocus();
            return false;
        }

        if (txtJuego.getText() == null || txtJuego.getText().isBlank()) {
            mostrarError("Debe ingresar el juego");
            txtJuego.requestFocus();
            return false;
        }

        if (!txtContadorIn.isDisabled()) {
            if (leerEntero(txtContadorIn) == null) {
                mostrarError("Debe ingresar el Contador In");
                txtContadorIn.requestFocus();
                return false;
            }
            if (leerEntero(txtContadorOut) == null) {
                mostrarError("Debe ingresar el Contador Out");
                txtContadorOut.requestFocus();
                return false;
            }
            if (leerEntero(txtContadorPrem) == null) {
                mostrarError("Debe ingresar el Contador Prem (Jackpot)");
                txtContadorPrem.requestFocus();
                return false;
            }
        }

        if (dpFechaAlta.getValue() == null) {
            mostrarError("Debe seleccionar la fecha de alta");
            dpFechaAlta.requestFocus();
            return false;
        }

        return true;
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

    private void cerrarModal() {
        Stage stage = (Stage) txtCodigo.getScene().getWindow();
        stage.close();
    }
}