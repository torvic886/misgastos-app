package com.misgastos.repository;

import com.misgastos.model.HorarioEspecial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HorarioEspecialRepository extends JpaRepository<HorarioEspecial, Long> {

    List<HorarioEspecial> findByTurnoId(Long turnoId);

    Optional<HorarioEspecial> findByTurnoIdAndTipoDia(Long turnoId, String tipoDia);
}