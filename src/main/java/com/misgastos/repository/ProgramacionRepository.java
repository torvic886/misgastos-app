package com.misgastos.repository;

import com.misgastos.model.Programacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProgramacionRepository extends JpaRepository<Programacion, Long> {

    /** Rango de fechas incluido, ordenado como se muestra: día, turno y la encargada primero. */
    List<Programacion> findByFechaBetweenOrderByFechaAscTurnoOrdenAscPosicionAsc(LocalDate desde, LocalDate hasta);

    boolean existsByEmpleadoId(Long empleadoId);

    boolean existsByTurnoId(Long turnoId);

}