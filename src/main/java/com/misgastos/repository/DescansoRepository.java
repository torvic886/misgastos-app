package com.misgastos.repository;

import com.misgastos.model.Descanso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DescansoRepository extends JpaRepository<Descanso, Long> {

    List<Descanso> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    boolean existsByEmpleadoId(Long empleadoId);

}