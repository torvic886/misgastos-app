package com.misgastos.service;

import com.misgastos.model.FilaContador;
import com.misgastos.model.Lectura;
import com.misgastos.model.Maquina;
import com.misgastos.repository.LecturaRepository;
import com.misgastos.repository.MaquinaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class LecturaService {

    private static final Logger log = LoggerFactory.getLogger(LecturaService.class);

    @Autowired
    private LecturaRepository lecturaRepository;

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    // ==================== REGISTRAR ====================

    /** Periodo que le toca a la próxima lectura de la máquina (1, 2 o 3). */
    public int siguientePeriodo(Long maquinaId) {
        Lectura ultima = ultimaValida(maquinaId);
        return ultima == null ? 1 : ultima.getPeriodo() % 3 + 1;
    }

    /** true si algún contador es menor que el de la última lectura válida (posible reinicio). */
    public boolean contadorMenor(Long maquinaId, long in, long out, long prem) {
        Lectura ant = ultimaValida(maquinaId);
        return ant != null
                && (in < ant.getContadorIn() || out < ant.getContadorOut() || prem < ant.getContadorPrem());
    }

    public Lectura registrar(Long maquinaId, LocalDate fecha, Long in, Long out, Long prem, boolean reinicio) {
        return registrar(maquinaId, fecha, in, out, prem, reinicio, null);
    }

    /** periodoInicial solo se usa en la primera lectura de la máquina (null = periodo 1). */
    public Lectura registrar(Long maquinaId, LocalDate fecha, Long in, Long out, Long prem,
                             boolean reinicio, Integer periodoInicial) {
        Maquina m = maquinaRepository.findById(maquinaId)
                .orElseThrow(() -> new RuntimeException("Máquina no encontrada"));
        if (!m.isActiva()) {
            throw new RuntimeException("La máquina " + m.getCodigo() + " está de baja");
        }
        validar(fecha, in, out, prem);

        Lectura ant = ultimaValida(maquinaId);
        if (ant != null && fecha.isBefore(ant.getFecha())) {
            throw new RuntimeException("La fecha no puede ser anterior a la última lectura ("
                    + ant.getFecha() + ")");
        }
        if (ant != null && !reinicio && contadorMenor(maquinaId, in, out, prem)) {
            throw new RuntimeException("Un contador es menor que el de la lectura anterior. "
                    + "Si la máquina se reinició, registra un reinicio.");
        }

        int periodo = ant != null ? ant.getPeriodo() % 3 + 1 : (periodoInicial != null ? periodoInicial : 1);
        if (periodo < 1 || periodo > 3) {
            throw new RuntimeException("El periodo debe ser 1, 2 o 3");
        }

        Lectura l = nueva(m, fecha, in, out, prem);
        l.setPeriodo(periodo);
        // La primera lectura es normal: se compara contra la base de la máquina
        l.setTipo(ant != null && reinicio ? Lectura.REINICIO : Lectura.NORMAL);
        lecturaRepository.save(l);

        // Al cerrar el periodo 3 comienza un ciclo nuevo: el periodo 1 copia los contadores del 3
        if (l.getPeriodo() == 3) {
            Lectura copia = nueva(m, fecha, in, out, prem);
            copia.setPeriodo(1);
            copia.setTipo(Lectura.COPIA);
            lecturaRepository.save(copia);
            log.debug("Ciclo cerrado en la máquina {}: periodo 1 creado como copia", m.getCodigo());
        }

        log.info("Lectura registrada");
        log.debug("Lectura registrada: máquina {}, periodo {}, tipo {}", m.getCodigo(), l.getPeriodo(), l.getTipo());
        auditoriaService.registrarCambio("REGISTRAR_LECTURA", "Máquina " + m.getCodigo() + ", periodo "
                + l.getPeriodo() + " (" + l.getTipo() + "), fecha " + fecha);
        return l;
    }

    // ==================== EDITAR Y ANULAR ====================

    public Lectura editar(Long id, LocalDate fecha, Long in, Long out, Long prem) {
        Lectura l = obtener(id);
        if (l.isAnulada()) {
            throw new RuntimeException("La lectura está anulada y no se puede editar");
        }
        validar(fecha, in, out, prem);

        List<Lectura> validas = validas(l.getMaquina().getId());
        int pos = indiceDe(validas, id);
        Lectura previa = pos > 0 ? validas.get(pos - 1) : null;
        Lectura sig = pos >= 0 && pos < validas.size() - 1 ? validas.get(pos + 1) : null;

        // La copia del periodo 3 acompaña a su lectura: se edita junto con ella
        Lectura copia = (l.getPeriodo() == 3 && sig != null && Lectura.COPIA.equals(sig.getTipo())) ? sig : null;
        Lectura despues = copia != null ? (pos + 2 < validas.size() ? validas.get(pos + 2) : null) : sig;

        if (previa != null && fecha.isBefore(previa.getFecha())) {
            throw new RuntimeException("La fecha no puede ser anterior a la lectura previa (" + previa.getFecha() + ")");
        }
        if (despues != null && fecha.isAfter(despues.getFecha())) {
            throw new RuntimeException("La fecha no puede ser posterior a la lectura siguiente (" + despues.getFecha() + ")");
        }

        String antes = descripcion(l);
        l.setFecha(fecha);
        l.setContadorIn(in);
        l.setContadorOut(out);
        l.setContadorPrem(prem);
        if (Lectura.COPIA.equals(l.getTipo())) {
            l.setTipo(Lectura.NORMAL);
        }
        lecturaRepository.save(l);

        if (copia != null) {
            copia.setFecha(fecha);
            copia.setContadorIn(in);
            copia.setContadorOut(out);
            copia.setContadorPrem(prem);
            lecturaRepository.save(copia);
        }

        log.info("Lectura editada");
        log.debug("Lectura editada: id {}", id);
        auditoriaService.registrarCambio("EDITAR_LECTURA", "Máquina " + l.getMaquina().getCodigo()
                + ", periodo " + l.getPeriodo() + ": " + antes + " → " + descripcion(l));
        return l;
    }

    public void anular(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("El motivo de la anulación es obligatorio");
        }
        Lectura l = obtener(id);
        if (l.isAnulada()) {
            throw new RuntimeException("La lectura ya está anulada");
        }

        List<Lectura> validas = validas(l.getMaquina().getId());
        int pos = indiceDe(validas, id);
        Lectura sig = pos >= 0 && pos < validas.size() - 1 ? validas.get(pos + 1) : null;

        marcarAnulada(l, motivo.trim());
        if (l.getPeriodo() == 3 && sig != null && Lectura.COPIA.equals(sig.getTipo())) {
            marcarAnulada(sig, "Copia de la lectura anulada");
        }

        log.info("Lectura anulada");
        log.debug("Lectura anulada: id {}", id);
        auditoriaService.registrarCambio("ANULAR_LECTURA", "Máquina " + l.getMaquina().getCodigo()
                + ", periodo " + l.getPeriodo() + ", fecha " + l.getFecha() + ": " + motivo.trim());
    }

    // ==================== CONSULTAS Y CÁLCULO ====================

    /** Lecturas del rango con sus valores en pesos. maquinaId = null: todas las máquinas. */
    @Transactional(readOnly = true)
    public List<FilaContador> filas(Long maquinaId, LocalDate desde, LocalDate hasta) {
        List<FilaContador> resultado = new ArrayList<>();
        for (Maquina m : maquinas(maquinaId)) {
            for (FilaContador f : calcular(m)) {
                if (dentro(f.lectura().getFecha(), desde, hasta)) {
                    resultado.add(f);
                }
            }
        }
        return resultado;
    }

    /** Utilidad de una máquina en el rango: suma de los totales. Puede ser negativa. */
    @Transactional(readOnly = true)
    public BigDecimal utilidad(Maquina m, LocalDate desde, LocalDate hasta) {
        BigDecimal suma = BigDecimal.ZERO;
        for (FilaContador f : calcular(m)) {
            if (f.total() != null && dentro(f.lectura().getFecha(), desde, hasta)) {
                suma = suma.add(f.total());
            }
        }
        return suma;
    }

    /** true si la máquina tiene al menos una lectura válida en el rango. */
    @Transactional(readOnly = true)
    public boolean tieneLecturas(Maquina m, LocalDate desde, LocalDate hasta) {
        return validas(m.getId()).stream().anyMatch(l -> dentro(l.getFecha(), desde, hasta));
    }

    @Transactional(readOnly = true)
    public List<Lectura> historial(Long maquinaId) {
        return lecturaRepository.findByMaquinaIdOrderByFechaAscIdAsc(maquinaId);
    }

    private List<FilaContador> calcular(Maquina m) {
        List<FilaContador> filas = new ArrayList<>();
        Lectura ant = null;
        for (Lectura l : validas(m.getId())) {
            BigDecimal dif = pesos(l.getContadorIn() - l.getContadorOut() - l.getContadorPrem(), l);
            BigDecimal total = null;
            // La primera lectura es solo el punto de partida: no lleva Total.
            // Las demás: su Diferencia menos la Diferencia de la lectura anterior.
            if (ant != null && Lectura.NORMAL.equals(l.getTipo())) {
                long delta = (l.getContadorIn() - ant.getContadorIn())
                        - (l.getContadorOut() - ant.getContadorOut())
                        - (l.getContadorPrem() - ant.getContadorPrem());
                total = pesos(delta, l);
            }
            filas.add(new FilaContador(l, dif, total));
            ant = l;
        }
        return filas;
    }

    
    @Transactional(readOnly = true)
    public boolean existenLecturas(Long maquinaId) {
        return lecturaRepository.existsByMaquinaId(maquinaId);
    }
    
    /** Corrige los contadores de la lectura INICIAL (la primera válida de la máquina). */
    public void corregirInicial(Long maquinaId, BigDecimal denominacion, long in, long out, long prem) {
        List<Lectura> validas = validas(maquinaId);
        if (validas.isEmpty() || !Lectura.INICIAL.equals(validas.get(0).getTipo())) {
            return;
        }
        Lectura l = validas.get(0);
        l.setContadorIn(in);
        l.setContadorOut(out);
        l.setContadorPrem(prem);
        // La denominación solo se corrige si es la única lectura; si hay más, cada una conserva la suya
        if (validas.size() == 1) {
            l.setDenominacion(denominacion);
        }
        lecturaRepository.save(l);
    }

    @Transactional(readOnly = true)
    public long contarLecturas(Long maquinaId) {
        return lecturaRepository.countByMaquinaId(maquinaId);
    }

    public void eliminarLecturasDe(Long maquinaId) {
        lecturaRepository.deleteByMaquinaId(maquinaId);
    }

    // ==================== UTILIDADES ====================

    private List<Maquina> maquinas(Long maquinaId) {
        if (maquinaId != null) {
            return maquinaRepository.findById(maquinaId).map(List::of).orElse(List.of());
        }
        List<Maquina> todas = new ArrayList<>(maquinaRepository.findAll());
        todas.sort(Comparator.comparing(Maquina::getCodigo));
        return todas;
    }

    private List<Lectura> validas(Long maquinaId) {
        return lecturaRepository.findByMaquinaIdAndAnuladaFalseOrderByFechaAscIdAsc(maquinaId);
    }

    private Lectura ultimaValida(Long maquinaId) {
        List<Lectura> validas = validas(maquinaId);
        return validas.isEmpty() ? null : validas.get(validas.size() - 1);
    }

    private int indiceDe(List<Lectura> lista, Long id) {
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private Lectura obtener(Long id) {
        return lecturaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lectura no encontrada"));
    }

    private Lectura nueva(Maquina m, LocalDate fecha, long in, long out, long prem) {
        Lectura l = new Lectura();
        l.setMaquina(m);
        l.setFecha(fecha);
        l.setContadorIn(in);
        l.setContadorOut(out);
        l.setContadorPrem(prem);
        l.setDenominacion(m.getDenominacion());
        return l;
    }

    private void marcarAnulada(Lectura l, String motivo) {
        l.setAnulada(true);
        l.setMotivoAnulacion(motivo);
        lecturaRepository.save(l);
    }

    private BigDecimal pesos(long valor, Lectura l) {
        return l.getDenominacion().multiply(BigDecimal.valueOf(valor));
    }

    private boolean dentro(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        return (desde == null || !fecha.isBefore(desde)) && (hasta == null || !fecha.isAfter(hasta));
    }

    private String descripcion(Lectura l) {
        return l.getFecha() + " In " + l.getContadorIn() + " Out " + l.getContadorOut()
                + " Prem " + l.getContadorPrem();
    }

    private void validar(LocalDate fecha, Long in, Long out, Long prem) {
        if (fecha == null) {
            throw new RuntimeException("La fecha es obligatoria");
        }
        if (in == null || in < 0) {
            throw new RuntimeException("El contador In no puede estar vacío ni ser negativo");
        }
        if (out == null || out < 0) {
            throw new RuntimeException("El contador Out no puede estar vacío ni ser negativo");
        }
        if (prem == null || prem < 0) {
            throw new RuntimeException("El contador Prem no puede estar vacío ni ser negativo");
        }
    }
}