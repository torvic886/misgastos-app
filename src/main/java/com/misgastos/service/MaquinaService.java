package com.misgastos.service;

import com.misgastos.model.Maquina;
import com.misgastos.repository.MaquinaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class MaquinaService {

    private static final Logger log = LoggerFactory.getLogger(MaquinaService.class);

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private LecturaService lecturaService;

    @Autowired
    private AuditoriaService auditoriaService;

    public Maquina crear(Long codigo, BigDecimal denominacion, String nombre, String serial, String juego,
                         Long contadorIn, Long contadorOut, Long contadorPrem, LocalDate fechaAlta) {
        validarDatos(codigo, denominacion, nombre, serial, juego);
        
        if (maquinaRepository.existsByCodigo(codigo)) {
            log.warn("No se creó la máquina: el código ya existe");
            log.debug("Código duplicado al crear máquina: {}", codigo);
            throw new RuntimeException("Ya existe una máquina con el código " + codigo);
        }

        Maquina m = new Maquina();
        m.setCodigo(codigo);
        m.setDenominacion(denominacion);
        m.setNombre(nombre.trim());
        m.setSerial(serial.trim());
        m.setJuego(juego.trim());
        m.setContadorIn(contadorIn == null ? 0L : contadorIn);
        m.setContadorOut(contadorOut == null ? 0L : contadorOut);
        m.setContadorPrem(contadorPrem == null ? 0L : contadorPrem);
        m.setFechaAlta(fechaAlta != null ? fechaAlta : LocalDate.now());
        m.setActiva(true);

        Maquina guardada = maquinaRepository.save(m);


        log.info("Máquina creada");
        log.debug("Máquina creada: id {}, código {}", guardada.getId(), codigo);
        auditoriaService.registrarCambio("CREAR_MAQUINA",
                "Máquina " + codigo + " (" + nombre.trim() + ") creada");
        return guardada;
    }

    public Maquina actualizar(Long id, Long codigo, BigDecimal denominacion, String nombre, String serial,
                              String juego, Long contadorIn, Long contadorOut, Long contadorPrem) {
        validarDatos(codigo, denominacion, nombre, serial, juego);
        Maquina m = obtener(id);

        Optional<Maquina> otra = maquinaRepository.findByCodigo(codigo);
        if (otra.isPresent() && !otra.get().getId().equals(id)) {
            log.warn("No se actualizó la máquina: el código ya existe en otra máquina");
            log.debug("Código duplicado al actualizar máquina id {}: {}", id, codigo);
            throw new RuntimeException("Ya existe otra máquina con el código " + codigo);
        }

        m.setCodigo(codigo);
        m.setDenominacion(denominacion);
        m.setNombre(nombre.trim());
        m.setSerial(serial.trim());
        m.setJuego(juego.trim());

        validarContadores(contadorIn, contadorOut, contadorPrem);
        m.setContadorIn(contadorIn);
        m.setContadorOut(contadorOut);
        m.setContadorPrem(contadorPrem);
        // Si ya tiene lecturas, se corrige también la lectura INICIAL para que el historial coincida
        if (lecturaService.existenLecturas(id)) {
            lecturaService.corregirInicial(id, denominacion, contadorIn, contadorOut, contadorPrem);
        }

        Maquina guardada = maquinaRepository.save(m);
        log.info("Máquina actualizada");
        log.debug("Máquina actualizada: id {}, código {}", id, codigo);
        auditoriaService.registrarCambio("ACTUALIZAR_MAQUINA", "Máquina " + codigo + " actualizada");
        return guardada;
    }

    public Maquina darDeBaja(Long id, LocalDate fechaBaja, String motivo) {
        Maquina m = obtener(id);
        if (!m.isActiva()) {
            throw new RuntimeException("La máquina ya está de baja");
        }
        if (fechaBaja == null) {
            throw new RuntimeException("La fecha de baja es obligatoria");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("El motivo de baja es obligatorio");
        }

        m.setActiva(false);
        m.setFechaBaja(fechaBaja);
        m.setMotivoBaja(motivo.trim());
        Maquina guardada = maquinaRepository.save(m);
        log.info("Máquina dada de baja");
        log.debug("Máquina de baja: id {}, código {}, fecha {}", id, m.getCodigo(), fechaBaja);
        auditoriaService.registrarCambio("BAJA_MAQUINA", "Máquina " + m.getCodigo()
                + " dada de baja el " + fechaBaja + ": " + motivo.trim());
        return guardada;
    }

    public Maquina reactivar(Long id) {
        Maquina m = obtener(id);
        if (m.isActiva()) {
            throw new RuntimeException("La máquina ya está activa");
        }

        m.setActiva(true);
        m.setFechaBaja(null);
        m.setMotivoBaja(null);
        Maquina guardada = maquinaRepository.save(m);
        log.info("Máquina reactivada");
        log.debug("Máquina reactivada: id {}, código {}", id, m.getCodigo());
        auditoriaService.registrarCambio("REACTIVAR_MAQUINA", "Máquina " + m.getCodigo() + " reactivada");
        return guardada;
    }

    /** Borra para siempre una máquina de baja, junto con sus lecturas. */
    public void eliminarDefinitivamente(Long id) {
        Maquina m = obtener(id);
        if (m.isActiva()) {
            throw new RuntimeException("Solo se pueden eliminar máquinas que están de baja");
        }

        long lecturas = lecturaService.contarLecturas(id);
        lecturaService.eliminarLecturasDe(id);
        maquinaRepository.delete(m);

        log.info("Máquina eliminada definitivamente");
        log.debug("Máquina eliminada: id {}, código {}, lecturas borradas {}", id, m.getCodigo(), lecturas);
        auditoriaService.registrarCambio("ELIMINAR_MAQUINA", "Máquina " + m.getCodigo()
                + " eliminada con " + lecturas + " lectura(s)");
    }

    public long contarLecturas(Long id) {
        return lecturaService.contarLecturas(id);
    }
    

    public List<Maquina> listarActivas() {
        return maquinaRepository.findByActivaTrueOrderByCodigoAsc();
    }

    public List<Maquina> listarBajas() {
        return maquinaRepository.findByActivaFalseOrderByFechaBajaDesc();
    }

    public Optional<Maquina> buscarPorId(Long id) {
        return maquinaRepository.findById(id);
    }

    private Maquina obtener(Long id) {
        return maquinaRepository.findById(id).orElseThrow(() -> {
            log.warn("Máquina no encontrada");
            log.debug("Máquina inexistente: id {}", id);
            return new RuntimeException("Máquina no encontrada");
        });
    }

    private void validarDatos(Long codigo, BigDecimal denominacion, String nombre, String serial, String juego) {
        if (codigo == null || codigo <= 0) {
            throw new RuntimeException("El código debe ser un número mayor que cero");
        }
        if (denominacion == null || denominacion.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("La denominación debe ser mayor que cero");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("El nombre es obligatorio");
        }
        if (serial == null || serial.isBlank()) {
            throw new RuntimeException("El serial es obligatorio");
        }
        if (juego == null || juego.isBlank()) {
            throw new RuntimeException("El juego es obligatorio");
        }
    }

    private void validarContadores(Long contadorIn, Long contadorOut, Long contadorPrem) {
        if (contadorIn == null || contadorIn < 0) {
            throw new RuntimeException("El contador In no puede estar vacío ni ser negativo");
        }
        if (contadorOut == null || contadorOut < 0) {
            throw new RuntimeException("El contador Out no puede estar vacío ni ser negativo");
        }
        if (contadorPrem == null || contadorPrem < 0) {
            throw new RuntimeException("El contador Prem no puede estar vacío ni ser negativo");
        }
    }
}