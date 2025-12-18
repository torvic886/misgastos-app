package com.misgastos.config;

import com.misgastos.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Override
    public void run(String... args) {
        // Crear usuario admin si no existe
        if (usuarioService.buscarPorUsername("admin").isEmpty()) {
            usuarioService.crearUsuario("admin", "admin123", "ADMIN");
            System.out.println("✅ Usuario admin creado");
        }
        
        // Crear usuario normal si no existe
        if (usuarioService.buscarPorUsername("usuario").isEmpty()) {
            usuarioService.crearUsuario("usuario", "usuario123", "USER");
            System.out.println("✅ Usuario normal creado");
        }
    }
}