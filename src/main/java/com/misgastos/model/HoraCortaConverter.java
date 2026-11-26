package com.misgastos.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Horas de turnos como texto HH:mm (08:30), igual que la migración V12. No es autoApply: solo donde se pida. */
@Converter
public class HoraCortaConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime hora) {
        return hora == null ? null : hora.format(FORMATO);
    }

    @Override
    public LocalTime convertToEntityAttribute(String texto) {
        return (texto == null || texto.isBlank()) ? null : LocalTime.parse(texto.trim(), FORMATO);
    }
}