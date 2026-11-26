package com.misgastos.controller;

import com.misgastos.model.Empleado;
import com.misgastos.service.EmpleadoService;
import com.misgastos.model.Turno;
import com.misgastos.service.TurnoService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import com.misgastos.model.Festivo;
import com.misgastos.model.Novedad;
import com.misgastos.model.TipoNovedad;
import com.misgastos.service.NovedadService;
import java.time.YearMonth;
import javafx.scene.control.Label;
import com.misgastos.service.FestivoService;
import com.misgastos.service.HorarioEspecialService;
import com.misgastos.util.FestivosColombia;
import javafx.scene.control.ComboBox;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.misgastos.model.HorarioEspecial;
import com.misgastos.model.TipoDiaHorario;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Pantalla Turnos. Por ahora solo la pestaña Empleados. */
@Slf4j
@Component
public class GestionTurnosController {

    @FXML private TableView<Empleado> tablaEmpleados;
    @FXML private TableColumn<Empleado, String> colEmpNombre;
    @FXML private TableColumn<Empleado, String> colEmpRol;
    @FXML private TableColumn<Empleado, String> colEmpIngreso;
    @FXML private TableColumn<Empleado, String> colEmpRetiro;
    @FXML private TableColumn<Empleado, String> colEmpEstado;
    @FXML private TableColumn<Empleado, Void> colEmpAcciones;
    @FXML private TableView<Turno> tablaTurnos;
    @FXML private TableColumn<Turno, String> colTurOrden;
    @FXML private TableColumn<Turno, String> colTurNombre;
    @FXML private TableColumn<Turno, String> colTurEtiqueta;
    @FXML private TableColumn<Turno, String> colTurHorario;
    @FXML private TableColumn<Turno, String> colTurCupos;
    @FXML private TableColumn<Turno, String> colTurEstado;
    @FXML private TableColumn<Turno, Void> colTurAcciones;
    @FXML private TableColumn<Turno, String> colTurSabado;
    @FXML private TableColumn<Turno, String> colTurDomingo;
    @FXML private ComboBox<Integer> cmbAnioFestivos;
    @FXML private TableView<Festivo> tablaFestivos;
    @FXML private TableColumn<Festivo, String> colFesFecha;
    @FXML private TableColumn<Festivo, String> colFesDia;
    @FXML private TableColumn<Festivo, String> colFesNombre;
    @FXML private TableColumn<Festivo, Void> colFesAcciones;
    @FXML private CalendarioTurnosController calendarioController;
    @FXML private Tab tabCalendario;
    @FXML private HBox boxBotonesCalendario;
    @FXML private TableView<Novedad> tablaNovedades;
    @FXML private TableColumn<Novedad, String> colNovEmpleado;
    @FXML private TableColumn<Novedad, String> colNovTipo;
    @FXML private TableColumn<Novedad, String> colNovDesde;
    @FXML private TableColumn<Novedad, String> colNovHasta;
    @FXML private TableColumn<Novedad, String> colNovDias;
    @FXML private TableColumn<Novedad, String> colNovSegunda;
    @FXML private TableColumn<Novedad, String> colNovMotivo;
    @FXML private TableColumn<Novedad, String> colNovRegistrado;
    @FXML private TableColumn<Novedad, Void> colNovAcciones;
    @FXML private Label lblMesNovedades;
    
    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private TurnoService turnoService;
    

    @Autowired
    private HorarioEspecialService horarioEspecialService;
    
    @Autowired
    private FestivoService festivoService;
    

    @Autowired
    private NovedadService novedadService;
    
    @Autowired
    private ApplicationContext springContext;

    private final ObservableList<Empleado> empleados = FXCollections.observableArrayList();
    private final ObservableList<Turno> turnos = FXCollections.observableArrayList();
    /** Horarios especiales para las columnas de la tabla. Clave: "idTurno|TIPO_DIA". */
    private final Map<String, String> horariosEspeciales = new HashMap<>();
    private final ObservableList<Festivo> festivos = FXCollections.observableArrayList();
    private final ObservableList<Novedad> novedades = FXCollections.observableArrayList();
    private YearMonth mesNovedades = YearMonth.now();
    private final DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm");
    
