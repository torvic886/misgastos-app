package com.misgastos.repository;

import com.misgastos.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {
    
    List<Gasto> findByUsuarioId(Long usuarioId);
    
    List<Gasto> findByFechaBetween(LocalDate inicio, LocalDate fin);
    
    List<Gasto> findByCategoriaId(Long categoriaId);
    
    List<Gasto> findTop10ByProductoContainingIgnoreCaseOrderByFechaDesc(String producto);
    
    Optional<Gasto> findTopByProductoIgnoreCaseOrderByFechaDescHoraDesc(String producto);
    
    @Query("SELECT SUM(g.valorTotal) FROM Gasto g WHERE g.fecha BETWEEN :inicio AND :fin")
    BigDecimal sumarGastosPorPeriodo(LocalDate inicio, LocalDate fin);
    
    @Query("SELECT g.categoria.nombre, SUM(g.valorTotal) FROM Gasto g GROUP BY g.categoria.nombre")
    List<Object[]> sumarPorCategoria();
    
    @Query("SELECT g.producto, SUM(g.valorTotal) FROM Gasto g GROUP BY g.producto ORDER BY SUM(g.valorTotal) DESC")
    List<Object[]> topProductos();
    
    @Query("""
    	    SELECT g FROM Gasto g
    	    WHERE UPPER(g.producto) LIKE UPPER(CONCAT('%', :producto, '%'))
    	    ORDER BY g.fecha DESC, g.hora DESC
    	""")
    	List<Gasto> buscarPorProductoLike(@Param("producto") String producto);
}