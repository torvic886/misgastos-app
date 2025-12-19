package com.misgastos.config;

import com.misgastos.service.CategoriaService;
import com.misgastos.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private CategoriaService categoriaService;
    
    @Override
    public void run(String... args) {
        // Crear usuarios
        if (usuarioService.buscarPorUsername("admin").isEmpty()) {
            usuarioService.crearUsuario("admin", "admin123", "ADMIN");
            System.out.println("✅ Usuario admin creado");
        }
        
        if (usuarioService.buscarPorUsername("usuario").isEmpty()) {
            usuarioService.crearUsuario("usuario", "usuario123", "USER");
            System.out.println("✅ Usuario normal creado");
        }
        
        // Crear categorías y subcategorías
        if (categoriaService.listarCategorias().isEmpty()) {
            crearCategoriasIniciales();
        }
    }
    
    private void crearCategoriasIniciales() {
        // Alimentación
        var alimentos = categoriaService.crearCategoria("Alimentación", "🍔", "#FF6B6B");
        categoriaService.crearSubcategoria("Supermercado", alimentos.getId());
        categoriaService.crearSubcategoria("Restaurantes", alimentos.getId());
        categoriaService.crearSubcategoria("Comida rápida", alimentos.getId());
        
        // Transporte
        var transporte = categoriaService.crearCategoria("Transporte", "🚗", "#4ECDC4");
        categoriaService.crearSubcategoria("Gasolina", transporte.getId());
        categoriaService.crearSubcategoria("Taxi/Uber", transporte.getId());
        categoriaService.crearSubcategoria("Transporte público", transporte.getId());
        
        // Entretenimiento
        var entretenimiento = categoriaService.crearCategoria("Entretenimiento", "🎮", "#95E1D3");
        categoriaService.crearSubcategoria("Cine", entretenimiento.getId());
        categoriaService.crearSubcategoria("Streaming", entretenimiento.getId());
        categoriaService.crearSubcategoria("Juegos", entretenimiento.getId());
        
        // Salud
        var salud = categoriaService.crearCategoria("Salud", "💊", "#F38181");
        categoriaService.crearSubcategoria("Medicamentos", salud.getId());
        categoriaService.crearSubcategoria("Consultas médicas", salud.getId());
        categoriaService.crearSubcategoria("Gimnasio", salud.getId());
        
        // Servicios
        var servicios = categoriaService.crearCategoria("Servicios", "💡", "#FFA502");
        categoriaService.crearSubcategoria("Electricidad", servicios.getId());
        categoriaService.crearSubcategoria("Agua", servicios.getId());
        categoriaService.crearSubcategoria("Internet", servicios.getId());
        
        System.out.println("✅ Categorías y subcategorías creadas");
    }
}