package com.misgastos.service;

import com.misgastos.model.Gasto;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Cuenta, por cédula y en un rango de fechas, los bonos, parqueaderos y bingos
 * de la categoría «Beneficios Clientes». Solo lee gastos; no guarda nada.
 */
@Service
public class ClienteMovimientoService {

    /** bonos y parqueaderos = número de gastos; bingos = suma del campo Cantidad. */
    public record Conteo(int bonos, int parqueaderos, int bingos) {
    }

    @Autowired
    private GastoService gastoService;

    public Map<String, Conteo> contar(LocalDate desde, LocalDate hasta) {
        Map<String, int[]> acumulado = new HashMap<>();

        for (Gasto g : gastoService.listarPorPeriodo(desde, hasta)) {
            if (g.getCategoria() == null || g.getSubcategoria() == null) continue;
            if (!"Beneficios Clientes".equalsIgnoreCase(g.getCategoria().getNombre())) continue;

            String cedula = g.getCedula() == null ? "" : g.getCedula().trim();
            if (cedula.isEmpty()) continue;

            String sub = g.getSubcategoria().getNombre().toLowerCase();
            int[] cuenta = acumulado.computeIfAbsent(cedula, k -> new int[3]);

            if (sub.contains("bono")) {
                cuenta[0]++;
            } else if (sub.contains("parqueo") || sub.contains("parqueadero")) {
                cuenta[1]++;
            } else if (sub.contains("bingo")) {
                cuenta[2] += g.getCantidad() == null ? 0 : g.getCantidad();
            }
        }

        Map<String, Conteo> resultado = new HashMap<>();
        acumulado.forEach((ced, c) -> resultado.put(ced, new Conteo(c[0], c[1], c[2])));
        return resultado;
    }
}