package com.misgastos.controller;

import com.misgastos.service.GastoService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Component
public class InicioController {
    
    @FXML
    private Label lblTotalHoy;
    
    @FXML
    private Label lblTotalSemana;
    
    @FXML
    private Label lblTotalMes;
    
    @FXML
    private Label lblTotalAnio;
    
    @FXML
    private VBox vboxTopProductos;
    
    @FXML
    private VBox vboxCategorias;
    
    @Autowired
    private GastoService gastoService;
    
    @FXML
    public void initialize() {
        cargarIndicadores();
        cargarTopProductos();
        cargarCategoriasResumen();
    }
    
    private void cargarIndicadores() {
        LocalDate hoy = LocalDate.now();
        
        // Hoy
        BigDecimal totalHoy = gastoService.calcularTotalPorPeriodo(hoy, hoy);
        lblTotalHoy.setText("$" + formatearNumero(totalHoy));
        
        // Semana (lunes a domingo)
        LocalDate inicioSemana = hoy.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate finSemana = hoy.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        BigDecimal totalSemana = gastoService.calcularTotalPorPeriodo(inicioSemana, finSemana);
        lblTotalSemana.setText("$" + formatearNumero(totalSemana));
        
        // Mes
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate finMes = hoy.with(TemporalAdjusters.lastDayOfMonth());
        BigDecimal totalMes = gastoService.calcularTotalPorPeriodo(inicioMes, finMes);
        lblTotalMes.setText("$" + formatearNumero(totalMes));
        
        // Año
        LocalDate inicioAnio = hoy.withDayOfYear(1);
        LocalDate finAnio = hoy.with(TemporalAdjusters.lastDayOfYear());
        BigDecimal totalAnio = gastoService.calcularTotalPorPeriodo(inicioAnio, finAnio);
        lblTotalAnio.setText("$" + formatearNumero(totalAnio));
    }
    
    private void cargarTopProductos() {
        List<Map<String, Object>> topProductos = gastoService.obtenerTopProductos(5);
        vboxTopProductos.getChildren().clear();
        
        for (Map<String, Object> item : topProductos) {
            String producto = (String) item.get("producto");
            BigDecimal total = (BigDecimal) item.get("total");
            
            Label label = new Label(producto + ": $" + formatearNumero(total));
            label.setStyle("-fx-font-size: 14px; -fx-text-fill: #2d3748; -fx-padding: 5;");
            vboxTopProductos.getChildren().add(label);
        }
    }
    
    private void cargarCategoriasResumen() {
        Map<String, BigDecimal> categorias = gastoService.obtenerGastosPorCategoria();
        vboxCategorias.getChildren().clear();
        
        for (Map.Entry<String, BigDecimal> entry : categorias.entrySet()) {
            Label label = new Label(entry.getKey() + ": $" + formatearNumero(entry.getValue()));
            label.setStyle("-fx-font-size: 14px; -fx-text-fill: #2d3748; -fx-padding: 5;");
            vboxCategorias.getChildren().add(label);
        }
    }
    
    private String formatearNumero(BigDecimal numero) {
        return String.format("%,.2f", numero);
    }
}