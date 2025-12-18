package com.misgastos.service;

import com.misgastos.model.AuditoriaLog;
import com.misgastos.model.Usuario;
import com.misgastos.repository.AuditoriaLogRepository;
import com.misgastos.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AuditoriaService {
    
    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    public void registrarAccion(Long usuarioId, String accion, String detalles) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        AuditoriaLog log = new AuditoriaLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setDetalles(detalles);
        
        auditoriaLogRepository.save(log);
    }
    
    public List<AuditoriaLog> listarTodos() {
        return auditoriaLogRepository.findAll();
    }
    
    public List<AuditoriaLog> listarPorUsuario(Long usuarioId) {
        return auditoriaLogRepository.findByUsuarioId(usuarioId);
    }
}