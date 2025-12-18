package com.misgastos.repository;

import com.misgastos.model.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {
    
    List<Subcategoria> findByCategoriaId(Long categoriaId);
}