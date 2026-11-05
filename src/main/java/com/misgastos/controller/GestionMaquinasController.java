package com.misgastos.controller;

import com.misgastos.model.Maquina;
import com.misgastos.service.MaquinaService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class GestionMaquinasController {

    @FXML private TextField txtBuscar;
    @FXML private Tab tabActivas;
    @FXML private Tab tabBajas;

    @FXML private TableView<Maquina> tablaActivas;
    @FXML private TableColumn<Maquina, String> colActCodigo;
    @FXML private TableColumn<Maquina, String> colActDenominacion;
    @FXML private TableColumn<Maquina, String> colActFechaAlta;
    
    @FXML private TableView<Maquina> tablaBajas;
    @FXML private TableColumn<Maquina, String> colBajaCodigo;
    @FXML private TableColumn<Maquina, String> colBajaDenominacion;
    @FXML private TableColumn<Maquina, String> colBajaFecha;
    @FXML private TableColumn<Maquina, String> colBajaMotivo;
    
    @FXML private TableColumn<Maquina, String> colActNombre;
    @FXML private TableColumn<Maquina, String> colActSerial;
    @FXML private TableColumn<Maquina, String> colActJuego;
    @FXML private TableColumn<Maquina, String> colBajaNombre;
    @FXML private TableColumn<Maquina, String> colBajaJuego;
    
    @Autowired
    private MaquinaService maquinaService;

    @Autowired
    private ApplicationContext springContext;

    private final ObservableList<Maquina> activas = FXCollections.observableArrayList();
    private final ObservableList<Maquina> bajas = FXCollections.observableArrayList();
    private FilteredList<Maquina> activasFiltradas;
    private FilteredList<Maquina> bajasFiltradas;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NumberFormat currencyFormat;

    public GestionMaquinasController() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        this.currencyFormat = new DecimalFormat("$ #,##0", symbols);
    }

    @FXML
    public void initialize() {
        log.debug("Inicializando gestión de máquinas");

        activasFiltradas = new FilteredList<>(activas, m -> true);
        bajasFiltradas = new FilteredList<>(bajas, m -> true);
        tablaActivas.setItems(activasFiltradas);
        tablaBajas.setItems(bajasFiltradas);

        configurarTablaActivas();
        configurarTablaBajas();
        configurarMenus();

        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> filtrar(nuevo));

        cargarDatos();
    }

    // ==================== TABLAS ====================

    private void configurarTablaActivas() {
        colActCodigo.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getCodigo())));
        colActNombre.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getNombre())));
        colActSerial.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getSerial())));
        colActJuego.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getJuego())));
        colActDenominacion.setCellValueFactory(c ->
                new SimpleStringProperty(formatearMoneda(c.getValue().getDenominacion())));
        colActFechaAlta.setCellValueFactory(c ->
                new SimpleStringProperty(formatearFecha(c.getValue().getFechaAlta())));

    }

    private void configurarTablaBajas() {
        colBajaCodigo.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getCodigo())));
        colBajaNombre.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getNombre())));
        colBajaJuego.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().getJuego())));
        colBajaDenominacion.setCellValueFactory(c ->
                new SimpleStringProperty(formatearMoneda(c.getValue().getDenominacion())));
        colBajaFecha.setCellValueFactory(c ->
                new SimpleStringProperty(formatearFecha(c.getValue().getFechaBaja())));
        colBajaMotivo.setCellValueFactory(c -> {
            String motivo = c.getValue().getMotivoBaja();
            return new SimpleStringProperty(motivo == null ? "" : motivo);
        });

    }

    // ==================== DATOS Y FILTRO ====================

    private void cargarDatos() {
        try {
            activas.setAll(maquinaService.listarActivas());
            bajas.setAll(maquinaService.listarBajas());

            tabActivas.setText("Activas (" + activas.size() + ")");
            tabBajas.setText("Bajas (" + bajas.size() + ")");

            log.debug("Máquinas cargadas: {} activas, {} de baja", activas.size(), bajas.size());
        } catch (Exception e) {
            mostrarError("Error al cargar datos", e.getMessage());
            log.error("No se pudieron cargar las máquinas", e);
        }
    }

    private void filtrar(String texto) {
        String t = texto == null ? "" : texto.trim().toLowerCase();

        activasFiltradas.setPredicate(m -> t.isEmpty() || coincide(m, t));

        bajasFiltradas.setPredicate(m -> {
            if (t.isEmpty() || coincide(m, t)) {
                return true;
            }
            return m.getMotivoBaja() != null && m.getMotivoBaja().toLowerCase().contains(t);
        });
    }

    private boolean coincide(Maquina m, String t) {
        return String.valueOf(m.getCodigo()).contains(t)
                || texto(m.getNombre()).toLowerCase().contains(t)
                || texto(m.getSerial()).toLowerCase().contains(t)
                || texto(m.getJuego()).toLowerCase().contains(t);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    // ==================== ACCIONES ====================

    @FXML
    private void handleNueva() {
        abrirModal(null);
    }

    @FXML
    private void handleRefresh() {
        txtBuscar.clear();
        cargarDatos();
    }

    private void handleDarDeBaja(Maquina maquina) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Dar de baja la máquina");
        dialogo.setHeaderText("Máquina " + maquina.getCodigo());

        DatePicker dpFecha = new DatePicker(LocalDate.now());
        dpFecha.setMaxWidth(Double.MAX_VALUE);
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo de la baja");
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #ef4444;");

        VBox contenido = new VBox(8,
                new Label("Fecha de baja:"), dpFecha,
                new Label("Motivo:"), txtMotivo,
                lblError);
        contenido.setPadding(new Insets(10));

        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());

        Button ok = (Button) dialogo.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            if (dpFecha.getValue() == null) {
                lblError.setText("Seleccione la fecha de baja");
                ev.consume();
            } else if (txtMotivo.getText() == null || txtMotivo.getText().isBlank()) {
                lblError.setText("Escriba el motivo de la baja");
                ev.consume();
            }
        });

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            try {
                maquinaService.darDeBaja(maquina.getId(), dpFecha.getValue(), txtMotivo.getText());
                cargarDatos();
            } catch (Exception e) {
                mostrarError("Error al dar de baja", e.getMessage());
                log.error("No se pudo dar de baja la máquina {}", maquina.getId(), e);
            }
        }
    }

    private void handleReactivar(Maquina maquina) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar reactivación");
        confirmacion.setHeaderText("¿Reactivar la máquina " + maquina.getCodigo() + "?");
        confirmacion.setContentText("Volverá a aparecer en el ingreso de contadores. "
                + "Su historial se conserva.");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            try {
                maquinaService.reactivar(maquina.getId());
                cargarDatos();
            } catch (Exception e) {
                mostrarError("Error al reactivar", e.getMessage());
                log.error("No se pudo reactivar la máquina {}", maquina.getId(), e);
            }
        }
    }
    
    private void handleEliminar(Maquina maquina) {
        long lecturas = maquinaService.contarLecturas(maquina.getId());

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar definitivamente");
        confirmacion.setHeaderText("¿Eliminar la máquina " + maquina.getCodigo() + " para siempre?");
        confirmacion.setContentText(lecturas > 0
                ? "También se borrarán sus " + lecturas + " lectura(s) y dejará de salir en los informes. "
                  + "Esto no se puede deshacer."
                : "Esto no se puede deshacer.");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            try {
                maquinaService.eliminarDefinitivamente(maquina.getId());
                cargarDatos();
            } catch (Exception e) {
                mostrarError("Error al eliminar", e.getMessage());
                log.error("No se pudo eliminar la máquina {}", maquina.getId(), e);
            }
        }
    }

    private void abrirModal(Maquina maquina) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-maquina.fxml"));
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();

            ModalMaquinaController controller = loader.getController();
            controller.setMaquina(maquina);
            controller.setContadoresBloqueados(false);
            controller.setOnGuardarCallback(this::cargarDatos);

            Stage stage = new Stage();
            stage.setTitle(maquina == null ? "Ingresar máquina" : "Editar máquina");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);

            String css = getClass().getResource("/css/styles.css").toExternalForm();
            stage.getScene().getStylesheets().add(css);

            stage.showAndWait();

        } catch (Exception e) {
            mostrarError("Error al abrir la ventana", e.getMessage());
            log.error("No se pudo abrir el modal de máquina", e);
        }
    }

    // ==================== UTILIDADES ====================

    private String formatearMoneda(BigDecimal valor) {
        if (valor == null) return "$ 0";
        return currencyFormat.format(valor.longValue());
    }

    private String formatearFecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(dateFormatter);
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
    
    /** Clic derecho sobre una fila: sustituye a la columna Acciones. */
    private void configurarMenus() {
        tablaActivas.setRowFactory(tv -> {
            TableRow<Maquina> fila = new TableRow<>();
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null) abrirModal(fila.getItem());
            });
            MenuItem baja = new MenuItem("Dar de baja");
            baja.setOnAction(e -> {
                if (fila.getItem() != null) handleDarDeBaja(fila.getItem());
            });
            ContextMenu menu = new ContextMenu(editar, baja);
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return fila;
        });

        tablaBajas.setRowFactory(tv -> {
            TableRow<Maquina> fila = new TableRow<>();
            MenuItem reactivar = new MenuItem("Reactivar");
            reactivar.setOnAction(e -> {
                if (fila.getItem() != null) handleReactivar(fila.getItem());
            });
            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> {
                if (fila.getItem() != null) handleEliminar(fila.getItem());
            });
            ContextMenu menu = new ContextMenu(reactivar, eliminar);
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return fila;
        });
    }
}