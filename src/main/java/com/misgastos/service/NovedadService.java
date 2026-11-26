package com.misgastos.service;

import com.misgastos.model.Empleado;
import com.misgastos.model.Novedad;
import com.misgastos.model.TipoNovedad;
import com.misgastos.model.Usuario;
import com.misgastos.repository.EmpleadoRepository;
import com.misgastos.repository.NovedadRepository;
import com.misgastos.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Incapacidades, permisos, vacaciones, faltas, cambios de turno y notas.
 * Solo informan: nunca modifican programacion ni descanso.
 */
@Service
@Transactional
public class NovedadService {

    private static final Logger log = LoggerFactory.getLogger(NovedadService.class);
    private static final int MAX_MOTIVO = 300;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private NovedadRepository novedadRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SesionActual sesionActual;

    @Autowired
    private AuditoriaService auditoriaService;

    /** Días que cubre la novedad, contando el primero y el último. */
    public static long dias(Novedad n) {
        return ChronoUnit.DAYS.between(n.getFechaDesde(), n.getFechaHasta()) + 1;
    }

    public Novedad crear(Long empleadoId, TipoNovedad tipo, LocalDate desde, LocalDate hasta,
            Long empleado2Id, String motivo) {
        LocalDate fin = hasta == null ? desde : hasta;
        validar(empleadoId, tipo, desde, fin, empleado2Id, motivo);

        Novedad n = new Novedad();
        aplicar(n, empleadoId, tipo, desde, fin, empleado2Id, motivo);
        n.setRegistradoPor(usuarioActual());

        Novedad guardada = novedadRepository.save(n);
        log.info("Novedad creada");
        log.debug("Novedad creada: id {}, tipo {}, {} a {}", guardada.getId(), tipo, desde, fin);
        auditoriaService.registrarCambio("CREAR_NOVEDAD", "Novedad creada: " + texto(guardada));
        return guardada;
    }

    public Novedad actualizar(Long id, Long empleadoId, TipoNovedad tipo, LocalDate desde, LocalDate hasta,
            Long empleado2Id, String motivo) {
        LocalDate fin = hasta == null ? desde : hasta;
        validar(empleadoId, tipo, desde, fin, empleado2Id, motivo);
        Novedad n = obtener(id);

        String antes = texto(n);
        aplicar(n, empleadoId, tipo, desde, fin, empleado2Id, motivo);
        String despues = texto(n);
        if (antes.equals(despues)) {
            log.debug("Novedad id {}: sin cambios, no se guarda", id);
            return n;
        }

        Novedad guardada = novedadRepository.save(n);
        log.info("Novedad actualizada");
        log.debug("Novedad actualizada: id {}", id);
        auditoriaService.registrarCambio("EDITAR_NOVEDAD", antes + " → " + despues);
        return guardada;
    }

    public void eliminar(Long id) {
        Novedad n = obtener(id);
        String detalle = texto(n);
        novedadRepository.delete(n);
        log.info("Novedad eliminada");
        log.debug("Novedad eliminada: id {}", id);
        auditoriaService.registrarCambio("ELIMINAR_NOVEDAD", "Novedad eliminada: " + detalle);
    }

    /** Novedades que tocan el rango (por ejemplo, un mes completo). */
    @Transactional(readOnly = true)
    public List<Novedad> listarEntre(LocalDate desde, LocalDate hasta) {
        return novedadRepository
                .findByFechaDesdeLessThanEqualAndFechaHastaGreaterThanEqualOrderByFechaDesdeAsc(hasta, desde);
    }

