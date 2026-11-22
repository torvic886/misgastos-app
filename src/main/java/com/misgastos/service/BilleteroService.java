package com.misgastos.service;

import com.misgastos.model.Billetero;
import com.misgastos.repository.BilleteroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@Service
@Transactional
public class BilleteroService {

    private static final Logger log = LoggerFactory.getLogger(BilleteroService.class);
    
    @Autowired
    private BilleteroRepository billeteroRepository;
    
    // Crear nuevo billetero (o guardar cambios si ya tiene id)
    public Billetero guardar(Billetero billetero) {
        boolean nuevo = billetero.getId() == null;
        Billetero guardado = billeteroRepository.save(billetero);
        log.info(nuevo ? "Billetero creado" : "Billetero actualizado");
        log.debug("Billetero {}: id {}", nuevo ? "creado" : "actualizado", guardado.getId());
        return guardado;
    }
    
    // Actualizar billetero existente
    public Billetero actualizar(Billetero billetero) {
        Billetero guardado = billeteroRepository.save(billetero);
        log.info("Billetero actualizado");
        log.debug("Billetero actualizado: id {}", guardado.getId());
        return guardado;
    }
    
    // Eliminar billetero
    public void eliminar(Long id) {
        billeteroRepository.deleteById(id);
        log.info("Billetero eliminado");
        log.debug("Billetero eliminado: id {}", id);
    }
    
    // Obtener por ID
    public Optional<Billetero> obtenerPorId(Long id) {
        return billeteroRepository.findById(id);
    }
    
    // Listar todos
    public List<Billetero> listarTodos() {
        return billeteroRepository.findAllByOrderByFechaDesc();
    }
    
    // Buscar por rango de fechas
    public List<Billetero> buscarPorRangoFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        return billeteroRepository.findByFechaBetweenOrderByFechaDesc(fechaInicio, fechaFin);
    }
    
    // Buscar por fecha exacta
    public List<Billetero> buscarPorFecha(LocalDate fecha) {
        return billeteroRepository.findByFecha(fecha);
    }
    
    // Obtener totales generales
    public Map<String, BigDecimal> obtenerTotales() {
        Map<String, BigDecimal> totales = new HashMap<>();
        
        Double totalBilletero = billeteroRepository.getTotalBilletero();
        Double totalPremios = billeteroRepository.getTotalPremios();
        Double totalDiferencia = billeteroRepository.getTotalDiferencia();
        
        totales.put("billetero", totalBilletero != null ? BigDecimal.valueOf(totalBilletero) : BigDecimal.ZERO);
        totales.put("premios", totalPremios != null ? BigDecimal.valueOf(totalPremios) : BigDecimal.ZERO);
        totales.put("diferencia", totalDiferencia != null ? BigDecimal.valueOf(totalDiferencia) : BigDecimal.ZERO);
        
        return totales;
    }
    
    // Obtener totales por rango de fechas
    public Map<String, BigDecimal> obtenerTotalesPorFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        Map<String, BigDecimal> totales = new HashMap<>();
        
        Double totalBilletero = billeteroRepository.getTotalBilleteroByFechas(fechaInicio, fechaFin);
        Double totalPremios = billeteroRepository.getTotalPremiosByFechas(fechaInicio, fechaFin);
        Double totalDiferencia = billeteroRepository.getTotalDiferenciaByFechas(fechaInicio, fechaFin);
        
        totales.put("billetero", totalBilletero != null ? BigDecimal.valueOf(totalBilletero) : BigDecimal.ZERO);
        totales.put("premios", totalPremios != null ? BigDecimal.valueOf(totalPremios) : BigDecimal.ZERO);
        totales.put("diferencia", totalDiferencia != null ? BigDecimal.valueOf(totalDiferencia) : BigDecimal.ZERO);
        
        return totales;
    }
    
    /** Diferencia acumulada del mes y promedio por día de un registro. */
    public record Acumulado(BigDecimal diferenciaAcumulada, BigDecimal promedioDia) {
    }

    /**
     * Para cada registro de los meses que aparecen en la lista calcula la diferencia acumulada
     * desde el día 1 de su mes y el promedio (acumulada / día del mes, truncado como en el Excel).
     * Usa el mes completo aunque la lista esté filtrada. No guarda nada en la base.
     */
    @Transactional(readOnly = true)
    public Map<Long, Acumulado> calcularAcumulados(Collection<Billetero> mostrados) {
        Set<YearMonth> meses = new HashSet<>();
        for (Billetero b : mostrados) {
            meses.add(YearMonth.from(b.getFecha()));
        }

        Map<Long, Acumulado> resultado = new HashMap<>();
        for (YearMonth mes : meses) {
            List<Billetero> delMes = new ArrayList<>(
                    billeteroRepository.findByFechaBetweenOrderByFechaDesc(mes.atDay(1), mes.atEndOfMonth()));
            delMes.sort(Comparator.comparing(Billetero::getFecha).thenComparing(Billetero::getId));

            BigDecimal acumulado = BigDecimal.ZERO;
            for (Billetero b : delMes) {
                if (b.getDiferencia() != null) {
                    acumulado = acumulado.add(b.getDiferencia());
                }
                BigDecimal promedio = acumulado.divide(
                        BigDecimal.valueOf(b.getFecha().getDayOfMonth()), 0, RoundingMode.DOWN);
                resultado.put(b.getId(), new Acumulado(acumulado, promedio));
            }
            log.debug("Billeteros: acumulados calculados para {} ({} registros)", mes, delMes.size());
        }
        return resultado;
    }
    
}