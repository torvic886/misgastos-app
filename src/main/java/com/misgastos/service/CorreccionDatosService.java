package com.misgastos.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Corrección masiva de ortografía en los gastos ya registrados (producto y
 * nombre de la persona). Va por SQL directo para incluir también los gastos
 * de la papelera: así, al restaurar uno no vuelve el texto mal escrito.
 *
 * Los campos permitidos son una lista fija (enum Campo): el nombre de la
 * columna nunca viene de texto escrito por el usuario.
 *
 * Los mensajes de IllegalArgumentException están pensados para mostrarse
 * tal cual al usuario.
 */
@Service
public class CorreccionDatosService {

    private static final Logger log = LoggerFactory.getLogger(CorreccionDatosService.class);

    /** Máximo de valores por consulta (SQLite limita los parámetros). */
    private static final int LOTE = 500;
    private static final int MAX_LARGO = 100;

    @Autowired
    private AuditoriaService auditoriaService;

    @PersistenceContext
    private EntityManager entityManager;

    /** Campos de la tabla gastos que se pueden corregir. */
    public enum Campo {
        PRODUCTO("Producto", "producto", "CORREGIR_PRODUCTO"),
        NOMBRE_PERSONA("Nombre de la persona", "nombre_persona", "CORREGIR_NOMBRE_PERSONA");

        private final String etiqueta;
        private final String columna;
        private final String accionAuditoria;

        Campo(String etiqueta, String columna, String accionAuditoria) {
            this.etiqueta = etiqueta;
            this.columna = columna;
            this.accionAuditoria = accionAuditoria;
        }

        public String columna() {
            return columna;
        }

