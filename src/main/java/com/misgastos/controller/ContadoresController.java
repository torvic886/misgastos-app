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
import java.time.format.TextStyle;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

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

    /** BASE: lectura anterior al rango contra la que se calculó el primer Total (solo se muestra, no suma). */
    private enum Clase { LECTURA, BASE, ENCABEZADO, TOTAL, ESPACIO }

    /** Una fila de la tabla: lectura, base del mes anterior, total de la máquina o espacio entre máquinas. */
    private record FilaHist(Clase clase, FilaContador fila, Maquina maquina, BigDecimal total) { }

    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private CheckBox chkIntermedia;

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
                sugerirFecha(u);
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

    /** Propone la fecha de la próxima lectura según la última: día 16 o último día del mes. */
    private void sugerirFecha(Lectura ultima) {
        int siguiente = lecturaService.siguientePeriodo(maquinaActual.getId());
        LocalDate f = ultima.getFecha();
        if (siguiente == 2) {
            // Base del 31/12 -> 16 del mes siguiente; base del 01/12 -> 16 del mismo mes
            LocalDate mes = f.getDayOfMonth() > 15 ? f.plusMonths(1) : f;
            dpFecha.setValue(mes.withDayOfMonth(16));
        } else if (siguiente == 3) {
            dpFecha.setValue(f.withDayOfMonth(f.lengthOfMonth()));
        }
    }

    /**
     * Muestra las lecturas del rango y, cuando existe, la base del cierre anterior.
     * Una COPIA del mes anterior también se muestra si todavía no hay lecturas
     * en el mes consultado. Ninguna BASE interviene en las sumas.
     */
    private void refrescarTabla() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            tabla.setItems(FXCollections.observableArrayList());
            lblTotalHistorial.setText(dinero(BigDecimal.ZERO));
            return;
        }

        // Lecturas de todas las máquinas ya calculadas por el servicio.
        // No recalculamos ningún Total en el controlador.
        List<FilaContador> todas = lecturaService.filas(null, null, null);
        Map<Long, List<FilaContador>> porMaquina = new LinkedHashMap<>();
        Map<Long, FilaContador> basesSinLectura = new LinkedHashMap<>();
        Map<Long, Maquina> maquinas = new LinkedHashMap<>();
        Map<Long, FilaContador> ultimasPrevias = new LinkedHashMap<>();

        LocalDate inicioMesAnterior = desde == null ? null
                : desde.withDayOfMonth(1).minusMonths(1);
        for (FilaContador f : todas) {
            Lectura l = f.lectura();
            Long id = l.getMaquina().getId();
            maquinas.putIfAbsent(id, l.getMaquina());

            if (desde != null && l.getFecha().isBefore(desde)) {
                ultimasPrevias.put(id, f); // la última que se asigne es la más reciente
            }

            boolean dentro = (desde == null || !l.getFecha().isBefore(desde))
                    && (hasta == null || !l.getFecha().isAfter(hasta));
            if (dentro && !Lectura.COPIA.equals(l.getTipo())) {
                porMaquina.computeIfAbsent(id, k -> new ArrayList<>()).add(f);
            }

            // Solo el cierre del mes inmediatamente anterior, nunca uno viejo.
            if (inicioMesAnterior != null && Lectura.COPIA.equals(l.getTipo())
                    && !l.getFecha().isBefore(inicioMesAnterior)
                    && l.getFecha().isBefore(desde)) {
                FilaContador actual = basesSinLectura.get(id);
                if (actual == null || !l.getFecha().isBefore(actual.lectura().getFecha())
                        && (l.getFecha().isAfter(actual.lectura().getFecha())
                        || l.getId() > actual.lectura().getId())) {
                    basesSinLectura.put(id, f);
                }
            }
        }

        // También incluir bloques de máquinas que solo tienen una base del mes anterior.
        // La iteración mantiene el orden por código provisto por LecturaService.
        List<FilaHist> filas = new ArrayList<>();
        BigDecimal granTotal = BigDecimal.ZERO;
        boolean primera = true;
        for (Map.Entry<Long, Maquina> entrada : maquinas.entrySet()) {
            Long id = entrada.getKey();
            Maquina m = entrada.getValue();
            List<FilaContador> grupo = porMaquina.getOrDefault(id, List.of());
            FilaContador base = grupo.isEmpty() ? basesSinLectura.get(id)
                    : buscarBase(m, grupo.get(0));
            if (base == null && grupo.isEmpty() && activasPorCodigo.get(m.getCodigo()) != null) {
                base = ultimasPrevias.get(id);
            }
            log.debug("Base previa: maquina {} grupo {} base {} activa {} previa {}",
                    m.getCodigo(), grupo.size(), base, activasPorCodigo.get(m.getCodigo()) != null,
                    ultimasPrevias.get(id) != null);
            if (grupo.isEmpty() && base == null) {
                continue;
            }
            if (!primera) {
                filas.add(new FilaHist(Clase.ENCABEZADO, null, m, null));
            }
            primera = false;

            if (base != null) {
                filas.add(new FilaHist(Clase.BASE, base, m, null));
            }

            BigDecimal suma = BigDecimal.ZERO;
            for (FilaContador f : grupo) {
                filas.add(new FilaHist(Clase.LECTURA, f, m, null));
                if (f.total() != null) {
                    suma = suma.add(f.total());
                }
            }
            // Un bloque solo con BASE no tiene utilidad ni fila de Total.
            if (!grupo.isEmpty()) {
                filas.add(new FilaHist(Clase.TOTAL, null, m, suma));
                granTotal = granTotal.add(suma);
            }
            filas.add(new FilaHist(Clase.ESPACIO, null, m, null));
        }
        tabla.setItems(FXCollections.observableArrayList(filas));

        lblTotalHistorial.setText(dinero(granTotal));
        lblTotalHistorial.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;"
                + (granTotal.signum() < 0 ? " -fx-text-fill: #dc2626;" : ""));

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

    private void ponerRango(LocalDate desde, LocalDate hasta) {
        LocalDate actual = dpDesde.getValue();
        // Se asigna en un orden que nunca deja Desde > Hasta, ni siquiera un instante
        if (actual == null || desde.isAfter(actual)) {
            dpHasta.setValue(hasta);
            dpDesde.setValue(desde);
        } else {
            dpDesde.setValue(desde);
            dpHasta.setValue(hasta);
        }
    }

    private YearMonth mesVisible() {
        LocalDate d = dpDesde.getValue() != null ? dpDesde.getValue() : LocalDate.now();
        return YearMonth.from(d);
    }

    @FXML private void handleMesAnterior() {
        YearMonth ym = mesVisible().minusMonths(1);
        ponerRango(ym.atDay(1), ym.atEndOfMonth());
    }

    @FXML private void handleMesSiguiente() {
        YearMonth ym = mesVisible().plusMonths(1);
        ponerRango(ym.atDay(1), ym.atEndOfMonth());
    }

    @FXML private void handleEsteMes() {
        YearMonth ym = YearMonth.now();
        ponerRango(ym.atDay(1), ym.atEndOfMonth());
    }

    @FXML private void handleQuincena1() {
        YearMonth ym = mesVisible();
        ponerRango(ym.atDay(1), ym.atDay(15));
    }

    @FXML private void handleQuincena2() {
        YearMonth ym = mesVisible();
        ponerRango(ym.atDay(16), ym.atEndOfMonth());
    }

    /**
     * Lectura inmediatamente anterior a la primera fila visible de la máquina, solo si cae antes del
     * rango y la primera fila tiene Total (es decir, si el Total se calculó contra ella).
     */
    private FilaContador buscarBase(Maquina m, FilaContador primeraVisible) {
        LocalDate desde = dpDesde.getValue();
        if (desde == null || primeraVisible.total() == null) {
            return null;
        }
        List<FilaContador> todas = lecturaService.filas(m.getId(), null, null);
        for (int i = 0; i < todas.size(); i++) {
            if (todas.get(i).lectura().getId().equals(primeraVisible.lectura().getId())) {
                if (i == 0) {
                    return null;
                }
                FilaContador anterior = todas.get(i - 1);
                return anterior.lectura().getFecha().isBefore(desde) ? anterior : null;
            }
        }
        return null;
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
        chkIntermedia.setSelected(false);
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

        // Aviso de cierre de mes: la lectura del periodo 3 que se hace el 1.º, 2 o 3 cierra el mes anterior
        int periodoAGuardar = primeraLectura ? cmbPeriodo.getValue()
                : lecturaService.siguientePeriodo(maquinaActual.getId());
        if (!chkIntermedia.isSelected() && periodoAGuardar == 3 && dpFecha.getValue().getDayOfMonth() <= 3) {
            LocalDate escrita = dpFecha.getValue();
            LocalDate sugerida = escrita.withDayOfMonth(1).minusDays(1);
            Boolean usarSugerida = preguntarFechaCierre(escrita, sugerida);
            if (usarSugerida == null) {
                return;
            }
            if (usarSugerida) {
                dpFecha.setValue(sugerida);
                if (primeraLectura) {
                    // Cambiar la fecha vuelve a sugerir el periodo: se conserva el escogido
                    cmbPeriodo.setValue(periodoAGuardar);
                }
            }
        }

        boolean reinicio = chkReinicio.isSelected();
        try {
            if (!reinicio && lecturaService.contadorMenor(maquinaActual.getId(), in, out, prem)) {
                Boolean esDano = preguntarReinicio();
                if (esDano == null) {
                    return;
                }
                reinicio = true;
                if (esDano) {
                    chkIntermedia.setSelected(true);
                }
            }

            Long codigo = maquinaActual.getCodigo();
            Lectura l = lecturaService.registrar(maquinaActual.getId(), dpFecha.getValue(), in, out, prem, reinicio,
                    primeraLectura ? cmbPeriodo.getValue() : null, chkIntermedia.isSelected());

            // El historial de la máquina se queda visible; solo se limpian los contadores
            txtIn.clear();
            txtOut.clear();
            txtPrem.clear();
            chkReinicio.setSelected(false);
            // Si la fecha queda fuera del rango que se ve, el rango pasa a ser el mes de esa lectura
            LocalDate fechaLectura = dpFecha.getValue();
            boolean fueraDelRango = dpDesde.getValue() == null || dpHasta.getValue() == null
                    || fechaLectura.isBefore(dpDesde.getValue()) || fechaLectura.isAfter(dpHasta.getValue());
            if (fueraDelRango) {
                dpDesde.setValue(fechaLectura.withDayOfMonth(1));
                dpHasta.setValue(fechaLectura.withDayOfMonth(fechaLectura.lengthOfMonth()));
            }
            actualizarPanel();
            String aviso = "Lectura guardada: máquina " + codigo + ", periodo " + l.getPeriodo();
            if (l.getPeriodo() == 3) {
                aviso += ". Quedó también como base del mes siguiente (periodo 1).";
            }
            mostrarEstado(aviso, false);
            txtCodigo.clear();
            limpiarMaquina();
            txtCodigo.requestFocus();

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

    /** null = cancelar; true = reinicio por daño/cambio (intermedia); false = reinicio normal. */
    private Boolean preguntarReinicio() {
        ButtonType dano = new ButtonType("Daño o cambio", ButtonBar.ButtonData.YES);
        ButtonType normal = new ButtonType("Reinicio normal", ButtonBar.ButtonData.NO);

        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Si la máquina se dañó o se cambió el contador, escoge «Daño o cambio»: se guarda como "
                        + "reinicio sin total y no consume periodo. Si fue un reinicio normal, escoge "
                        + "«Reinicio normal». Si fue un error de digitación, pulsa Cancelar y corrígelo.",
                dano, normal, ButtonType.CANCEL);
        a.setTitle("Contador menor al anterior");
        a.setHeaderText("Un contador es menor que el de la lectura anterior");
        a.setGraphic(null);
        a.getDialogPane().getStylesheets().add(css());

        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() == ButtonType.CANCEL) {
            return null;
        }
        return r.get() == dano;
    }

    /**
     * Pregunta con qué fecha guardar una lectura del periodo 3 hecha los primeros días del mes.
     * Devuelve true si se usa la fecha sugerida (fin del mes que cierra), false si se mantiene la escrita
     * y null si se cancela.
     */
    private Boolean preguntarFechaCierre(LocalDate escrita, LocalDate sugerida) {
        Locale es = new Locale("es", "CO");
        String mesCierra = sugerida.getMonth().getDisplayName(TextStyle.FULL, es);
        String mesEscrito = escrita.getMonth().getDisplayName(TextStyle.FULL, es);

        ButtonType usar = new ButtonType("Usar " + sugerida.format(formatoFecha), ButtonBar.ButtonData.YES);
        ButtonType mantener = new ButtonType("Mantener " + escrita.format(formatoFecha), ButtonBar.ButtonData.NO);

        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Con la fecha " + sugerida.format(formatoFecha) + " el Total cuenta en " + mesCierra
                        + " (como en el Excel). Con la fecha " + escrita.format(formatoFecha)
                        + " cuenta en " + mesEscrito + ".",
                usar, mantener, ButtonType.CANCEL);
        a.setTitle("Fecha del cierre de mes");
        a.setHeaderText("Esta lectura cierra " + mesCierra + ". ¿Con qué fecha la guardo?");
        a.setGraphic(null);
        a.getDialogPane().getStylesheets().add(css());

        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() == ButtonType.CANCEL) {
            return null;
        }
        return r.get() == usar;
    }

    private void handleEditar(FilaContador fila) {
        Lectura l = fila.lectura();

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Editar lectura");
        dialogo.setHeaderText("Máquina " + l.getMaquina().getCodigo() + " · periodo " + l.getPeriodo());

        DatePicker dp = new DatePicker(l.getFecha());
        dp.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Integer> cbPeriodo = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3));
        cbPeriodo.setValue(l.getPeriodo());
        cbPeriodo.setMaxWidth(Double.MAX_VALUE);
        TextField tIn = campoNumero(l.getContadorIn());
        TextField tOut = campoNumero(l.getContadorOut());
        TextField tPrem = campoNumero(l.getContadorPrem());
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #ef4444;");
        lblError.setWrapText(true);

        VBox contenido = new VBox(8,
                new Label("Fecha:"), dp,
                new Label("Periodo:"), cbPeriodo,
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
                        leerEntero(tIn), leerEntero(tOut), leerEntero(tPrem), cbPeriodo.getValue());
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
            if (h.clase() == Clase.BASE) {
                // La base nunca muestra Total: solo explica contra qué se calculó la fila siguiente
                return new SimpleStringProperty("");
            }
            return dato(h, "Total", f -> f.total() == null && Lectura.REINICIO.equals(f.lectura().getTipo())
                    ? "Reinicio" : dinero(f.total()));
        });
        ajustarColumnas();

        for (TableColumn<FilaHist, String> c : List.of(colDif, colTotal)) {
            celdaCifra(c);
        }
        for (TableColumn<FilaHist, String> c : List.of(colDen, colMaquina)) {
            celdaCentrada(c);
        }
        // Doble clic para editar en la misma celda. campo: 0 fecha, 1 In, 2 Out, 3 Prem, 4 periodo
        celdaEditable(colFecha, 0, false);
        celdaEditable(colPeriodo, 4, false);
        celdaEditable(colIn, 1, true);
        celdaEditable(colOut, 2, true);
        celdaEditable(colPrem, 3, true);

        tabla.setRowFactory(tv -> {
            TableRow<FilaHist> fila = new TableRow<>() {
                @Override
                protected void updateItem(FilaHist h, boolean empty) {
                    super.updateItem(h, empty);
                    setTooltip(null);
                    if (empty || h == null || h.clase() == Clase.LECTURA) {
                        setStyle("");
                    } else if (h.clase() == Clase.BASE) {
                        setStyle("-fx-background-color: #f1f5f9; -fx-text-background-color: #94a3b8;"
                                + " -fx-text-fill: #94a3b8; -fx-font-style: italic;");
                        setTooltip(new Tooltip("Lectura base: el primer Total del mes se calculó contra esta fila. "
                                + "No suma en el total."));
                    } else if (h.clase() == Clase.TOTAL) {
                        setStyle("-fx-background-color: #fef9c3; -fx-font-weight: bold;");
                    } else if (h.clase() == Clase.ENCABEZADO) {
                        setStyle("-fx-background-color: #e8eef9; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: transparent;");
                    }
                }
            };

            // Clic derecho solo sobre filas de lectura (no sobre base, totales ni espacios)
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

    /**
     * Texto de una celda: el valor en lecturas y en la base gris, el título en encabezados
     * y vacío en totales y espacios.
     */
    private SimpleStringProperty dato(FilaHist h, String titulo, Function<FilaContador, String> extractor) {
        return switch (h.clase()) {
            case LECTURA, BASE -> new SimpleStringProperty(extractor.apply(h.fila()));
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

    /** Columnas centradas (Den, Máquina): los títulos usan el estilo del encabezado. */
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

    /**
     * Celda editable con doble clic: Enter guarda, Esc cancela.
     * campo: 0 fecha, 1 In, 2 Out, 3 Prem, 4 periodo.
     */
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
                        : campo == 4 ? String.valueOf(l.getPeriodo())
                        : miles.format(campo == 1 ? l.getContadorIn()
                                : campo == 2 ? l.getContadorOut() : l.getContadorPrem());

                TextField campoEdicion = new TextField(inicial);
                if (campo == 4) {
                    soloDigitos(campoEdicion, 1);
                } else if (campo != 0) {
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

    /** Guarda el cambio de una celda con la misma regla y auditoría de "Editar lectura". campo 4 = periodo. */
    private void guardarCelda(FilaContador fila, int campo, String texto) {
        Lectura l = fila.lectura();
        try {
            LocalDate fecha = l.getFecha();
            Long in = l.getContadorIn();
            Long out = l.getContadorOut();
            Long prem = l.getContadorPrem();
            Integer periodo = null; // null = no cambia el periodo

            if (campo == 0) {
                fecha = LocalDate.parse(texto.trim(),
                        DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT));
            } else {
                String limpio = texto == null ? "" : texto.replaceAll("[^0-9]", "");
                if (limpio.isEmpty()) {
                    mostrarEstado(campo == 4 ? "El periodo no puede quedar vacío"
                            : "El contador no puede quedar vacío", true);
                    return;
                }
                long valor = Long.parseLong(limpio);
                if (campo == 4) {
                    periodo = (int) valor;
                } else if (campo == 1) {
                    in = valor;
                } else if (campo == 2) {
                    out = valor;
                } else {
                    prem = valor;
                }
            }

            lecturaService.editar(l.getId(), fecha, in, out, prem, periodo);
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
        campo.setTextFormatter(new TextFormatter<String>(cambio -> {
            if (!cambio.isContentChange()) {
                return cambio;
            }
            String nuevo = cambio.getControlNewText();
            String limpio = nuevo.replaceAll("[^0-9]", "");
            if (limpio.length() > maxDigitos) {
                limpio = limpio.substring(0, maxDigitos);
            }
            String formateado = limpio.isEmpty() ? "" : miles.format(Long.parseLong(limpio));
            cambio.setRange(0, cambio.getControlText().length());
            cambio.setText(formateado);
            cambio.selectRange(formateado.length(), formateado.length());
            return cambio;
        }));
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
        // El aviso del periodo 3 es largo: que baje de línea en vez de cortarse
        lblEstado.setWrapText(true);
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