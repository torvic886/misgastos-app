package com.misgastos.service;

import com.misgastos.model.Turno;
import com.misgastos.repository.EncargadaSemanaRepository;
import com.misgastos.repository.ProgramacionRepository;
import com.misgastos.repository.TurnoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Turnos configurables (Turno 1, Turno 2, Medio turno...). Cambiar un horario no altera los días ya guardados. */
@Service
@Transactional
public class TurnoService {

    private static final Logger log = LoggerFactory.getLogger(TurnoService.class);
    private static final int MAX_NOMBRE = 50;
    private static final int MAX_ETIQUETA = 5;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private ProgramacionRepository programacionRepository;

    @Autowired
    private EncargadaSemanaRepository encargadaSemanaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Turno crear(String nombre, String etiqueta, LocalTime horaInicio, LocalTime horaFin,
                       int cupos, int orden) {
        validar(nombre, etiqueta, horaInicio, horaFin, cupos);
        String nombreLimpio = limpiar(nombre);
        String etiquetaLimpia = etiqueta.trim();
        validarDuplicados(nombreLimpio, etiquetaLimpia, null);

        Turno t = new Turno();
        t.setNombre(nombreLimpio);
        t.setEtiqueta(etiquetaLimpia);
        t.setHoraInicio(horaInicio);
        t.setHoraFin(horaFin);
        t.setCupos(cupos);
        t.setOrden(orden);
        t.setActivo(true);

        Turno guardado = turnoRepository.save(t);
        log.info("Turno creado");
        log.debug("Turno creado: id {}, {} a {}, cupos {}", guardado.getId(), horaInicio, horaFin, cupos);
        auditoriaService.registrarCambio("CREAR_TURNO", "Turno " + nombreLimpio + " creado: "
                + horaInicio + " a " + horaFin + ", cupos " + cupos);
        return guardado;
    }

    public Turno actualizar(Long id, String nombre, String etiqueta, LocalTime horaInicio, LocalTime horaFin,
                            int cupos, int orden) {
        validar(nombre, etiqueta, horaInicio, horaFin, cupos);
        Turno t = obtener(id);
        String nombreLimpio = limpiar(nombre);
        String etiquetaLimpia = etiqueta.trim();
        validarDuplicados(nombreLimpio, etiquetaLimpia, id);

        StringBuilder cambios = new StringBuilder();
        if (!t.getNombre().equals(nombreLimpio)) {
            cambios.append("nombre ").append(t.getNombre()).append(" → ").append(nombreLimpio).append("; ");
        }
        if (!t.getEtiqueta().equals(etiquetaLimpia)) {
            cambios.append("etiqueta ").append(t.getEtiqueta()).append(" → ").append(etiquetaLimpia).append("; ");
        }
        boolean cambioHorario = !Objects.equals(t.getHoraInicio(), horaInicio)
                || !Objects.equals(t.getHoraFin(), horaFin);
        if (cambioHorario) {
            cambios.append("horario ").append(t.getHoraInicio()).append("-").append(t.getHoraFin())
                    .append(" → ").append(horaInicio).append("-").append(horaFin)
                    .append(" (los días ya guardados no cambian); ");
        }
        if (t.getCupos() != cupos) {
            cambios.append("cupos ").append(t.getCupos()).append(" → ").append(cupos).append("; ");
        }
        if (t.getOrden() != orden) {
            cambios.append("orden ").append(t.getOrden()).append(" → ").append(orden).append("; ");
        }
        if (cambios.length() == 0) {
            log.debug("Turno id {}: sin cambios, no se guarda", id);
            return t;
        }

        String anterior = t.getNombre();
        t.setNombre(nombreLimpio);
        t.setEtiqueta(etiquetaLimpia);
        t.setHoraInicio(horaInicio);
        t.setHoraFin(horaFin);
        t.setCupos(cupos);
        t.setOrden(orden);

        Turno guardado = turnoRepository.save(t);
        log.info("Turno actualizado");
        log.debug("Turno actualizado: id {}, cambio de horario {}", id, cambioHorario);
        auditoriaService.registrarCambio("EDITAR_TURNO", "Turno " + anterior + ": " + cambios);
        return guardado;
    }

