package com.misgastos.repository;

import com.misgastos.model.Maquina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaquinaRepository extends JpaRepository<Maquina, Long> {

    Optional<Maquina> findByCodigo(Long codigo);

    boolean existsByCodigo(Long codigo);

    List<Maquina> findByActivaTrueOrderByCodigoAsc();

    List<Maquina> findByActivaFalseOrderByFechaBajaDesc();

}