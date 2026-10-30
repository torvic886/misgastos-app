package com.misgastos.service;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.PresupuestoCategoria;
import com.misgastos.repository.CategoriaRepository;
import com.misgastos.repository.PresupuestoCategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class PresupuestoService {

	private static final Logger log = LoggerFactory.getLogger(PresupuestoService.class);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Una categoría con su tope (puede ser null) y lo gastado en el mes. */
    public record EstadoPresupuesto(Long categoriaId, String categoria, BigDecimal tope, BigDecimal gastado) {

        public boolean tieneTope() {
            return tope != null && tope.signum() > 0;
        }

        public double porcentaje() {
            if (!tieneTope()) {
                return 0;
            }
            return gastado.multiply(CIEN).divide(tope, 1, RoundingMode.HALF_UP).doubleValue();
        }
    }

    @Autowired
    private PresupuestoCategoriaRepository presupuestoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private GastoService gastoService;

    @Autowired
    private AuditoriaService auditoriaService;

    /** Todas las categorías con su tope y lo gastado en el mes de la fecha dada. */
    @Transactional(readOnly = true)
    public List<EstadoPresupuesto> estadoDelMes(LocalDate referencia) {
        Map<Long, BigDecimal> topes = new HashMap<>();
        for (PresupuestoCategoria p : presupuestoRepository.findAll()) {
            topes.put(p.getCategoriaId(), p.getMonto());
        }
        Map<Long, BigDecimal> gastado = gastadoPorCategoria(referencia);

        List<EstadoPresupuesto> filas = new ArrayList<>();
        for (Categoria c : categoriaRepository.findAll()) {
            filas.add(new EstadoPresupuesto(c.getId(), c.getNombre(),
                    topes.get(c.getId()), gastado.getOrDefault(c.getId(), BigDecimal.ZERO)));
        }
        filas.sort(Comparator.comparing(EstadoPresupuesto::categoria, String.CASE_INSENSITIVE_ORDER));
        return filas;
    }

    public void fijarTope(Long categoriaId, BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El tope debe ser mayor que cero");
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        BigDecimal anterior = presupuestoRepository.findById(categoriaId)
                .map(PresupuestoCategoria::getMonto).orElse(null);

        presupuestoRepository.save(new PresupuestoCategoria(categoriaId, monto));

        String antes = anterior == null ? "sin tope" : dinero(anterior);
        auditoriaService.registrarCambioGasto("FIJAR_PRESUPUESTO",
                "Presupuesto mensual de " + categoria.getNombre() + ": " + antes + " → " + dinero(monto));
        log.info("Tope de presupuesto fijado");
        log.debug("Tope fijado: categoría id {}", categoriaId);
    }

    public void quitarTope(Long categoriaId) {
        Optional<PresupuestoCategoria> actual = presupuestoRepository.findById(categoriaId);
        if (actual.isEmpty()) {
        	log.debug("Quitar tope: la categoría id {} no tenía tope", categoriaId);
            return;
        }
        String nombre = categoriaRepository.findById(categoriaId).map(Categoria::getNombre).orElse("categoría " + categoriaId);
        BigDecimal anterior = actual.get().getMonto();
        presupuestoRepository.deleteById(categoriaId);

        auditoriaService.registrarCambioGasto("QUITAR_PRESUPUESTO",
                "Presupuesto mensual de " + nombre + ": " + dinero(anterior) + " → sin tope");
        log.info("Tope de presupuesto quitado");
        log.debug("Tope quitado: categoría id {}", categoriaId);
    }

    /**
     * Mensaje de aviso tras registrar un gasto, o null si no hay nada que avisar.
     * Avisa al cruzar el 80 % del tope y en cada gasto mientras se esté en el 100 % o más.
     */
    @Transactional(readOnly = true)
    public String avisoTrasGasto(Gasto gasto) {
        if (gasto == null || gasto.getCategoria() == null || gasto.getFecha() == null
                || gasto.getValorTotal() == null) {
            return null;
        }
        Long categoriaId = gasto.getCategoria().getId();
        Optional<PresupuestoCategoria> presupuesto = presupuestoRepository.findById(categoriaId);
        if (presupuesto.isEmpty() || presupuesto.get().getMonto().signum() <= 0) {
            return null;
        }
        BigDecimal tope = presupuesto.get().getMonto();
        BigDecimal gastado = gastadoPorCategoria(gasto.getFecha()).getOrDefault(categoriaId, BigDecimal.ZERO);
        BigDecimal antes = gastado.subtract(gasto.getValorTotal());

        long porcentaje = gastado.multiply(CIEN).divide(tope, 0, RoundingMode.HALF_UP).longValue();
        String nombre = gasto.getCategoria().getNombre();

        if (alcanza(gastado, tope, 100)) {
        	log.info("Aviso de presupuesto: tope superado en una categoría");
            log.debug("Presupuesto superado: categoría id {}, {} %", categoriaId, porcentaje);
        	return "🚨 Presupuesto superado en " + nombre + ": llevas " + dinero(gastado)
                    + " de " + dinero(tope) + " este mes (" + porcentaje + " %).";
        }
        if (alcanza(gastado, tope, 80) && !alcanza(antes, tope, 80)) {
        	log.info("Aviso de presupuesto: se cruzó el 80 % en una categoría");
            log.debug("Presupuesto al 80 %: categoría id {}, {} %", categoriaId, porcentaje);
        	return "⚠ " + nombre + " ya va en el " + porcentaje + " % de su presupuesto mensual: "
                    + dinero(gastado) + " de " + dinero(tope) + ".";
        }
        return null;
    }

    // ==================== AUXILIARES ====================

    private boolean alcanza(BigDecimal gastado, BigDecimal tope, int porcentaje) {
        return gastado.multiply(CIEN).compareTo(tope.multiply(BigDecimal.valueOf(porcentaje))) >= 0;
    }

    private Map<Long, BigDecimal> gastadoPorCategoria(LocalDate referencia) {
        LocalDate inicio = referencia.withDayOfMonth(1);
        LocalDate fin = referencia.with(TemporalAdjusters.lastDayOfMonth());
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (Gasto g : gastoService.listarPorPeriodo(inicio, fin)) {
            if (g.getCategoria() == null || g.getValorTotal() == null) {
                continue;
            }
            mapa.merge(g.getCategoria().getId(), g.getValorTotal(), BigDecimal::add);
        }
        return mapa;
    }

    private String dinero(BigDecimal v) {
        NumberFormat f = NumberFormat.getInstance(new Locale("es", "CO"));
        f.setMaximumFractionDigits(0);
        return "$" + f.format(v);
    }
}