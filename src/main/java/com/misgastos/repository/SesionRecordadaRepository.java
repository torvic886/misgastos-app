package com.misgastos.repository;

import com.misgastos.model.SesionRecordada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SesionRecordadaRepository extends JpaRepository<SesionRecordada, Long> {
    
    Optional<SesionRecordada> findByToken(String token);
    
    Optional<SesionRecordada> findByUsuarioId(Long usuarioId);
    
    void deleteByUsuarioId(Long usuarioId);
    
    void deleteByFechaExpiracionBefore(LocalDateTime fecha);
}