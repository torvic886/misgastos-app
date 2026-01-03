package com.misgastos.service;

import com.misgastos.model.Usuario;
import com.misgastos.model.Rol;
import com.misgastos.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UsuarioService {
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private AuditoriaService auditoriaService;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    // ==================== CREAR USUARIO ====================
    public Usuario crearUsuario(String username, String password, String rol, String usuarioCreador) {
        if (usuarioRepository.existsByUsername(username)) {
            throw new RuntimeException("El usuario ya existe");
        }
        
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setFechaCreacion(LocalDateTime.now());
        
        Usuario saved = usuarioRepository.save(usuario);
        
        // ✅ Auditoría: obtener ID del usuario creador
        Optional<Usuario> creador = usuarioRepository.findByUsername(usuarioCreador);
        if (creador.isPresent()) {
            auditoriaService.registrarAccion(
                creador.get().getId(),
                "CREAR_USUARIO",
                "Usuario creado: " + username + " con rol: " + rol
            );
        }
        
        return saved;
    }
    
    // ==================== ACTUALIZAR USUARIO ====================
    public Usuario actualizarUsuario(Long id, String username, String rol, Boolean activo, String usuarioModificador) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        String cambios = "";
        
        if (!usuario.getUsername().equals(username)) {
            cambios += "Username: " + usuario.getUsername() + " → " + username + "; ";
            usuario.setUsername(username);
        }
        
        if (!usuario.getRol().equals(rol)) {
            cambios += "Rol: " + usuario.getRol() + " → " + rol + "; ";
            usuario.setRol(rol);
        }
        
        if (!usuario.getActivo().equals(activo)) {
            cambios += "Activo: " + usuario.getActivo() + " → " + activo + "; ";
            usuario.setActivo(activo);
        }
        
        Usuario updated = usuarioRepository.save(usuario);
        
        if (!cambios.isEmpty()) {
            Optional<Usuario> modificador = usuarioRepository.findByUsername(usuarioModificador);
            if (modificador.isPresent()) {
                auditoriaService.registrarAccion(
                    modificador.get().getId(),
                    "ACTUALIZAR_USUARIO",
                    "Usuario modificado: " + username + " - Cambios: " + cambios
                );
            }
        }
        
        return updated;
    }
    
    // ==================== CAMBIAR CONTRASEÑA ====================
    public void cambiarPassword(Long id, String nuevaPassword, String usuarioModificador) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
        
        Optional<Usuario> modificador = usuarioRepository.findByUsername(usuarioModificador);
        if (modificador.isPresent()) {
            auditoriaService.registrarAccion(
                modificador.get().getId(),
                "CAMBIAR_PASSWORD",
                "Contraseña cambiada para usuario: " + usuario.getUsername()
            );
        }
    }
    
    // ==================== ELIMINAR USUARIO ====================
    public void eliminarUsuario(Long id, String usuarioEliminador) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        String username = usuario.getUsername();
        usuarioRepository.delete(usuario);
        
        Optional<Usuario> eliminador = usuarioRepository.findByUsername(usuarioEliminador);
        if (eliminador.isPresent()) {
            auditoriaService.registrarAccion(
                eliminador.get().getId(),
                "ELIMINAR_USUARIO",
                "Usuario eliminado: " + username
            );
        }
    }
    
    // ==================== AUTENTICACIÓN ====================
    public boolean autenticar(String username, String password) {
        Optional<Usuario> usuario = usuarioRepository.findByUsername(username);
        if (usuario.isPresent() && usuario.get().getActivo()) {
            boolean autenticado = passwordEncoder.matches(password, usuario.get().getPassword());
            
            if (autenticado) {
                auditoriaService.registrarAccion(
                    usuario.get().getId(),
                    "LOGIN",
                    "Inicio de sesión exitoso"
                );
            }
            
            return autenticado;
        }
        return false;
    }
    
    // ==================== CONSULTAS ====================
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }
    
    public Optional<Usuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }
    
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }
    
    public List<Usuario> listarActivos() {
        return usuarioRepository.findAll().stream()
            .filter(Usuario::getActivo)
            .toList();
    }
    
    public boolean esAdministrador(String username) {
        return buscarPorUsername(username)
            .map(u -> Rol.ADMINISTRADOR.name().equals(u.getRol()))
            .orElse(false);
    }
}