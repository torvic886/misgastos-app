package com.misgastos.service;

import com.misgastos.model.Descanso;
import com.misgastos.model.Empleado;
import com.misgastos.model.EncargadaSemana;
import com.misgastos.model.Festivo;
import com.misgastos.model.Programacion;
import com.misgastos.model.Turno;
import com.misgastos.repository.DescansoRepository;
import com.misgastos.repository.EncargadaSemanaRepository;
import com.misgastos.repository.ProgramacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.misgastos.model.Novedad;
import com.misgastos.model.TipoNovedad;

/**
 * Programación de turnos. Lo que hay en programacion y descanso es lo realmente
 * trabajado. Parte 1: consultas. Parte 2: propuesta de semana y avisos (nada de
 * esto guarda datos).
 */
@Slf4j
@Service
@Transactional
public class ProgramacionService {

	/**
	 * Máximo de días que se consultan de una vez (protege contra rangos por error).
	 */
	private static final int MAX_DIAS_RANGO = 400;

	/**
	 * Etiqueta del medio turno (½). Es la misma que usa el modal de turnos para
	 * reconocerlo.
	 */
	private static final String ETIQUETA_MEDIO = "\u00BD";

	private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");

	/**
	 * Un día ya armado para mostrar: quién trabaja, quién descansa y si es domingo
	 * o festivo.
	 */
	public record DiaProgramado(LocalDate fecha, boolean domingo, String nombreFestivo, List<Programacion> trabajan,
			List<Empleado> descansan) {

		public boolean esFestivo() {
			return nombreFestivo != null;
		}

		public boolean esDomingoOFestivo() {
			return domingo || esFestivo();
		}
	}

	/**
	 * Una celda de la semana: esa persona, ese día, ese turno. turnoId null =
	 * descanso.
	 */
	public record Asignacion(LocalDate fecha, Long empleadoId, Long turnoId) {

		public boolean esDescanso() {
			return turnoId == null;
		}
	}

	/**
	 * Semana lista para mostrar en el ingreso rápido. Si copiadaDeAnterior es true,
	 * todavía no está guardada. encargadas: id del turno -> id de la encargada de
	 * esa semana.
	 */
	public record PropuestaSemana(LocalDate lunes, boolean copiadaDeAnterior, List<Asignacion> asignaciones,
			Map<Long, Long> encargadas) {
	}

	/** Aviso de un día (no bloquea nada). */
	public record Aviso(LocalDate fecha, String mensaje) {
	}

	private final ProgramacionRepository programacionRepository;
	private final DescansoRepository descansoRepository;
	private final EncargadaSemanaRepository encargadaSemanaRepository;
	private final FestivoService festivoService;
	private final EmpleadoService empleadoService;
	private final TurnoService turnoService;
	private final AuditoriaService auditoriaService;
	private final HorarioEspecialService horarioEspecialService;
	private final NovedadService novedadService;

	public ProgramacionService(ProgramacionRepository programacionRepository, DescansoRepository descansoRepository,
			EncargadaSemanaRepository encargadaSemanaRepository, FestivoService festivoService,
			EmpleadoService empleadoService, TurnoService turnoService, AuditoriaService auditoriaService,
			HorarioEspecialService horarioEspecialService, NovedadService novedadService) {
		this.programacionRepository = programacionRepository;
		this.descansoRepository = descansoRepository;
		this.encargadaSemanaRepository = encargadaSemanaRepository;
		this.festivoService = festivoService;
		this.empleadoService = empleadoService;
		this.turnoService = turnoService;
		this.auditoriaService = auditoriaService;
		this.horarioEspecialService = horarioEspecialService;
		this.novedadService = novedadService;
	}

	/**
	 * Lunes de la semana a la que pertenece la fecha (si ya es lunes, la misma
	 * fecha).
	 */
	public static LocalDate lunesDe(LocalDate fecha) {
		return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	// ====================== PARTE 1: CONSULTAS ======================

	/** Todos los días del mes, del 1 al último, en orden. */
	@Transactional(readOnly = true)
	public List<DiaProgramado> leerMes(YearMonth mes) {
		if (mes == null) {
			throw new RuntimeException("Indique el mes que desea consultar");
		}
		return leerRango(mes.atDay(1), mes.atEndOfMonth());
	}

	/** Los 7 días de la semana, de lunes a domingo. La fecha debe ser un lunes. */
	@Transactional(readOnly = true)
	public List<DiaProgramado> leerSemana(LocalDate lunes) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		return leerRango(lunes, lunes.plusDays(6));
	}