    @FXML
    public void initialize() {
        log.debug("Inicializando pantalla de turnos");
        configurarTablaEmpleados();
        cargarEmpleados();
        configurarTablaTurnos();
        cargarTurnos();
        configurarTablaFestivos();
        configurarFiltroAnio();
        configurarTablaNovedades();
        cargarNovedades();
        // Los botones del calendario solo se ven cuando la pestaña Calendario está activa
        boxBotonesCalendario.visibleProperty().bind(tabCalendario.selectedProperty());
        boxBotonesCalendario.managedProperty().bind(boxBotonesCalendario.visibleProperty());
    }

    private void configurarTablaEmpleados() {
        colEmpNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colEmpRol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isEsEncargada() ? "Encargada" : "Rotativo"));
        colEmpIngreso.setCellValueFactory(c -> new SimpleStringProperty(fecha(c.getValue().getFechaIngreso())));
        colEmpRetiro.setCellValueFactory(c -> new SimpleStringProperty(fecha(c.getValue().getFechaRetiro())));
        colEmpEstado.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFechaRetiro() == null ? "Activo" : "Retirado"));
        // Columna Acciones: selecciona la fila y reutiliza los handlers de siempre
        colEmpAcciones.setCellFactory(column -> new TableCell<Empleado, Void>() {
            private final Button btnEditar = new Button("✏ Editar");
            private final Button btnEliminar = new Button("🗑");
            private final HBox contenedor = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.getStyleClass().add("secondary-button");
                btnEditar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");

                btnEliminar.getStyleClass().add("danger-button");
                btnEliminar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; "
                        + "-fx-background-color: #ef4444; -fx-text-fill: white;");

                btnEditar.setOnAction(e -> {
                    tablaEmpleados.getSelectionModel().select(getIndex());
                    handleEditarEmpleado();
                });
                btnEliminar.setOnAction(e -> {
                    tablaEmpleados.getSelectionModel().select(getIndex());
                    handleEliminarEmpleado();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : contenedor);
            }
        });

        // Clic derecho sobre la fila (mismo patrón que Máquinas)
        tablaEmpleados.setRowFactory(tv -> {
            TableRow<Empleado> fila = new TableRow<>();
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    tablaEmpleados.getSelectionModel().select(fila.getItem());
                    handleEditarEmpleado();
                }
            });
            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    tablaEmpleados.getSelectionModel().select(fila.getItem());
                    handleEliminarEmpleado();
                }
            });
            ContextMenu menu = new ContextMenu(editar, eliminar);
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return fila;
        });
        tablaEmpleados.setItems(empleados);
        tablaEmpleados.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                handleEditarEmpleado();
            }
        });
    }

    private void cargarEmpleados() {
        try {
            empleados.setAll(empleadoService.listarTodos());
            log.debug("Empleados cargados: {}", empleados.size());
        } catch (Exception e) {
            log.error("No se pudieron cargar los empleados", e);
            mostrarError("Error al cargar", e.getMessage());
        }
    }

    @FXML
    private void handleNuevoEmpleado() {
        abrirModalEmpleado(null);
    }

    @FXML
    private void handleEditarEmpleado() {
        Empleado seleccionado = tablaEmpleados.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAviso("Seleccione un empleado de la tabla");
            return;
        }
        abrirModalEmpleado(seleccionado);
    }

    @FXML
    private void handleEliminarEmpleado() {
        Empleado seleccionado = tablaEmpleados.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAviso("Seleccione un empleado de la tabla");
            return;
        }
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar a " + seleccionado.getNombre() + "?\nSolo se puede si no tiene turnos, "
                        + "descansos ni novedades. Si ya trabajó, edítelo y ponga su fecha de retiro.",
                ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText(null);
        Optional<ButtonType> respuesta = confirmar.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.YES) {
            return;
        }
        try {
            empleadoService.eliminar(seleccionado.getId());
            cargarEmpleados();
        } catch (Exception e) {
            log.error("No se pudo eliminar el empleado {}", seleccionado.getId(), e);
            mostrarError("No se pudo eliminar", e.getMessage());
        }
    }

    @FXML
    private void handleActualizarEmpleados() {
        cargarEmpleados();
    }

    private void abrirModalEmpleado(Empleado empleado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-empleado.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            ModalEmpleadoController controller = loader.getController();
            controller.setEmpleado(empleado);
            controller.setOnGuardarCallback(this::cargarEmpleados);

            Stage stage = new Stage();
            stage.setTitle(empleado == null ? "Nuevo empleado" : "Editar empleado");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();
        } catch (Exception e) {
            log.error("No se pudo abrir el modal de empleado", e);
            mostrarError("Error al abrir la ventana", e.getMessage());
        }
    }

    private String fecha(LocalDate f) {
        return f == null ? "" : f.format(formatoFecha);
    }
    
    // ====================== FESTIVOS ======================

    private void configurarTablaFestivos() {
        colFesFecha.setCellValueFactory(c -> new SimpleStringProperty(fecha(c.getValue().getFecha())));
        colFesDia.setCellValueFactory(c -> new SimpleStringProperty(diaSemana(c.getValue().getFecha())));
        colFesNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));

        colFesAcciones.setCellFactory(column -> new TableCell<Festivo, Void>() {
            private final Button btnEditar = new Button("✏ Editar");
            private final Button btnEliminar = new Button("🗑");
            private final HBox contenedor = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.getStyleClass().add("secondary-button");
                btnEditar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");

                btnEliminar.getStyleClass().add("danger-button");
                btnEliminar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; "
                        + "-fx-background-color: #ef4444; -fx-text-fill: white;");

                btnEditar.setOnAction(e -> abrirModalFestivo(getTableView().getItems().get(getIndex())));
                btnEliminar.setOnAction(e -> eliminarFestivo(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : contenedor);
            }
        });

        tablaFestivos.setRowFactory(tv -> {
            TableRow<Festivo> fila = new TableRow<>();
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    abrirModalFestivo(fila.getItem());
                }
            });
            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    eliminarFestivo(fila.getItem());
                }
            });
            ContextMenu menu = new ContextMenu(editar, eliminar);
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && fila.getItem() != null) {
                    abrirModalFestivo(fila.getItem());
                }
            });
            return fila;
        });

        tablaFestivos.setItems(festivos);
    }

    private void configurarFiltroAnio() {
        int actual = LocalDate.now().getYear();
        cmbAnioFestivos.setItems(FXCollections.observableArrayList(
                actual - 1, actual, actual + 1, actual + 2));
        cmbAnioFestivos.valueProperty().addListener((o, a, b) -> cargarFestivos());
        cmbAnioFestivos.setValue(actual);
    }

    private void cargarFestivos() {
        Integer anio = cmbAnioFestivos.getValue();
        if (anio == null) {
            return;
        }
        try {
            festivos.setAll(festivoService.listarPorAnio(anio));
            log.debug("Festivos cargados: {}", festivos.size());
        } catch (Exception e) {
            log.error("No se pudieron cargar los festivos", e);
            mostrarError("Error al cargar", e.getMessage());
        }
    }

    @FXML
    private void handleNuevoFestivo() {
        abrirModalFestivo(null);
    }

    @FXML
    private void handleActualizarFestivos() {
        cargarFestivos();
    }

    /** Propone los festivos de Colombia del año elegido y guarda solo los que faltan. */
    @FXML
    private void handleGenerarFestivos() {
        Integer anio = cmbAnioFestivos.getValue();
        if (anio == null) {
            mostrarAviso("Elija el año");
            return;
        }
        try {
            Set<LocalDate> existentes = festivoService.listarPorAnio(anio).stream()
                    .map(Festivo::getFecha)
                    .collect(Collectors.toSet());
            List<FestivosColombia.FestivoCalculado> faltantes = FestivosColombia.deAnio(anio).stream()
                    .filter(f -> !existentes.contains(f.fecha()))
                    .toList();

            if (faltantes.isEmpty()) {
                mostrarAviso("Los festivos de " + anio + " ya están todos cargados.");
                return;
            }

            StringBuilder texto = new StringBuilder("Se van a agregar " + faltantes.size()
                    + " festivos de " + anio + ":\n\n");
            for (FestivosColombia.FestivoCalculado f : faltantes) {
                texto.append(fecha(f.fecha())).append("  ")
                        .append(diaSemana(f.fecha())).append("  -  ")
                        .append(f.nombre()).append("\n");
            }
            texto.append("\nSon los que da la ley. Confírmelos con quien lleva la nómina. "
                    + "Después puede editar o borrar cualquiera.");

            Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, texto.toString(),
                    ButtonType.YES, ButtonType.NO);
            confirmar.setHeaderText(null);
            confirmar.setTitle("Generar festivos");
            Optional<ButtonType> respuesta = confirmar.showAndWait();
            if (respuesta.isEmpty() || respuesta.get() != ButtonType.YES) {
                return;
            }

            for (FestivosColombia.FestivoCalculado f : faltantes) {
                festivoService.crear(f.fecha(), f.nombre());
            }
            log.info("Festivos generados: {}", faltantes.size());
            cargarFestivos();
        } catch (Exception e) {
            log.error("No se pudieron generar los festivos", e);
            mostrarError("No se pudieron generar", e.getMessage());
            cargarFestivos();
        }
    }

    private void eliminarFestivo(Festivo festivo) {
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar el festivo " + festivo.getNombre() + " (" + fecha(festivo.getFecha()) + ")?",
                ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText(null);
        Optional<ButtonType> respuesta = confirmar.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.YES) {
            return;
        }
        try {
            festivoService.eliminar(festivo.getId());
            cargarFestivos();
        } catch (Exception e) {
            log.error("No se pudo eliminar el festivo {}", festivo.getId(), e);
            mostrarError("No se pudo eliminar", e.getMessage());
        }
    }

    private void abrirModalFestivo(Festivo festivo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-festivo.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            ModalFestivoController controller = loader.getController();
            controller.setFestivo(festivo);
            controller.setOnGuardarCallback(this::cargarFestivos);

            Stage stage = new Stage();
            stage.setTitle(festivo == null ? "Nuevo festivo" : "Editar festivo");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();
        } catch (Exception e) {
            log.error("No se pudo abrir el modal de festivo", e);
            mostrarError("Error al abrir la ventana", e.getMessage());
        }
    }

    /** Nombre del día en español con la primera letra en mayúscula: "Lunes". */
    private String diaSemana(LocalDate f) {
        String dia = f.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "CO"));
        return dia.substring(0, 1).toUpperCase() + dia.substring(1);
    }
    
    // ====================== HORARIOS (TURNOS) ======================

    private void configurarTablaTurnos() {
        colTurOrden.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOrden())));
        colTurNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colTurEtiqueta.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEtiqueta()));
        colTurHorario.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHoraInicio().format(formatoHora) + " – "
                        + c.getValue().getHoraFin().format(formatoHora)));
        colTurCupos.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getCupos())));
        colTurEstado.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActivo() ? "Activo" : "Inactivo"));

        colTurSabado.setCellValueFactory(c ->
        new SimpleStringProperty(textoEspecial(c.getValue(), TipoDiaHorario.SABADO)));