        public String accionAuditoria() {
            return accionAuditoria;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Un valor tal como está guardado y en cuántos gastos aparece. */
    public record ValorConteo(String valor, long cantidad) {
    }

    /** Resultado de corregir: gastos cambiados y clientes actualizados. */
    public record Resultado(int gastos, int clientes) {
    }

    /** Valores distintos del campo (tal cual están guardados) con su cantidad de gastos. */
    @Transactional(readOnly = true)
    public List<ValorConteo> listar(Campo campo) {
        String c = campo.columna();
        String sql = "SELECT " + c + ", COUNT(*) FROM gastos WHERE " + c + " IS NOT NULL AND TRIM(" + c
            + ") <> '' GROUP BY " + c + " ORDER BY " + c + " COLLATE NOCASE";
        @SuppressWarnings("unchecked")
        List<Object[]> filas = entityManager.createNativeQuery(sql).getResultList();
        List<ValorConteo> lista = new ArrayList<>(filas.size());
        for (Object[] f : filas) {
            lista.add(new ValorConteo((String) f[0], ((Number) f[1]).longValue()));
        }
        log.debug("Valores distintos de {}: {}", c, lista.size());
        return lista;
    }

    /** Cuántos gastos cambiarían si los valores se reemplazan por el nuevo texto. */
    @Transactional(readOnly = true)
    public long contarAfectados(Campo campo, List<String> valores, String nuevoTexto) {
        String nuevo = nuevoTexto == null ? "" : nuevoTexto.replaceAll("\\s+", " ").trim();
        if (valores == null || valores.isEmpty() || nuevo.isEmpty()) {
            return 0;
        }
        String c = campo.columna();
        long total = 0;
        for (List<String> lote : lotes(valores)) {
            Number n = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM gastos WHERE " + c + " IN (:valores) AND " + c + " <> :nuevo")
                .setParameter("valores", lote)
                .setParameter("nuevo", nuevo)
                .getSingleResult();
            total += n.longValue();
        }
        return total;
    }

    /** Cuántas cédulas distintas tienen esos nombres (para avisar antes de corregir un nombre). */
    @Transactional(readOnly = true)
    public int contarCedulas(List<String> nombres) {
        return cedulasDe(nombres).size();
    }

    /**
     * Reemplaza los valores por el texto correcto en todos los gastos (papelera incluida).
     * Para el nombre de la persona, si actualizarClientes es true, también actualiza la
     * lista de Clientes de las cédulas afectadas. Todo en una sola transacción.
     */
    @Transactional
    public Resultado corregir(Campo campo, List<String> valores, String nuevoTexto, boolean actualizarClientes) {
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un valor de la lista.");
        }
        String nuevo = nuevoTexto == null ? "" : nuevoTexto.replaceAll("\\s+", " ").trim();
        if (nuevo.isEmpty()) {
            throw new IllegalArgumentException("Escribe el texto correcto.");
        }
        if (nuevo.length() > MAX_LARGO) {
            throw new IllegalArgumentException("El texto no puede pasar de " + MAX_LARGO + " letras.");
        }

        String c = campo.columna();
        boolean tocaClientes = campo == Campo.NOMBRE_PERSONA && actualizarClientes;

        // Las cédulas se buscan ANTES de cambiar los gastos, porque después ya no se
        // podrían encontrar por el nombre viejo.
        List<String> cedulas = tocaClientes ? cedulasDe(valores) : List.of();

        int gastos = 0;
        for (List<String> lote : lotes(valores)) {
            gastos += entityManager.createNativeQuery(
                    "UPDATE gastos SET " + c + " = :nuevo WHERE " + c + " IN (:valores) AND " + c + " <> :nuevo")
                .setParameter("nuevo", nuevo)
                .setParameter("valores", lote)
                .executeUpdate();
        }
        log.info("Corrección de {}: {} gasto(s) actualizados", c, gastos);
        log.debug("Corrección de {}: {} valor(es) → '{}'", c, valores.size(), nuevo);

        int clientes = 0;
        for (List<String> lote : lotes(cedulas)) {
            clientes += entityManager.createQuery(
                    "UPDATE Cliente c SET c.nombre = :nuevo WHERE c.cedula IN :cedulas AND c.nombre <> :nuevo")
                .setParameter("nuevo", nuevo)
                .setParameter("cedulas", lote)
                .executeUpdate();
        }
        if (tocaClientes) {
            log.info("Corrección de nombre: {} cliente(s) actualizados", clientes);
            log.debug("Cédulas revisadas en Clientes: {}", cedulas.size());
        }

        if (gastos > 0 || clientes > 0) {
            String detalle = campo + ": " + resumir(valores) + " → '" + nuevo + "'; " + gastos
                + " gasto(s) corregidos" + (tocaClientes ? ", " + clientes + " cliente(s) actualizados" : "");
            auditoriaService.registrarCambioGasto(campo.accionAuditoria(), detalle);
        } else {
            log.warn("Corrección de {} sin cambios: los gastos ya tenían ese texto", c);
        }
        return new Resultado(gastos, clientes);
    }

    private List<String> cedulasDe(List<String> nombres) {
        List<String> cedulas = new ArrayList<>();
        if (nombres == null) {
            return cedulas;
        }
        for (List<String> lote : lotes(nombres)) {
            @SuppressWarnings("unchecked")
            List<String> encontradas = entityManager.createNativeQuery(
                    "SELECT DISTINCT TRIM(cedula) FROM gastos WHERE nombre_persona IN (:valores) "
                    + "AND cedula IS NOT NULL AND TRIM(cedula) <> ''")
                .setParameter("valores", lote)
                .getResultList();
            for (String ced : encontradas) {
                if (!cedulas.contains(ced)) {
                    cedulas.add(ced);
                }
            }
        }
        return cedulas;
    }

    private static List<List<String>> lotes(List<String> todos) {
        List<List<String>> lotes = new ArrayList<>();
        for (int i = 0; i < todos.size(); i += LOTE) {
            lotes.add(todos.subList(i, Math.min(i + LOTE, todos.size())));
        }
        return lotes;
    }

    /** Hasta 5 valores viejos para la auditoría, para que el detalle no sea enorme. */
    private static String resumir(List<String> valores) {
        List<String> primeros = valores.size() > 5 ? valores.subList(0, 5) : valores;
        String texto = "'" + String.join("', '", primeros) + "'";
        return valores.size() > 5 ? texto + " y " + (valores.size() - 5) + " más" : texto;
    }
}