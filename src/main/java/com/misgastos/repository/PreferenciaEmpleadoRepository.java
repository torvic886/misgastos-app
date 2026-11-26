package com.misgastos.repository;

import com.misgastos.model.PreferenciaEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreferenciaEmpleadoRepository extends JpaRepository<PreferenciaEmpleado, Long> {

    List<PreferenciaEmpleado> findByEmpleadoId(Long empleadoId);

    List<PreferenciaEmpleado> findByTipo(String tipo);

    Optional<PreferenciaEmpleado> findByEmpleadoIdAndDiaSemanaAndTipo(Long empleadoId, String diaSemana, String tipo);
}