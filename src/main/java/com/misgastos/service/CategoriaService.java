package com.misgastos.service;

import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;
import com.misgastos.repository.CategoriaRepository;
import com.misgastos.repository.SubcategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoriaService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private SubcategoriaRepository subcategoriaRepository;
    
    public Categoria crearCategoria(String nombre, String icono, String color) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setIcono(icono);
        categoria.setColor(color);
        return categoriaRepository.save(categoria);
    }
    
    public Subcategoria crearSubcategoria(String nombre, Long categoriaId) {
        Optional<Categoria> categoria = categoriaRepository.findById(categoriaId);
        if (categoria.isEmpty()) {
            throw new RuntimeException("Categoría no encontrada");
        }
        
        Subcategoria subcategoria = new Subcategoria();
        subcategoria.setNombre(nombre);
        subcategoria.setCategoria(categoria.get());
        return subcategoriaRepository.save(subcategoria);
    }
    
    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAll();
    }
    
    public List<Subcategoria> listarSubcategoriasPorCategoria(Long categoriaId) {
        return subcategoriaRepository.findByCategoriaId(categoriaId);
    }
    
    public Optional<Categoria> buscarCategoriaPorId(Long id) {
        return categoriaRepository.findById(id);
    }
}