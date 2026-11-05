package com.misgastos.model;

import java.util.EnumSet;
import java.util.Set;

/** Ventanas del menú sobre las que se puede dar o quitar permiso a cada usuario. */
public enum Ventana {

    INICIO("Inicio", "GASTOS", "/fxml/inicio.fxml"),
    GASTOS("Mis Gastos", "GASTOS", "/fxml/registro-gasto.fxml", "/fxml/lista-gastos.fxml",
            "/fxml/buscar-editar-gastos.fxml", "/fxml/papelera.fxml"),
    INFORMES("Informes", "GASTOS", "/fxml/informes.fxml"),
    CONSULTAS("Consultas", "GASTOS", "/fxml/consulta-totales.fxml"),
    USUARIOS("Usuarios", "PERSONAS", "/fxml/gestion-usuarios.fxml"),
    CLIENTES("Clientes", "PERSONAS", "/fxml/clientes.fxml"),
    BILLETEROS("Billeteros", "PERSONAS", "/fxml/gestion-billeteros.fxml"),
    REPORTES_BILLETEROS("Reportes Billeteros", "PERSONAS", "/fxml/reportes-billeteros.fxml"),
    MAQUINAS("Máquinas", "MAQUINAS", "/fxml/gestion-maquinas.fxml"),
    CONTADORES("Contadores", "MAQUINAS", "/fxml/contadores.fxml"),
    PRESUPUESTOS("Presupuestos", "SISTEMA", "/fxml/presupuestos.fxml"),
    REGISTRO_CAMBIOS("Registro de cambios", "SISTEMA", "/fxml/registro-cambios.fxml"),
    CORREGIR_DATOS("Corregir datos", "SISTEMA", "/fxml/corregir-datos.fxml"),
    RESPALDO("Respaldo", "SISTEMA", "/fxml/respaldo.fxml"),
    DIAGNOSTICO("Diagnóstico y registros", "SISTEMA", "/fxml/diagnostico.fxml");

    private final String etiqueta;
    private final String grupo;
    private final String[] rutas;

    Ventana(String etiqueta, String grupo, String... rutas) {
        this.etiqueta = etiqueta;
        this.grupo = grupo;
        this.rutas = rutas;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getGrupo() {
        return grupo;
    }

    /** La ventana a la que pertenece una pantalla (ruta del .fxml), o null si no está controlada. */
    public static Ventana deRuta(String ruta) {
        if (ruta == null) {
            return null;
        }
        for (Ventana v : values()) {
            for (String r : v.rutas) {
                if (ruta.equals(r)) {
                    return v;
                }
            }
        }
        return null;
    }

    /** Lo que cada rol puede abrir hoy, sin permisos guardados. Es el comportamiento de siempre. */
    public static Set<Ventana> porDefecto(String rol) {
        if ("USUARIO".equals(rol) || "USER".equals(rol)) {
            return EnumSet.of(INICIO, GASTOS, CLIENTES, BILLETEROS, REPORTES_BILLETEROS);
        }
        EnumSet<Ventana> todas = EnumSet.allOf(Ventana.class);
        if (!"ADMIN_GENERAL".equals(rol)) {
            todas.remove(USUARIOS);
        }
        return todas;
    }

    /** Convierte "INICIO,GASTOS" en un conjunto; ignora nombres desconocidos. */
    public static Set<Ventana> desdeTexto(String texto) {
        EnumSet<Ventana> resultado = EnumSet.noneOf(Ventana.class);
        if (texto == null) {
            return resultado;
        }
        for (String parte : texto.split(",")) {
            String nombre = parte.trim();
            if (nombre.isEmpty()) {
                continue;
            }
            try {
                resultado.add(valueOf(nombre));
            } catch (IllegalArgumentException ignorada) {
                // un nombre que ya no existe no debe romper el inicio de sesión
            }
        }
        return resultado;
    }

    public static String aTexto(Set<Ventana> ventanas) {
        StringBuilder sb = new StringBuilder();
        for (Ventana v : values()) {
            if (ventanas.contains(v)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(v.name());
            }
        }
        return sb.toString();
    }

    /**
     * Ventanas efectivas de un usuario. El Administrador general siempre tiene todas,
     * incluidas las que se agreguen después, sin importar los permisos guardados.
     * Sin permisos guardados (null) los demás usan los de su rol.
     * Usuarios es exclusiva del Administrador general.
     */
    public static Set<Ventana> efectivas(String rol, String permisosGuardados) {
        if ("ADMIN_GENERAL".equals(rol)) {
            return EnumSet.allOf(Ventana.class);
        }
        if (permisosGuardados == null) {
            return porDefecto(rol);
        }
        Set<Ventana> resultado = desdeTexto(permisosGuardados);
        resultado.remove(USUARIOS);
        return resultado;
    }
}