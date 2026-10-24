package com.misgastos.service;

import com.misgastos.model.Gasto;
import com.misgastos.model.Usuario;
import com.misgastos.model.Categoria;
import com.misgastos.model.Subcategoria;
import com.misgastos.repository.GastoRepository;
import com.misgastos.repository.UsuarioRepository;
import com.misgastos.repository.CategoriaRepository;
import com.misgastos.repository.SubcategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import com.misgastos.util.ProductoNormalizador;
import java.util.LinkedHashMap;

@Service
@Transactional
public class GastoService {

    private static final String FLECHA = " → ";

    @Autowired
    private GastoRepository gastoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Gasto registrarGasto(Long usuarioId, Long categoriaId, Long subcategoriaId, String producto,
            Integer cantidad, BigDecimal valorUnitario, String notas, String cedula, String nombrePersona) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        Subcategoria subcategoria = subcategoriaRepository.findById(subcategoriaId)
                .orElseThrow(() -> new RuntimeException("Subcategoría no encontrada"));

        Gasto gasto = new Gasto();
        gasto.setFecha(LocalDate.now());
        gasto.setHora(LocalTime.now());
        gasto.setUsuario(usuario);
        gasto.setCategoria(categoria);
        gasto.setSubcategoria(subcategoria);
        gasto.setProducto(producto);
        gasto.setCantidad(cantidad);
        gasto.setValorUnitario(valorUnitario);
        gasto.setValorTotal(valorUnitario.multiply(BigDecimal.valueOf(cantidad)));
        gasto.setNotas(notas);
        gasto.setCedula(cedula);
        gasto.setNombrePersona(nombrePersona);