    /** Historial de una persona, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<Novedad> listarDeEmpleado(Long empleadoId) {
        return novedadRepository.findByEmpleadoIdOrderByFechaDesdeDesc(empleadoId);
    }

    /**
     * Novedades de la misma persona que se cruzan con el rango. No bloquea: la pantalla
     * avisa y deja guardar si el usuario confirma. idExcluir es la que se está editando.
     */
    @Transactional(readOnly = true)
    public List<Novedad> buscarCruces(Long empleadoId, LocalDate desde, LocalDate hasta, Long idExcluir) {
        if (empleadoId == null || desde == null) {
            return List.of();
        }
        LocalDate fin = hasta == null ? desde : hasta;
        return novedadRepository
                .findByEmpleadoIdAndFechaDesdeLessThanEqualAndFechaHastaGreaterThanEqual(empleadoId, fin, desde)
                .stream()
                .filter(o -> idExcluir == null || !o.getId().equals(idExcluir))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Novedad> buscarPorId(Long id) {
        return novedadRepository.findById(id);
    }

    /** Resumen de una novedad para el Registro de cambios y los avisos. */
    public String texto(Novedad n) {
        TipoNovedad t = TipoNovedad.deNombre(n.getTipo());
        StringBuilder sb = new StringBuilder();
        sb.append(t == null ? n.getTipo() : t.getEtiqueta()).append(" de ").append(n.getEmpleado().getNombre());
        if (n.getEmpleado2() != null) {
            sb.append(" con ").append(n.getEmpleado2().getNombre());
        }
        sb.append(", ").append(n.getFechaDesde().format(FORMATO));
        if (!n.getFechaHasta().equals(n.getFechaDesde())) {
            sb.append(" a ").append(n.getFechaHasta().format(FORMATO));
        }
        if (n.getMotivo() != null && !n.getMotivo().isBlank()) {
            sb.append(" (").append(n.getMotivo()).append(")");
        }
        return sb.toString();
    }

    private void aplicar(Novedad n, Long empleadoId, TipoNovedad tipo, LocalDate desde, LocalDate hasta,
            Long empleado2Id, String motivo) {
        n.setEmpleado(buscarEmpleado(empleadoId));
        n.setTipo(tipo.name());
        n.setFechaDesde(desde);
        n.setFechaHasta(hasta);
        n.setEmpleado2(tipo == TipoNovedad.CAMBIO_TURNO ? buscarEmpleado(empleado2Id) : null);
        String limpio = motivo == null ? "" : motivo.trim().replaceAll("\\s+", " ");
        n.setMotivo(limpio.isEmpty() ? null : limpio);
    }

    private void validar(Long empleadoId, TipoNovedad tipo, LocalDate desde, LocalDate hasta,
            Long empleado2Id, String motivo) {
        if (empleadoId == null) {
            throw new RuntimeException("Elija el empleado");
        }
        if (tipo == null) {
            throw new RuntimeException("Elija el tipo de novedad");
        }
        if (desde == null) {
            throw new RuntimeException("La fecha desde es obligatoria");
        }
        if (hasta.isBefore(desde)) {
            throw new RuntimeException("La fecha hasta no puede ser anterior a la fecha desde");
        }
        if (tipo == TipoNovedad.CAMBIO_TURNO) {
            if (empleado2Id == null) {
                throw new RuntimeException("En un cambio de turno hay que elegir la segunda persona");
            }
            if (empleado2Id.equals(empleadoId)) {
                throw new RuntimeException("La segunda persona debe ser distinta del empleado");
            }
        }
        if (motivo != null && motivo.trim().replaceAll("\\s+", " ").length() > MAX_MOTIVO) {
            throw new RuntimeException("El motivo no puede pasar de " + MAX_MOTIVO + " caracteres");
        }
    }

    private Empleado buscarEmpleado(Long id) {
        return empleadoRepository.findById(id).orElseThrow(() -> {
            log.warn("Empleado no encontrado al guardar una novedad");
            log.debug("Empleado inexistente: id {}", id);
            return new RuntimeException("El empleado no existe");
        });
    }

    private Novedad obtener(Long id) {
        return novedadRepository.findById(id).orElseThrow(() -> {
            log.warn("Novedad no encontrada");
            log.debug("Novedad inexistente: id {}", id);
            return new RuntimeException("Novedad no encontrada");
        });
    }

    private Usuario usuarioActual() {
        if (!sesionActual.hayUsuario()) {
            log.warn("No se guardó la novedad: no hay usuario en sesión");
            throw new RuntimeException("No hay un usuario con la sesión abierta");
        }
        return usuarioRepository.findById(sesionActual.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}