package com.misgastos.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Converter
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public String convertToDatabaseColumn(LocalDateTime valor) {
        return valor == null ? null : valor.format(FORMATO);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String texto) {
        return texto == null || texto.isBlank() ? null
                : LocalDateTime.parse(texto.trim().replace(' ', 'T'));
    }
}