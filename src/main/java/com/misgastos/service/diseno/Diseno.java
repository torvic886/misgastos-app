package com.misgastos.service.diseno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Sistema de diseño común de los informes PDF y Excel:
 * paleta de colores, nombre de la aplicación y formatos de presentación.
 */
public final class Diseno {

    private Diseno() {
    }

    public static final String APP = "ExpenseFlow";

    // ---------- Paleta (RGB) ----------
    public static final int[] TINTA = {31, 41, 55};               // texto principal
    public static final int[] TEXTO_SUAVE = {107, 114, 128};      // texto secundario
    public static final int[] LINEA = {229, 231, 235};            // separadores finos
    public static final int[] LINEA_FUERTE = {156, 163, 175};     // línea bajo encabezados
    public static final int[] FONDO_ENCABEZADO = {243, 244, 246}; // encabezados de tabla
    public static final int[] FONDO_ZEBRA = {249, 250, 251};      // filas alternas
    public static final int[] ACENTO = {55, 48, 163};             // color de acento
    public static final int[] ACENTO_SUAVE = {238, 242, 255};     // grupos y destacados
    public static final int[] POSITIVO = {4, 120, 87};            // disminución del gasto
    public static final int[] NEGATIVO = {185, 28, 28};           // aumento del gasto
    public static final int[] BLANCO = {255, 255, 255};

    private static final Locale CO = new Locale("es", "CO");
    private static final Locale ES = new Locale("es");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ---------- Formatos ----------

    /** Pesos colombianos sin decimales: $1.234.567 */
    public static String moneda(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        BigDecimal redondeado = valor.setScale(0, RoundingMode.HALF_UP);
        String numero = NumberFormat.getIntegerInstance(CO).format(redondeado.abs());
        return (redondeado.signum() < 0 ? "-$" : "$") + numero;
    }

    /** Pesos con signo explícito: +$1.000 / -$1.000 */
    public static String monedaConSigno(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        String texto = moneda(valor);
        return valor.signum() > 0 ? "+" + texto : texto;
    }

    public static String entero(long valor) {
        return NumberFormat.getIntegerInstance(CO).format(valor);
    }

    /** Recibe el porcentaje de 0 a 100: 12,5% */
    public static String porcentaje(double valor) {
        return String.format(CO, "%.1f%%", valor);
    }

    /** Variación con signo: +12,5% / -3,0% */
    public static String cambio(double valor) {
        return String.format(CO, "%+.1f%%", valor);
    }

    public static String fecha(LocalDate fecha) {
        return fecha == null ? "-" : fecha.format(FECHA);
    }

    public static String hora(Object hora) {
        if (hora == null) {
            return "-";
        }
        if (hora instanceof LocalTime) {
            return ((LocalTime) hora).format(HORA);
        }
        return hora.toString();
    }

    public static String fechaHoraActual() {
        return LocalDateTime.now().format(FECHA_HORA);
    }

    /** "Ene 26", "Feb 26"... */
    public static String etiquetaMes(YearMonth mes) {
        String nombre = mes.getMonth().getDisplayName(TextStyle.SHORT, ES).replace(".", "");
        String anio = String.valueOf(mes.getYear());
        return capitalizar(nombre) + " " + anio.substring(Math.max(0, anio.length() - 2));
    }

    /** "Enero", "Febrero"... */
    public static String nombreMes(int mes) {
        return capitalizar(java.time.Month.of(mes).getDisplayName(TextStyle.FULL, ES));
    }

    private static String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        return texto.substring(0, 1).toUpperCase(ES) + texto.substring(1);
    }
}