	/**
	 * Cada día del rango (ambos extremos incluidos), con sus turnos y descansos.
	 */
	@Transactional(readOnly = true)
	public List<DiaProgramado> leerRango(LocalDate desde, LocalDate hasta) {
		if (desde == null || hasta == null || hasta.isBefore(desde)) {
			throw new RuntimeException("El rango de fechas no es válido");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) >= MAX_DIAS_RANGO) {
			throw new RuntimeException("El rango de fechas es demasiado grande");
		}

		Map<LocalDate, List<Programacion>> trabajan = new LinkedHashMap<>();
		Map<LocalDate, List<Empleado>> descansan = new HashMap<>();
		for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
			trabajan.put(d, new ArrayList<>());
			descansan.put(d, new ArrayList<>());
		}

		// El repositorio ya los entrega ordenados por fecha, orden del turno y posición
		for (Programacion p : programacionRepository.findByFechaBetweenOrderByFechaAscTurnoOrdenAscPosicionAsc(desde,
				hasta)) {
			trabajan.get(p.getFecha()).add(p);
		}
		for (Descanso d : descansoRepository.findByFechaBetweenOrderByFechaAsc(desde, hasta)) {
			descansan.get(d.getFecha()).add(d.getEmpleado());
		}

		Map<LocalDate, String> festivos = new HashMap<>();
		for (Festivo f : festivoService.listarEntre(desde, hasta)) {
			festivos.put(f.getFecha(), f.getNombre());
		}

		List<DiaProgramado> dias = new ArrayList<>();
		for (Map.Entry<LocalDate, List<Programacion>> e : trabajan.entrySet()) {
			LocalDate fecha = e.getKey();
			List<Empleado> quienesDescansan = descansan.get(fecha);
			quienesDescansan.sort(Comparator.comparing(Empleado::getNombre, String.CASE_INSENSITIVE_ORDER));
			dias.add(new DiaProgramado(fecha, fecha.getDayOfWeek() == DayOfWeek.SUNDAY, festivos.get(fecha),
					e.getValue(), quienesDescansan));
		}
		log.debug("Programación leída: {} días, del {} al {}", dias.size(), desde, hasta);
		return dias;
	}

	/**
	 * Encargada de cada turno en la semana que empieza ese lunes. Clave: id del
	 * turno.
	 */
	@Transactional(readOnly = true)
	public Map<Long, Empleado> encargadasDeSemana(LocalDate lunes) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		Map<Long, Empleado> resultado = new HashMap<>();
		for (EncargadaSemana es : encargadaSemanaRepository.deSemana(lunes)) {
			resultado.put(es.getTurno().getId(), es.getEmpleado());
		}
		log.debug("Encargadas de la semana del {}: {}", lunes, resultado.size());
		return resultado;
	}

	// ====================== PARTE 2: PROPUESTA Y AVISOS ======================

	/**
	 * Semana para el ingreso rápido, sin guardar nada. Si ya tiene datos, devuelve
	 * esos. Si está vacía, copia la semana anterior intercambiando a las dos
	 * encargadas entre el turno 1 y el 2. Rotativos y descansos quedan como
	 * estaban. Si la anterior también está vacía, sale vacía.
	 */
	@Transactional(readOnly = true)
	public PropuestaSemana proponerSemana(LocalDate lunes) {
		List<DiaProgramado> dias = leerSemana(lunes);
		if (hayDatos(dias)) {
			log.debug("Semana del {} ya guardada: se muestra tal cual", lunes);
			return new PropuestaSemana(lunes, false, aAsignaciones(dias), idsEncargadas(lunes));
		}
		return copiarDeAnterior(lunes);
	}

	/**
	 * Copia la semana anterior intercambiando a las dos encargadas, aunque esta
	 * semana ya tenga datos. Sirve para el botón "Copiar semana anterior". No
	 * guarda nada.
	 */
	@Transactional(readOnly = true)
	public PropuestaSemana copiarDeAnterior(LocalDate lunes) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		LocalDate anterior = lunes.minusDays(7);
		List<DiaProgramado> diasAnterior = leerSemana(anterior);
		if (!hayDatos(diasAnterior)) {
			log.debug("Semana del {} vacía y la anterior también: propuesta vacía", lunes);
			return new PropuestaSemana(lunes, false, List.of(), Map.of());
		}

		Map<Long, Long> encargadasAnteriores = idsEncargadas(anterior);
		Map<Long, Long> encargadasNuevas = new HashMap<>(encargadasAnteriores);

		// Intercambio solo si la semana anterior tiene exactamente dos encargadas
		// distintas
		List<Map.Entry<Long, Long>> pares = new ArrayList<>(encargadasAnteriores.entrySet());
		boolean intercambiar = pares.size() == 2 && !pares.get(0).getValue().equals(pares.get(1).getValue());
		Long turno1 = null;
		Long turno2 = null;
		Long encargada1 = null;
		Long encargada2 = null;
		if (intercambiar) {
			turno1 = pares.get(0).getKey();
			encargada1 = pares.get(0).getValue();
			turno2 = pares.get(1).getKey();
			encargada2 = pares.get(1).getValue();
			encargadasNuevas.put(turno1, encargada2);
			encargadasNuevas.put(turno2, encargada1);
		} else {
			log.warn("No se intercambiaron encargadas: la semana anterior no tiene exactamente dos");
		}

		Map<Long, Long> rotacion = rotacionRotativos(lunes);
		List<Asignacion> copiadas = new ArrayList<>();
		for (Asignacion a : aAsignaciones(diasAnterior)) {
			Long turno = a.turnoId();
			boolean esEncargada = a.empleadoId().equals(encargada1) || a.empleadoId().equals(encargada2);
			if (intercambiar && turno != null && esEncargada) {
				if (turno.equals(turno1)) {
					turno = turno2;
				} else if (turno.equals(turno2)) {
					turno = turno1;
				}
			}
			Long empleadoNuevo = rotacion.getOrDefault(a.empleadoId(), a.empleadoId());
			copiadas.add(new Asignacion(a.fecha().plusDays(7), empleadoNuevo, turno));
		}
		log.debug("Semana del {} propuesta copiando la anterior: {} celdas", lunes, copiadas.size());
		return new PropuestaSemana(lunes, true, copiadas, encargadasNuevas);
	}

	/**
	 * Rotación de los rotativos vigentes: la fila de cada uno pasa al siguiente
	 * (ordenados por id) y el último pasa al primero. Con menos de dos no rota. Se
	 * calcula al consultar, no se guarda.
	 */
	private Map<Long, Long> rotacionRotativos(LocalDate lunes) {
		List<Empleado> rotativos = empleadoService.listarTodos().stream()
				.filter(e -> !e.isEsEncargada() && e.vigenteEn(lunes)).sorted(Comparator.comparing(Empleado::getId))
				.toList();
		Map<Long, Long> rotacion = new HashMap<>();
		if (rotativos.size() < 2) {
			log.debug("Semana del {}: menos de dos rotativos vigentes, no se rota", lunes);
			return rotacion;
		}
		for (int i = 0; i < rotativos.size(); i++) {
			rotacion.put(rotativos.get(i).getId(), rotativos.get((i + 1) % rotativos.size()).getId());
		}
		log.debug("Semana del {}: rotan {} rotativos", lunes, rotativos.size());
		return rotacion;
	}

	/**
	 * Avisos de la semana para las celdas que se muestran (guardadas o todavía en
	 * pantalla). Solo informan: nada bloquea. Revisa cupos de cada turno, el medio
	 * turno fuera de viernes y sábado, personas sin turno ni descanso, y personas
	 * fuera de su vigencia.
	 */
	@Transactional(readOnly = true)
	public List<Aviso> avisos(LocalDate lunes, List<Asignacion> asignaciones) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		List<Asignacion> celdas = asignaciones == null ? List.of() : asignaciones;

		Map<Long, Empleado> empleados = empleadoService.listarTodos().stream()
				.collect(Collectors.toMap(Empleado::getId, e -> e));
		List<Turno> turnos = turnoService.listarActivos();

		List<Aviso> avisos = new ArrayList<>();
		List<Novedad> novedadesSemana = novedadService.listarEntre(lunes, lunes.plusDays(6));
		for (int i = 0; i < 7; i++) {
			LocalDate fecha = lunes.plusDays(i);
			List<Asignacion> delDia = celdas.stream().filter(a -> a.fecha().equals(fecha)).toList();
			boolean viernesOSabado = fecha.getDayOfWeek() == DayOfWeek.FRIDAY
					|| fecha.getDayOfWeek() == DayOfWeek.SATURDAY;

			// Cupos por turno
			for (Turno t : turnos) {
				long n = delDia.stream().filter(a -> t.getId().equals(a.turnoId())).count();
				boolean esMedio = ETIQUETA_MEDIO.equals(t.getEtiqueta());
				long esperado = (esMedio && !viernesOSabado) ? 0 : t.getCupos();
				if (n > esperado) {
					if (esMedio && !viernesOSabado) {
						avisos.add(new Aviso(fecha, t.getNombre() + " asignado fuera de viernes y sábado"));
					} else {
						avisos.add(new Aviso(fecha,
								"Sobra gente en " + t.getNombre() + ": hay " + n + " y los cupos son " + esperado));
					}
				} else if (n < esperado) {
					if (!(esMedio && empleados.values().stream()
							.anyMatch(e -> tieneNovedadQueQuitaTurno(novedadesSemana, e.getId(), fecha)))) {
						avisos.add(
								new Aviso(fecha, "Falta gente en " + t.getNombre() + ": hay " + n + " de " + esperado));
					}
				}
			}

			// Vigentes ese día sin turno ni descanso
			List<Long> conCelda = delDia.stream().map(Asignacion::empleadoId).toList();
			String sinAsignar = empleados.values().stream()
					.filter(e -> e.vigenteEn(fecha) && !conCelda.contains(e.getId())
							&& !tieneNovedadQueQuitaTurno(novedadesSemana, e.getId(), fecha))
					.map(Empleado::getNombre).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
			if (!sinAsignar.isEmpty()) {
				avisos.add(new Aviso(fecha, "Sin turno ni descanso: " + sinAsignar));
			}

			// Asignados fuera de su vigencia (antes de ingresar o después de retirarse)
			for (Asignacion a : delDia) {
				Empleado e = empleados.get(a.empleadoId());
				if (e == null) {
					log.warn("Asignación con un empleado que no existe");
					continue;
				}
				if (!e.vigenteEn(fecha)) {
					avisos.add(new Aviso(fecha, e.getNombre() + " no está vigente el " + fecha.format(FORMATO_DIA)
							+ " (revise su ingreso o retiro)"));
				}
			}

			// Incapacidad, vacaciones, permiso o falta de alguien que tiene turno ese día
			for (Asignacion a : delDia) {
				if (a.turnoId() == null) {
					continue;
				}
				for (Novedad n : novedadesSemana) {
					if (!n.getEmpleado().getId().equals(a.empleadoId()) || fecha.isBefore(n.getFechaDesde())
							|| fecha.isAfter(n.getFechaHasta())) {
						continue;
					}
					TipoNovedad tipo = TipoNovedad.deNombre(n.getTipo());
					if (tipo == TipoNovedad.INCAPACIDAD || tipo == TipoNovedad.VACACIONES || tipo == TipoNovedad.PERMISO
							|| tipo == TipoNovedad.FALTA) {
						avisos.add(new Aviso(fecha,
								n.getEmpleado().getNombre() + " tiene " + tipo.getEtiqueta().toLowerCase() + " el "
										+ fecha.format(FORMATO_DIA) + " y está asignado a un turno"));
					}
				}
			}
		}
		log.debug("Avisos de la semana del {}: {}", lunes, avisos.size());
		return avisos;
	}

	/**
	 * True si la persona tiene incapacidad, vacaciones, permiso o falta ese día (no
	 * trabaja por una razón conocida).
	 */
	private boolean tieneNovedadQueQuitaTurno(List<Novedad> novedades, Long empleadoId, LocalDate fecha) {
		for (Novedad n : novedades) {
			if (!n.getEmpleado().getId().equals(empleadoId) || fecha.isBefore(n.getFechaDesde())
					|| fecha.isAfter(n.getFechaHasta())) {
				continue;
			}
			TipoNovedad tipo = TipoNovedad.deNombre(n.getTipo());
			if (tipo == TipoNovedad.INCAPACIDAD || tipo == TipoNovedad.VACACIONES || tipo == TipoNovedad.PERMISO
					|| tipo == TipoNovedad.FALTA) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Novedades de la semana por persona y día, para marcar celdas en el Ingreso
	 * rápido. Clave: "fecha|empleadoId" (la misma forma que usa guardarSemana). En
	 * un cambio de turno se marcan las dos personas. Solo lee: no guarda nada.
	 */
	@Transactional(readOnly = true)
	public Map<String, List<Novedad>> novedadesDeSemana(LocalDate lunes) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		LocalDate domingo = lunes.plusDays(6);
		Map<String, List<Novedad>> resultado = new HashMap<>();
		for (Novedad n : novedadService.listarEntre(lunes, domingo)) {
			LocalDate ini = n.getFechaDesde().isBefore(lunes) ? lunes : n.getFechaDesde();
			LocalDate fin = n.getFechaHasta().isAfter(domingo) ? domingo : n.getFechaHasta();
			for (LocalDate dia = ini; !dia.isAfter(fin); dia = dia.plusDays(1)) {
				resultado.computeIfAbsent(dia + "|" + n.getEmpleado().getId(), k -> new ArrayList<>()).add(n);
				if (n.getEmpleado2() != null) {
					resultado.computeIfAbsent(dia + "|" + n.getEmpleado2().getId(), k -> new ArrayList<>()).add(n);
				}
			}
		}
		log.debug("Novedades de la semana del {}: {} celdas marcadas", lunes, resultado.size());
		return resultado;
	}

	/** Alguien con novedad que afecta el turno ese día, y quién podría cubrirlo. */
	public record CoberturaNovedad(LocalDate fecha, Long empleadoId, String tipo, Long turnoId, List<Long> candidatos,
			Long propuestoId) {
	}

	private static final Set<String> TIPOS_QUE_AFECTAN = Set.of("INCAPACIDAD", "VACACIONES", "PERMISO", "FALTA");

	/**
	 * Tipo de novedad que le quita el turno a esa persona ese día, o null si no
	 * tiene ninguna.
	 */
	private String tipoQueAfecta(Map<String, List<Novedad>> novedades, LocalDate fecha, Long empleadoId) {
		List<Novedad> delDia = novedades.get(fecha + "|" + empleadoId);
		if (delDia == null) {
			return null;
		}
		for (Novedad n : delDia) {
			if (n.getTipo() != null && TIPOS_QUE_AFECTAN.contains(n.getTipo())) {
				return n.getTipo();
			}
		}
		return null;
	}

	/**
	 * Para cada persona asignada a un turno que tenga incapacidad, vacaciones,
	 * permiso o falta ese día, propone quién la cubre: alguien que descansa ese día
	 * (primero rotativos, las encargadas de último). Solo calcula sobre las celdas
	 * recibidas: no guarda nada.
	 */
	@Transactional(readOnly = true)
	public List<CoberturaNovedad> proponerCoberturaNovedades(LocalDate lunes, List<Asignacion> asignaciones) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		List<Asignacion> celdas = asignaciones == null ? List.of() : asignaciones;
		Map<String, List<Novedad>> novedades = novedadesDeSemana(lunes);
		Map<Long, Empleado> empleados = empleadoService.listarTodos().stream()
				.collect(Collectors.toMap(Empleado::getId, e -> e));
		List<CoberturaNovedad> resultado = new ArrayList<>();

		Long medioId = turnoService.listarActivos().stream().filter(t -> ETIQUETA_MEDIO.equals(t.getEtiqueta()))
				.map(Turno::getId).findFirst().orElse(null);

		for (int d = 0; d < 7; d++) {
			LocalDate fecha = lunes.plusDays(d);
			List<Long> candidatos = new ArrayList<>();
			for (Asignacion a : celdas) {
				if (a.fecha().equals(fecha) && a.esDescanso() && empleados.containsKey(a.empleadoId())
						&& tipoQueAfecta(novedades, fecha, a.empleadoId()) == null) {
					candidatos.add(a.empleadoId());
				}
			}
			candidatos.sort(Comparator.comparing((Long id) -> empleados.get(id).isEsEncargada())
					.thenComparing(id -> empleados.get(id).getNombre(), String.CASE_INSENSITIVE_ORDER));

			// Viernes y sábado nadie descansa: primero propone a quien está en el medio
			// turno
			if (d == 4 || d == 5) {
				List<Long> delMedio = new ArrayList<>();
				for (Asignacion a : celdas) {
					if (a.fecha().equals(fecha) && medioId != null && medioId.equals(a.turnoId())
							&& empleados.containsKey(a.empleadoId())
							&& tipoQueAfecta(novedades, fecha, a.empleadoId()) == null) {
						delMedio.add(a.empleadoId());
					}
				}
				candidatos.addAll(0, delMedio);
				log.debug("Cobertura del {}: {} candidatos del medio turno", fecha, delMedio.size());
			}

			List<Long> usados = new ArrayList<>();
			for (Asignacion a : celdas) {
				if (!a.fecha().equals(fecha) || a.esDescanso()) {
					continue;
				}
				String tipo = tipoQueAfecta(novedades, fecha, a.empleadoId());
				if (tipo == null) {
					continue;
				}
				Long propuesto = null;
				for (Long c : candidatos) {
					if (!usados.contains(c)) {
						propuesto = c;
						break;
					}
				}
				if (propuesto != null) {
					usados.add(propuesto);
				}
				resultado.add(new CoberturaNovedad(fecha, a.empleadoId(), tipo, a.turnoId(),
						new ArrayList<>(candidatos), propuesto));
			}
		}
		log.debug("Cobertura de novedades de la semana del {}: {} cambios propuestos", lunes, resultado.size());
		return resultado;
	}

	// ====================== PARTE 3: GUARDAR SEMANA ======================

	/**
	 * Guarda la semana completa en una sola transacción. Borra programacion,
	 * descanso y encargada_semana de esos 7 días y los recrea. Devuelve cuántas
	 * cosas cambiaron (0 = no se tocó nada). Audita un resumen de lo que cambió, no
	 * una línea por celda.
	 */
	public int guardarSemana(LocalDate lunes, List<Asignacion> asignaciones, Map<Long, Long> encargadas) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		List<Asignacion> celdas = asignaciones == null ? List.of() : asignaciones;
		Map<Long, Long> encargadasNuevas = encargadas == null ? Map.of() : encargadas;
		LocalDate domingo = lunes.plusDays(6);
		Set<LocalDate> festivosSemana = festivoService.fechasEntre(lunes, domingo);

		Map<Long, Empleado> empleados = empleadoService.listarTodos().stream()
				.collect(Collectors.toMap(Empleado::getId, e -> e));
		Map<Long, Turno> turnos = turnoService.listarTodos().stream().collect(Collectors.toMap(Turno::getId, t -> t));

		// Lo que hay guardado hoy
		List<Programacion> progAntes = programacionRepository
				.findByFechaBetweenOrderByFechaAscTurnoOrdenAscPosicionAsc(lunes, domingo);
		List<Descanso> descAntes = descansoRepository.findByFechaBetweenOrderByFechaAsc(lunes, domingo);
		List<EncargadaSemana> encAntes = encargadaSemanaRepository.deSemana(lunes);

		// Estado anterior: clave "fecha|empleado" -> id del turno, o "D" si descansaba
		Map<String, String> antes = new HashMap<>();
		Map<String, Programacion> progPorClave = new HashMap<>();
		for (Programacion p : progAntes) {
			String clave = p.getFecha() + "|" + p.getEmpleado().getId();
			antes.put(clave, String.valueOf(p.getTurno().getId()));
			progPorClave.put(clave, p);
		}
		for (Descanso d : descAntes) {
			antes.put(d.getFecha() + "|" + d.getEmpleado().getId(), "D");
		}

		// Validación de lo que llega. Los avisos de cobertura no bloquean, esto sí
		Map<String, String> despues = new HashMap<>();
		for (Asignacion a : celdas) {
			if (a.fecha() == null || a.fecha().isBefore(lunes) || a.fecha().isAfter(domingo)) {
				throw new RuntimeException("Hay una celda con una fecha fuera de la semana");
			}
			Empleado e = empleados.get(a.empleadoId());
			if (e == null) {
				throw new RuntimeException("Hay una celda con un empleado que no existe");
			}
			String clave = a.fecha() + "|" + a.empleadoId();
			if (despues.containsKey(clave)) {
				log.warn("No se guardó la semana: una persona repetida el mismo día");
				throw new RuntimeException(e.getNombre() + " está repetido el " + a.fecha().format(FORMATO_DIA)
						+ ": una persona solo puede tener una celda por día");
			}
			if (a.turnoId() != null) {
				Turno t = turnos.get(a.turnoId());
				if (t == null) {
					throw new RuntimeException("Hay una celda con un turno que no existe");
				}
				// Un turno inactivo no se ofrece de nuevo, pero la celda que ya lo tenía se
				// conserva
				if (!t.isActivo() && !String.valueOf(t.getId()).equals(antes.get(clave))) {
					throw new RuntimeException("El turno " + t.getNombre() + " está inactivo");
				}
			}
			despues.put(clave, a.turnoId() == null ? "D" : String.valueOf(a.turnoId()));
		}
		for (Map.Entry<Long, Long> en : encargadasNuevas.entrySet()) {
			if (!turnos.containsKey(en.getKey()) || !empleados.containsKey(en.getValue())) {
				throw new RuntimeException("La encargada indicada para un turno no es válida");
			}
		}

		// Qué cambió (antes contra después)
		Map<Long, Long> encargadasAntes = new HashMap<>();
		for (EncargadaSemana es : encAntes) {
			encargadasAntes.put(es.getTurno().getId(), es.getEmpleado().getId());
		}
		Set<String> claves = new HashSet<>(antes.keySet());
		claves.addAll(despues.keySet());
		List<String> detalles = new ArrayList<>();
		for (String clave : claves.stream().sorted().toList()) {
			String a = antes.get(clave);
			String d = despues.get(clave);
			if (Objects.equals(a, d)) {
				continue;
			}
			String[] partes = clave.split("\\|");
			LocalDate fecha = LocalDate.parse(partes[0]);
			Empleado e = empleados.get(Long.parseLong(partes[1]));
			String nombre = e == null ? "?" : e.getNombre();
			detalles.add(fecha.format(FORMATO_DIA) + " " + nombre + ": " + etiquetaCelda(a, turnos) + "→"
					+ etiquetaCelda(d, turnos));
		}
		for (Turno t : turnos.values()) {
			Long ea = encargadasAntes.get(t.getId());
			Long en = encargadasNuevas.get(t.getId());
			if (!Objects.equals(ea, en)) {
				detalles.add(
						"Encargada " + t.getNombre() + ": " + nombreDe(ea, empleados) + "→" + nombreDe(en, empleados));
			}
		}
		if (detalles.isEmpty()) {
			log.info("Semana sin cambios: no se guardó nada");
			return 0;
		}

		// Agrupa a quienes trabajan por día y turno, y numera la posición (encargada
		// primero)
		Map<String, List<Asignacion>> porDiaYTurno = new LinkedHashMap<>();
		for (Asignacion a : celdas) {
			if (a.turnoId() != null) {
				porDiaYTurno.computeIfAbsent(a.fecha() + "|" + a.turnoId(), k -> new ArrayList<>()).add(a);
			}
		}

		// Borra lo anterior. El flush va antes de crear: Hibernate hace los INSERT
		// antes que los
		// DELETE, y con UNIQUE (fecha, empleado) chocaría al recrear la misma fila
		programacionRepository.deleteAll(progAntes);
		descansoRepository.deleteAll(descAntes);
		encargadaSemanaRepository.deleteAll(encAntes);
		programacionRepository.flush();
		descansoRepository.flush();
		encargadaSemanaRepository.flush();

		for (List<Asignacion> grupo : porDiaYTurno.values()) {
			Long turnoId = grupo.get(0).turnoId();
			Turno turno = turnos.get(turnoId);
			Long encargadaDelTurno = encargadasNuevas.get(turnoId);
			grupo.sort(Comparator
					.comparingInt((Asignacion a) -> rangoEnTurno(a.empleadoId(), encargadaDelTurno, empleados))
					.thenComparing(a -> empleados.get(a.empleadoId()).getNombre(), String.CASE_INSENSITIVE_ORDER));
			int posicion = 1;
			for (Asignacion a : grupo) {
				Programacion p = new Programacion();
				p.setFecha(a.fecha());
				p.setTurno(turno);
				p.setEmpleado(empleados.get(a.empleadoId()));
				p.setPosicion(posicion++);
				// Si seguía en el mismo turno se conservan sus horas (pudieron ajustarse ese
				// día)
				Programacion previa = progPorClave.get(a.fecha() + "|" + a.empleadoId());
				if (previa != null && previa.getTurno().getId().equals(turnoId)) {
					p.setHoraEntrada(previa.getHoraEntrada());
					p.setHoraSalida(previa.getHoraSalida());
				} else {
					// Horario del turno para ese día: el especial (sábado, domingo o festivo) o el
					// base
					HorarioEspecialService.Horario horario = horarioEspecialService.horarioDelDia(turno, a.fecha(),
							festivosSemana.contains(a.fecha()));
					p.setHoraEntrada(horario.inicio());
					p.setHoraSalida(horario.fin());
				}
				programacionRepository.save(p);
			}
		}
		for (Asignacion a : celdas) {
			if (a.turnoId() == null) {
				Descanso d = new Descanso();
				d.setFecha(a.fecha());
				d.setEmpleado(empleados.get(a.empleadoId()));
				descansoRepository.save(d);
			}
		}
		for (Map.Entry<Long, Long> en : encargadasNuevas.entrySet()) {
			EncargadaSemana es = new EncargadaSemana();
			es.setLunes(lunes);
			es.setTurno(turnos.get(en.getKey()));
			es.setEmpleado(empleados.get(en.getValue()));
			encargadaSemanaRepository.save(es);
		}

		int cambios = detalles.size();
		String resumen = "Semana del " + lunes.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ": " + cambios
				+ (cambios == 1 ? " cambio. " : " cambios. ") + String.join("; ", detalles);
		if (resumen.length() > 500) {
			resumen = resumen.substring(0, 497) + "...";
		}
		auditoriaService.registrarCambio("GUARDAR_SEMANA", resumen);
		log.info("Semana guardada: {} cambios", cambios);
		log.debug("Semana del {} guardada: {} celdas", lunes, celdas.size());
		return cambios;
	}

	/** Una hora guardada que cambiaría al recalcular la semana. */
	public record CambioHora(Long programacionId, LocalDate fecha, String empleado, String turno,
			LocalTime entradaActual, LocalTime salidaActual, LocalTime entradaNueva, LocalTime salidaNueva) {
		public String texto() {
			return fecha.format(DateTimeFormatter.ofPattern("dd/MM")) + " " + empleado + " (" + turno + "): "
					+ entradaActual + "-" + salidaActual + " → " + entradaNueva + "-" + salidaNueva;
		}
	}

	/**
	 * Lista, sin cambiar nada, las filas guardadas de la semana cuyas horas no
	 * coinciden con el horario del día (base, sábado o domingo o festivo). El medio
	 * turno no se revisa.
	 */
	@Transactional(readOnly = true)
	public List<CambioHora> previsualizarRecalculo(LocalDate lunes) {
		if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new RuntimeException("La semana debe empezar un lunes");
		}
		LocalDate domingo = lunes.plusDays(6);
		Set<LocalDate> festivosSemana = festivoService.fechasEntre(lunes, domingo);
		List<CambioHora> cambios = new ArrayList<>();
		for (Programacion p : programacionRepository.findByFechaBetweenOrderByFechaAscTurnoOrdenAscPosicionAsc(lunes,
				domingo)) {
			Turno turno = p.getTurno();
			if ("½".equals(turno.getEtiqueta())) {
				continue;
			}
			HorarioEspecialService.Horario h = horarioEspecialService.horarioDelDia(turno, p.getFecha(),
					festivosSemana.contains(p.getFecha()));
			if (Objects.equals(h.inicio(), p.getHoraEntrada()) && Objects.equals(h.fin(), p.getHoraSalida())) {
				continue;
			}
			cambios.add(new CambioHora(p.getId(), p.getFecha(), p.getEmpleado().getNombre(), turno.getNombre(),
					p.getHoraEntrada(), p.getHoraSalida(), h.inicio(), h.fin()));
		}
		log.debug("Vista previa de recálculo de la semana del {}: {} filas cambiarían", lunes, cambios.size());
		return cambios;
	}

	/**
	 * Aplica el recálculo a la semana en una sola transacción. Devuelve cuántas
	 * filas cambió.
	 */
	public int recalcularHoras(LocalDate lunes) {
		List<CambioHora> cambios = previsualizarRecalculo(lunes);
		if (cambios.isEmpty()) {
			log.info("Recálculo de horas: nada que cambiar");
			return 0;
		}
		for (CambioHora c : cambios) {
			Programacion p = programacionRepository.findById(c.programacionId()).orElseThrow(
					() -> new RuntimeException("Una fila de la semana ya no existe; abre la semana de nuevo"));
			p.setHoraEntrada(c.entradaNueva());
			p.setHoraSalida(c.salidaNueva());
			programacionRepository.save(p);
		}
		String resumen = "Semana del " + lunes.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ": " + cambios.size()
				+ (cambios.size() == 1 ? " horario recalculado. " : " horarios recalculados. ")
				+ cambios.stream().map(CambioHora::texto).collect(Collectors.joining("; "));
		if (resumen.length() > 500) {
			resumen = resumen.substring(0, 497) + "...";
		}
		auditoriaService.registrarCambio("RECALCULAR_HORAS", resumen);
		log.info("Horas recalculadas: {} filas", cambios.size());
		log.debug("Recálculo de la semana del {} aplicado", lunes);
		return cambios.size();
	}

	/**
	 * 0 = encargada de la semana en ese turno, 1 = otra encargada, 2 = rotativo.
	 */
	private int rangoEnTurno(Long empleadoId, Long encargadaDelTurno, Map<Long, Empleado> empleados) {
		if (empleadoId.equals(encargadaDelTurno)) {
			return 0;
		}
		Empleado e = empleados.get(empleadoId);
		return (e != null && e.isEsEncargada()) ? 1 : 2;
	}

	private String etiquetaCelda(String codigo, Map<Long, Turno> turnos) {
		if (codigo == null) {
			return "—";
		}
		if ("D".equals(codigo)) {
			return "Des";
		}
		Turno t = turnos.get(Long.parseLong(codigo));
		return t == null ? "?" : t.getEtiqueta();
	}

	private String nombreDe(Long empleadoId, Map<Long, Empleado> empleados) {
		if (empleadoId == null) {
			return "—";
		}
		Empleado e = empleados.get(empleadoId);
		return e == null ? "?" : e.getNombre();
	}

	private boolean hayDatos(List<DiaProgramado> dias) {
		return dias.stream().anyMatch(d -> !d.trabajan().isEmpty() || !d.descansan().isEmpty());
	}

	private List<Asignacion> aAsignaciones(List<DiaProgramado> dias) {
		List<Asignacion> resultado = new ArrayList<>();
		for (DiaProgramado dia : dias) {
			for (Programacion p : dia.trabajan()) {
				resultado.add(new Asignacion(dia.fecha(), p.getEmpleado().getId(), p.getTurno().getId()));
			}
			for (Empleado e : dia.descansan()) {
				resultado.add(new Asignacion(dia.fecha(), e.getId(), null));
			}
		}
		return resultado;
	}

	private Map<Long, Long> idsEncargadas(LocalDate lunes) {
		Map<Long, Long> ids = new HashMap<>();
		for (Map.Entry<Long, Empleado> e : encargadasDeSemana(lunes).entrySet()) {
			ids.put(e.getKey(), e.getValue().getId());
		}
		return ids;
	}
}