package com.misgastos.repository;

import com.misgastos.model.PresupuestoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PresupuestoCategoriaRepository extends JpaRepository<PresupuestoCategoria, Long> {
}
