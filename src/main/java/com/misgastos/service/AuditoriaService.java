package com.misgastos.service;

import com.misgastos.model.AuditoriaLog;
import com.misgastos.model.Usuario;
import com.misgastos.repository.AuditoriaLogRepository;
import com.misgastos.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final int MAX_DETALLES = 500; // largo de la columna detalles

    @Autowired
    private AuditoriaLogRepository auditoriaLogRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SesionActual sesionActual;

    public void registrarAccion(Long usuarioId, String accion, String detalles) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        AuditoriaLog registro = new AuditoriaLog();
        registro.setUsuario(usuario);
        registro.setAccion(accion);
        registro.setDetalles(detalles);

        auditoriaLogRepository.save(registro);
    }

    /** Anota un cambio de gasto a nombre del usuario actual. Nunca debe romper la operación del gasto. */
    public void registrarCambioGasto(String accion, String detalles) {
        try {
            if (!sesionActual.hayUsuario()) {
                log.warn("Auditoría omitida ({}): no hay usuario en sesión", accion);
                return;
            }
            String texto = detalles == null ? "" : detalles;
            if (texto.length() > MAX_DETALLES) {
                texto = texto.substring(0, MAX_DETALLES - 3) + "...";
            }
            registrarAccion(sesionActual.getUsuarioId(), accion, texto);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la auditoría ({}): {}", accion, e.getMessage());
        }
    }

    public List<AuditoriaLog> listarTodos() {
        return auditoriaLogRepository.findAll();
    }

    public List<AuditoriaLog> listarPorUsuario(Long usuarioId) {
        return auditoriaLogRepository.findByUsuarioId(usuarioId);
    }

    /** Filtros opcionales: usuarioId, accion, desde y hasta (hasta incluido). */
    public List<AuditoriaLog> buscar(Long usuarioId, String accion, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.of(2000, 1, 1);
        LocalDate h = hasta != null ? hasta : LocalDate.of(2100, 1, 1);
        String a = (accion == null || accion.isBlank()) ? null : accion;
        return auditoriaLogRepository.buscar(usuarioId, a, d.atStartOfDay(), h.plusDays(1).atStartOfDay());
    }

    public List<String> listarAcciones() {
        return auditoriaLogRepository.listarAcciones();
    }
}