package com.misgastos.service;

import com.misgastos.model.Lectura;
import com.misgastos.repository.LecturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Borra lecturas de contadores (no toca las máquinas). La primera lectura de cada
 * máquina (INICIAL y su copia del mismo día) nunca se borra.
 * seleccionar() solo cuenta; borrar() elimina de verdad lo que seleccionó.
 */
@Service
@Transactional
public class LecturasBorradoService {

    private static final Logger log = LoggerFactory.getLogger(LecturasBorradoService.class);

    @Autowired
    private LecturaRepository lecturaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public enum Modo { MES, DESDE_MES, TODO }

    public record Seleccion(List<Long> ids, int maquinas) { }

    @Transactional(readOnly = true)
    public Seleccion seleccionar(Modo modo, int anio, int mes) {
        LocalDate desde = null;
        LocalDate hasta = null;
        if (modo != Modo.TODO) {
            desde = LocalDate.of(anio, mes, 1);
            if (modo == Modo.MES) {
                hasta = desde.withDayOfMonth(desde.lengthOfMonth());
            }
        }

        List<Lectura> todas = lecturaRepository.findAll();

        // Primera lectura de cada máquina: INICIAL y la copia con su misma fecha
        Set<String> inicios = new HashSet<>();
        for (Lectura l : todas) {
            if (Lectura.INICIAL.equals(l.getTipo()) && !l.isAnulada()) {
                inicios.add(l.getMaquina().getId() + "|" + l.getFecha());
            }
        }

        List<Long> ids = new ArrayList<>();
        Set<Long> maquinas = new HashSet<>();
        for (Lectura l : todas) {
            boolean protegida = !l.isAnulada() && (Lectura.INICIAL.equals(l.getTipo())
                    || (Lectura.COPIA.equals(l.getTipo())
                            && inicios.contains(l.getMaquina().getId() + "|" + l.getFecha())));
            if (protegida) {
                continue;
            }
            boolean dentro = (desde == null || !l.getFecha().isBefore(desde))
                    && (hasta == null || !l.getFecha().isAfter(hasta));
            if (dentro) {
                ids.add(l.getId());
                maquinas.add(l.getMaquina().getId());
            }
        }
        log.debug("Selección para borrar lecturas: {} lecturas, {} máquinas", ids.size(), maquinas.size());
        return new Seleccion(ids, maquinas.size());
    }

    public int borrar(Seleccion seleccion, String descripcion) {
        List<Long> ids = seleccion.ids();
        for (int i = 0; i < ids.size(); i += 500) {
            lecturaRepository.deleteAllByIdInBatch(ids.subList(i, Math.min(i + 500, ids.size())));
        }
        log.info("Lecturas de contadores borradas");
        log.debug("Lecturas borradas: {}", ids.size());
        auditoriaService.registrarCambio("BORRAR_LECTURAS",
                "Lecturas borradas: " + ids.size() + " de " + seleccion.maquinas() + " máquina(s). " + descripcion);
        return ids.size();
    }
}