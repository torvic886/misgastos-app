package com.misgastos.service;

import com.misgastos.model.Usuario;
import com.misgastos.model.Rol;
import com.misgastos.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private AuditoriaService auditoriaService;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    // ==================== CREAR USUARIO ====================
    public Usuario crearUsuario(String username, String password, String rol, String usuarioCreador) {
        return crearUsuario(username, password, rol, usuarioCreador, null);
    }

    // Misma creación, con avatar opcional ("HOMBRE" o "MUJER")
    public Usuario crearUsuario(String username, String password, String rol, String usuarioCreador, String avatar) {
        if (usuarioRepository.existsByUsername(username)) {
            log.warn("No se creó el usuario: el nombre ya existe");
            log.debug("Nombre de usuario duplicado al crear: '{}'", username);
            throw new RuntimeException("El usuario ya existe");
        }
        
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setFechaCreacion(LocalDateTime.now());
        usuario.setAvatar(avatar);
        
        Usuario saved = usuarioRepository.save(usuario);
        log.info("Usuario creado");
        log.debug("Usuario creado: id {}, nombre '{}', rol {}", saved.getId(), username, rol);
        
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
        return actualizarUsuario(id, username, rol, activo, usuarioModificador, null);
    }

    // Misma actualización, con avatar opcional (si es null, el avatar no cambia)
    public Usuario actualizarUsuario(Long id, String username, String rol, Boolean activo, String usuarioModificador, String avatar) {
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
        
        if (avatar != null && !avatar.equals(usuario.getAvatar())) {
            cambios += "Avatar: " + usuario.getAvatar() + " → " + avatar + "; ";
            usuario.setAvatar(avatar);
        }

        Usuario updated = usuarioRepository.save(usuario);
        
        if (!cambios.isEmpty()) {
            log.info("Usuario actualizado");
            log.debug("Usuario actualizado: id {}, cambios: {}", id, cambios);
            Optional<Usuario> modificador = usuarioRepository.findByUsername(usuarioModificador);
            if (modificador.isPresent()) {
                auditoriaService.registrarAccion(
                    modificador.get().getId(),
                    "ACTUALIZAR_USUARIO",
                    "Usuario modificado: " + username + " - Cambios: " + cambios
                );
            }
        } else {
            log.debug("Actualización de usuario sin cambios: id {}", id);
        }
        
        return updated;
    }
    
    // ==================== CAMBIAR CONTRASEÑA ====================
    public void cambiarPassword(Long id, String nuevaPassword, String usuarioModificador) {
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
        log.info("Contraseña de un usuario cambiada");
        log.debug("Contraseña cambiada: usuario id {}", id);
        
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
        log.info("Usuario eliminado");
        log.debug("Usuario eliminado: id {}, nombre '{}'", id, username);
        
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
                log.info("Inicio de sesión exitoso");
                log.debug("Inicio de sesión exitoso: usuario '{}'", username);
                auditoriaService.registrarAccion(
                    usuario.get().getId(),
                    "LOGIN",
                    "Inicio de sesión exitoso"
                );
            } else {
                log.warn("Inicio de sesión fallido: contraseña incorrecta");
                log.debug("Contraseña incorrecta para el usuario '{}'", username);
            }
            
            return autenticado;
        }
        
        if (usuario.isEmpty()) {
            log.warn("Inicio de sesión fallido: el usuario no existe");
            log.debug("Usuario inexistente al iniciar sesión: '{}'", username);
        } else {
            log.warn("Inicio de sesión fallido: el usuario está inactivo");
            log.debug("Usuario inactivo al iniciar sesión: '{}'", username);
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
    
    public boolean existenUsuarios() {
        return usuarioRepository.count() > 0;
    }
}