package com.misgastos.service;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.Subcategoria;
import com.misgastos.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consulta flexible de totales: el usuario combina filtros (periodo, categoría,
 * subcategoría, producto, persona, quién registró) y obtiene la suma, la cantidad
 * y un desglose.
 *
 * Es un servicio NUEVO: no modifica ni reemplaza ningún cálculo existente.
 *
 * Regla clave para que las cuentas cuadren: el total, el promedio y el desglose
 * se calculan a partir de EXACTAMENTE las mismas filas que se devuelven en el
 * detalle. Por eso la suma de lo que se ve en pantalla siempre es igual al total.
 */
@Service
public class ConsultaTotalesService {

    public static final String AGRUPAR_AUTOMATICO = "Automático (según los filtros)";
    public static final String AGRUPAR_CATEGORIA = "Categoría";
    public static final String AGRUPAR_SUBCATEGORIA = "Subcategoría";
    public static final String AGRUPAR_PRODUCTO = "Producto";
    public static final String AGRUPAR_MES = "Mes";
    public static final String AGRUPAR_USUARIO = "Registrado por";

    private static final Locale CO = Locale.forLanguageTag("es-CO");

    @PersistenceContext
    private EntityManager em;

    // ==================== MODELOS ====================

    /** Filtros de la consulta. Todo lo que sea null o vacío significa "sin filtrar". */
    public static class Filtro {
        public LocalDate desde;
        public LocalDate hasta;
        public Long categoriaId;
        public Long subcategoriaId;
        public Long usuarioId;
        public String producto;
        public boolean productoExacto;
        public String persona;
    }

    /** Una fila del desglose (un grupo). */
    public record Fila(String nombre, BigDecimal total, long cantidad, double porcentaje,
                       Long categoriaId, Long subcategoriaId, String producto) {
    }

    public record Resultado(List<Gasto> gastos, BigDecimal total, long cantidad,
                            BigDecimal promedio, List<Fila> desglose, String agrupadoPor) {
    }

    private static class Acumulado {
        String nombre;
        BigDecimal total = BigDecimal.ZERO;
        long cantidad;
        Long categoriaId;
        Long subcategoriaId;
        String producto;
        YearMonth mes;
    }

    // ==================== LISTAS PARA LOS FILTROS ====================

