package com.misgastos.controller;

import com.misgastos.model.Empleado;
import com.misgastos.model.Turno;
import com.misgastos.service.EmpleadoService;
import com.misgastos.service.ProgramacionService;
import com.misgastos.service.ProgramacionService.Asignacion;
import com.misgastos.service.ProgramacionService.Aviso;
import com.misgastos.service.ProgramacionService.DiaProgramado;
import com.misgastos.service.ProgramacionService.PropuestaSemana;
import com.misgastos.service.TurnoService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.misgastos.model.Novedad;
import com.misgastos.service.NovedadService;

/**
 * Ingreso rápido de una semana: una fila por empleado y una columna por día.
 * Cada celda es un chip (1, 2, ½, Des o vacía) que se pinta con una brocha o se
 * cambia en orden. Nada se guarda hasta pulsar Guardar semana.
 */
@Slf4j
@Component
public class ModalSemanaController {

	/**
	 * Valor de una celda de descanso. Vacío es null, y un turno es su id (siempre
	 * mayor que 0).
	 */
	private static final Long DESCANSO = 0L;
	/** Etiqueta del medio turno (½), la misma que usa el modal de turnos. */
	private static final String ETIQUETA_MEDIO = "\u00BD";
	private static final Locale ES = new Locale("es", "CO");
	private static final String[] NOMBRES_DIA = { "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };

	// Colores {fondo, texto}, los mismos del calendario
	private static final String[] AZUL = { "#DCE7FF", "#1D3FA8" };
	private static final String[] VERDE = { "#D5F1EB", "#0B6B5C" };
	private static final String[] NARANJA = { "#FFE6C7", "#8A4B08" };
	private static final String[] MORADO = { "#EDE9FE", "#5B21B6" };
	private static final String[] GRIS = { "#ECEEF4", "#5B6378" };
	private static final String ROJO = "#BE123C";

	@FXML
	private Label lblTitulo;
	@FXML
	private Label lblSubtitulo;
	@FXML
	private HBox boxBrochas;
	@FXML
	private GridPane gridSemana;
	@FXML
	private Label lblError;

	@Autowired
	private ProgramacionService programacionService;

	@Autowired
	private EmpleadoService empleadoService;

	@Autowired
	private TurnoService turnoService;

	@Autowired
	private NovedadService novedadService;

	private LocalDate lunes;
	private List<Turno> turnosActivos = new ArrayList<>();
	private Map<Long, Turno> turnosPorId = new HashMap<>();
	private Map<Long, String[]> colores = new HashMap<>();
	private List<Empleado> filas = new ArrayList<>();
	/** Celdas actuales: id del empleado -> 7 valores (lunes a domingo). */
	private Map<Long, Long[]> celdas = new LinkedHashMap<>();
	/** Cómo está guardada la semana hoy, para saber si hay cambios sin guardar. */
	private Map<Long, Long[]> original = new HashMap<>();
	private Map<LocalDate, String> festivos = new HashMap<>();
	private Button[][] botones = new Button[0][0];
	private final Label[] controles = new Label[7];
	private ToggleGroup grupoBrocha = new ToggleGroup();
	private String estadoTexto = "";
	/** Novedades de la semana. Clave: "fecha|empleadoId". */
	private Map<String, List<Novedad>> novedadesSemana = new HashMap<>();

	/** Abre la semana a la que pertenece la fecha. */
	public void setSemana(LocalDate fecha) {
		ocultarError();
		try {
			this.lunes = ProgramacionService.lunesDe(fecha);
			cargarTurnos();
			construirBrochas();
			cargarSemanaActual();
		} catch (Exception e) {
			log.error("No se pudo abrir la semana", e);
			mostrarError(e.getMessage());
		}
	}

	// ====================== CARGA ======================

	private void cargarTurnos() {
		turnosPorId = new HashMap<>();
		colores = new HashMap<>();
		int indice = 0;
		for (Turno t : turnoService.listarTodos()) {
			turnosPorId.put(t.getId(), t);
			if (ETIQUETA_MEDIO.equals(t.getEtiqueta())) {
				colores.put(t.getId(), NARANJA);
			} else {
				colores.put(t.getId(), indice == 0 ? AZUL : indice == 1 ? VERDE : MORADO);
				indice++;
			}
		}
		turnosActivos = turnoService.listarActivos().stream().sorted(Comparator.comparingInt(Turno::getOrden))
				.collect(Collectors.toList());
	}

	private void construirBrochas() {
		boxBrochas.getChildren().clear();
		grupoBrocha = new ToggleGroup();
		for (Turno t : turnosActivos) {
			ToggleButton b = new ToggleButton(t.getEtiqueta() + " " + t.getNombre());
			b.setUserData(t.getId());
			estiloBrocha(b, colores.getOrDefault(t.getId(), AZUL));
			b.setToggleGroup(grupoBrocha);
			boxBrochas.getChildren().add(b);
		}
		ToggleButton descanso = new ToggleButton("D Descanso");
		descanso.setUserData(DESCANSO);
		estiloBrocha(descanso, GRIS);
		descanso.setToggleGroup(grupoBrocha);
		boxBrochas.getChildren().add(descanso);

		// Como en la foto, arranca con el primer turno elegido
		if (!grupoBrocha.getToggles().isEmpty()) {
			grupoBrocha.selectToggle(grupoBrocha.getToggles().get(0));
		}
	}

	private void estiloBrocha(ToggleButton b, String[] color) {
		Runnable aplicar = () -> b.setStyle("-fx-background-color: " + color[0] + "; -fx-text-fill: " + color[1]
				+ "; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 8 14; -fx-cursor: hand;"
				+ (b.isSelected() ? " -fx-border-color: #3730A3; -fx-border-width: 2; -fx-border-radius: 10;" : ""));
		b.selectedProperty().addListener((o, a, nuevo) -> aplicar.run());
		aplicar.run();
	}

	/**
	 * Carga la semana: la guardada, o la anterior copiada si está vacía (todavía
	 * sin guardar).
	 */
	private void cargarSemanaActual() {
		festivos = new HashMap<>();
		for (DiaProgramado d : programacionService.leerSemana(lunes)) {
			if (d.esFestivo()) {
				festivos.put(d.fecha(), d.nombreFestivo());
			}
		}
		novedadesSemana = programacionService.novedadesDeSemana(lunes);

		PropuestaSemana propuesta = programacionService.proponerSemana(lunes);
		aplicar(propuesta.asignaciones());

		if (propuesta.copiadaDeAnterior()) {
			// En la base esta semana está vacía: lo copiado cuenta como cambio sin guardar
			original = copiaVacia();
			estadoTexto = "Viene copiada de la semana anterior, solo cambia lo que haga falta.";
		} else if (propuesta.asignaciones().isEmpty()) {
			original = copiarCeldas();
			estadoTexto = "Semana sin programar: arme la semana con la brocha o copie la anterior.";
		} else {
			original = copiarCeldas();
			estadoTexto = "Semana ya guardada.";
		}
		refrescar();
		log.debug("Semana del {} cargada en el ingreso rápido", lunes);
	}

	/**
	 * Llena las celdas con las asignaciones, decide las filas y dibuja la
	 * cuadrícula.
	 */
	private void aplicar(List<Asignacion> asignaciones) {
		celdas = new LinkedHashMap<>();
		for (Asignacion a : asignaciones) {
			int dia = (int) ChronoUnit.DAYS.between(lunes, a.fecha());
			if (dia < 0 || dia > 6) {
				continue;
			}
			Long[] fila = celdas.computeIfAbsent(a.empleadoId(), k -> new Long[7]);
			fila[dia] = a.esDescanso() ? DESCANSO : a.turnoId();
		}

		// Filas: vigentes algún día de la semana, o con celda copiada (aunque ya no
		// estén vigentes)
		filas = empleadoService.listarTodos().stream().filter(e -> celdas.containsKey(e.getId()) || vigenteAlgunDia(e))
				.sorted(Comparator.comparing((Empleado e) -> !e.isEsEncargada()).thenComparing(Empleado::getNombre,
						String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());
		for (Empleado e : filas) {
			celdas.putIfAbsent(e.getId(), new Long[7]);
		}
		dibujarCuadricula();
	}

	private boolean vigenteAlgunDia(Empleado e) {
		for (int i = 0; i < 7; i++) {
			if (e.vigenteEn(lunes.plusDays(i))) {
				return true;
			}
		}
		return false;
	}

	// ====================== DIBUJO ======================

	private void dibujarCuadricula() {
		gridSemana.getChildren().clear();
		gridSemana.getColumnConstraints().clear();

		ColumnConstraints columnaNombre = new ColumnConstraints();
		columnaNombre.setMinWidth(110);
		columnaNombre.setPrefWidth(130);
		gridSemana.getColumnConstraints().add(columnaNombre);
		for (int i = 0; i < 7; i++) {
			ColumnConstraints c = new ColumnConstraints();
			c.setMinWidth(100);
			c.setHgrow(Priority.ALWAYS);
			gridSemana.getColumnConstraints().add(c);
		}

		for (int d = 0; d < 7; d++) {
			LocalDate fecha = lunes.plusDays(d);
			boolean especial = fecha.getDayOfWeek() == DayOfWeek.SUNDAY || festivos.containsKey(fecha);
			Label encabezado = new Label(NOMBRES_DIA[d] + " " + fecha.getDayOfMonth());
			encabezado.setMaxWidth(Double.MAX_VALUE);
			encabezado.setAlignment(Pos.CENTER);
			encabezado.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (especial ? ROJO : "#3A4056") + ";");
			if (festivos.containsKey(fecha)) {
				encabezado.setTooltip(new Tooltip("Festivo: " + festivos.get(fecha)));
			}
			gridSemana.add(encabezado, d + 1, 0);
		}

		botones = new Button[filas.size()][7];
		for (int f = 0; f < filas.size(); f++) {
			Empleado e = filas.get(f);
			Label nombre = new Label(primerNombre(e.getNombre()));
			nombre.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F2433;");
			nombre.setTooltip(new Tooltip(e.getNombre()));
			gridSemana.add(nombre, 0, f + 1);

			Long[] valores = celdas.get(e.getId());
			for (int d = 0; d < 7; d++) {
				final int fila = f;
				final int dia = d;
				Button chip = new Button();
				chip.setMaxWidth(Double.MAX_VALUE);
				chip.setPrefHeight(42);
				chip.setOnAction(ev -> tocar(fila, dia));
				pintarChip(chip, valores[d]);
				marcarNovedad(chip, f, d);
				botones[f][d] = chip;
				gridSemana.add(chip, d + 1, f + 1);
			}
		}

		int filaControl = filas.size() + 1;
		for (int d = 0; d < 7; d++) {
			Label control = new Label();
			control.setMaxWidth(Double.MAX_VALUE);
			control.setAlignment(Pos.CENTER);
			controles[d] = control;
			gridSemana.add(control, d + 1, filaControl);
		}
	}

	private void pintarChip(Button chip, Long valor) {
		String texto;
		String estilo;
		if (valor == null) {
			texto = "";
			estilo = "-fx-background-color: transparent; -fx-border-color: #C9CEDD; -fx-border-style: dashed; "
					+ "-fx-border-radius: 10;";
		} else if (DESCANSO.equals(valor)) {
			texto = "Des";
			estilo = estiloChip(GRIS);
		} else {
			Turno t = turnosPorId.get(valor);
			texto = t == null ? "?" : t.getEtiqueta();
			estilo = estiloChip(colores.getOrDefault(valor, AZUL));
		}
		chip.setText(texto);
		chip.setStyle(estilo + " -fx-font-weight: bold; -fx-font-size: 15px; -fx-cursor: hand;");
	}

	private String estiloChip(String[] color) {
		return "-fx-background-color: " + color[0] + "; -fx-text-fill: " + color[1] + "; -fx-background-radius: 10;";
	}

	/**
	 * Borde ámbar y tooltip si esa persona tiene una novedad ese día. No cambia el
	 * valor de la celda.
	 */
	private void marcarNovedad(Button chip, int fila, int dia) {
		String clave = lunes.plusDays(dia) + "|" + filas.get(fila).getId();
		List<Novedad> delDia = novedadesSemana.get(clave);
		if (delDia == null || delDia.isEmpty()) {
			chip.setTooltip(null);
			return;
		}
		chip.setStyle(chip.getStyle() + " -fx-border-color: #D97706; -fx-border-width: 2; -fx-border-radius: 10;");
		chip.setTooltip(new Tooltip(
				delDia.stream().map(n -> "• " + novedadService.texto(n)).collect(Collectors.joining("\n"))));
	}

	// ====================== EDICIÓN ======================

	/**
	 * Con brocha elegida pinta la celda. Sin brocha la cambia en orden: 1, 2, ½,
	 * Des y vacía.
	 */
	private void tocar(int fila, int dia) {
		Long[] valores = celdas.get(filas.get(fila).getId());
		Toggle elegida = grupoBrocha.getSelectedToggle();
		if (elegida != null) {
			valores[dia] = (Long) elegida.getUserData();
		} else {
			valores[dia] = siguienteEstado(valores[dia]);
		}
		pintarChip(botones[fila][dia], valores[dia]);
		marcarNovedad(botones[fila][dia], fila, dia);
		refrescar();
	}

	private Long siguienteEstado(Long actual) {
		List<Long> estados = new ArrayList<>();
		for (Turno t : turnosActivos) {
			estados.add(t.getId());
		}
		estados.add(DESCANSO);
		estados.add(null);
		int indice = estados.indexOf(actual);
		return estados.get((indice + 1) % estados.size());
	}

	@FXML
	private void handleCopiarAnterior() {
		ocultarError();
		try {
			PropuestaSemana copia = programacionService.copiarDeAnterior(lunes);
			if (copia.asignaciones().isEmpty()) {
				log.warn("No se copió: la semana anterior no tiene nada programado");
				mostrarError("La semana anterior no tiene nada programado para copiar");
				return;
			}
			aplicar(copia.asignaciones());
			estadoTexto = "Viene copiada de la semana anterior, solo cambia lo que haga falta.";
			refrescar();
		} catch (Exception e) {
			log.error("No se pudo copiar la semana anterior", e);
			mostrarError(e.getMessage());
		}
	}

	// ====================== AVISOS Y ENCARGADAS ======================

	/** Actualiza el subtítulo y la fila de control de cada día. */
	private void refrescar() {
		lblTitulo.setText(tituloSemana());
		lblSubtitulo.setText(textoEncargadas() + " " + estadoTexto);

		Map<LocalDate, List<String>> avisos = new HashMap<>();
		try {
			for (Aviso a : programacionService.avisos(lunes, aAsignaciones())) {
				avisos.computeIfAbsent(a.fecha(), k -> new ArrayList<>()).add(a.mensaje());
			}
		} catch (Exception e) {
			log.error("No se pudieron calcular los avisos", e);
		}
		for (int d = 0; d < 7; d++) {
			Label control = controles[d];
			if (control == null) {
				continue;
			}
			LocalDate fecha = lunes.plusDays(d);
			List<String> delDia = avisos.get(fecha);
			if (delDia == null || delDia.isEmpty()) {
                control.setText("OK " + asignadoDelDia(d));
				control.setStyle("-fx-text-fill: #0B6B5C; -fx-font-weight: bold;");
				control.setTooltip(null);
			} else {
				control.setText("⚠ " + delDia.size() + (delDia.size() == 1 ? " aviso" : " avisos"));
				control.setStyle("-fx-text-fill: #B45309; -fx-font-weight: bold;");
				control.setTooltip(new Tooltip(String.join("\n", delDia)));
			}
		}
	}

	/**
	 * "2+2" entre semana y "2+2+½" viernes y sábado, según los turnos activos y sus
	 * cupos.
	 */
	private String esperadoDelDia(LocalDate fecha) {
		boolean viernesOSabado = fecha.getDayOfWeek() == DayOfWeek.FRIDAY || fecha.getDayOfWeek() == DayOfWeek.SATURDAY;
		List<String> partes = new ArrayList<>();
		for (Turno t : turnosActivos) {
			boolean medio = ETIQUETA_MEDIO.equals(t.getEtiqueta());
			if (medio && !viernesOSabado) {
				continue;
			}
			partes.add(medio ? ETIQUETA_MEDIO : String.valueOf(t.getCupos()));
		}
		return String.join("+", partes);
	}
	

    /**
     * Lo realmente asignado ese día, turno por turno: "2+2" o "2+2+½". El medio turno
     * solo se muestra si alguien lo tiene. Lee las celdas en pantalla, no guarda nada.
     */
    private String asignadoDelDia(int dia) {
        List<String> partes = new ArrayList<>();
        for (Turno t : turnosActivos) {
            int n = 0;
            for (Long[] fila : celdas.values()) {
                if (t.getId().equals(fila[dia])) {
                    n++;
                }
            }
            if (ETIQUETA_MEDIO.equals(t.getEtiqueta())) {
                if (n > 0) {
                    partes.add(ETIQUETA_MEDIO);
                }
            } else {
                partes.add(String.valueOf(n));
            }
        }
        return String.join("+", partes);
    }

	/**
	 * Encargada de cada turno completo: la primera encargada que aparece en ese
	 * turno de lunes a domingo. El medio turno no tiene encargada. Se guarda con la
	 * semana.
	 */
	private Map<Long, Long> encargadasDerivadas() {
		Map<Long, Long> resultado = new LinkedHashMap<>();
		for (Turno t : turnosActivos) {
			if (ETIQUETA_MEDIO.equals(t.getEtiqueta())) {
				continue;
			}
			buscar: for (int d = 0; d < 7; d++) {
				for (Empleado e : filas) {
					if (e.isEsEncargada() && t.getId().equals(celdas.get(e.getId())[d])) {
						resultado.put(t.getId(), e.getId());
						break buscar;
					}
				}
			}
		}
		return resultado;
	}

	private String textoEncargadas() {
		Map<Long, Long> encargadas = encargadasDerivadas();
		if (encargadas.isEmpty()) {
			return "Todavía no hay encargadas en esta semana.";
		}
		Map<Long, Empleado> porId = new HashMap<>();
		for (Empleado e : filas) {
			porId.put(e.getId(), e);
		}
		List<String> partes = new ArrayList<>();
		for (Map.Entry<Long, Long> en : encargadas.entrySet()) {
			Empleado e = porId.get(en.getValue());
			Turno t = turnosPorId.get(en.getKey());
			if (e != null && t != null) {
				partes.add(primerNombre(e.getNombre()) + " en el " + t.getNombre().toLowerCase(ES));
			}
		}
		String lista = partes.size() <= 1 ? String.join("", partes)
				: String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
		return "Encargadas esta semana: " + lista + ".";
	}

	// ====================== GUARDAR Y NAVEGAR ======================

	@FXML
	private void handleGuardar() {
		ocultarError();
		try {
			guardarSemanaActual();
			cerrar();
		} catch (Exception e) {
			log.error("No se pudo guardar la semana", e);
			mostrarError(e.getMessage());
		}
	}

	private void guardarSemanaActual() {
		int cambios = programacionService.guardarSemana(lunes, aAsignaciones(), encargadasDerivadas());
		original = copiarCeldas();
		log.info("Semana guardada desde el ingreso rápido");
		log.debug("Guardado de la semana del {}: {} cambios", lunes, cambios);
	}

	@FXML
	private void handleRecalcular() {
		ocultarError();
		try {
			if (hayCambios()) {
				Alert guardarAntes = new Alert(Alert.AlertType.CONFIRMATION,
						"Hay cambios sin guardar. Para recalcular hay que guardar la semana primero. ¿Guardarla ahora?",
						ButtonType.YES, ButtonType.NO);
				guardarAntes.setHeaderText(null);
				Optional<ButtonType> r = guardarAntes.showAndWait();
				if (r.isEmpty() || r.get() != ButtonType.YES) {
					return;
				}
				guardarSemanaActual();
			}
			List<ProgramacionService.CambioHora> cambios = programacionService.previsualizarRecalculo(lunes);
			if (cambios.isEmpty()) {
				Alert nada = new Alert(Alert.AlertType.INFORMATION,
						"Las horas de esta semana ya corresponden a su horario.");
				nada.setHeaderText(null);
				nada.showAndWait();
				return;
			}
			StringBuilder texto = new StringBuilder("Se cambiarían estas horas (actuales → nuevas):\n\n");
			int mostrados = 0;
			for (ProgramacionService.CambioHora c : cambios) {
				if (mostrados == 12) {
					texto.append("... y ").append(cambios.size() - 12).append(" más\n");
					break;
				}
				texto.append(c.texto()).append("\n");
				mostrados++;
			}
			Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, texto.toString(), ButtonType.YES, ButtonType.NO);
			confirmar.setHeaderText(
					"¿Recalcular " + cambios.size() + (cambios.size() == 1 ? " horario?" : " horarios?"));
			confirmar.setResizable(true);
			Optional<ButtonType> r = confirmar.showAndWait();
			if (r.isEmpty() || r.get() != ButtonType.YES) {
				return;
			}
			int n = programacionService.recalcularHoras(lunes);
			log.info("Recálculo de horas hecho desde el ingreso rápido");
			log.debug("Recálculo de la semana del {}: {} filas", lunes, n);
			Alert listo = new Alert(Alert.AlertType.INFORMATION,
					n + (n == 1 ? " horario recalculado." : " horarios recalculados."));
			listo.setHeaderText(null);
			listo.showAndWait();
		} catch (Exception e) {
			log.error("No se pudo recalcular las horas de la semana", e);
			mostrarError(e.getMessage());
		}
	}

