package com.misgastos.controller;

import com.misgastos.model.Lectura;
import com.misgastos.model.Maquina;
import com.misgastos.service.LecturaService;
import com.misgastos.service.MaquinaService;
import com.misgastos.util.CopiarTabla;
import com.misgastos.util.SelectorFecha;
import com.misgastos.service.MaquinaImportService;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.scene.control.TabPane;
import javafx.geometry.Pos;
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
import java.util.List;
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
    @FXML private TableColumn<Maquina, String> colBajaIn;
    @FXML private TableColumn<Maquina, String> colBajaOut;
    @FXML private TableColumn<Maquina, String> colBajaPrem;
    @FXML private TableColumn<Maquina, String> colActIn;
    @FXML private TableColumn<Maquina, String> colActOut;
    @FXML private TableColumn<Maquina, String> colActPrem;
    @FXML private TabPane tabPane;
    
    @Autowired
    private MaquinaService maquinaService;
    
    @Autowired
    private LecturaService lecturaService;

    @Autowired
    private MaquinaImportService importService;
    
    private final java.util.Map<Long, Lectura> ultimasLecturas = new java.util.HashMap<>();

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

        configurarListasFiltradas();
        configurarTablaActivas();
        tablaActivas.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        configurarTablaBajas();
        configurarMenus();
        configurarBusqueda();

        cargarDatos();
    }
    
    private void configurarListasFiltradas() {
        activasFiltradas = new FilteredList<>(activas, m -> true);
        bajasFiltradas = new FilteredList<>(bajas, m -> true);
        tablaActivas.setItems(activasFiltradas);
        tablaBajas.setItems(bajasFiltradas);
    }

    private void configurarBusqueda() {
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> filtrar(nuevo));
    }

    // ==================== TABLAS ====================

    private void configurarTablaActivas() {
        colActIn.setCellValueFactory(c -> new SimpleStringProperty(contadorBaja(c.getValue(), 0)));
        colActOut.setCellValueFactory(c -> new SimpleStringProperty(contadorBaja(c.getValue(), 1)));
        colActPrem.setCellValueFactory(c -> new SimpleStringProperty(contadorBaja(c.getValue(), 2)));
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

        colBajaIn.setCellValueFactory(c -> new SimpleStringProperty(contadorBaja(c.getValue(), 0)));
        colBajaOut.setCellValueFactory(c -> new SimpleStringProperty(contadorBaja(c.getValue(), 1)));
        colBajaPrem.setCellValueFactory(c -> new SimpleStringProperty(contadorBaja(c.getValue(), 2)));
    }

    // ==================== DATOS Y FILTRO ====================

    private void cargarDatos() {
        try {
            activas.setAll(maquinaService.listarActivas());
            bajas.setAll(maquinaService.listarBajas());

            ultimasLecturas.clear();
            List<Maquina> todas = new java.util.ArrayList<>(activas);
            todas.addAll(bajas);
            for (Maquina m : todas) {
                Lectura u = lecturaService.ultimaLectura(m.getId());
                if (u != null) {
                    ultimasLecturas.put(m.getId(), u);
                }
            }
            
            tabActivas.setText("Activas (" + activas.size() + ")");
            tabBajas.setText("Bajas (" + bajas.size() + ")");

            log.debug("Máquinas cargadas: {} activas, {} de baja", activas.size(), bajas.size());
        } catch (Exception e) {
            mostrarError("Error al cargar datos", e.getMessage());
            log.error("No se pudieron cargar las máquinas", e);
        }
    }
    
    /** Último contador de la máquina (0 = In, 1 = Out, 2 = Prem); vacío si no tiene lecturas. */
    private String contadorBaja(Maquina m, int cual) {
        Lectura u = ultimasLecturas.get(m.getId());
        if (u == null) {
            return "";
        }
        long valor = cual == 0 ? u.getContadorIn() : cual == 1 ? u.getContadorOut() : u.getContadorPrem();
        return currencyFormat.format(valor);
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
    
    @FXML
    private void handleImportar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir Excel de máquinas");
        selector.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
        File archivo = selector.showOpenDialog(tablaActivas.getScene().getWindow());
        if (archivo == null) {
            return;
        }

        MaquinaImportService.VistaPrevia vista;
        try {
            vista = importService.analizar(archivo);
        } catch (Exception e) {
            log.error("No se pudo leer el Excel de máquinas: {}", archivo.getName(), e);
            mostrarError("No se pudo leer el archivo",
                "Revisa que sea un Excel válido y que no esté abierto en otro programa.\n\n" + e.getMessage());
            return;
        }

        StringBuilder detalle = new StringBuilder();
        if (!vista.problemas().isEmpty()) {
            detalle.append("Filas con problema (no se importan):\n")
                   .append(String.join("\n", vista.problemas()));
        }
        if (!vista.yaExisten().isEmpty()) {
            if (detalle.length() > 0) {
                detalle.append("\n\n");
            }
            detalle.append("Códigos que ya existen (se omiten): ")
                   .append(vista.yaExisten().stream().map(String::valueOf)
                       .collect(java.util.stream.Collectors.joining(", ")));
        }

        String resumen = "Máquinas nuevas: " + vista.nuevas().size() + "\n"
            + "Ya existen (se omiten): " + vista.yaExisten().size() + "\n"
            + "Filas con problema (no se importan): " + vista.problemas().size() + "\n"
            + (vista.traeContadores()
                ? "El archivo trae contadores: se guardan como lectura inicial."
                : "El archivo no trae contadores: quedan en cero.");

        if (vista.nuevas().isEmpty()) {
            Alert nada = new Alert(Alert.AlertType.INFORMATION);
            nada.setTitle("Nada para importar");
            nada.setHeaderText(null);
            nada.setContentText(resumen + "\n\nNo hay máquinas nuevas para guardar.");
            if (detalle.length() > 0) {
                TextArea area = new TextArea(detalle.toString());
                area.setEditable(false);
                area.setPrefRowCount(10);
                nada.getDialogPane().setExpandableContent(area);
                nada.getDialogPane().setExpanded(true);
            }
            nada.showAndWait();
            return;
        }

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Importar máquinas");
        dialogo.setHeaderText("Archivo: " + archivo.getName());

        DatePicker dpFecha = new DatePicker(LocalDate.now());
        SelectorFecha.configurar(dpFecha);
        HBox filaFecha = new HBox(8, new Label("Fecha de alta:"), dpFecha);
        filaFecha.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(12, new Label(resumen), filaFecha);
        contenido.setPadding(new Insets(10));
        dialogo.getDialogPane().setContent(contenido);

        if (detalle.length() > 0) {
            TextArea area = new TextArea(detalle.toString());
            area.setEditable(false);
            area.setPrefRowCount(8);
            dialogo.getDialogPane().setExpandableContent(area);
            dialogo.getDialogPane().setExpanded(true);
        }

        ButtonType importar = new ButtonType("Importar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(importar, ButtonType.CANCEL);

        Optional<ButtonType> resp = dialogo.showAndWait();
        if (resp.isEmpty() || resp.get() != importar) {
            log.info("Importación de máquinas cancelada por el usuario");
            return;
        }
        LocalDate fecha = dpFecha.getValue();
        if (fecha == null) {
            mostrarError("Fecha de alta", "Escoge la fecha de alta y vuelve a importar.");
            return;
        }

        MaquinaImportService.Resultado resultado = importService.aplicar(vista, fecha);
        cargarDatos();

        Alert fin = new Alert(resultado.errores().isEmpty()
            ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        fin.setTitle("Importación lista");
        fin.setHeaderText(null);
        fin.setContentText("Máquinas creadas: " + resultado.creadas()
            + (resultado.errores().isEmpty() ? "" : "\nCon error: " + resultado.errores().size()));
        if (!resultado.errores().isEmpty()) {
            TextArea area = new TextArea(String.join("\n", resultado.errores()));
            area.setEditable(false);
            area.setPrefRowCount(8);
            fin.getDialogPane().setExpandableContent(area);
            fin.getDialogPane().setExpanded(true);
        }
        fin.showAndWait();
    }
    
    @FXML
    private void handleCambiarFecha() {
        List<Maquina> seleccion = new java.util.ArrayList<>(tablaActivas.getSelectionModel().getSelectedItems());

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Cambiar fecha de alta");
        dialogo.setHeaderText("Mueve la fecha de alta y su lectura inicial");

        ToggleGroup grupo = new ToggleGroup();
        RadioButton rbSeleccion = new RadioButton("Solo las seleccionadas (" + seleccion.size() + ")");
        RadioButton rbTodas = new RadioButton("Todas las activas (" + activas.size() + ")");
        rbSeleccion.setToggleGroup(grupo);
        rbTodas.setToggleGroup(grupo);
        if (seleccion.isEmpty()) {
            rbSeleccion.setDisable(true);
            rbTodas.setSelected(true);
        } else {
            rbSeleccion.setSelected(true);
        }

        DatePicker dpFecha = new DatePicker(LocalDate.now());
        SelectorFecha.configurar(dpFecha);
        HBox filaFecha = new HBox(8, new Label("Nueva fecha de alta:"), dpFecha);
        filaFecha.setAlignment(Pos.CENTER_LEFT);

        VBox contenido = new VBox(12, rbSeleccion, rbTodas, filaFecha);
        contenido.setPadding(new Insets(10));
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        LocalDate fecha = dpFecha.getValue();
        if (fecha == null) {
            mostrarError("Fecha de alta", "Elige una fecha.");
            return;
        }

        List<Maquina> destino = rbTodas.isSelected() ? new java.util.ArrayList<>(activas) : seleccion;
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se cambiará la fecha de alta de " + destino.size() + " máquina(s) a "
                + fecha.format(dateFormatter) + ".\n\nTambién se moverá su lectura inicial.\n¿Continuar?",
                ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText(null);
        if (confirmar.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            List<Long> ids = destino.stream().map(Maquina::getId).toList();
            int cambiadas = maquinaService.cambiarFechaAlta(ids, fecha);
            log.info("Fecha de alta cambiada desde la pantalla: {} máquina(s)", cambiadas);
            Alert listo = new Alert(Alert.AlertType.INFORMATION,
                    "Fecha de alta cambiada en " + cambiadas + " máquina(s).");
            listo.setHeaderText(null);
            listo.showAndWait();
            cargarDatos();
        } catch (Exception e) {
            log.error("No se pudo cambiar la fecha de alta", e);
            mostrarError("No se pudo cambiar la fecha", e.getMessage());
        }
    }

    private void handleDarDeBaja(Maquina maquina) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Dar de baja la máquina");
        dialogo.setHeaderText("Máquina " + maquina.getCodigo());

        DatePicker dpFecha = new DatePicker();
        SelectorFecha.configurar(dpFecha);
        dpFecha.setValue(LocalDate.now());
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
    
    /** Clic derecho: actúa sobre todas las filas seleccionadas (Ctrl+clic, Shift+clic o Ctrl+A). */
    private void configurarMenus() {
        tablaActivas.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tablaBajas.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        CopiarTabla.habilitar(tablaActivas);
        CopiarTabla.habilitar(tablaBajas);
        
        tablaActivas.setRowFactory(tv -> {
            TableRow<Maquina> fila = new TableRow<>();
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null) abrirModal(fila.getItem());
            });
            MenuItem baja = new MenuItem("Dar de baja");
            baja.setOnAction(e -> {
                List<Maquina> elegidas =
                        new java.util.ArrayList<>(tablaActivas.getSelectionModel().getSelectedItems());
                if (elegidas.isEmpty() && fila.getItem() != null) {
                    elegidas.add(fila.getItem());
                }
                if (elegidas.size() == 1) {
                    handleDarDeBaja(elegidas.get(0));
                } else if (elegidas.size() > 1) {
                    handleDarDeBajaVarias(elegidas);
                }
            });
            ContextMenu menu = new ContextMenu(editar, baja);
            menu.setOnShowing(e -> {
                int n = tablaActivas.getSelectionModel().getSelectedItems().size();
                editar.setDisable(n > 1);
                baja.setText(n > 1 ? "Dar de baja (" + n + " máquinas)" : "Dar de baja");
            });
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
                List<Maquina> elegidas =
                        new java.util.ArrayList<>(tablaBajas.getSelectionModel().getSelectedItems());
                if (elegidas.isEmpty() && fila.getItem() != null) {
                    elegidas.add(fila.getItem());
                }
                if (elegidas.size() == 1) {
                    handleEliminar(elegidas.get(0));
                } else if (elegidas.size() > 1) {
                    handleEliminarVarias(elegidas);
                }
            });
            ContextMenu menu = new ContextMenu(reactivar, eliminar);
            menu.setOnShowing(e -> {
                int n = tablaBajas.getSelectionModel().getSelectedItems().size();
                reactivar.setDisable(n > 1);
                eliminar.setText(n > 1 ? "Eliminar (" + n + " máquinas)" : "Eliminar");
            });
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return fila;
        });
    }

    // ==================== VARIAS A LA VEZ ====================

    private String codigos(List<Maquina> lista) {
        return lista.stream().map(m -> String.valueOf(m.getCodigo()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** Una sola fecha y un solo motivo para todas; cada máquina se da de baja por separado. */
    private void handleDarDeBajaVarias(List<Maquina> elegidas) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Dar de baja varias máquinas");
        dialogo.setHeaderText("Se darán de baja " + elegidas.size() + " máquinas");

        Label lblCodigos = new Label("Códigos: " + codigos(elegidas));
        lblCodigos.setWrapText(true);
        lblCodigos.setMaxWidth(420);

        DatePicker dpFecha = new DatePicker();
        SelectorFecha.configurar(dpFecha);
        dpFecha.setValue(LocalDate.now());
        dpFecha.setMaxWidth(Double.MAX_VALUE);
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo de la baja (el mismo para todas)");
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #ef4444;");

        VBox contenido = new VBox(8, lblCodigos,
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

        Optional<ButtonType> respuesta = dialogo.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        LocalDate fecha = dpFecha.getValue();
        String motivo = txtMotivo.getText();
        int hechas = 0;
        List<String> errores = new java.util.ArrayList<>();
        for (Maquina m : elegidas) {
            try {
                maquinaService.darDeBaja(m.getId(), fecha, motivo);
                hechas++;
            } catch (Exception e) {
                errores.add("Código " + m.getCodigo() + ": " + e.getMessage());
                log.error("No se pudo dar de baja la máquina {}", m.getId(), e);
            }
        }
        cargarDatos();
        mostrarResumen("Bajas", "dadas de baja", hechas, errores);
    }

    /** Pide confirmación mostrando los códigos y el total de lecturas que se borrarán. */
    private void handleEliminarVarias(List<Maquina> elegidas) {
        long lecturas = 0;
        for (Maquina m : elegidas) {
            lecturas += maquinaService.contarLecturas(m.getId());
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar definitivamente");
        confirmacion.setHeaderText("¿Eliminar " + elegidas.size() + " máquinas para siempre?");
        Label detalle = new Label("Códigos: " + codigos(elegidas) + "\n\n"
                + (lecturas > 0
                    ? "También se borrarán sus " + lecturas + " lectura(s) en total y dejarán de salir "
                      + "en los informes. "
                    : "")
                + "Esto no se puede deshacer.");
        detalle.setWrapText(true);
        detalle.setMaxWidth(420);
        confirmacion.getDialogPane().setContent(detalle);

        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        int hechas = 0;
        List<String> errores = new java.util.ArrayList<>();
        for (Maquina m : elegidas) {
            try {
                maquinaService.eliminarDefinitivamente(m.getId());
                hechas++;
            } catch (Exception e) {
                errores.add("Código " + m.getCodigo() + ": " + e.getMessage());
                log.error("No se pudo eliminar la máquina {}", m.getId(), e);
            }
        }
        cargarDatos();
        mostrarResumen("Eliminación", "eliminadas", hechas, errores);
    }

    private void mostrarResumen(String titulo, String accion, int hechas, List<String> errores) {
        Alert fin = new Alert(errores.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        fin.setTitle(titulo);
        fin.setHeaderText(null);
        fin.setContentText("Máquinas " + accion + ": " + hechas
                + (errores.isEmpty() ? "" : "\nCon error: " + errores.size()));
        if (!errores.isEmpty()) {
            TextArea area = new TextArea(String.join("\n", errores));
            area.setEditable(false);
            area.setPrefRowCount(8);
            fin.getDialogPane().setExpandableContent(area);
            fin.getDialogPane().setExpanded(true);
        }
        fin.showAndWait();
    }
}
