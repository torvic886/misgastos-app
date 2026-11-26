package com.misgastos.service;

import com.misgastos.model.Festivo;
import com.misgastos.repository.FestivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Festivos configurables. Los domingos no se guardan: se calculan desde la fecha. */
@Service
@Transactional
public class FestivoService {

    private static final Logger log = LoggerFactory.getLogger(FestivoService.class);
    private static final int MAX_NOMBRE = 100;

    @Autowired
    private FestivoRepository festivoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Festivo crear(LocalDate fecha, String nombre) {
        validar(fecha, nombre);
        String limpio = limpiar(nombre);
        validarDuplicado(fecha, null);

        Festivo f = new Festivo();
        f.setFecha(fecha);
        f.setNombre(limpio);

        Festivo guardado = festivoRepository.save(f);
        log.info("Festivo creado");
        log.debug("Festivo creado: id {}, fecha {}", guardado.getId(), fecha);
        auditoriaService.registrarCambio("CREAR_FESTIVO", "Festivo " + fecha + " (" + limpio + ") creado");
        return guardado;
    }

    public Festivo actualizar(Long id, LocalDate fecha, String nombre) {
        validar(fecha, nombre);
        Festivo f = obtener(id);
        String limpio = limpiar(nombre);
        validarDuplicado(fecha, id);

        if (f.getFecha().equals(fecha) && f.getNombre().equals(limpio)) {
            log.debug("Festivo id {}: sin cambios, no se guarda", id);
            return f;
        }

        String antes = f.getFecha() + " (" + f.getNombre() + ")";
        f.setFecha(fecha);
        f.setNombre(limpio);

        Festivo guardado = festivoRepository.save(f);
        log.info("Festivo actualizado");
        log.debug("Festivo actualizado: id {}", id);
        auditoriaService.registrarCambio("EDITAR_FESTIVO", "Festivo " + antes + " → " + fecha + " (" + limpio + ")");
        return guardado;
    }

    public void eliminar(Long id) {
        Festivo f = obtener(id);
        festivoRepository.delete(f);
        log.info("Festivo eliminado");
        log.debug("Festivo eliminado: id {}, fecha {}", id, f.getFecha());
        auditoriaService.registrarCambio("ELIMINAR_FESTIVO", "Festivo " + f.getFecha() + " (" + f.getNombre() + ") eliminado");
    }

    public List<Festivo> listarPorAnio(int anio) {
        return festivoRepository.findByFechaBetweenOrderByFechaAsc(
                LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31));
    }

    public List<Festivo> listarEntre(LocalDate desde, LocalDate hasta) {
        return festivoRepository.findByFechaBetweenOrderByFechaAsc(desde, hasta);
    }

    /** Las fechas festivas del rango, para consultar "¿es festivo?" sin ir a la base por cada día. */
    public Set<LocalDate> fechasEntre(LocalDate desde, LocalDate hasta) {
        Set<LocalDate> fechas = new HashSet<>();
        for (Festivo f : festivoRepository.findByFechaBetweenOrderByFechaAsc(desde, hasta)) {
            fechas.add(f.getFecha());
        }
        return fechas;
    }

    public boolean esFestivo(LocalDate fecha) {
        return festivoRepository.existsByFecha(fecha);
    }

    public Optional<Festivo> buscarPorId(Long id) {
        return festivoRepository.findById(id);
    }

    private Festivo obtener(Long id) {
        return festivoRepository.findById(id).orElseThrow(() -> {
            log.warn("Festivo no encontrado");
            log.debug("Festivo inexistente: id {}", id);
            return new RuntimeException("Festivo no encontrado");
        });
    }

    private String limpiar(String texto) {
        return texto.trim().replaceAll("\\s+", " ");
    }

    private void validar(LocalDate fecha, String nombre) {
        if (fecha == null) {
            throw new RuntimeException("La fecha es obligatoria");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("El nombre es obligatorio");
        }
        if (limpiar(nombre).length() > MAX_NOMBRE) {
            throw new RuntimeException("El nombre no puede pasar de " + MAX_NOMBRE + " caracteres");
        }
    }

    private void validarDuplicado(LocalDate fecha, Long idExcluir) {
        boolean duplicado = festivoRepository.findByFechaBetweenOrderByFechaAsc(fecha, fecha).stream()
                .anyMatch(o -> idExcluir == null || !o.getId().equals(idExcluir));
        if (duplicado) {
            log.warn("No se guardó el festivo: la fecha ya existe");
            log.debug("Festivo duplicado: {}", fecha);
            throw new RuntimeException("Ya hay un festivo el " + fecha);
        }
    }
}