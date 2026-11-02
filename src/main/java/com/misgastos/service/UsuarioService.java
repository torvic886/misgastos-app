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
import com.misgastos.model.Ventana;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

@Service
@Transactional
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private AuditoriaService auditoriaService;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    // ==================== REGLA: SOLO EL ADMINISTRADOR GENERAL ====================
    private void exigirAdministradorGeneral(String username) {
        boolean permitido = username != null && usuarioRepository.findByUsername(username)
            .map(u -> Rol.ADMIN_GENERAL.name().equals(u.getRol()))
            .orElse(false);
        if (!permitido) {
            log.warn("Gestión de usuarios rechazada: quien la pidió no es Administrador general");
            log.debug("Gestión de usuarios rechazada para '{}'", username);
            throw new RuntimeException("Solo el Administrador general puede gestionar usuarios");
        }
    }
    
    // ==================== CREAR USUARIO ====================
    public Usuario crearUsuario(String username, String password, String rol, String usuarioCreador) {
        return crearUsuario(username, password, rol, usuarioCreador, null);
    }

    // Misma creación, con avatar opcional ("HOMBRE" o "MUJER")
    public Usuario crearUsuario(String username, String password, String rol, String usuarioCreador, String avatar) {
        exigirAdministradorGeneral(usuarioCreador);
        if (Rol.ADMIN_GENERAL.name().equals(rol)) {
            log.warn("No se creó el usuario: no se puede asignar el rol Administrador general");
            throw new RuntimeException("El rol Administrador general no se puede asignar");
        }
    	
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
        
        exigirAdministradorGeneral(usuarioModificador);
        boolean destinoEsGeneral = Rol.ADMIN_GENERAL.name().equals(usuario.getRol());
        if (Rol.ADMIN_GENERAL.name().equals(rol) && !destinoEsGeneral) {
            throw new RuntimeException("El rol Administrador general no se puede asignar");
        }
        if (destinoEsGeneral && !Rol.ADMIN_GENERAL.name().equals(rol)) {
            throw new RuntimeException("No se puede quitar el rol Administrador general desde la pantalla");
        }
        if (destinoEsGeneral && !Boolean.TRUE.equals(activo)) {
            throw new RuntimeException("No se puede desactivar al Administrador general");
        }
        
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
        exigirAdministradorGeneral(usuarioModificador);
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
        
        exigirAdministradorGeneral(usuarioEliminador);
        if (Rol.ADMIN_GENERAL.name().equals(usuario.getRol())) {
            throw new RuntimeException("No se puede eliminar al Administrador general");
        }
        
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
    
    // ==================== PERMISOS POR VENTANA ====================
    /** Guarda las ventanas permitidas de un usuario. ventanas = null: vuelve a los permisos de su rol. */
    public Usuario guardarPermisos(Long id, Set<Ventana> ventanas, String usuarioModificador) {
        exigirAdministradorGeneral(usuarioModificador);
        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String textoAntes = usuario.getPermisos();
        Set<Ventana> antes = Ventana.efectivas(usuario.getRol(), textoAntes);

        String textoNuevo = null;
        Set<Ventana> despues = Ventana.porDefecto(usuario.getRol());
        if (ventanas != null) {
            Set<Ventana> limpio = EnumSet.noneOf(Ventana.class);
            limpio.addAll(ventanas);
            // efectivas() aplica la regla de Usuarios: solo el Administrador general la tiene
            despues = Ventana.efectivas(usuario.getRol(), Ventana.aTexto(limpio));
            textoNuevo = Ventana.aTexto(despues);
        }

        if (Objects.equals(textoAntes, textoNuevo)) {
            log.debug("Permisos sin cambios: usuario id {}", id);
            return usuario;
        }

        usuario.setPermisos(textoNuevo);
        Usuario guardado = usuarioRepository.save(usuario);

        String detalle = "Permisos de " + usuario.getUsername() + ": "
            + (textoNuevo == null ? "vuelven a los de su rol" : diferenciaPermisos(antes, despues));
        if (detalle.length() > 500) {
            detalle = detalle.substring(0, 497) + "...";
        }
        log.info("Permisos de un usuario actualizados");
        log.debug("Permisos actualizados: usuario id {}, {}", id, detalle);

        final String detalleFinal = detalle;
        usuarioRepository.findByUsername(usuarioModificador).ifPresent(m ->
            auditoriaService.registrarAccion(m.getId(), "PERMISOS_USUARIO", detalleFinal));

        return guardado;
    }

    private String diferenciaPermisos(Set<Ventana> antes, Set<Ventana> despues) {
        StringJoiner agregadas = new StringJoiner(", ");
        StringJoiner quitadas = new StringJoiner(", ");
        for (Ventana v : Ventana.values()) {
            if (despues.contains(v) && !antes.contains(v)) {
                agregadas.add(v.getEtiqueta());
            } else if (!despues.contains(v) && antes.contains(v)) {
                quitadas.add(v.getEtiqueta());
            }
        }
        StringBuilder sb = new StringBuilder();
        if (agregadas.length() > 0) {
            sb.append("Agregó: ").append(agregadas).append(". ");
        }
        if (quitadas.length() > 0) {
            sb.append("Quitó: ").append(quitadas).append(". ");
        }
        if (sb.length() == 0) {
            sb.append("Quedan como permisos personalizados, sin cambios de ventanas.");
        }
        return sb.toString().trim();
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
                .map(u -> Rol.ADMINISTRADOR.name().equals(u.getRol())
                        || Rol.ADMIN_GENERAL.name().equals(u.getRol()))
            .orElse(false);
    }
    
    public boolean existenUsuarios() {
        return usuarioRepository.count() > 0;
    }
}