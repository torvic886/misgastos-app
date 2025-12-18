package com.misgastos.service;

import com.misgastos.model.Usuario;
import com.misgastos.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UsuarioService {
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public Usuario crearUsuario(String username, String password, String rol) {
        if (usuarioRepository.existsByUsername(username)) {
            throw new RuntimeException("El usuario ya existe");
        }
        
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(true);
        
        return usuarioRepository.save(usuario);
    }
    
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }
    
    public boolean autenticar(String username, String password) {
        Optional<Usuario> usuario = usuarioRepository.findByUsername(username);
        if (usuario.isPresent() && usuario.get().getActivo()) {
            return passwordEncoder.matches(password, usuario.get().getPassword());
        }
        return false;
    }
    
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }
}