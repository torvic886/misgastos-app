package com.misgastos.service;

import com.misgastos.model.Billetero;
import com.misgastos.repository.BilleteroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class BilleteroService {
    
    @Autowired
    private BilleteroRepository billeteroRepository;
    
    // Crear nuevo billetero
    public Billetero guardar(Billetero billetero) {
        return billeteroRepository.save(billetero);
    }
    
    // Actualizar billetero existente
    public Billetero actualizar(Billetero billetero) {
        return billeteroRepository.save(billetero);
    }
    
    // Eliminar billetero
    public void eliminar(Long id) {
        billeteroRepository.deleteById(id);
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
}