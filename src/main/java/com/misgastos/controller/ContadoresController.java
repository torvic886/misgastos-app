package com.misgastos.controller;

import com.misgastos.model.FilaContador;
import com.misgastos.model.Lectura;
import com.misgastos.model.Maquina;
import com.misgastos.service.LecturaService;
import com.misgastos.service.MaquinaService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.TableView;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class ContadoresController {

    @FXML private TextField txtCodigo;
    @FXML private Label lblMaquina;
    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<Integer> cmbPeriodo;
    @FXML private TextField txtIn;
    @FXML private TextField txtOut;
    @FXML private TextField txtPrem;
    @FXML private CheckBox chkReinicio;
    @FXML private Label lblUltima;
    @FXML private Label lblEstado;
    @FXML private Label lblHistorial;
    @FXML private Label lblTotalHistorial;

    @FXML private TableView<FilaHist> tabla;
    @FXML private TableColumn<FilaHist, String> colFecha;
    @FXML private TableColumn<FilaHist, String> colDen;
    @FXML private TableColumn<FilaHist, String> colMaquina;
    @FXML private TableColumn<FilaHist, String> colPeriodo;   
    @FXML private TableColumn<FilaHist, String> colIn;
    @FXML private TableColumn<FilaHist, String> colOut;
    @FXML private TableColumn<FilaHist, String> colPrem;
    @FXML private TableColumn<FilaHist, String> colDif;
    @FXML private TableColumn<FilaHist, String> colTotal;
    
    private enum Clase { LECTURA, ENCABEZADO, TOTAL, ESPACIO }

    /** Una fila de la tabla: lectura, total de la máquina o espacio entre máquinas. */
    private record FilaHist(Clase clase, FilaContador fila, Maquina maquina, BigDecimal total) { }

    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    
    @Autowired
    private MaquinaService maquinaService;

    @Autowired
    private LecturaService lecturaService;

    private final Map<Long, Maquina> activasPorCodigo = new HashMap<>();
    private Maquina maquinaActual;
    private boolean primeraLectura;

    private final DecimalFormat miles;
    private final DecimalFormat pesos;
    private final DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Títulos de la fila de encabezado entre máquinas (todas las columnas). */
    private static final Set<String> TITULOS = Set.of("Fecha", "Den", "Máquina", "Periodo",
            "Contador_In", "Contador_Out", "Prem(Jackpot)", "Diferencia", "Total");

    /** Mismo tamaño y peso que el encabezado de la tabla (11 px, negrita). */
    private static final String ESTILO_TITULO =
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569;"
            + " -fx-alignment: CENTER; -fx-padding: 8 2 8 2;";
    
    public ContadoresController() {
        DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("es", "CO"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        miles = new DecimalFormat("#,##0", s);
        pesos = new DecimalFormat("$ #,##0;-$ #,##0", s);
    }

    @FXML
    public void initialize() {
        log.debug("Inicializando pantalla de contadores");
        dpFecha.setValue(LocalDate.now());
        cmbPeriodo.getItems().addAll(1, 2, 3);
        cmbPeriodo.setDisable(true);
        dpFecha.valueProperty().addListener((obs, a, n) -> sugerirPeriodo());

        soloDigitos(txtCodigo, 12);
        aplicarFormatoMiles(txtIn, 15);
        aplicarFormatoMiles(txtOut, 15);
        aplicarFormatoMiles(txtPrem, 15);

        configurarTabla();
        configurarEnter();
        cargarMaquinas();

        txtCodigo.focusedProperty().addListener((obs, antes, enfocado) -> {
            if (!enfocado) {
                buscarMaquina();
            }
        });

        Platform.runLater(() -> txtCodigo.requestFocus());
        
        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy.withDayOfMonth(hoy.lengthOfMonth()));
        dpDesde.valueProperty().addListener((obs, a, n) -> refrescarTabla());
        dpHasta.valueProperty().addListener((obs, a, n) -> refrescarTabla());
        refrescarTabla();
        // La línea del nombre de la máquina solo ocupa espacio cuando tiene texto
        lblMaquina.managedProperty().bind(lblMaquina.textProperty().isNotEmpty());
        lblMaquina.visibleProperty().bind(lblMaquina.textProperty().isNotEmpty());
    
    }

    // ==================== MÁQUINA ====================

    private void cargarMaquinas() {
        activasPorCodigo.clear();
        for (Maquina m : maquinaService.listarActivas()) {
            activasPorCodigo.put(m.getCodigo(), m);
        }
        log.debug("Contadores: {} máquinas activas disponibles", activasPorCodigo.size());
    }

    private void buscarMaquina() {
        String texto = txtCodigo.getText();
        if (texto == null || texto.isBlank()) {
            limpiarMaquina();
            return;
        }

        long codigo = Long.parseLong(texto.trim());
        if (maquinaActual != null && maquinaActual.getCodigo() == codigo) {
            return;
        }

        Maquina m = activasPorCodigo.get(codigo);
        if (m == null) {
            limpiarMaquina();
            mostrarEstado("No hay una máquina activa con el código " + codigo, true);
            return;
        }

        ocultarEstado();
        maquinaActual = m;
        lblMaquina.setText(m.getNombre() + " · " + m.getJuego() + " · Den. " + pesos.format(m.getDenominacion().longValue()));
        actualizarPanel();
    }

    private boolean actualizarPanel() {
        boolean sinLecturas = false;
        if (maquinaActual != null) {
            List<FilaContador> propias = lecturaService.filas(maquinaActual.getId(), null, null);
            primeraLectura = propias.isEmpty();
            cmbPeriodo.setDisable(!primeraLectura);
            if (propias.isEmpty()) {
                lblUltima.setText("Sin lecturas: esta será la primera. Escoja su periodo");
                sinLecturas = true;
                sugerirPeriodo();
            } else {
                cmbPeriodo.setValue(lecturaService.siguientePeriodo(maquinaActual.getId()));
                Lectura u = propias.get(propias.size() - 1).lectura();
                lblUltima.setText("Última lectura: " + u.getFecha().format(formatoFecha) + " · periodo "
                        + u.getPeriodo() + " · In " + miles.format(u.getContadorIn())
                        + " · Out " + miles.format(u.getContadorOut())
                        + " · Prem " + miles.format(u.getContadorPrem()));
            }
        }
        refrescarTabla();
        return sinLecturas;
    }
    
    /** En la primera lectura de la máquina se sugiere el periodo según el día; se puede cambiar. */
    private void sugerirPeriodo() {
        if (maquinaActual == null || !primeraLectura || dpFecha.getValue() == null) {
            return;
        }
        int dia = dpFecha.getValue().getDayOfMonth();
        cmbPeriodo.setValue(dia <= 8 ? 1 : dia <= 23 ? 2 : 3);
    }
    
    /** Todas las máquinas del rango de fechas, en bloques: lecturas, total y un espacio. */
    private void refrescarTabla() {
        Map<Long, List<FilaContador>> porMaquina = new java.util.LinkedHashMap<>();
        for (FilaContador f : lecturaService.filas(null, dpDesde.getValue(), dpHasta.getValue())) {
            // La copia repite los contadores del periodo 3 y se edita junto con él: no se muestra
            if (Lectura.COPIA.equals(f.lectura().getTipo())) {
                continue;
            }
            porMaquina.computeIfAbsent(f.lectura().getMaquina().getId(), k -> new ArrayList<>()).add(f);
        }

        List<FilaHist> filas = new ArrayList<>();
        BigDecimal granTotal = BigDecimal.ZERO;
        boolean primera = true;
        for (List<FilaContador> grupo : porMaquina.values()) {
            Maquina m = grupo.get(0).lectura().getMaquina();
            if (!primera) {
                filas.add(new FilaHist(Clase.ENCABEZADO, null, m, null));
            }
            primera = false;
            BigDecimal suma = BigDecimal.ZERO;
            for (FilaContador f : grupo) {
                filas.add(new FilaHist(Clase.LECTURA, f, m, null));
                if (f.total() != null) {
                    suma = suma.add(f.total());
                }
            }
            filas.add(new FilaHist(Clase.TOTAL, null, m, suma));
            filas.add(new FilaHist(Clase.ESPACIO, null, m, null));
            granTotal = granTotal.add(suma);
        }
        tabla.setItems(FXCollections.observableArrayList(filas));

        lblTotalHistorial.setText(dinero(granTotal));
        lblTotalHistorial.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;"
                + (granTotal.signum() < 0 ? " -fx-text-fill: #dc2626;" : ""));

        // Saltar al bloque de la máquina que se está trabajando
        if (maquinaActual != null) {
            for (int i = 0; i < filas.size(); i++) {
                FilaHist h = filas.get(i);
                if (h.clase() == Clase.TOTAL && h.maquina().getId().equals(maquinaActual.getId())) {
                    final int pos = i;
                    Platform.runLater(() -> tabla.scrollTo(Math.max(0, pos - 6)));
                    break;
                }
            }
        }
    }

    private void limpiarMaquina() {
        maquinaActual = null;
        lblMaquina.setText("");
        primeraLectura = false;
        cmbPeriodo.setValue(null);
        cmbPeriodo.setDisable(true);
        lblUltima.setText("");
        txtIn.clear();
        txtOut.clear();
        txtPrem.clear();
        chkReinicio.setSelected(false);
    }
    
    @FXML
    private void handleToggleReinicio() {
        chkReinicio.setSelected(!chkReinicio.isSelected());
    }

    // ==================== ACCIONES ====================

    @FXML
    private void handleGuardar() {
        ocultarEstado();
        buscarMaquina();
        if (maquinaActual == null) {
            mostrarEstado("Ingrese el código de una máquina activa", true);
            txtCodigo.requestFocus();
            return;
        }

        confirmarFecha(dpFecha);
        if (dpFecha.getValue() == null) {
            mostrarEstado("Seleccione la fecha del periodo", true);
            dpFecha.requestFocus();
            return;
        }

        Long in = leerEntero(txtIn);
        Long out = leerEntero(txtOut);
        Long prem = leerEntero(txtPrem);
        if (in == null) {
            mostrarEstado("Ingrese el contador In", true);
            txtIn.requestFocus();
            return;
        }
        if (out == null) {
            mostrarEstado("Ingrese el contador Out", true);
            txtOut.requestFocus();
            return;
        }
        if (prem == null) {
            mostrarEstado("Ingrese el contador Prem (si no hubo premios, ponga el mismo valor anterior)", true);
            txtPrem.requestFocus();
            return;
        }
        
        if (primeraLectura && cmbPeriodo.getValue() == null) {
            mostrarEstado("Escoja el periodo de la primera lectura", true);
            cmbPeriodo.requestFocus();
            return;
        }

        boolean reinicio = chkReinicio.isSelected();
        try {
            if (!reinicio && lecturaService.contadorMenor(maquinaActual.getId(), in, out, prem)) {
                if (!confirmarReinicio()) {
                    return;
                }
                reinicio = true;
            }

            Long codigo = maquinaActual.getCodigo();
            Lectura l = lecturaService.registrar(maquinaActual.getId(), dpFecha.getValue(), in, out, prem, reinicio,
                    primeraLectura ? cmbPeriodo.getValue() : null);

            // El historial de la máquina se queda visible; solo se limpian los contadores
            txtIn.clear();
            txtOut.clear();
            txtPrem.clear();
            chkReinicio.setSelected(false);
            // Si la fecha queda fuera del rango que se ve, se amplía el rango
            LocalDate fechaLectura = dpFecha.getValue();
            if (dpDesde.getValue() == null || fechaLectura.isBefore(dpDesde.getValue())) {
                dpDesde.setValue(fechaLectura.withDayOfMonth(1));
            }
            if (dpHasta.getValue() == null || fechaLectura.isAfter(dpHasta.getValue())) {
                dpHasta.setValue(fechaLectura.withDayOfMonth(fechaLectura.lengthOfMonth()));
            }
            actualizarPanel();
            mostrarEstado("Lectura guardada: máquina " + codigo + ", periodo " + l.getPeriodo(), false);
            txtCodigo.requestFocus();
            txtCodigo.selectAll();

        } catch (Exception e) {
            log.error("No se pudo registrar la lectura", e);
            mostrarEstado(e.getMessage(), true);
        }
    }

    @FXML
    private void handleLimpiar() {
        txtCodigo.clear();
        limpiarMaquina();
        ocultarEstado();
        txtCodigo.requestFocus();
    }

    private boolean confirmarReinicio() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Contador menor al anterior");
        a.setHeaderText("Un contador es menor que el de la lectura anterior");
        a.setContentText("¿La máquina se reinició o se cambió el contador? Si es así, se guardará como un "
                + "reinicio (sin total). Si fue un error de digitación, pulsa Cancelar y corrígelo.");
        a.getDialogPane().getStylesheets().add(css());
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void handleEditar(FilaContador fila) {
        Lectura l = fila.lectura();

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Editar lectura");
        dialogo.setHeaderText("Máquina " + l.getMaquina().getCodigo() + " · periodo " + l.getPeriodo());

        DatePicker dp = new DatePicker(l.getFecha());
        dp.setMaxWidth(Double.MAX_VALUE);
        TextField tIn = campoNumero(l.getContadorIn());
        TextField tOut = campoNumero(l.getContadorOut());
        TextField tPrem = campoNumero(l.getContadorPrem());
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #ef4444;");
        lblError.setWrapText(true);

        VBox contenido = new VBox(8,
                new Label("Fecha:"), dp,
                new Label("Contador In:"), tIn,
                new Label("Contador Out:"), tOut,
                new Label("Contador Prem:"), tPrem,
                lblError);
        contenido.setPadding(new Insets(10));
        contenido.setPrefWidth(340);

        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.getDialogPane().getStylesheets().add(css());

        // El guardado ocurre aquí: si falla, el diálogo queda abierto con el mensaje
        Button ok = (Button) dialogo.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                confirmarFecha(dp);
                lecturaService.editar(l.getId(), dp.getValue(),
                        leerEntero(tIn), leerEntero(tOut), leerEntero(tPrem));
            } catch (Exception e) {
                lblError.setText(e.getMessage());
                ev.consume();
            }
        });

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            actualizarPanel();
            mostrarEstado("Lectura editada", false);
        }
    }

    private void handleAnular(FilaContador fila) {
        Lectura l = fila.lectura();

        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Anular lectura");
        dialogo.setHeaderText("¿Anular la lectura de la máquina " + l.getMaquina().getCodigo()
                + ", periodo " + l.getPeriodo() + " del " + l.getFecha().format(formatoFecha) + "?");
        dialogo.setContentText("Motivo:");
        dialogo.getDialogPane().getStylesheets().add(css());

        Optional<String> motivo = dialogo.showAndWait();
        if (motivo.isPresent()) {
            try {
                lecturaService.anular(l.getId(), motivo.get());
                actualizarPanel();
                mostrarEstado("Lectura anulada", false);
            } catch (Exception e) {
                log.error("No se pudo anular la lectura {}", l.getId(), e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error al anular");
                alert.setHeaderText(null);
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    // ==================== TABLA ====================

    private void configurarTabla() {
    	colFecha.setCellValueFactory(c -> dato(c.getValue(), "Fecha", f -> f.lectura().getFecha().format(formatoFecha)));
    	colDen.setCellValueFactory(c -> dato(c.getValue(), "Den", f ->
    	        f.lectura().getDenominacion().stripTrailingZeros().toPlainString()));
    	colMaquina.setCellValueFactory(c -> dato(c.getValue(), "Máquina", f ->
    	        String.valueOf(f.lectura().getMaquina().getCodigo())));
    	colPeriodo.setCellValueFactory(c -> dato(c.getValue(), "Periodo", f -> String.valueOf(f.lectura().getPeriodo())));
    	colIn.setCellValueFactory(c -> dato(c.getValue(), "Contador_In", f -> pesos.format(f.lectura().getContadorIn())));
    	colOut.setCellValueFactory(c -> dato(c.getValue(), "Contador_Out", f -> pesos.format(f.lectura().getContadorOut())));
    	colPrem.setCellValueFactory(c -> dato(c.getValue(), "Prem(Jackpot)", f -> pesos.format(f.lectura().getContadorPrem())));
    	colDif.setCellValueFactory(c -> {
    	    FilaHist h = c.getValue();
    	    if (h.clase() == Clase.TOTAL) {
    	        return new SimpleStringProperty("Total " + h.maquina().getCodigo());
    	    }
    	    return dato(h, "Diferencia", f -> dinero(f.diferencia()));
    	});
    	colTotal.setCellValueFactory(c -> {
    	    FilaHist h = c.getValue();
    	    if (h.clase() == Clase.TOTAL) {
    	        return new SimpleStringProperty(dinero(h.total()));
    	    }
    	    return dato(h, "Total", f -> f.total() == null && Lectura.REINICIO.equals(f.lectura().getTipo())
    	            ? "Reinicio" : dinero(f.total()));
    	});
        ajustarColumnas();
        
        for (TableColumn<FilaHist, String> c : List.of(colDif, colTotal)) {
            celdaCifra(c);
        }
        for (TableColumn<FilaHist, String> c : List.of(colDen, colMaquina, colPeriodo)) {
            celdaCentrada(c);
        }
        celdaEditable(colFecha, 0, false);
        celdaEditable(colIn, 1, true);
        celdaEditable(colOut, 2, true);
        celdaEditable(colPrem, 3, true);

        tabla.setRowFactory(tv -> {
            TableRow<FilaHist> fila = new TableRow<>() {
                @Override
                protected void updateItem(FilaHist h, boolean empty) {
                    super.updateItem(h, empty);
                    if (empty || h == null || h.clase() == Clase.LECTURA) {
                        setStyle("");
                    } else if (h.clase() == Clase.TOTAL) {
                        setStyle("-fx-background-color: #fef9c3; -fx-font-weight: bold;");
                    } else if (h.clase() == Clase.ENCABEZADO) {
                        setStyle("-fx-background-color: #e8eef9; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: transparent;");
                    }
                }
            };

            // Clic derecho solo sobre filas de lectura (no sobre totales ni espacios)
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null && fila.getItem().clase() == Clase.LECTURA) {
                    handleEditar(fila.getItem().fila());
                }
            });
            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> {
                if (fila.getItem() != null && fila.getItem().clase() == Clase.LECTURA) {
                    handleAnular(fila.getItem().fila());
                }
            });
            ContextMenu menu = new ContextMenu(editar, eliminar);
            fila.contextMenuProperty().bind(Bindings.createObjectBinding(
                    () -> !fila.isEmpty() && fila.getItem() != null && fila.getItem().clase() == Clase.LECTURA
                            ? menu : null,
                    fila.itemProperty(), fila.emptyProperty()));
            return fila;
        });
    }
    
    /** Columnas pequeñas fijas; las 5 de dinero se reparten el resto, sin bajar de su mínimo. */
    private void ajustarColumnas() {
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        fijo(colFecha, 86);
        fijo(colDen, 40);
        fijo(colMaquina, 70);
        fijo(colPeriodo, 68);
        double fijos = 86 + 40 + 70 + 68;

        flexible(colIn, 112, fijos);
        flexible(colOut, 112, fijos);
        flexible(colPrem, 112, fijos);
        flexible(colDif, 112, fijos);
        flexible(colTotal, 112, fijos);

        // Ordenar por encabezado rompería los bloques por máquina
        tabla.getColumns().forEach(c -> c.setSortable(false));
    }
    
    private void fijo(TableColumn<FilaHist, String> col, double ancho) {
        col.setMinWidth(ancho);
        col.setPrefWidth(ancho);
        col.setMaxWidth(ancho);
    }

    private void flexible(TableColumn<FilaHist, String> col, double minimo, double fijos) {
        col.setMinWidth(minimo);
        col.prefWidthProperty().bind(tabla.widthProperty().subtract(20 + fijos).divide(5));
    }

    private void ancho(TableColumn<FilaHist, String> col, double fraccion, double minimo) {
        col.setMinWidth(minimo);
        col.prefWidthProperty().bind(tabla.widthProperty().subtract(22).multiply(fraccion));
    }

    /** Texto de una celda: el valor en lecturas, el título en encabezados y vacío en totales y espacios. */
    private SimpleStringProperty dato(FilaHist h, String titulo, java.util.function.Function<FilaContador, String> extractor) {
        return switch (h.clase()) {
            case LECTURA -> new SimpleStringProperty(extractor.apply(h.fila()));
            case ENCABEZADO -> new SimpleStringProperty(titulo);
            default -> new SimpleStringProperty("");
        };
    }

    private void celdaCifra(TableColumn<FilaHist, String> col) {
        col.setCellFactory(c -> new TableCell<FilaHist, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                boolean negativo = !empty && item != null && item.startsWith("-");
                boolean titulo = !empty && item != null && TITULOS.contains(item);
                if (titulo) {
                    setStyle(ESTILO_TITULO);
                } else {
                    setStyle("-fx-alignment: CENTER_RIGHT; -fx-padding: 8 10 8 6;"
                            + (negativo ? " -fx-text-fill: #dc2626;" : ""));
                }
            }
        });
    }

    /** Columnas centradas (Fecha, Den, Máquina, Periodo): los títulos usan el estilo del encabezado. */
    private void celdaCentrada(TableColumn<FilaHist, String> col) {
        col.setCellFactory(c -> new TableCell<FilaHist, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                boolean titulo = !empty && item != null && TITULOS.contains(item);
                setStyle(titulo ? ESTILO_TITULO : "-fx-alignment: CENTER;");
            }
        });
    }
    
    /** Celda editable con doble clic: Enter guarda, Esc cancela. campo: 0 fecha, 1 In, 2 Out, 3 Prem. */
    private void celdaEditable(TableColumn<FilaHist, String> col, int campo, boolean derecha) {
        col.setCellFactory(c -> new TableCell<FilaHist, String>() {
            private TextField editor;

            {
                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && !isEmpty() && esLectura()) {
                        iniciarEdicion();
                    }
                });
            }

            private boolean esLectura() {
                return getTableRow() != null && getTableRow().getItem() != null
                        && getTableRow().getItem().clase() == Clase.LECTURA;
            }

            private void iniciarEdicion() {
                FilaContador fila = getTableRow().getItem().fila();
                Lectura l = fila.lectura();
                String inicial = campo == 0
                        ? l.getFecha().format(formatoFecha)
                        : miles.format(campo == 1 ? l.getContadorIn()
                                : campo == 2 ? l.getContadorOut() : l.getContadorPrem());

                TextField campoEdicion = new TextField(inicial);
                if (campo != 0) {
                    aplicarFormatoMiles(campoEdicion, 15);
                }
                campoEdicion.setStyle("-fx-pref-height: 28px; -fx-font-size: 12px; -fx-padding: 2 6;"
                        + (derecha ? " -fx-alignment: CENTER_RIGHT;" : " -fx-alignment: CENTER;"));
                editor = campoEdicion;

                setText(null);
                setGraphic(campoEdicion);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                Platform.runLater(() -> {
                    campoEdicion.requestFocus();
                    campoEdicion.selectAll();
                });

                campoEdicion.setOnKeyPressed(ev -> {
                    if (ev.getCode() == KeyCode.ENTER) {
                        String texto = campoEdicion.getText();
                        terminar();
                        guardarCelda(fila, campo, texto);
                        ev.consume();
                    } else if (ev.getCode() == KeyCode.ESCAPE) {
                        terminar();
                        ev.consume();
                    }
                });
                campoEdicion.focusedProperty().addListener((obs, antes, enfocado) -> {
                    if (!enfocado && editor == campoEdicion) {
                        terminar();
                    }
                });
            }

            private void terminar() {
                if (editor == null) {
                    return;
                }
                editor = null;
                setGraphic(null);
                setContentDisplay(ContentDisplay.TEXT_ONLY);
                setText(getItem());
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                editor = null;
                setGraphic(null);
                setContentDisplay(ContentDisplay.TEXT_ONLY);
                setText(empty ? null : item);
                boolean titulo = !empty && item != null && TITULOS.contains(item);
                boolean negativo = !empty && item != null && item.startsWith("-");
                if (titulo) {
                    setStyle(ESTILO_TITULO);
                } else {
                    setStyle((derecha ? "-fx-alignment: CENTER_RIGHT; -fx-padding: 8 10 8 6;"
                            : "-fx-alignment: CENTER;")
                            + (negativo ? " -fx-text-fill: #dc2626;" : ""));
                }
            }
        });
    }

    /** Guarda el cambio de una celda con la misma regla y auditoría de "Editar lectura". */
    private void guardarCelda(FilaContador fila, int campo, String texto) {
        Lectura l = fila.lectura();
        try {
            LocalDate fecha = l.getFecha();
            Long in = l.getContadorIn();
            Long out = l.getContadorOut();
            Long prem = l.getContadorPrem();

            if (campo == 0) {
                fecha = LocalDate.parse(texto.trim(),
                        DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT));
            } else {
                String limpio = texto == null ? "" : texto.replaceAll("[^0-9]", "");
                if (limpio.isEmpty()) {
                    mostrarEstado("El contador no puede quedar vacío", true);
                    return;
                }
                long valor = Long.parseLong(limpio);
                if (campo == 1) {
                    in = valor;
                } else if (campo == 2) {
                    out = valor;
                } else {
                    prem = valor;
                }
            }

            lecturaService.editar(l.getId(), fecha, in, out, prem);
            actualizarPanel();
            mostrarEstado("Lectura editada", false);
        } catch (DateTimeParseException e) {
            mostrarEstado("Fecha inválida: escríbala como dd/mm/aaaa", true);
        } catch (Exception e) {
            log.error("No se pudo editar la lectura {}", l.getId(), e);
            mostrarEstado(e.getMessage(), true);
        }
    }

    private String tipoLegible(String tipo) {
        return switch (tipo) {
            case Lectura.INICIAL -> "Inicial";
            case Lectura.COPIA -> "Copia";
            case Lectura.REINICIO -> "Reinicio";
            default -> "Normal";
        };
    }

    private String dinero(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        return pesos.format(valor.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    // ==================== ENTRADA DE DATOS ====================

    private void configurarEnter() {
        txtCodigo.setOnAction(e -> {
            buscarMaquina();
            if (maquinaActual != null) {
                dpFecha.requestFocus();
            }
        });
        txtIn.setOnAction(e -> txtOut.requestFocus());
        txtOut.setOnAction(e -> txtPrem.requestFocus());
        txtPrem.setOnAction(e -> handleGuardar());

        dpFecha.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                confirmarFecha(dpFecha);
                e.consume();
                txtIn.requestFocus();
            }
        });
    }

    private void confirmarFecha(DatePicker dp) {
        try {
            LocalDate fecha = dp.getConverter().fromString(dp.getEditor().getText());
            if (fecha != null) {
                dp.setValue(fecha);
            }
        } catch (Exception ignorada) {
            // si el texto no es una fecha válida, se conserva la fecha elegida antes
        }
    }

    private void soloDigitos(TextField campo, int max) {
        campo.textProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                return;
            }
            String limpio = nuevo.replaceAll("[^0-9]", "");
            if (limpio.length() > max) {
                limpio = limpio.substring(0, max);
            }
            if (!limpio.equals(nuevo)) {
                campo.setText(limpio);
            }
        });
    }

    /** Deja solo dígitos con separador de miles y limita la cantidad de dígitos. */
    private void aplicarFormatoMiles(TextField campo, int maxDigitos) {
        boolean[] formateando = {false};
        campo.textProperty().addListener((obs, anterior, nuevo) -> {
            if (formateando[0] || nuevo == null) {
                return;
            }
            formateando[0] = true;
            try {
                String limpio = nuevo.replaceAll("[^0-9]", "");
                if (limpio.length() > maxDigitos) {
                    limpio = limpio.substring(0, maxDigitos);
                }
                String formateado = limpio.isEmpty() ? "" : miles.format(Long.parseLong(limpio));
                if (!formateado.equals(nuevo)) {
                    campo.setText(formateado);
                    campo.positionCaret(formateado.length());
                }
            } finally {
                formateando[0] = false;
            }
        });
    }

    private TextField campoNumero(long valor) {
        TextField campo = new TextField();
        aplicarFormatoMiles(campo, 15);
        campo.setText(miles.format(valor));
        return campo;
    }

    private Long leerEntero(TextField campo) {
        String limpio = campo.getText() == null ? "" : campo.getText().replaceAll("[^0-9]", "");
        return limpio.isEmpty() ? null : Long.parseLong(limpio);
    }

    // ==================== MENSAJES ====================

    private void mostrarEstado(String mensaje, boolean error) {
        lblEstado.setText(mensaje);
        lblEstado.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: "
                + (error ? "#dc2626" : "#059669") + ";");
        lblEstado.setVisible(true);
        lblEstado.setManaged(true);
    }

    private void ocultarEstado() {
        lblEstado.setVisible(false);
        lblEstado.setManaged(false);
    }

    private String css() {
        return getClass().getResource("/css/styles.css").toExternalForm();
    }
}