	@FXML
	private void handleAplicarNovedades() {
		ocultarError();
		try {
			List<ProgramacionService.CoberturaNovedad> propuestas = programacionService
					.proponerCoberturaNovedades(lunes, aAsignaciones());
			if (propuestas.isEmpty()) {
				Alert nada = new Alert(Alert.AlertType.INFORMATION,
						"No hay novedades que dejen sin trabajar a alguien con turno en esta semana.");
				nada.setHeaderText(null);
				nada.showAndWait();
				return;
			}

			Map<Long, Empleado> porId = new HashMap<>();
			for (Empleado e : filas) {
				porId.put(e.getId(), e);
			}

			VBox contenido = new VBox(10);
			List<ComboBox<String>> combos = new ArrayList<>();
			List<List<Long>> opciones = new ArrayList<>();
			for (ProgramacionService.CoberturaNovedad p : propuestas) {
				Empleado sale = porId.get(p.empleadoId());
				String nombreSale = sale == null ? "?" : primerNombre(sale.getNombre());
				Label texto = new Label(String.format("%02d/%02d: %s (%s) sale de su turno. Cubre:",
						p.fecha().getDayOfMonth(), p.fecha().getMonthValue(), nombreSale, p.tipo().toLowerCase()));

				List<Long> ids = new ArrayList<>();
				ids.add(null);
				ComboBox<String> combo = new ComboBox<>();
				combo.getItems().add("Nadie (queda vacío)");
				int seleccion = 0;
				for (Long c : p.candidatos()) {
					ids.add(c);
					int diaCelda = (int) ChronoUnit.DAYS.between(lunes, p.fecha());
					String quienEra = DESCANSO.equals(celdas.get(c)[diaCelda]) ? " (descansaba)" : " (medio turno)";
					combo.getItems().add(primerNombre(porId.get(c).getNombre()) + quienEra);
					if (c.equals(p.propuestoId())) {
						seleccion = ids.size() - 1;
					}
				}
				combo.getSelectionModel().select(seleccion);
				combos.add(combo);
				opciones.add(ids);
				contenido.getChildren().addAll(texto, combo);
			}

			Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
			confirmar.setHeaderText("Novedades que afectan los turnos de esta semana");
			confirmar.getDialogPane().setContent(contenido);
			confirmar.setResizable(true);
			Optional<ButtonType> r = confirmar.showAndWait();
			if (r.isEmpty() || r.get() != ButtonType.OK) {
				return;
			}

			// Un reemplazo no puede cubrir dos turnos el mismo día
			List<String> usados = new ArrayList<>();
			for (int i = 0; i < propuestas.size(); i++) {
				Long elegido = opciones.get(i).get(combos.get(i).getSelectionModel().getSelectedIndex());
				if (elegido != null) {
					String clave = propuestas.get(i).fecha() + "|" + elegido;
					if (usados.contains(clave)) {
						mostrarError("La misma persona no puede cubrir dos turnos el mismo día");
						return;
					}
					usados.add(clave);
				}
			}

			for (int i = 0; i < propuestas.size(); i++) {
				ProgramacionService.CoberturaNovedad p = propuestas.get(i);
				int dia = (int) ChronoUnit.DAYS.between(lunes, p.fecha());
				celdas.get(p.empleadoId())[dia] = null;
				Long elegido = opciones.get(i).get(combos.get(i).getSelectionModel().getSelectedIndex());
				if (elegido != null) {
					celdas.get(elegido)[dia] = p.turnoId();
				}
			}
			aplicar(aAsignaciones());
			estadoTexto = "Novedades aplicadas sin guardar: revise y pulse Guardar semana.";
			refrescar();
			log.info("Novedades aplicadas en el ingreso rápido (sin guardar)");
			log.debug("Semana del {}: {} cambios por novedades", lunes, propuestas.size());
		} catch (Exception e) {
			log.error("No se pudieron aplicar las novedades de la semana", e);
			mostrarError(e.getMessage());
		}
	}

