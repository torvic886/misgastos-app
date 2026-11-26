package com.misgastos.util;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula los festivos de Colombia de un año según la Ley 51 de 1983 (Ley Emiliani).
 * Solo propone la lista: lo que se guarda y se usa es la tabla festivo, que se puede editar.
 * Confirmar con quien lleva la nómina antes de depender de estas fechas.
 */
@Slf4j
public final class FestivosColombia {

    /** Festivo calculado, todavía sin guardar. */
    public record FestivoCalculado(LocalDate fecha, String nombre) { }

    private FestivosColombia() { }

    public static List<FestivoCalculado> deAnio(int anio) {
        List<FestivoCalculado> lista = new ArrayList<>();

        // Fechas fijas: no se mueven
        lista.add(new FestivoCalculado(LocalDate.of(anio, 1, 1), "Año Nuevo"));
        lista.add(new FestivoCalculado(LocalDate.of(anio, 5, 1), "Día del Trabajo"));
        lista.add(new FestivoCalculado(LocalDate.of(anio, 7, 20), "Día de la Independencia"));
        lista.add(new FestivoCalculado(LocalDate.of(anio, 8, 7), "Batalla de Boyacá"));
        lista.add(new FestivoCalculado(LocalDate.of(anio, 12, 8), "Inmaculada Concepción"));
        lista.add(new FestivoCalculado(LocalDate.of(anio, 12, 25), "Navidad"));

        // Fechas que se pasan al lunes siguiente (si ya caen lunes, se quedan)
        lista.add(lunes(LocalDate.of(anio, 1, 6), "Día de los Reyes Magos"));
        lista.add(lunes(LocalDate.of(anio, 3, 19), "Día de San José"));
        lista.add(lunes(LocalDate.of(anio, 6, 29), "San Pedro y San Pablo"));
        lista.add(lunes(LocalDate.of(anio, 8, 15), "Asunción de la Virgen"));
        lista.add(lunes(LocalDate.of(anio, 10, 12), "Día de la Raza"));
        lista.add(lunes(LocalDate.of(anio, 11, 1), "Todos los Santos"));
        lista.add(lunes(LocalDate.of(anio, 11, 11), "Independencia de Cartagena"));

        // Dependen de la Semana Santa
        LocalDate pascua = domingoDePascua(anio);
        lista.add(new FestivoCalculado(pascua.minusDays(3), "Jueves Santo"));
        lista.add(new FestivoCalculado(pascua.minusDays(2), "Viernes Santo"));
        lista.add(lunes(pascua.plusDays(39), "Ascensión del Señor"));
        lista.add(lunes(pascua.plusDays(60), "Corpus Christi"));
        lista.add(lunes(pascua.plusDays(68), "Sagrado Corazón de Jesús"));

        lista.sort(Comparator.comparing(FestivoCalculado::fecha));
        log.debug("Festivos calculados para {}: {}", anio, lista.size());
        return lista;
    }

    /** Pasa la fecha al lunes siguiente; si ya es lunes, no la mueve. */
    private static FestivoCalculado lunes(LocalDate fecha, String nombre) {
        return new FestivoCalculado(fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)), nombre);
    }

    /** Domingo de Pascua (algoritmo de Meeus/Jones/Butcher, calendario gregoriano). */
    static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}