        return gastoRepository.save(gasto);
    }

    public List<Gasto> listarTodos() {
        return gastoRepository.findAll();
    }

    public List<Gasto> listarPorUsuario(Long usuarioId) {
        return gastoRepository.findByUsuarioId(usuarioId);
    }

    public List<Gasto> listarPorPeriodo(LocalDate inicio, LocalDate fin) {
        return gastoRepository.findByFechaBetween(inicio, fin);
    }

    public BigDecimal calcularTotalPorPeriodo(LocalDate inicio, LocalDate fin) {
        BigDecimal total = gastoRepository.sumarGastosPorPeriodo(inicio, fin);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Map<String, BigDecimal> obtenerGastosPorCategoria() {
        List<Object[]> resultados = gastoRepository.sumarPorCategoria();
        Map<String, BigDecimal> mapa = new HashMap<>();
        for (Object[] obj : resultados) {
            mapa.put((String) obj[0], (BigDecimal) obj[1]);
        }
        return mapa;
    }

    public List<String> buscarProductos(String texto) {
        String t = ProductoNormalizador.clave(texto);
        if (t.isEmpty())
            return List.of();
        Map<String, String> unicos = new LinkedHashMap<>();
        for (String p : gastoRepository.productosActivos()) {
            String c = ProductoNormalizador.clave(p);
            if (c.contains(t))
                unicos.putIfAbsent(c, p);
        }
        return unicos.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }

    public List<Map<String, Object>> obtenerTopProductos(int limite) {
        List<Object[]> resultados = gastoRepository.topProductos();
        return resultados.stream().limit(limite).map(obj -> {
            Map<String, Object> item = new HashMap<>();
            item.put("producto", obj[0]);
            item.put("total", obj[1]);
            return item;
        }).collect(Collectors.toList());
    }

    // Borrado lógico: el gasto va a la papelera
    public void eliminarGasto(Long id) {
        Gasto gasto = gastoRepository.findById(id).orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        String etiqueta = etiqueta(gasto);
        String resumen = resumen(gasto);

        gasto.setFechaEliminacion(LocalDateTime.now());
        gastoRepository.save(gasto);

        auditoriaService.registrarCambioGasto("BORRAR_GASTO",
                "Borrado a la papelera del " + etiqueta + ": " + resumen);
    }

    public Optional<Gasto> buscarUltimoGastoPorProducto(String producto) {
        String clave = ProductoNormalizador.clave(producto);
        if (clave.isEmpty())
            return Optional.empty();
        List<String> escrituras = gastoRepository.productosActivos().stream()
                .filter(p -> ProductoNormalizador.clave(p).equals(clave))
                .toList();
        if (escrituras.isEmpty())
            return Optional.empty();
        List<Gasto> lista = gastoRepository.findByProductoInOrderByFechaDescHoraDesc(escrituras);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    public List<Gasto> obtenerUltimosGastos(Long usuarioId, int limite) {
        return gastoRepository.findUltimosPorUsuario(usuarioId, limite);
    }

    public List<Gasto> obtenerUltimosGastosGeneral(int limite) {
        return gastoRepository.findUltimos(limite);
    }

    public boolean existeProducto(String producto) {
        String clave = ProductoNormalizador.clave(producto);
        if (clave.isEmpty())
            return false;
        return gastoRepository.productosActivos().stream()
                .anyMatch(p -> ProductoNormalizador.clave(p).equals(clave));
    }

    public List<Gasto> buscarTodosPorPeriodo(LocalDate inicio, LocalDate fin) {
        return gastoRepository.findByFechaBetweenOrderByFechaDescHoraDesc(inicio, fin);
    }

    public List<Gasto> buscarPorFecha(LocalDate fecha) {
        return gastoRepository.findByFechaExacta(fecha);
    }

    public List<Gasto> buscarPorUsuarioYPeriodo(Long usuarioId, LocalDate inicio, LocalDate fin) {
        return gastoRepository.findByUsuarioAndFechaBetween(usuarioId, inicio, fin);
    }

    public Gasto actualizarGasto(Long id, Long categoriaId, Long subcategoriaId, String producto, Integer cantidad,
            BigDecimal valorUnitario, String notas, LocalDate fecha, LocalTime hora) {

        Gasto gasto = gastoRepository.findById(id).orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        Subcategoria subcategoria = subcategoriaRepository.findById(subcategoriaId)
                .orElseThrow(() -> new RuntimeException("Subcategoría no encontrada"));

        producto = unificarProducto(producto);
        
        // Antes de modificar: anotar qué va a cambiar (comparando por id, no por entidad)
        String etiqueta = etiqueta(gasto);
        List<String> cambios = new ArrayList<>();

        if (!Objects.equals(gasto.getCategoria().getId(), categoria.getId()))
            cambios.add("categoría " + gasto.getCategoria().getNombre() + FLECHA + categoria.getNombre());
        if (!Objects.equals(gasto.getSubcategoria().getId(), subcategoria.getId()))
            cambios.add("subcategoría " + gasto.getSubcategoria().getNombre() + FLECHA + subcategoria.getNombre());
        if (!Objects.equals(gasto.getProducto(), producto))
            cambios.add("producto " + gasto.getProducto() + FLECHA + producto);
        if (!Objects.equals(gasto.getCantidad(), cantidad))
            cambios.add("cantidad " + gasto.getCantidad() + FLECHA + cantidad);
        if (gasto.getValorUnitario().compareTo(valorUnitario) != 0)
            cambios.add("valor unitario " + dinero(gasto.getValorUnitario()) + FLECHA + dinero(valorUnitario));
        if (!textoOVacio(gasto.getNotas()).equals(textoOVacio(notas)))
            cambios.add("notas \"" + textoOVacio(gasto.getNotas()) + "\"" + FLECHA + "\"" + textoOVacio(notas) + "\"");
        if (!Objects.equals(gasto.getFecha(), fecha))
            cambios.add("fecha " + gasto.getFecha() + FLECHA + fecha);
        if (!mismaHora(gasto.getHora(), hora))
            cambios.add("hora " + horaCorta(gasto.getHora()) + FLECHA + horaCorta(hora));

        gasto.setCategoria(categoria);
        gasto.setSubcategoria(subcategoria);
        gasto.setProducto(producto);
        gasto.setCantidad(cantidad);
        gasto.setValorUnitario(valorUnitario);
        gasto.setValorTotal(valorUnitario.multiply(BigDecimal.valueOf(cantidad)));
        gasto.setNotas(notas);
        gasto.setFecha(fecha);
        gasto.setHora(hora);

        Gasto guardado = gastoRepository.save(gasto);

        if (!cambios.isEmpty()) {
            auditoriaService.registrarCambioGasto("EDITAR_GASTO",
                    "Edición del " + etiqueta + ": " + String.join(", ", cambios));
        }

        return guardado;
    }

    public Optional<Gasto> buscarPorId(Long id) {
        return gastoRepository.findById(id);
    }

    public Gasto registrarGasto(Long usuarioId, Long categoriaId, Long subcategoriaId, String producto,
            Integer cantidad, BigDecimal valorUnitario, String notas, String cedula) {
        return registrarGasto(usuarioId, categoriaId, subcategoriaId, producto, cantidad, valorUnitario, notas, cedula,
                null);
    }

    public Optional<String> buscarNombrePorCedula(String cedula) {
        if (cedula == null || cedula.isBlank())
            return Optional.empty();
        return gastoRepository.ultimoNombrePorCedula(cedula.trim());
    }

    public List<Gasto> listarEliminados() {
        return gastoRepository.findEliminados();
    }

    public void restaurarGasto(Long id) {
        Optional<Gasto> antes = buscarEnPapelera(id);
        gastoRepository.restaurar(id);
        antes.ifPresent(g -> auditoriaService.registrarCambioGasto("RESTAURAR_GASTO",
                "Restauración del " + etiqueta(g) + ": " + resumen(g)));
    }

    public void eliminarDefinitivamente(Long id) {
        Optional<Gasto> antes = buscarEnPapelera(id);
        gastoRepository.borrarDefinitivo(id);
        antes.ifPresent(g -> auditoriaService.registrarCambioGasto("ELIMINAR_DEFINITIVO",
                "Eliminación definitiva del " + etiqueta(g) + ": " + resumen(g)));
    }

    public int vaciarPapelera() {
        List<Gasto> antes = new ArrayList<>(gastoRepository.findEliminados());
        int eliminados = gastoRepository.vaciarPapelera();

        for (Gasto g : antes) {
            auditoriaService.registrarCambioGasto("ELIMINAR_DEFINITIVO",
                    "Eliminación definitiva del " + etiqueta(g) + " (al vaciar la papelera): " + resumen(g));
        }
        if (eliminados > 0) {
            auditoriaService.registrarCambioGasto("VACIAR_PAPELERA",
                    "Papelera vaciada: " + eliminados + " gasto(s) eliminados definitivamente");
        }
        return eliminados;
    }

    // ==================== AUXILIARES DE AUDITORÍA ====================

    // findById no ve los gastos de la papelera (@SQLRestriction), por eso se busca en esta lista
    private Optional<Gasto> buscarEnPapelera(Long id) {
        return gastoRepository.findEliminados().stream()
                .filter(g -> g.getId().equals(id))
                .findFirst();
    }

    private String etiqueta(Gasto g) {
        return "gasto " + g.getId() + " (" + g.getProducto() + ")";
    }

    private String resumen(Gasto g) {
        return g.getCantidad() + " x " + dinero(g.getValorUnitario()) + " = " + dinero(g.getValorTotal())
                + ", " + g.getCategoria().getNombre() + " / " + g.getSubcategoria().getNombre()
                + ", fecha " + g.getFecha();
    }

    // Formato colombiano: $10.000 (con decimales solo si los hay)
    private String dinero(BigDecimal v) {
        if (v == null)
            return "-";
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.forLanguageTag("es-CO"));
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        boolean entero = v.stripTrailingZeros().scale() <= 0;
        return "$" + new DecimalFormat(entero ? "#,##0" : "#,##0.00", s).format(v);
    }

    private String textoOVacio(String s) {
        return s == null ? "" : s.trim();
    }

    // La hora se compara al minuto para no registrar cambios por segundos o nanosegundos
    private boolean mismaHora(LocalTime a, LocalTime b) {
        if (a == null || b == null)
            return a == b;
        return a.truncatedTo(ChronoUnit.MINUTES).equals(b.truncatedTo(ChronoUnit.MINUTES));
    }

    private String horaCorta(LocalTime h) {
        return h == null ? "-" : h.truncatedTo(ChronoUnit.MINUTES).toString();
    }
    
    /**
     * Escritura oficial del producto: si ya existe uno igual (sin importar mayúsculas, tildes o
     * espacios) usa la que más se repite, incluida la papelera; si no, el texto limpio como se escribió.
     */
    private String unificarProducto(String producto) {
        String limpio = ProductoNormalizador.limpiar(producto);
        String clave = ProductoNormalizador.clave(limpio);
        for (String existente : gastoRepository.productosPorFrecuencia()) {
            if (ProductoNormalizador.clave(existente).equals(clave)) {
                return existente;
            }
        }
        return limpio;
    }
}