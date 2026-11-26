package com.misgastos.repository;

import com.misgastos.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    List<Turno> findByActivoTrueOrderByOrdenAsc();

    boolean existsByNombreIgnoreCase(String nombre);

}