package com.misgastos.repository;

import com.misgastos.model.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    List<AuditoriaLog> findByUsuarioId(Long usuarioId);

    List<AuditoriaLog> findTop50ByOrderByFechaDesc();

    @Query("""
        SELECT a FROM AuditoriaLog a
        WHERE (:usuarioId IS NULL OR a.usuario.id = :usuarioId)
          AND (:accion IS NULL OR a.accion = :accion)
          AND a.fecha >= :desde AND a.fecha < :hasta
        ORDER BY a.fecha DESC
        """)
    List<AuditoriaLog> buscar(@Param("usuarioId") Long usuarioId,
                              @Param("accion") String accion,
                              @Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta);

    @Query("SELECT DISTINCT a.accion FROM AuditoriaLog a ORDER BY a.accion")
    List<String> listarAcciones();
}