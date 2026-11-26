package com.misgastos.service;

import com.misgastos.model.Empleado;
import com.misgastos.repository.DescansoRepository;
import com.misgastos.repository.EmpleadoRepository;
import com.misgastos.repository.EncargadaSemanaRepository;
import com.misgastos.repository.NovedadRepository;
import com.misgastos.repository.ProgramacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.text.Normalizer;

/** Empleados del módulo de turnos (separados de los usuarios de la app). Nunca se borra a quien tenga historial. */
@Service
@Transactional
public class EmpleadoService {

    private static final Logger log = LoggerFactory.getLogger(EmpleadoService.class);
    private static final int MAX_NOMBRE = 100;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private ProgramacionRepository programacionRepository;

    @Autowired
    private DescansoRepository descansoRepository;

    @Autowired
    private NovedadRepository novedadRepository;

    @Autowired
    private EncargadaSemanaRepository encargadaSemanaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Empleado crear(String nombre, LocalDate fechaIngreso, boolean esEncargada) {
        validar(nombre, fechaIngreso, null);
        String limpio = limpiarNombre(nombre);
        validarDuplicado(limpio, null);

        Empleado e = new Empleado();
        e.setNombre(limpio);
        e.setFechaIngreso(fechaIngreso);
        e.setEsEncargada(esEncargada);

        Empleado guardado = empleadoRepository.save(e);
        log.info("Empleado creado");
        log.debug("Empleado creado: id {}, encargada {}, ingreso {}", guardado.getId(), esEncargada, fechaIngreso);
        auditoriaService.registrarCambio("CREAR_EMPLEADO", "Empleado " + limpio + " creado"
                + (esEncargada ? " (encargada)" : "") + ", ingreso " + fechaIngreso);
        return guardado;
    }

    public Empleado actualizar(Long id, String nombre, LocalDate fechaIngreso, boolean esEncargada,
                               LocalDate fechaRetiro) {
        validar(nombre, fechaIngreso, fechaRetiro);
        Empleado e = obtener(id);
        String limpio = limpiarNombre(nombre);
        validarDuplicado(limpio, id);

        StringBuilder cambios = new StringBuilder();
        if (!e.getNombre().equals(limpio)) {
            cambios.append("nombre ").append(e.getNombre()).append(" → ").append(limpio).append("; ");
        }
        if (!Objects.equals(e.getFechaIngreso(), fechaIngreso)) {
            cambios.append("ingreso ").append(e.getFechaIngreso()).append(" → ").append(fechaIngreso).append("; ");
        }
        if (e.isEsEncargada() != esEncargada) {
            cambios.append("encargada ").append(e.isEsEncargada() ? "sí" : "no")
                    .append(" → ").append(esEncargada ? "sí" : "no").append("; ");
        }
        if (!Objects.equals(e.getFechaRetiro(), fechaRetiro)) {
            cambios.append("retiro ").append(e.getFechaRetiro() == null ? "ninguno" : e.getFechaRetiro())
                    .append(" → ").append(fechaRetiro == null ? "ninguno" : fechaRetiro).append("; ");
        }
        if (cambios.length() == 0) {
            log.debug("Empleado id {}: sin cambios, no se guarda", id);
            return e;
        }

        String anterior = e.getNombre();
        e.setNombre(limpio);
        e.setFechaIngreso(fechaIngreso);
        e.setEsEncargada(esEncargada);
        e.setFechaRetiro(fechaRetiro);

        Empleado guardado = empleadoRepository.save(e);
        log.info("Empleado actualizado");
        log.debug("Empleado actualizado: id {}", id);
        auditoriaService.registrarCambio("EDITAR_EMPLEADO", "Empleado " + anterior + ": " + cambios);
        return guardado;
    }

    public Empleado retirar(Long id, LocalDate fechaRetiro) {
        Empleado e = obtener(id);
        if (e.getFechaRetiro() != null) {
            throw new RuntimeException("El empleado ya está retirado");
        }
        if (fechaRetiro == null) {
            throw new RuntimeException("La fecha de retiro es obligatoria");
        }
        if (e.getFechaIngreso() != null && fechaRetiro.isBefore(e.getFechaIngreso())) {
            throw new RuntimeException("La fecha de retiro no puede ser anterior a la de ingreso");
        }

        e.setFechaRetiro(fechaRetiro);
        Empleado guardado = empleadoRepository.save(e);
        log.info("Empleado retirado");
        log.debug("Empleado retirado: id {}, fecha {}", id, fechaRetiro);
        auditoriaService.registrarCambio("RETIRAR_EMPLEADO", "Empleado " + e.getNombre()
                + " retirado el " + fechaRetiro);
        return guardado;
    }