    public Turno desactivar(Long id) {
        Turno t = obtener(id);
        if (!t.isActivo()) {
            throw new RuntimeException("El turno ya está desactivado");
        }
        t.setActivo(false);
        Turno guardado = turnoRepository.save(t);
        log.info("Turno desactivado");
        log.debug("Turno desactivado: id {}", id);
        auditoriaService.registrarCambio("DESACTIVAR_TURNO", "Turno " + t.getNombre() + " desactivado");
        return guardado;
    }

    public Turno reactivar(Long id) {
        Turno t = obtener(id);
        if (t.isActivo()) {
            throw new RuntimeException("El turno ya está activo");
        }
        t.setActivo(true);
        Turno guardado = turnoRepository.save(t);
        log.info("Turno reactivado");
        log.debug("Turno reactivado: id {}", id);
        auditoriaService.registrarCambio("REACTIVAR_TURNO", "Turno " + t.getNombre() + " reactivado");
        return guardado;
    }

    /** Borra de verdad solo si nunca se usó. Sirve para corregir un turno creado por error. */
    public void eliminar(Long id) {
        Turno t = obtener(id);
        if (tieneHistorial(id)) {
            log.warn("No se eliminó el turno: tiene historial");
            log.debug("Turno id {} con historial: no se elimina", id);
            throw new RuntimeException("No se puede eliminar " + t.getNombre()
                    + " porque ya tiene días programados. Desactívelo en su lugar.");
        }
        turnoRepository.delete(t);
        log.info("Turno eliminado");
        log.debug("Turno eliminado: id {}", id);
        auditoriaService.registrarCambio("ELIMINAR_TURNO", "Turno " + t.getNombre() + " eliminado");
    }

    public boolean tieneHistorial(Long id) {
        return programacionRepository.existsByTurnoId(id)
                || encargadaSemanaRepository.findAll().stream()
                        .anyMatch(es -> es.getTurno() != null && id.equals(es.getTurno().getId()));
    }

    /** Todos (activos e inactivos), en el orden en que se muestran. */
    public List<Turno> listarTodos() {
        return turnoRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Turno::getOrden).thenComparing(Turno::getNombre))
                .toList();
    }

    public List<Turno> listarActivos() {
        return turnoRepository.findByActivoTrueOrderByOrdenAsc();
    }

    public Optional<Turno> buscarPorId(Long id) {
        return turnoRepository.findById(id);
    }

    private Turno obtener(Long id) {
        return turnoRepository.findById(id).orElseThrow(() -> {
            log.warn("Turno no encontrado");
            log.debug("Turno inexistente: id {}", id);
            return new RuntimeException("Turno no encontrado");
        });
    }

    private String limpiar(String texto) {
        return texto.trim().replaceAll("\\s+", " ");
    }

    private void validar(String nombre, String etiqueta, LocalTime horaInicio, LocalTime horaFin, int cupos) {
        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("El nombre es obligatorio");
        }
        if (limpiar(nombre).length() > MAX_NOMBRE) {
            throw new RuntimeException("El nombre no puede pasar de " + MAX_NOMBRE + " caracteres");
        }
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new RuntimeException("La etiqueta es obligatoria (por ejemplo 1, 2 o M)");
        }
        if (etiqueta.trim().length() > MAX_ETIQUETA) {
            throw new RuntimeException("La etiqueta no puede pasar de " + MAX_ETIQUETA + " caracteres");
        }
        if (horaInicio == null || horaFin == null) {
            throw new RuntimeException("La hora de inicio y la de fin son obligatorias");
        }
        if (horaInicio.equals(horaFin)) {
            throw new RuntimeException("La hora de inicio y la de fin no pueden ser iguales");
        }
        if (cupos < 0) {
            throw new RuntimeException("Los cupos no pueden ser negativos");
        }
    }

    private void validarDuplicados(String nombreLimpio, String etiquetaLimpia, Long idExcluir) {
        for (Turno o : turnoRepository.findAll()) {
            if (idExcluir != null && o.getId().equals(idExcluir)) {
                continue;
            }
            if (o.getNombre().equalsIgnoreCase(nombreLimpio)) {
                log.warn("No se guardó el turno: el nombre ya existe");
                throw new RuntimeException("Ya existe un turno con el nombre " + nombreLimpio);
            }
            if (o.getEtiqueta().equalsIgnoreCase(etiquetaLimpia)) {
                log.warn("No se guardó el turno: la etiqueta ya existe");
                throw new RuntimeException("Ya existe un turno con la etiqueta " + etiquetaLimpia);
            }
        }
    }
}