    @Transactional(readOnly = true)
    public List<Categoria> categorias() {
        return em.createQuery("SELECT c FROM Categoria c ORDER BY c.nombre", Categoria.class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<Subcategoria> subcategorias(Long categoriaId) {
        return em.createQuery(
                "SELECT s FROM Subcategoria s WHERE s.categoria.id = :categoriaId ORDER BY s.nombre",
                Subcategoria.class)
                .setParameter("categoriaId", categoriaId)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<Usuario> usuarios() {
        return em.createQuery("SELECT u FROM Usuario u ORDER BY u.username", Usuario.class)
                .getResultList();
    }

    // ==================== CONSULTA ====================

    @Transactional(readOnly = true)
    public Resultado consultar(Filtro f, String agrupacion) {

        StringBuilder jpql = new StringBuilder(
                "SELECT g FROM Gasto g "
                + "JOIN FETCH g.categoria JOIN FETCH g.subcategoria JOIN FETCH g.usuario "
                + "WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();

        if (f.desde != null) {
            jpql.append(" AND g.fecha >= :desde");
            params.put("desde", f.desde);
        }
        if (f.hasta != null) {
            jpql.append(" AND g.fecha <= :hasta");
            params.put("hasta", f.hasta);
        }
        if (f.categoriaId != null) {
            jpql.append(" AND g.categoria.id = :categoriaId");
            params.put("categoriaId", f.categoriaId);
        }
        if (f.subcategoriaId != null) {
            jpql.append(" AND g.subcategoria.id = :subcategoriaId");
            params.put("subcategoriaId", f.subcategoriaId);
        }
        if (f.usuarioId != null) {
            jpql.append(" AND g.usuario.id = :usuarioId");
            params.put("usuarioId", f.usuarioId);
        }
        if (hayTexto(f.producto)) {
            if (f.productoExacto) {
                // Misma comparación que usa GastoRepository.existeProducto
                jpql.append(" AND UPPER(TRIM(REPLACE(REPLACE(g.producto, '  ', ' '), '  ', ' '))) "
                        + "= UPPER(TRIM(REPLACE(REPLACE(:producto, '  ', ' '), '  ', ' ')))");
            } else {
                // Misma búsqueda que usa GastoRepository.buscarProductos
                jpql.append(" AND UPPER(g.producto) LIKE UPPER(CONCAT('%', :producto, '%'))");
            }
            params.put("producto", f.producto.trim());
        }
        if (hayTexto(f.persona)) {
            jpql.append(" AND (UPPER(g.cedula) LIKE UPPER(CONCAT('%', :persona, '%')) "
                    + "OR UPPER(g.nombrePersona) LIKE UPPER(CONCAT('%', :persona, '%')))");
            params.put("persona", f.persona.trim());
        }
        jpql.append(" ORDER BY g.fecha DESC, g.hora DESC");

        TypedQuery<Gasto> query = em.createQuery(jpql.toString(), Gasto.class);
        params.forEach(query::setParameter);
        List<Gasto> gastos = query.getResultList();

        // ----- Total: suma de las mismas filas que se muestran -----
        BigDecimal total = BigDecimal.ZERO;
        for (Gasto g : gastos) {
            total = total.add(valor(g));
        }
        long cantidad = gastos.size();
        BigDecimal promedio = cantidad == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);

        String agrupadoPor = resolverAgrupacion(agrupacion, f);
        List<Fila> desglose = desglosar(gastos, agrupadoPor, f, total);

        return new Resultado(gastos, total, cantidad, promedio, desglose, agrupadoPor);
    }

    // ==================== DESGLOSE ====================

    private String resolverAgrupacion(String agrupacion, Filtro f) {
        if (agrupacion != null && !agrupacion.isBlank() && !agrupacion.startsWith("Auto")) {
            return agrupacion;
        }
        // Automático: baja un nivel según lo que el usuario ya filtró
        if (hayTexto(f.producto) || f.subcategoriaId != null) {
            return AGRUPAR_PRODUCTO;
        }
        if (f.categoriaId != null) {
            return AGRUPAR_SUBCATEGORIA;
        }
        return AGRUPAR_CATEGORIA;
    }

    private List<Fila> desglosar(List<Gasto> gastos, String agrupadoPor, Filtro f, BigDecimal total) {
        Map<Object, Acumulado> grupos = new LinkedHashMap<>();

        for (Gasto g : gastos) {
            Object clave;
            String nombre;
            Long categoriaId = g.getCategoria().getId();
            Long subcategoriaId = g.getSubcategoria().getId();
            String producto = null;
            YearMonth mes = null;

            if (AGRUPAR_CATEGORIA.equals(agrupadoPor)) {
                clave = categoriaId;
                nombre = g.getCategoria().getNombre();
                subcategoriaId = null;
            } else if (AGRUPAR_SUBCATEGORIA.equals(agrupadoPor)) {
                clave = subcategoriaId;
                nombre = f.categoriaId == null
                        ? g.getCategoria().getNombre() + " › " + g.getSubcategoria().getNombre()
                        : g.getSubcategoria().getNombre();
            } else if (AGRUPAR_PRODUCTO.equals(agrupadoPor)) {
                producto = g.getProducto() == null ? "" : g.getProducto().trim();
                clave = claveProducto(producto);
                nombre = producto.isEmpty() ? "(sin nombre)" : producto;
                categoriaId = null;
                subcategoriaId = null;
            } else if (AGRUPAR_MES.equals(agrupadoPor)) {
                mes = YearMonth.from(g.getFecha());
                clave = mes;
                nombre = nombreMes(mes);
                categoriaId = null;
                subcategoriaId = null;
            } else { // Registrado por
                clave = g.getUsuario().getId();
                nombre = g.getUsuario().getUsername();
                categoriaId = null;
                subcategoriaId = null;
            }

            Acumulado a = grupos.get(clave);
            if (a == null) {
                a = new Acumulado();
                a.nombre = nombre;
                a.categoriaId = categoriaId;
                a.subcategoriaId = subcategoriaId;
                a.producto = producto;
                a.mes = mes;
                grupos.put(clave, a);
            }
            a.total = a.total.add(valor(g));
            a.cantidad++;
        }

        List<Acumulado> lista = new ArrayList<>(grupos.values());
        if (AGRUPAR_MES.equals(agrupadoPor)) {
            lista.sort((x, y) -> y.mes.compareTo(x.mes)); // más reciente primero
        } else {
            lista.sort((x, y) -> y.total.compareTo(x.total)); // el que más pesa primero
        }

        List<Fila> filas = new ArrayList<>();
        for (Acumulado a : lista) {
            double porcentaje = total.signum() == 0
                    ? 0
                    : a.total.multiply(BigDecimal.valueOf(100))
                            .divide(total, 4, RoundingMode.HALF_UP).doubleValue();
            filas.add(new Fila(a.nombre, a.total, a.cantidad, porcentaje,
                    a.categoriaId, a.subcategoriaId, a.producto));
        }
        return filas;
    }

    // ==================== AUXILIARES ====================

    /** Valor del gasto con 2 decimales exactos, para que las sumas no arrastren residuos. */
    private static BigDecimal valor(Gasto g) {
        return g.getValorTotal() == null
                ? BigDecimal.ZERO
                : g.getValorTotal().setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean hayTexto(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Clave para agrupar productos igual que los compara la base de datos en el
     * filtro "exacto": sin espacios de más y con mayúsculas solo en a-z (como el
     * UPPER de SQLite). Así, al hacer doble clic en un producto del desglose, el
     * total filtrado es idéntico al del grupo.
     */
    private static String claveProducto(String s) {
        String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        StringBuilder sb = new StringBuilder(t.length());
        for (char c : t.toCharArray()) {
            sb.append(c >= 'a' && c <= 'z' ? (char) (c - 32) : c);
        }
        return sb.toString();
    }

    private static String nombreMes(YearMonth ym) {
        String mes = ym.getMonth().getDisplayName(TextStyle.FULL, CO);
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + ym.getYear();
    }
}