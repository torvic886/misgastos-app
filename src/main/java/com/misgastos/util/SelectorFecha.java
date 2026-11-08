package com.misgastos.util;

import javafx.scene.control.DatePicker;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Da a un DatePicker: escritura manual dd/MM/yyyy con barras automáticas y validación. */
public final class SelectorFecha {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FORMATO_CORTO =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private SelectorFecha() { }

    public static void configurar(DatePicker dp) {
        dp.setEditable(true);
        dp.setPromptText("dd/mm/aaaa");

        dp.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate fecha) {
                return fecha == null ? "" : FORMATO.format(fecha);
            }

            @Override
            public LocalDate fromString(String texto) {
                if (texto == null || texto.isBlank()) {
                    return null;
                }
                LocalDate fecha = leer(texto);
                // Texto inválido: se conserva la fecha que ya tenía
                return fecha != null ? fecha : dp.getValue();
            }
        });

        // Solo dígitos, con la barra puesta sola: 07 -> 07/  y  07/11 -> 07/11/
        dp.getEditor().setTextFormatter(new TextFormatter<String>(cambio -> {
            if (!cambio.isContentChange()) {
                return cambio;
            }
            if (cambio.getText().isEmpty()) {
                return cambio;   // al borrar no se reformatea: el cursor se queda donde está
            }
            String digitos = cambio.getControlNewText().replaceAll("[^0-9]", "");
            if (digitos.length() > 8) {
                digitos = digitos.substring(0, 8);
            }
            boolean escribiendo = !cambio.getText().isEmpty();
            if (escribiendo && digitos.length() == 1 && digitos.charAt(0) > '3') {
                digitos = "0" + digitos;                      // 7 -> 07
            }
            if (escribiendo && digitos.length() == 3 && digitos.charAt(2) > '1') {
                digitos = digitos.substring(0, 2) + "0" + digitos.charAt(2);   // 07/8 -> 07/08
            }
            StringBuilder sb = new StringBuilder(digitos);
            if (digitos.length() > 4) {
                sb.insert(4, '/');
            }
            if (digitos.length() > 2) {
                sb.insert(2, '/');
            }
            if (escribiendo && (digitos.length() == 2 || digitos.length() == 4)) {
                sb.append('/');
            }
            cambio.setRange(0, cambio.getControlText().length());
            cambio.setText(sb.toString());
            cambio.selectRange(sb.length(), sb.length());
            return cambio;
        }));

        // Validación al salir del campo: si no es una fecha real, avisa y vuelve a la anterior
        dp.getEditor().focusedProperty().addListener((obs, antes, enfocado) -> {
            if (enfocado) {
                return;
            }
            String texto = dp.getEditor().getText();
            if (texto == null || texto.isBlank()) {
                dp.setValue(null);
                marcar(dp, false);
                return;
            }
            LocalDate fecha = leer(texto);
            if (fecha == null) {
                marcar(dp, true);
                dp.getEditor().setText(dp.getConverter().toString(dp.getValue()));
            } else {
                marcar(dp, false);
                dp.setValue(fecha);
                dp.getEditor().setText(FORMATO.format(fecha));
            }
        });
    }

    private static LocalDate leer(String texto) {
        String t = texto.trim();
        try {
            return LocalDate.parse(t, FORMATO);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(t, FORMATO_CORTO);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static void marcar(DatePicker dp, boolean invalida) {
        dp.getEditor().getStyleClass().remove("fecha-invalida");
        if (invalida) {
            dp.getEditor().getStyleClass().add("fecha-invalida");
        }
    }
}