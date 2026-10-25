package com.misgastos.service;

import com.misgastos.model.SesionRecordada;
import com.misgastos.repository.SesionRecordadaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.Preferences;

@Slf4j
@Service
public class SesionRecordadaService {
    
    @Autowired
    private SesionRecordadaRepository sesionRepository;
    
    private static final String PREF_TOKEN_KEY = "session_token";
    private static final int DIAS_EXPIRACION = 30; // Token válido por 30 días
    
    /**
     * Crea una sesión "Recordarme" y la guarda en BD y en la PC
     */
    @Transactional
    public void crearSesionRecordada(Long usuarioId) {
        // 1. Generar token único
        String token = UUID.randomUUID().toString();
        
        // 2. Calcular fecha de expiración (30 días desde ahora)
        LocalDateTime fechaExpiracion = LocalDateTime.now().plusDays(DIAS_EXPIRACION);
        
        // 3. Eliminar sesión anterior si existe
        sesionRepository.deleteByUsuarioId(usuarioId);
        
        // 4. Crear nueva sesión en BD
        SesionRecordada sesion = new SesionRecordada(usuarioId, token, fechaExpiracion);
        sesionRepository.save(sesion);
        
        // 5. Guardar token en PC del usuario
        guardarTokenLocal(token);
        
        log.info("Sesión recordada creada para usuario {}", usuarioId);
    }
    
    /**
     * Valida si existe un token guardado localmente y si es válido en BD
     */
    public Optional<Long> validarSesionLocal() {
        // 1. Leer token de la PC
        String tokenLocal = leerTokenLocal();
        
        if (tokenLocal == null) {
            return Optional.empty();
        }
        
        // 2. Buscar token en BD
        Optional<SesionRecordada> sesionOpt = sesionRepository.findByToken(tokenLocal);
        
        if (sesionOpt.isEmpty()) {
            log.debug("Token local sin sesión en BD; se borra el token local");
            borrarTokenLocal(); // Token no existe en BD
            return Optional.empty();
        }
        
        SesionRecordada sesion = sesionOpt.get();
        
        // 3. Verificar si expiró
        if (sesion.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            log.debug("Sesión recordada expirada para usuario {}", sesion.getUsuarioId());
            eliminarSesion(sesion.getUsuarioId());
            return Optional.empty();
        }
        
        // 4. Token válido, retornar usuario_id
        log.debug("Sesión recordada válida para usuario {}", sesion.getUsuarioId());
        return Optional.of(sesion.getUsuarioId());
    }
    
    /**
     * Elimina la sesión de BD y PC
     */
    @Transactional
    public void eliminarSesion(Long usuarioId) {
        sesionRepository.deleteByUsuarioId(usuarioId);
        borrarTokenLocal();
        log.info("Sesión recordada eliminada para usuario {}", usuarioId);
    }
    
    /**
     * Limpia tokens expirados de la BD (ejecutar periódicamente)
     */
    @Transactional
    public void limpiarSesionesExpiradas() {
        sesionRepository.deleteByFechaExpiracionBefore(LocalDateTime.now());
    }
    
    // ========== MÉTODOS PRIVADOS PARA MANEJAR PREFERENCES ==========
    
    private void guardarTokenLocal(String token) {
        Preferences prefs = Preferences.userNodeForPackage(SesionRecordadaService.class);
        prefs.put(PREF_TOKEN_KEY, token);
    }
    
    private String leerTokenLocal() {
        Preferences prefs = Preferences.userNodeForPackage(SesionRecordadaService.class);
        return prefs.get(PREF_TOKEN_KEY, null);
    }
    
    private void borrarTokenLocal() {
        Preferences prefs = Preferences.userNodeForPackage(SesionRecordadaService.class);
        prefs.remove(PREF_TOKEN_KEY);
    }
}