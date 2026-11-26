package com.misgastos.repository;

import com.misgastos.model.Festivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FestivoRepository extends JpaRepository<Festivo, Long> {

    List<Festivo> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    boolean existsByFecha(LocalDate fecha);

}