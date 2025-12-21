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

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {
    
    List<Gasto> findByUsuarioId(Long usuarioId);
    
    List<Gasto> findByFechaBetween(LocalDate inicio, LocalDate fin);
    
    List<Gasto> findByCategoriaId(Long categoriaId);
    
    @Query("SELECT SUM(g.valorTotal) FROM Gasto g WHERE g.fecha BETWEEN :inicio AND :fin")
    BigDecimal sumarGastosPorPeriodo(LocalDate inicio, LocalDate fin);
    
    @Query("SELECT g.categoria.nombre, SUM(g.valorTotal) FROM Gasto g GROUP BY g.categoria.nombre")
    List<Object[]> sumarPorCategoria();
    
    @Query("SELECT g.producto, SUM(g.valorTotal) FROM Gasto g GROUP BY g.producto ORDER BY SUM(g.valorTotal) DESC")
    List<Object[]> topProductos();
    
    @Query("""
        SELECT g FROM Gasto g
        WHERE g.usuario.id = :usuarioId
        ORDER BY g.fecha DESC, g.hora DESC
    """)
    List<Gasto> findUltimosPorUsuario(
        @Param("usuarioId") Long usuarioId,
        Pageable pageable
    );

    default List<Gasto> findUltimosPorUsuario(Long usuarioId, int limite) {
        return findUltimosPorUsuario(usuarioId, PageRequest.of(0, limite));
    }
    
    @Query("""
        SELECT DISTINCT g.producto
        FROM Gasto g
        WHERE UPPER(g.producto) LIKE UPPER(CONCAT('%', :texto, '%'))
        ORDER BY g.producto
    """)
    List<String> buscarProductos(@Param("texto") String texto);
    
    /**
     * ✅ VERIFICACIÓN MEJORADA DE EXISTENCIA
     * 
     * Normaliza espacios antes de comparar:
     * - TRIM elimina espacios al inicio/final
     * - REPLACE elimina espacios múltiples
     * - UPPER hace comparación case-insensitive
     */
    @Query("""
        SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END
        FROM Gasto g
        WHERE UPPER(TRIM(REPLACE(REPLACE(g.producto, '  ', ' '), '  ', ' '))) 
            = UPPER(TRIM(REPLACE(REPLACE(:producto, '  ', ' '), '  ', ' ')))
    """)
    boolean existeProducto(@Param("producto") String producto);

    /**
     * ✅ BÚSQUEDA MEJORADA DE ÚLTIMOS GASTOS POR PRODUCTO
     */
    @Query("""
        SELECT g
        FROM Gasto g
        WHERE UPPER(TRIM(REPLACE(REPLACE(g.producto, '  ', ' '), '  ', ' '))) 
            = UPPER(TRIM(REPLACE(REPLACE(:producto, '  ', ' '), '  ', ' ')))
        ORDER BY g.fecha DESC, g.hora DESC
    """)
    List<Gasto> ultimosGastosPorProducto(@Param("producto") String producto);
}