package com.misgastos.repository;

import com.misgastos.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {
    
    List<Gasto> findByUsuarioId(Long usuarioId);
    
    List<Gasto> findByFechaBetween(LocalDate inicio, LocalDate fin);
    
    List<Gasto> findByCategoriaId(Long categoriaId);
    
    @Query("SELECT SUM(g.valorTotal) FROM Gasto g WHERE g.fecha BETWEEN :inicio AND :fin")
    BigDecimal sumarGastosPorPeriodo(LocalDate inicio, LocalDate fin);
}