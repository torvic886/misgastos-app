package com.misgastos.repository;

import com.misgastos.model.Lectura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface LecturaRepository extends JpaRepository<Lectura, Long> {

    List<Lectura> findByMaquinaIdOrderByFechaAscIdAsc(Long maquinaId);

    List<Lectura> findByMaquinaIdAndAnuladaFalseOrderByFechaAscIdAsc(Long maquinaId);

    boolean existsByMaquinaId(Long maquinaId);

    long countByMaquinaId(Long maquinaId);

    @Transactional
    void deleteByMaquinaId(Long maquinaId);
}