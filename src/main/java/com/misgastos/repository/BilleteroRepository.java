package com.misgastos.repository;

import com.misgastos.model.Billetero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BilleteroRepository extends JpaRepository<Billetero, Long> {
    
    // Buscar por rango de fechas
    List<Billetero> findByFechaBetweenOrderByFechaDesc(LocalDate fechaInicio, LocalDate fechaFin);
    
    // Buscar todos ordenados por fecha descendente
    List<Billetero> findAllByOrderByFechaDesc();
    
    // Buscar por fecha exacta
    List<Billetero> findByFecha(LocalDate fecha);
    
    // Obtener totales para el panel de resumen
    @Query("SELECT SUM(b.billetero) FROM Billetero b")
    Double getTotalBilletero();
    
    @Query("SELECT SUM(b.premios) FROM Billetero b")
    Double getTotalPremios();
    
    @Query("SELECT SUM(b.diferencia) FROM Billetero b")
    Double getTotalDiferencia();
    
    // Obtener totales por rango de fechas
    @Query("SELECT SUM(b.billetero) FROM Billetero b WHERE b.fecha BETWEEN :inicio AND :fin")
    Double getTotalBilleteroByFechas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
    
    @Query("SELECT SUM(b.premios) FROM Billetero b WHERE b.fecha BETWEEN :inicio AND :fin")
    Double getTotalPremiosByFechas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
    
    @Query("SELECT SUM(b.diferencia) FROM Billetero b WHERE b.fecha BETWEEN :inicio AND :fin")
    Double getTotalDiferenciaByFechas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}