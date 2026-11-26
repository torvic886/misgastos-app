package com.misgastos.repository;

import com.misgastos.model.EncargadaSemana;
import com.misgastos.model.EncargadaSemanaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EncargadaSemanaRepository extends JpaRepository<EncargadaSemana, EncargadaSemanaId> {

    /** El texto es el lunes en formato yyyy-MM-dd. */
    List<EncargadaSemana> findBySemanaInicio(String semanaInicio);

    List<EncargadaSemana> findBySemanaInicioBetween(String desde, String hasta);

    default List<EncargadaSemana> deSemana(LocalDate lunes) {
        return findBySemanaInicio(lunes.toString());
    }

    default List<EncargadaSemana> entre(LocalDate desde, LocalDate hasta) {
        return findBySemanaInicioBetween(desde.toString(), hasta.toString());
    }
}