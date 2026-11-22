package com.misgastos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.misgastos.model.FilaContador;
import com.misgastos.model.Lectura;

/**
 * Hoja Promedio del Informe Mensual de Gestión: acumulado de billetes y premios por mes.
 * Los meses con lecturas de contadores se calculan solos; los meses sin lecturas
 * (anteriores al uso de la app) salen de la tabla promedio_mensual (V11).
 * Solo lectura sobre lecturas y máquinas: no cambia ningún dato de contadores.
 */
@Service
public class PromedioService {

    private static final Logger log = LoggerFactory.getLogger(PromedioService.class);

    public record FilaPromedio(int mes, BigDecimal bill, BigDecimal premios, boolean calculado) {
        public BigDecimal diferencia() {
            return bill.subtract(premios);
        }
    }

    @Autowired
    private LecturaService lecturaService;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Doce filas (enero a diciembre) del año. Los meses después de hastaMes quedan en cero.
     * Un mes con lecturas se calcula; si no tiene, se usan los valores guardados; si tampoco, cero.
     */
    public List<FilaPromedio> generar(int anio, int hastaMes) {
        log.debug("Promedio: generando año {} hasta el mes {}", anio, hastaMes);
        LocalDate fin = YearMonth.of(anio, hastaMes).atEndOfMonth();

        BigDecimal[] bill = new BigDecimal[13];
        BigDecimal[] premios = new BigDecimal[13];
        boolean[] conLecturas = new boolean[13];
        for (int m = 1; m <= 12; m++) {
            bill[m] = BigDecimal.ZERO;
            premios[m] = BigDecimal.ZERO;
        }

        // filas() entrega las lecturas de cada máquina en orden; la anterior de cada una
        // se guarda aquí para sacar los deltas igual que lo hace LecturaService
        Map<Long, Lectura> anterior = new HashMap<>();
        for (FilaContador f : lecturaService.filas(null, null, fin)) {
            Lectura l = f.lectura();
            Lectura ant = anterior.put(l.getMaquina().getId(), l);
            // total null = inicial, copia o reinicio: no suma
            if (ant == null || f.total() == null || l.getFecha().getYear() != anio) {
                continue;
            }
            int mes = l.getFecha().getMonthValue();
            BigDecimal den = l.getDenominacion();
            long deltaIn = l.getContadorIn() - ant.getContadorIn();
            long deltaPremios = (l.getContadorOut() - ant.getContadorOut())
                    + (l.getContadorPrem() - ant.getContadorPrem());
            bill[mes] = bill[mes].add(den.multiply(BigDecimal.valueOf(deltaIn)));
            premios[mes] = premios[mes].add(den.multiply(BigDecimal.valueOf(deltaPremios)));
            conLecturas[mes] = true;
        }

        Map<Integer, long[]> guardados = leerGuardados(anio);
        List<FilaPromedio> filas = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            if (m > hastaMes) {
                filas.add(new FilaPromedio(m, BigDecimal.ZERO, BigDecimal.ZERO, false));
            } else if (conLecturas[m]) {
                if (guardados.containsKey(m)) {
                    log.debug("Promedio: el mes {} tiene lecturas, se ignoran los valores guardados", m);
                }
                filas.add(new FilaPromedio(m, bill[m], premios[m], true));
            } else if (guardados.containsKey(m)) {
                long[] v = guardados.get(m);
                filas.add(new FilaPromedio(m, BigDecimal.valueOf(v[0]), BigDecimal.valueOf(v[1]), false));
            } else {
                filas.add(new FilaPromedio(m, BigDecimal.ZERO, BigDecimal.ZERO, false));
            }
        }
        log.info("Promedio: año {} generado hasta el mes {}", anio, hastaMes);
        return filas;
    }

    /** Guarda (o reemplaza) los valores de un mes que no tiene lecturas en la app. */
    public void guardarMes(int anio, int mes, long bill, long premios) {
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12");
        }
        if (bill < 0 || premios < 0) {
            throw new IllegalArgumentException("Bill y premios no pueden ser negativos");
        }
        jdbc.update("INSERT INTO promedio_mensual (anio, mes, bill, premios) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(anio, mes) DO UPDATE SET bill = excluded.bill, premios = excluded.premios",
                anio, mes, bill, premios);
        log.info("Promedio: valores guardados para {}-{}", anio, mes);
        log.debug("Promedio: {}-{} bill={} premios={}", anio, mes, bill, premios);
    }

    private Map<Integer, long[]> leerGuardados(int anio) {
        Map<Integer, long[]> mapa = new HashMap<>();
        jdbc.query("SELECT mes, bill, premios FROM promedio_mensual WHERE anio = ?",
                rs -> {
                    mapa.put(rs.getInt("mes"), new long[] {rs.getLong("bill"), rs.getLong("premios")});
                },
                anio);
        log.debug("Promedio: {} mes(es) guardados en {}", mapa.size(), anio);
        return mapa;
    }
}