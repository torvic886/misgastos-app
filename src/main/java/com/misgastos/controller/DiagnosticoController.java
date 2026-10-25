package com.misgastos.controller;

import com.misgastos.diagnostico.ArchivoLog;
import com.misgastos.diagnostico.EntradaLog;
import com.misgastos.diagnostico.FiltroLogs;
import com.misgastos.diagnostico.LectorLogs;
import com.misgastos.diagnostico.PaginaLogs;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import java.util.List;

/**
 * Pantalla "Diagnóstico y registros": muestra los archivos de log reales (misgastos.log y
 * los archivados), con filtros, paginación y detalle. Solo muestra: toda la lectura
 * está en LectorLogs y se hace en un hilo aparte para no congelar la interfaz.
 * El acceso (administrador + clave) se controla desde el Dashboard.
 */
@Slf4j
@Component
public class DiagnosticoController {

    private static final DateTimeFormatter FECHA_TABLA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FECHA_DETALLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String TODOS_NIVELES = "Todos";
    private static final String TODOS_ARCHIVOS = "Todos (según las fechas)";
    private static final String SIN_SELECCION = "Selecciona un registro para ver sus detalles.";

    private static final ExecutorService EJECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lector-logs");
        t.setDaemon(true);
        return t;
    });

    @FXML private ComboBox<String> cmbNivel;
    @FXML private ComboBox<String> cmbArchivo;
    @FXML private TextField txtBuscar;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Button btnActualizar;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Button btnCopiar;
    @FXML private Button btnAbrirArchivo;
    @FXML private ProgressIndicator progreso;
    @FXML private TableView<EntradaLog> tabla;
    @FXML private TableColumn<EntradaLog, String> colFecha;
    @FXML private TableColumn<EntradaLog, String> colNivel;
    @FXML private TableColumn<EntradaLog, String> colClase;
    @FXML private TableColumn<EntradaLog, String> colMensaje;
    @FXML private Label lblPagina;
    @FXML private Label lblResumen;
    @FXML private Label lblCarpeta;
    @FXML private Label lblDetalleFecha;
    @FXML private Label lblDetalleNivel;
    @FXML private Label lblDetalleClase;
    @FXML private TextArea txtMensaje;
    @FXML private TextArea txtTraza;
    @FXML private Label lblNombreVista;
    @FXML private TextArea txtVista;

    @Autowired
    private LectorLogs lector;

    private final Map<String, ArchivoLog> archivoPorEtiqueta = new LinkedHashMap<>();
    private Task<PaginaLogs> tareaActual;
    private static final int LINEAS_VISTA = 200;
    private Task<List<String>> tareaVista;
    private EntradaLog seleccionada;
    private int paginaActual = 0;
    private int totalPaginas = 1;
    private int totalActual = -1;

    // ==================== INICIO ====================

    @FXML
    public void initialize() {
        cmbNivel.getItems().setAll(TODOS_NIVELES, "ERROR", "WARN", "INFO", "DEBUG");
        cmbNivel.setValue(TODOS_NIVELES);
        dpHasta.setValue(LocalDate.now());
        dpDesde.setValue(LocalDate.now().minusDays(7));
        lblCarpeta.setText(lector.carpeta().toString());

        configurarTabla();
        mostrarDetalle(null);
        recargarArchivos();
        consultar(0, true);
        cargarVistaArchivo();
    }

    private void configurarTabla() {
        tabla.setItems(FXCollections.observableArrayList());

        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha().format(FECHA_TABLA)));
        colNivel.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nivel()));
        colClase.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().clase()));
        colMensaje.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().mensaje()));

        // Nivel como etiqueta de color
        colNivel.setCellFactory(col -> new TableCell<>() {
            private final Label etiqueta = new Label();

            @Override
            protected void updateItem(String nivel, boolean vacio) {
                super.updateItem(nivel, vacio);
                if (vacio || nivel == null) {
                    setGraphic(null);
                    return;
                }
                etiqueta.setText(nivel);
                etiqueta.getStyleClass().setAll("label", "log-badge", "log-" + nivel.toLowerCase());
                setGraphic(etiqueta);
            }
        });

        // Mensaje en una línea; el texto completo sale al pasar el mouse y en el panel de detalles
        colMensaje.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                if (vacio || texto == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(texto);
                Tooltip tip = new Tooltip(texto);
                tip.setWrapText(true);
                tip.setMaxWidth(520);
                setTooltip(tip);
            }
        });

        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> mostrarDetalle(ahora));
    }

    private void recargarArchivos() {
        String elegido = cmbArchivo.getValue();
        archivoPorEtiqueta.clear();
        archivoPorEtiqueta.put(TODOS_ARCHIVOS, null);
        for (ArchivoLog a : lector.listarArchivos()) {
            archivoPorEtiqueta.put(a.nombre() + (a.esActual() ? " (actual)" : ""), a);
        }
        cmbArchivo.getItems().setAll(archivoPorEtiqueta.keySet());
        cmbArchivo.setValue(elegido != null && archivoPorEtiqueta.containsKey(elegido) ? elegido : TODOS_ARCHIVOS);
    }

    // ==================== CONSULTA (en segundo plano) ====================

    private void consultar(int pagina, boolean nuevaBusqueda) {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            alerta(Alert.AlertType.WARNING, "Revisa las fechas",
                    "La fecha inicial no puede ser posterior a la fecha final.");
            return;
        }

        if (tareaActual != null) {
            tareaActual.cancel();
        }

        String nivel = TODOS_NIVELES.equals(cmbNivel.getValue()) ? null : cmbNivel.getValue();
        FiltroLogs filtro = new FiltroLogs(nivel, txtBuscar.getText(), desde, hasta);
        ArchivoLog unico = archivoPorEtiqueta.get(cmbArchivo.getValue());
        int totalConocido = nuevaBusqueda ? -1 : totalActual;
        log.debug("Diagnóstico: consultando la página {}", pagina);

        Task<PaginaLogs> tarea = new Task<>() {
            @Override
            protected PaginaLogs call() throws Exception {
                return lector.leer(filtro, unico, pagina, totalConocido, this::isCancelled);
            }
        };
        tarea.setOnSucceeded(e -> {
            if (tarea != tareaActual) {
                return;
            }
            mostrarResultado(tarea.getValue(), unico);
            progreso.setVisible(false);
        });
        tarea.setOnFailed(e -> {
            if (tarea != tareaActual) {
                return;
            }
            log.error("No se pudieron leer los archivos de registro", tarea.getException());
            lblResumen.setText("No se pudieron leer los registros. Revisa que la carpeta de logs exista.");
            progreso.setVisible(false);
            actualizarPaginacion();
        });

        tareaActual = tarea;
        progreso.setVisible(true);
        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);
        EJECUTOR.execute(tarea);
    }

    private void mostrarResultado(PaginaLogs p, ArchivoLog unico) {
        paginaActual = p.pagina();
        totalActual = p.total();
        totalPaginas = p.totalPaginas();

        tabla.getSelectionModel().clearSelection();
        tabla.getItems().setAll(p.entradas());
        mostrarDetalle(null);
        if (!p.entradas().isEmpty()) {
            tabla.scrollTo(0);
        }

        String origen = unico != null
                ? unico.nombre() + (unico.esActual() ? " (actual)" : "")
                : "Todos (" + p.archivos() + " archivo(s))";
        lblResumen.setText("Registros mostrados: " + p.entradas().size() + " de " + p.total()
                + "     |     Archivo actual: " + origen);
        lblPagina.setText("Página " + (paginaActual + 1) + " de " + totalPaginas);
        actualizarPaginacion();
    }

    private void actualizarPaginacion() {
        btnAnterior.setDisable(paginaActual <= 0);
        btnSiguiente.setDisable(paginaActual >= totalPaginas - 1);
    }
    
    // ==================== VISTA DEL ARCHIVO (últimas líneas) ====================

    private void cargarVistaArchivo() {
        ArchivoLog elegido = archivoPorEtiqueta.get(cmbArchivo.getValue());
        ArchivoLog destino = elegido != null ? elegido : archivoActivo();

        if (tareaVista != null) {
            tareaVista.cancel();
        }
        if (destino == null) {
            lblNombreVista.setText("Sin archivo");
            txtVista.setText("Todavía no hay archivos de registro.");
            return;
        }

        lblNombreVista.setText(destino.nombre());
        Task<List<String>> tarea = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return lector.ultimasLineas(destino, LINEAS_VISTA, this::isCancelled);
            }
        };
        tarea.setOnSucceeded(e -> {
            if (tarea != tareaVista) {
                return;
            }
            List<String> lineas = tarea.getValue();
            txtVista.setText(lineas.isEmpty() ? "(archivo vacío)" : String.join("\n", lineas));
            Platform.runLater(() -> txtVista.setScrollTop(Double.MAX_VALUE));   // se ve lo más reciente
        });
        tarea.setOnFailed(e -> {
            if (tarea != tareaVista) {
                return;
            }
            log.error("No se pudo leer el archivo de registro para la vista previa", tarea.getException());
            txtVista.setText("No se pudo leer el archivo.");
        });
        tareaVista = tarea;
        EJECUTOR.execute(tarea);
    }

    private ArchivoLog archivoActivo() {
        return archivoPorEtiqueta.values().stream()
                .filter(a -> a != null && a.esActual())
                .findFirst()
                .orElse(null);
    }

    // ==================== DETALLE ====================

    private void mostrarDetalle(EntradaLog e) {
        seleccionada = e;
        boolean hay = e != null;
        btnCopiar.setDisable(!hay);
        btnAbrirArchivo.setDisable(!hay);
        lblDetalleNivel.setVisible(hay);

        if (!hay) {
            lblDetalleFecha.setText("—");
            lblDetalleClase.setText("—");
            txtMensaje.setText(SIN_SELECCION);
            txtTraza.clear();
            return;
        }
        lblDetalleFecha.setText(e.fecha().format(FECHA_DETALLE));
        lblDetalleNivel.setText(e.nivel());
        lblDetalleNivel.getStyleClass().setAll("label", "log-badge", "log-" + e.nivel().toLowerCase());
        lblDetalleClase.setText(e.clase());
        txtMensaje.setText(e.mensaje());
        txtTraza.setText(e.tieneDetalle() ? e.detalle() : "(sin excepción ni traza)");
        txtTraza.positionCaret(0);
    }

    // ==================== BOTONES ====================

    @FXML
    public void handleActualizar() {
        recargarArchivos();
        consultar(0, true);
        cargarVistaArchivo();
    }

    @FXML
    public void handleAnterior() {
        if (paginaActual > 0) {
            consultar(paginaActual - 1, false);
        }
    }

    @FXML
    public void handleSiguiente() {
        if (paginaActual < totalPaginas - 1) {
            consultar(paginaActual + 1, false);
        }
    }

    @FXML
    public void handleCopiar() {
        if (seleccionada == null) {
            return;
        }
        EntradaLog e = seleccionada;
        StringBuilder sb = new StringBuilder();
        sb.append(e.fecha().format(FECHA_DETALLE)).append(' ').append(e.nivel())
                .append(" [").append(e.hilo()).append("] ").append(e.clase())
                .append('\n').append(e.mensaje());
        if (e.tieneDetalle()) {
            sb.append('\n').append(e.detalle());
        }
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(contenido);

        btnCopiar.setText("¡Copiado!");
        PauseTransition pausa = new PauseTransition(Duration.seconds(1.5));
        pausa.setOnFinished(ev -> btnCopiar.setText("Copiar error"));
        pausa.play();
    }

    @FXML
    public void handleAbrirArchivo() {
        if (seleccionada == null) {
            return;
        }
        Path archivo = seleccionada.archivo();
        if (!Files.exists(archivo)) {
            alerta(Alert.AlertType.INFORMATION, "El archivo ya no existe",
                    "La rotación automática lo archivó o lo eliminó. Pulsa Actualizar para ver los archivos actuales.");
            return;
        }
        try {
            String nombre = archivo.getFileName().toString();
            if (esWindows()) {
                if (nombre.endsWith(".gz")) {
                    // Windows no abre .gz con el Bloc de notas: se muestra seleccionado en el Explorador
                    new ProcessBuilder("explorer.exe", "/select," + archivo).start();
                } else {
                    new ProcessBuilder("notepad.exe", archivo.toString()).start();
                }
            } else {
                new ProcessBuilder("xdg-open", archivo.toString()).start();
            }
        } catch (Exception e) {
            log.error("No se pudo abrir el archivo de registro", e);
            alerta(Alert.AlertType.ERROR, "No se pudo abrir el archivo",
                    "Puedes abrirlo a mano desde:\n" + archivo);
        }
    }

    @FXML
    public void handleAbrirCarpeta() {
        Path carpeta = lector.carpeta();
        try {
            Files.createDirectories(carpeta);
            String destino = carpeta.toString();
            ProcessBuilder pb = esWindows() ? new ProcessBuilder("explorer.exe", destino)
                    : new ProcessBuilder("xdg-open", destino);
            pb.start();
        } catch (Exception e) {
            log.error("No se pudo abrir la carpeta de logs", e);
            alerta(Alert.AlertType.ERROR, "No se pudo abrir la carpeta",
                    "Puedes abrirla a mano desde el Explorador de archivos:\n" + carpeta);
        }
    }

    // ==================== AUXILIARES ====================

    private boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        try {
            a.getDialogPane().getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignorada) {
            // sin estilos, el diálogo funciona igual
        }
        a.showAndWait();
    }
}