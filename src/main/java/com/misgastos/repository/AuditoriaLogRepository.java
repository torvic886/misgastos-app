package com.misgastos.repository;

import com.misgastos.model.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {
    
    List<AuditoriaLog> findByUsuarioId(Long usuarioId);
    
    List<AuditoriaLog> findTop50ByOrderByFechaDesc();
}