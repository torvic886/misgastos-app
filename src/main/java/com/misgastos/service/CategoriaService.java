package com.misgastos.service;

import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;
import com.misgastos.repository.CategoriaRepository;
import com.misgastos.repository.SubcategoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoriaService {

    private static final Logger log = LoggerFactory.getLogger(CategoriaService.class);
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private SubcategoriaRepository subcategoriaRepository;
    
    public Categoria crearCategoria(String nombre, String icono, String color) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setIcono(icono);
        categoria.setColor(color);
        Categoria guardada = categoriaRepository.save(categoria);
        log.info("Categoría creada");
        log.debug("Categoría creada: id {}, nombre '{}'", guardada.getId(), nombre);
        return guardada;
    }
    
    public Subcategoria crearSubcategoria(String nombre, Long categoriaId) {
        Optional<Categoria> categoria = categoriaRepository.findById(categoriaId);
        if (categoria.isEmpty()) {
            log.warn("No se creó la subcategoría: la categoría no existe");
            log.debug("Categoría inexistente al crear subcategoría: id {}", categoriaId);
            throw new RuntimeException("Categoría no encontrada");
        }
        
        Subcategoria subcategoria = new Subcategoria();
        subcategoria.setNombre(nombre);
        subcategoria.setCategoria(categoria.get());
        Subcategoria guardada = subcategoriaRepository.save(subcategoria);
        log.info("Subcategoría creada");
        log.debug("Subcategoría creada: id {}, nombre '{}', categoría id {}", guardada.getId(), nombre, categoriaId);
        return guardada;
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
    
    public Categoria crearSiNoExiste(String nombre) {

        String limpio = nombre.trim();

        return categoriaRepository
            .findByNombreIgnoreCase(limpio)
            .orElseGet(() -> {
                Categoria c = new Categoria();
                c.setNombre(limpio);
                Categoria guardada = categoriaRepository.save(c);
                log.info("Categoría creada (no existía)");
                log.debug("Categoría creada: id {}, nombre '{}'", guardada.getId(), limpio);
                return guardada;
            });
    }

    @Transactional
    public Subcategoria crearSubcategoriaSiNoExiste(Long categoriaId, String nombre) {

        String limpio = nombre.trim();

        return subcategoriaRepository
            .findByCategoriaIdAndNombreIgnoreCase(categoriaId, limpio)
            .orElseGet(() -> {
                Subcategoria s = new Subcategoria();
                s.setNombre(limpio);
                s.setCategoria(
                    categoriaRepository.findById(categoriaId)
                        .orElseThrow(() -> new RuntimeException("Categoría no encontrada"))
                );
                Subcategoria guardada = subcategoriaRepository.save(s);
                log.info("Subcategoría creada (no existía)");
                log.debug("Subcategoría creada: id {}, nombre '{}', categoría id {}", guardada.getId(), limpio, categoriaId);
                return guardada;
            });
    }

}