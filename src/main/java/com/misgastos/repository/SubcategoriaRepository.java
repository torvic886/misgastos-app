package com.misgastos.repository;

import com.misgastos.model.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {

	 List<Subcategoria> findByCategoriaId(Long categoriaId);
	
    Optional<Subcategoria> findByCategoriaIdAndNombreIgnoreCase(
        Long categoriaId,
        String nombre
    );

}
