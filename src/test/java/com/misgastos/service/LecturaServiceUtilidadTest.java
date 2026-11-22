package com.misgastos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.misgastos.model.Lectura;
import com.misgastos.model.Maquina;
import com.misgastos.repository.LecturaRepository;

@ExtendWith(MockitoExtension.class)
class LecturaServiceUtilidadTest {

    @Mock
    private LecturaRepository lecturaRepository; // falso: no usa la base de datos

    @InjectMocks
    private LecturaService servicio; // el servicio real, con el repositorio falso

    // Crea una lectura con denominación 10
    private Lectura lectura(long in, long out, long prem, int dia) {
        Lectura l = new Lectura();
        l.setFecha(LocalDate.of(2026, 11, dia));
        l.setContadorIn(in);
        l.setContadorOut(out);
        l.setContadorPrem(prem);
        l.setDenominacion(new BigDecimal("10"));
        return l;
    }

    @Test
    void utilidadNormal() {
        // 1. Preparar: lectura inicial y una normal
        Lectura inicial = lectura(1000, 800, 0, 1);
        inicial.setTipo(Lectura.INICIAL);
        Lectura normal = lectura(1500, 1100, 50, 8);
        normal.setTipo(Lectura.NORMAL);
        when(lecturaRepository.findByMaquinaIdAndAnuladaFalseOrderByFechaAscIdAsc(any()))
                .thenReturn(List.of(inicial, normal));

        // 2. Ejecutar
        BigDecimal resultado = servicio.utilidad(new Maquina(), null, null);

        // 3. Comparar: (500 - 300 - 50) x 10 = 1500
        assertEquals(0, new BigDecimal("1500").compareTo(resultado));
    }

    @Test
    void utilidadPuedeSerNegativa() {
        Lectura inicial = lectura(1000, 800, 0, 1);
        inicial.setTipo(Lectura.INICIAL);
        Lectura normal = lectura(1100, 1100, 0, 8);
        normal.setTipo(Lectura.NORMAL);
        when(lecturaRepository.findByMaquinaIdAndAnuladaFalseOrderByFechaAscIdAsc(any()))
                .thenReturn(List.of(inicial, normal));

        BigDecimal resultado = servicio.utilidad(new Maquina(), null, null);

        // (100 - 300 - 0) x 10 = -2000
        assertEquals(0, new BigDecimal("-2000").compareTo(resultado));
    }
}