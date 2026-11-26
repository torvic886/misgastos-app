package com.misgastos.repository;

import com.misgastos.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    List<Empleado> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

}