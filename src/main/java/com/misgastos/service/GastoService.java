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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class GastoService {
    
    @Autowired
    private GastoRepository gastoRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private SubcategoriaRepository subcategoriaRepository;
    
    public Gasto registrarGasto(Long usuarioId, Long categoriaId, Long subcategoriaId,
                                String producto, Integer cantidad, BigDecimal valorUnitario,
                                String notas) {
        
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
    
    public void eliminarGasto(Long id) {
        gastoRepository.deleteById(id);
    }
}