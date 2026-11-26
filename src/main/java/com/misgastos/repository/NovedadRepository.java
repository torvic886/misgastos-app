package com.misgastos.repository;

import com.misgastos.model.Novedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NovedadRepository extends JpaRepository<Novedad, Long> {

    /** Novedades que tocan el rango pedido (se cruzan con él, aunque empiecen antes o terminen después). */
    List<Novedad> findByFechaDesdeLessThanEqualAndFechaHastaGreaterThanEqualOrderByFechaDesdeAsc(LocalDate hasta, LocalDate desde);

    boolean existsByEmpleadoIdOrEmpleado2Id(Long empleadoId, Long empleado2Id);


    /** Novedades de una persona que se cruzan con el rango pedido. */
    List<Novedad> findByEmpleadoIdAndFechaDesdeLessThanEqualAndFechaHastaGreaterThanEqual(Long empleadoId, LocalDate hasta, LocalDate desde);

    /** Historial de una persona, de la más reciente a la más antigua. */
    List<Novedad> findByEmpleadoIdOrderByFechaDesdeDesc(Long empleadoId);

}