    public Empleado reactivar(Long id) {
        Empleado e = obtener(id);
        if (e.getFechaRetiro() == null) {
            throw new RuntimeException("El empleado ya está activo");
        }

        e.setFechaRetiro(null);
        Empleado guardado = empleadoRepository.save(e);
        log.info("Empleado reactivado");
        log.debug("Empleado reactivado: id {}", id);
        auditoriaService.registrarCambio("REACTIVAR_EMPLEADO", "Empleado " + e.getNombre() + " reactivado");
        return guardado;
    }

    /** Borra de verdad solo si no tiene historial. Sirve para corregir un empleado creado por error. */
    public void eliminar(Long id) {
        Empleado e = obtener(id);
        if (tieneHistorial(id)) {
            log.warn("No se eliminó el empleado: tiene historial");
            log.debug("Empleado id {} con historial: no se elimina", id);
            throw new RuntimeException("No se puede eliminar a " + e.getNombre()
                    + " porque tiene turnos, descansos o novedades. Retírelo en su lugar.");
        }

        empleadoRepository.delete(e);
        log.info("Empleado eliminado");
        log.debug("Empleado eliminado: id {}", id);
        auditoriaService.registrarCambio("ELIMINAR_EMPLEADO", "Empleado " + e.getNombre() + " eliminado");
    }

    public boolean tieneHistorial(Long id) {
        return programacionRepository.existsByEmpleadoId(id)
                || descansoRepository.existsByEmpleadoId(id)
                || novedadRepository.existsByEmpleadoIdOrEmpleado2Id(id, id)
                || encargadaSemanaRepository.findAll().stream()
                        .anyMatch(es -> es.getEmpleado() != null && id.equals(es.getEmpleado().getId()));
    }

    public List<Empleado> listarTodos() {
        return empleadoRepository.findAllByOrderByNombreAsc();
    }

    /** Los que trabajaban ese día, según sus fechas de ingreso y retiro. */
    public List<Empleado> listarVigentes(LocalDate dia) {
        return empleadoRepository.findAllByOrderByNombreAsc().stream()
                .filter(e -> e.vigenteEn(dia))
                .toList();
    }

    public Optional<Empleado> buscarPorId(Long id) {
        return empleadoRepository.findById(id);
    }

    private Empleado obtener(Long id) {
        return empleadoRepository.findById(id).orElseThrow(() -> {
            log.warn("Empleado no encontrado");
            log.debug("Empleado inexistente: id {}", id);
            return new RuntimeException("Empleado no encontrado");
        });
    }

    private String limpiarNombre(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    private void validar(String nombre, LocalDate fechaIngreso, LocalDate fechaRetiro) {
        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("El nombre es obligatorio");
        }
        if (limpiarNombre(nombre).length() > MAX_NOMBRE) {
            throw new RuntimeException("El nombre no puede pasar de " + MAX_NOMBRE + " caracteres");
        }
        if (fechaIngreso == null) {
            throw new RuntimeException("La fecha de ingreso es obligatoria");
        }
        if (fechaRetiro != null && fechaRetiro.isBefore(fechaIngreso)) {
            throw new RuntimeException("La fecha de retiro no puede ser anterior a la de ingreso");
        }
    }

    private void validarDuplicado(String nombreLimpio, Long idExcluir) {
        String buscado = normalizar(nombreLimpio);
        boolean duplicado = empleadoRepository.findAllByOrderByNombreAsc().stream()
                .anyMatch(o -> (idExcluir == null || !o.getId().equals(idExcluir))
                        && normalizar(o.getNombre()).equals(buscado));
        if (duplicado) {
            log.warn("No se guardó el empleado: el nombre ya existe");
            throw new RuntimeException("Ya existe un empleado con el nombre " + nombreLimpio
                    + " (se comparan sin tildes ni mayúsculas)");
        }
    }

    /** Minúsculas y sin tildes, para comparar nombres: "López" y "lopez" son el mismo. */
    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }
}