	@FXML
	private void handleCancelar() {
		if (hayCambios()) {
			Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, "Hay cambios sin guardar. ¿Descartarlos?",
					ButtonType.YES, ButtonType.NO);
			confirmar.setHeaderText(null);
			Optional<ButtonType> r = confirmar.showAndWait();
			if (r.isEmpty() || r.get() != ButtonType.YES) {
				return;
			}
		}
		cerrar();
	}

	@FXML
	private void handleSemanaAnterior() {
		irASemana(lunes.minusDays(7));
	}

	@FXML
	private void handleSemanaSiguiente() {
		irASemana(lunes.plusDays(7));
	}

	/**
	 * Cambia de semana. Si hay cambios sin guardar, pregunta si guardarlos o
	 * descartarlos.
	 */
	private void irASemana(LocalDate nuevoLunes) {
		ocultarError();
		if (hayCambios()) {
			ButtonType guardar = new ButtonType("Guardar");
			ButtonType descartar = new ButtonType("Descartar");
			ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
			Alert pregunta = new Alert(Alert.AlertType.CONFIRMATION, "Hay cambios sin guardar en esta semana.", guardar,
					descartar, cancelar);
			pregunta.setHeaderText(null);
			Optional<ButtonType> r = pregunta.showAndWait();
			if (r.isEmpty() || r.get() == cancelar) {
				return;
			}
			if (r.get() == guardar) {
				try {
					guardarSemanaActual();
				} catch (Exception e) {
					log.error("No se pudo guardar la semana antes de cambiar", e);
					mostrarError(e.getMessage());
					return;
				}
			}
		}
		try {
			lunes = nuevoLunes;
			cargarSemanaActual();
		} catch (Exception e) {
			log.error("No se pudo abrir la semana", e);
			mostrarError(e.getMessage());
		}
	}

	// ====================== AUXILIARES ======================

	private boolean hayCambios() {
		for (Map.Entry<Long, Long[]> e : celdas.entrySet()) {
			if (!Arrays.equals(e.getValue(), original.get(e.getKey()))) {
				return true;
			}
		}
		return false;
	}

	private Map<Long, Long[]> copiarCeldas() {
		Map<Long, Long[]> copia = new HashMap<>();
		for (Map.Entry<Long, Long[]> e : celdas.entrySet()) {
			copia.put(e.getKey(), e.getValue().clone());
		}
		return copia;
	}

	private Map<Long, Long[]> copiaVacia() {
		Map<Long, Long[]> copia = new HashMap<>();
		for (Long id : celdas.keySet()) {
			copia.put(id, new Long[7]);
		}
		return copia;
	}

	/**
	 * Las celdas llenas, listas para avisos y para guardar (las vacías no se
	 * mandan).
	 */
	private List<Asignacion> aAsignaciones() {
		List<Asignacion> resultado = new ArrayList<>();
		for (Empleado e : filas) {
			Long[] valores = celdas.get(e.getId());
			for (int d = 0; d < 7; d++) {
				if (valores[d] != null) {
					resultado.add(new Asignacion(lunes.plusDays(d), e.getId(),
							DESCANSO.equals(valores[d]) ? null : valores[d]));
				}
			}
		}
		return resultado;
	}

	private String tituloSemana() {
		LocalDate domingo = lunes.plusDays(6);
		if (lunes.getMonth() == domingo.getMonth()) {
			return "Semana del " + lunes.getDayOfMonth() + " al " + domingo.getDayOfMonth() + " de " + nombreMes(lunes);
		}
		return "Semana del " + lunes.getDayOfMonth() + " de " + nombreMes(lunes) + " al " + domingo.getDayOfMonth()
				+ " de " + nombreMes(domingo);
	}

	private String nombreMes(LocalDate fecha) {
		return fecha.getMonth().getDisplayName(TextStyle.FULL, ES);
	}

	/**
	 * Solo el primer nombre, con la primera letra en mayúscula: "KATHERINE LÓPEZ"
	 * pasa a "Katherine".
	 */
	private String primerNombre(String nombre) {
		String limpio = nombre == null ? "" : nombre.trim();
		if (limpio.isEmpty()) {
			return "?";
		}
		String primero = limpio.split("\\s+")[0];
		return primero.substring(0, 1).toUpperCase(ES) + primero.substring(1).toLowerCase(ES);
	}

	private void mostrarError(String mensaje) {
		lblError.setText("⚠ " + (mensaje == null ? "Error inesperado" : mensaje));
		lblError.setVisible(true);
		lblError.setManaged(true);
	}

	private void ocultarError() {
		lblError.setVisible(false);
		lblError.setManaged(false);
	}

	private void cerrar() {
		Stage stage = (Stage) lblTitulo.getScene().getWindow();
		stage.close();
	}
}