colTurDomingo.setCellValueFactory(c ->
        new SimpleStringProperty(textoEspecial(c.getValue(), TipoDiaHorario.DOMINGO_FESTIVO)));
        colTurAcciones.setCellFactory(column -> new TableCell<Turno, Void>() {
            private final Button btnEditar = new Button("✏ Editar");
            private final Button btnEliminar = new Button("🗑");
            private final HBox contenedor = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.getStyleClass().add("secondary-button");
                btnEditar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");

                btnEliminar.getStyleClass().add("danger-button");
                btnEliminar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; "
                        + "-fx-background-color: #ef4444; -fx-text-fill: white;");

                btnEditar.setOnAction(e -> abrirModalTurno(getTableView().getItems().get(getIndex())));
                btnEliminar.setOnAction(e -> eliminarTurno(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : contenedor);
            }
        });

        tablaTurnos.setRowFactory(tv -> {
            TableRow<Turno> fila = new TableRow<>();
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    abrirModalTurno(fila.getItem());
                }
            });
            MenuItem alternar = new MenuItem("Desactivar");
            alternar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    alternarActivoTurno(fila.getItem());
                }
            });
            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    eliminarTurno(fila.getItem());
                }
            });
            ContextMenu menu = new ContextMenu(editar, alternar, eliminar);
            menu.setOnShowing(e -> {
                if (fila.getItem() != null) {
                    alternar.setText(fila.getItem().isActivo() ? "Desactivar" : "Reactivar");
                }
            });
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && fila.getItem() != null) {
                    abrirModalTurno(fila.getItem());
                }
            });
            return fila;
        });

        tablaTurnos.setItems(turnos);
    }
    
    /** Carga los horarios especiales para mostrarlos en las columnas Sábado y Dom. o festivo. */
    private void cargarHorariosEspeciales() {
        horariosEspeciales.clear();
        for (HorarioEspecial h : horarioEspecialService.listarTodos()) {
            horariosEspeciales.put(h.getTurnoId() + "|" + h.getTipoDia(),
                    h.getHoraInicio().format(formatoHora) + " – " + h.getHoraFin().format(formatoHora));
        }
    }

    /** Horario especial de un turno para un tipo de día, o un guion si usa el horario base. */
    private String textoEspecial(Turno turno, TipoDiaHorario tipo) {
        return horariosEspeciales.getOrDefault(turno.getId() + "|" + tipo.name(), "—");
    }

    private void cargarTurnos() {
        try {
            cargarHorariosEspeciales();
            turnos.setAll(turnoService.listarTodos());
            log.debug("Turnos cargados: {}", turnos.size());
        } catch (Exception e) {
            log.error("No se pudieron cargar los turnos", e);
            mostrarError("Error al cargar", e.getMessage());
        }
    }

    @FXML
    private void handleNuevoTurno() {
        abrirModalTurno(null);
    }

    @FXML
    private void handleActualizarTurnos() {
        cargarTurnos();
    }

    private void eliminarTurno(Turno turno) {
        // Si ya se usó en la programación, no se puede borrar: se ofrece desactivar
        if (turnoService.tieneHistorial(turno.getId())) {
            if (!turno.isActivo()) {
                mostrarAviso(turno.getNombre() + " ya tiene días programados y no se puede eliminar. "
                        + "Ya está inactivo.");
                return;
            }
            Alert desactivar = new Alert(Alert.AlertType.CONFIRMATION,
                    turno.getNombre() + " ya tiene días programados, así que no se puede eliminar.\n"
                            + "¿Desactivarlo? Deja de ofrecerse, pero el historial se conserva.",
                    ButtonType.YES, ButtonType.NO);
            desactivar.setHeaderText(null);
            Optional<ButtonType> r = desactivar.showAndWait();
            if (r.isPresent() && r.get() == ButtonType.YES) {
                alternarActivoTurno(turno);
            }
            return;
        }
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar el turno " + turno.getNombre() + "?", ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText(null);
        Optional<ButtonType> respuesta = confirmar.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.YES) {
            return;
        }
        try {
            turnoService.eliminar(turno.getId());
            cargarTurnos();
        } catch (Exception e) {
            log.error("No se pudo eliminar el turno {}", turno.getId(), e);
            mostrarError("No se pudo eliminar", e.getMessage());
        }
    }

    private void alternarActivoTurno(Turno turno) {
        try {
            if (turno.isActivo()) {
                turnoService.desactivar(turno.getId());
            } else {
                turnoService.reactivar(turno.getId());
            }
            cargarTurnos();
        } catch (Exception e) {
            log.error("No se pudo cambiar el estado del turno {}", turno.getId(), e);
            mostrarError("No se pudo cambiar el estado", e.getMessage());
        }
    }

    private void abrirModalTurno(Turno turno) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-turno.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            ModalTurnoController controller = loader.getController();
            controller.setTurno(turno);
            controller.setOnGuardarCallback(this::cargarTurnos);

            Stage stage = new Stage();
            stage.setTitle(turno == null ? "Nuevo turno" : "Editar turno");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();
        } catch (Exception e) {
            log.error("No se pudo abrir el modal de turno", e);
            mostrarError("Error al abrir la ventana", e.getMessage());
        }
    }

    private void mostrarAviso(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje == null ? "Error inesperado" : mensaje);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
    

    // ====================== NOVEDADES ======================

    private void configurarTablaNovedades() {
        colNovEmpleado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmpleado().getNombre()));
        colNovTipo.setCellValueFactory(c -> {
            TipoNovedad t = TipoNovedad.deNombre(c.getValue().getTipo());
            return new SimpleStringProperty(t == null ? c.getValue().getTipo() : t.getEtiqueta());
        });
        colNovDesde.setCellValueFactory(c -> new SimpleStringProperty(fecha(c.getValue().getFechaDesde())));
        colNovHasta.setCellValueFactory(c -> new SimpleStringProperty(fecha(c.getValue().getFechaHasta())));
        colNovDias.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(NovedadService.dias(c.getValue()))));
        colNovSegunda.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEmpleado2() == null ? "—" : c.getValue().getEmpleado2().getNombre()));
        colNovMotivo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getMotivo() == null ? "" : c.getValue().getMotivo()));
        colNovRegistrado.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRegistradoPor() == null ? "" : c.getValue().getRegistradoPor().getUsername()));

        colNovAcciones.setCellFactory(column -> new TableCell<Novedad, Void>() {
            private final Button btnEditar = new Button("✏ Editar");
            private final Button btnEliminar = new Button("🗑");
            private final HBox contenedor = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.getStyleClass().add("secondary-button");
                btnEditar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");

                btnEliminar.getStyleClass().add("danger-button");
                btnEliminar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; "
                        + "-fx-background-color: #ef4444; -fx-text-fill: white;");

                btnEditar.setOnAction(e -> abrirModalNovedad(getTableView().getItems().get(getIndex())));
                btnEliminar.setOnAction(e -> eliminarNovedad(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : contenedor);
            }
        });

        tablaNovedades.setRowFactory(tv -> {
            TableRow<Novedad> fila = new TableRow<>();
            MenuItem editar = new MenuItem("Editar");
            editar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    abrirModalNovedad(fila.getItem());
                }
            });
            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> {
                if (fila.getItem() != null) {
                    eliminarNovedad(fila.getItem());
                }
            });
            ContextMenu menu = new ContextMenu(editar, eliminar);
            fila.contextMenuProperty().bind(
                    Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && fila.getItem() != null) {
                    abrirModalNovedad(fila.getItem());
                }
            });
            return fila;
        });

        tablaNovedades.setItems(novedades);
    }

    private void cargarNovedades() {
        String mes = mesNovedades.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "CO"));
        lblMesNovedades.setText(mes.substring(0, 1).toUpperCase() + mes.substring(1) + " " + mesNovedades.getYear());
        try {
            novedades.setAll(novedadService.listarEntre(mesNovedades.atDay(1), mesNovedades.atEndOfMonth()));
            log.debug("Novedades cargadas: {}", novedades.size());
        } catch (Exception e) {
            log.error("No se pudieron cargar las novedades", e);
            mostrarError("Error al cargar", e.getMessage());
        }
    }

    @FXML
    private void handleNovAnterior() {
        mesNovedades = mesNovedades.minusMonths(1);
        cargarNovedades();
    }

    @FXML
    private void handleNovSiguiente() {
        mesNovedades = mesNovedades.plusMonths(1);
        cargarNovedades();
    }

    @FXML
    private void handleNovEsteMes() {
        mesNovedades = YearMonth.now();
        cargarNovedades();
    }

    @FXML
    private void handleNovActualizar() {
        cargarNovedades();
    }

    @FXML
    private void handleNuevaNovedad() {
        abrirModalNovedad(null);
    }

    private void eliminarNovedad(Novedad novedad) {
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar esta novedad?\n\n" + novedadService.texto(novedad),
                ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText(null);
        Optional<ButtonType> respuesta = confirmar.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.YES) {
            return;
        }
        try {
            novedadService.eliminar(novedad.getId());
            cargarNovedades();
        } catch (Exception e) {
            log.error("No se pudo eliminar la novedad {}", novedad.getId(), e);
            mostrarError("No se pudo eliminar", e.getMessage());
        }
    }

    private void abrirModalNovedad(Novedad novedad) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-novedad.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            ModalNovedadController controller = loader.getController();
            controller.setNovedad(novedad);
            controller.setOnGuardarCallback(this::cargarNovedades);

            Stage stage = new Stage();
            stage.setTitle(novedad == null ? "Nueva novedad" : "Editar novedad");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();
        } catch (Exception e) {
            log.error("No se pudo abrir el modal de novedad", e);
            mostrarError("Error al abrir la ventana", e.getMessage());
        }
    }
    
    @FXML private void handleCalAnterior() { calendarioController.handleAnterior(); }
    @FXML private void handleCalEsteMes() { calendarioController.handleEsteMes(); }
    @FXML private void handleCalSiguiente() { calendarioController.handleSiguiente(); }
    @FXML private void handleCalActualizar() { calendarioController.handleActualizar(); }
    @FXML private void handleCalIngresarSemana() { calendarioController.handleIngresarSemana(); }
    
}