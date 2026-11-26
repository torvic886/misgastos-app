package com.misgastos.service;

import com.misgastos.model.HorarioEspecial;
import com.misgastos.model.TipoDiaHorario;
import com.misgastos.model.Turno;
import com.misgastos.repository.HorarioEspecialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Horarios especiales de los turnos (sábado y domingo o festivo). Si un turno no tiene horario
 * especial para ese tipo de día, se usa su horario base.
 */
@Slf4j
@Service
@Transactional
public class HorarioEspecialService {

    /** Entrada y salida de un turno en un día. Si la salida es menor, termina al día siguiente. */
    public record Horario(LocalTime inicio, LocalTime fin) { }

    private final HorarioEspecialRepository horarioRepository;
    private final TurnoService turnoService;
    private final AuditoriaService auditoriaService;

    public HorarioEspecialService(HorarioEspecialRepository horarioRepository,
                                  TurnoService turnoService,
                                  AuditoriaService auditoriaService) {
        this.horarioRepository = horarioRepository;
        this.turnoService = turnoService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<HorarioEspecial> listarTodos() {
        return horarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<HorarioEspecial> buscar(Long turnoId, TipoDiaHorario tipo) {
        return horarioRepository.findByTurnoIdAndTipoDia(turnoId, tipo.name());
    }

    /** Crea o actualiza el horario especial de un turno para un tipo de día. */
    public HorarioEspecial guardar(Long turnoId, TipoDiaHorario tipo, LocalTime inicio, LocalTime fin) {
        if (tipo == null) {
            throw new RuntimeException("Indique el tipo de día del horario");
        }
        if (inicio == null || fin == null) {
            throw new RuntimeException("Indique la hora de inicio y la de fin");
        }
        if (inicio.equals(fin)) {
            throw new RuntimeException("La hora de inicio y la de fin no pueden ser iguales");
        }
        Turno turno = turnoService.buscarPorId(turnoId)
                .orElseThrow(() -> new RuntimeException("El turno no existe"));

        Optional<HorarioEspecial> existente = horarioRepository.findByTurnoIdAndTipoDia(turnoId, tipo.name());
        HorarioEspecial horario = existente.orElseGet(HorarioEspecial::new);
        if (existente.isPresent() && inicio.equals(horario.getHoraInicio()) && fin.equals(horario.getHoraFin())) {
            log.debug("Horario especial sin cambios: turno {}, {}", turnoId, tipo);
            return horario;
        }
        horario.setTurnoId(turnoId);
        horario.setTipoDia(tipo.name());
        horario.setHoraInicio(inicio);
        horario.setHoraFin(fin);
        HorarioEspecial guardado = horarioRepository.save(horario);

        log.info("Horario especial guardado");
        log.debug("Horario especial: turno {}, {}, {} a {}", turnoId, tipo, inicio, fin);
        auditoriaService.registrarCambio("EDITAR_HORARIO_TURNO",
                turno.getNombre() + " (" + tipo.getEtiqueta() + "): " + inicio + " a " + fin);
        return guardado;
    }

    /** Quita el horario especial: ese tipo de día vuelve a usar el horario base del turno. */
    public void quitar(Long turnoId, TipoDiaHorario tipo) {
        Optional<HorarioEspecial> existente = horarioRepository.findByTurnoIdAndTipoDia(turnoId, tipo.name());
        if (existente.isEmpty()) {
            return;
        }
        String nombre = turnoService.buscarPorId(turnoId).map(Turno::getNombre).orElse("Turno");
        horarioRepository.delete(existente.get());
        log.info("Horario especial quitado");
        log.debug("Horario especial quitado: turno {}, {}", turnoId, tipo);
        auditoriaService.registrarCambio("QUITAR_HORARIO_TURNO",
                nombre + " (" + tipo.getEtiqueta() + ") vuelve al horario base");
    }

    /** Horario de un turno en una fecha: el especial si el día lo tiene, y si no el base. */
    @Transactional(readOnly = true)
    public Horario horarioDelDia(Turno turno, LocalDate fecha, boolean festivo) {
        TipoDiaHorario tipo = TipoDiaHorario.deFecha(fecha, festivo);
        if (tipo != null) {
            Optional<HorarioEspecial> especial =
                    horarioRepository.findByTurnoIdAndTipoDia(turno.getId(), tipo.name());
            if (especial.isPresent()) {
                return new Horario(especial.get().getHoraInicio(), especial.get().getHoraFin());
            }
        }
        return new Horario(turno.getHoraInicio(), turno.getHoraFin());
    }
}