package com.misgastos.controller;

import com.misgastos.model.Billetero;
import com.misgastos.service.BilleteroService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

@Component
public class ModalBilleteroController {

    @FXML private Label lblTitulo;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtBilletero;
    @FXML private TextField txtPremios;
    @FXML private TextField txtDiferencia;
    @FXML private Label lblError;
    
    @Autowired
    private BilleteroService billeteroService;
    
    private Billetero billetero;
    private Runnable onGuardarCallback;
    private final DecimalFormat plainFormat;
    
    // Variables para controlar el formato
    private boolean isFormattingBilletero = false;
    private boolean isFormattingPremios = false;
    
    public ModalBilleteroController() {
        // Formato para números con comas
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        plainFormat = new DecimalFormat("#,##0", symbols);
    }
    
    @FXML
    public void initialize() {
        System.out.println("📝 Inicializando Modal Billetero");
        
        // Configurar fecha por defecto
        dpFecha.setValue(LocalDate.now());
        
        // Configurar formato en tiempo real para Billetero
        configurarFormatoTiempoReal(txtBilletero, true);
        
        // Configurar formato en tiempo real para Premios
        configurarFormatoTiempoReal(txtPremios, false);
        
        // Calcular diferencia automáticamente
        txtBilletero.textProperty().addListener((obs, old, nuevo) -> {
            if (!isFormattingBilletero) {
                calcularDiferencia();
            }
        });
        
        txtPremios.textProperty().addListener((obs, old, nuevo) -> {
            if (!isFormattingPremios) {
                calcularDiferencia();
            }
        });
    }
    
    private void configurarFormatoTiempoReal(TextField textField, boolean isBilletero) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            // Evitar recursión
            if (isBilletero && isFormattingBilletero) return;
            if (!isBilletero && isFormattingPremios) return;
            
            if (isBilletero) isFormattingBilletero = true;
            else isFormattingPremios = true;
            
            try {
                if (newValue == null || newValue.isEmpty()) {
                    return;
                }
                
                // Guardar la posición del cursor
                int caretPosition = textField.getCaretPosition();
                
                // Limpiar todo excepto números
                String cleaned = newValue.replaceAll("[^0-9]", "");
                
                if (cleaned.isEmpty()) {
                    textField.setText("");
                    return;
                }
                
                // Convertir a número y formatear
                try {
                    long numero = Long.parseLong(cleaned);
                    String formatted = plainFormat.format(numero);
                    
                    // Solo actualizar si es diferente
                    if (!formatted.equals(newValue)) {
                        // Contar cuántas comas había antes del cursor
                        int comasAntes = contarCaracter(newValue.substring(0, Math.min(caretPosition, newValue.length())), ',');
                        
                        // Actualizar texto
                        textField.setText(formatted);
                        
                        // Contar cuántas comas hay ahora antes del cursor
                        int comasDespues = contarCaracter(formatted.substring(0, Math.min(caretPosition, formatted.length())), ',');
                        
                        // Ajustar posición del cursor
                        int nuevaPosicion = caretPosition + (comasDespues - comasAntes);
                        nuevaPosicion = Math.max(0, Math.min(nuevaPosicion, formatted.length()));
                        
                        textField.positionCaret(nuevaPosicion);
                    }
                } catch (NumberFormatException e) {
                    // Si el número es demasiado grande, mantener el valor anterior
                    textField.setText(oldValue);
                }
                
            } finally {
                if (isBilletero) isFormattingBilletero = false;
                else isFormattingPremios = false;
            }
        });
        
        // Ya no agregamos decimales al perder el foco
    }
    
    // Método ya no necesario, eliminado
    
    private int contarCaracter(String texto, char caracter) {
        int count = 0;
        for (char c : texto.toCharArray()) {
            if (c == caracter) count++;
        }
        return count;
    }
    
    private void calcularDiferencia() {
        try {
            double billeteroValor = parsearValor(txtBilletero.getText());
            double premiosValor = parsearValor(txtPremios.getText());
            double diferencia = billeteroValor - premiosValor;
            
            // Formatear SIN decimales
            txtDiferencia.setText(plainFormat.format((long)diferencia));
            
            // Aplicar estilo según el valor
            if (diferencia > 0) {
                txtDiferencia.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
            } else if (diferencia < 0) {
                txtDiferencia.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            } else {
                txtDiferencia.setStyle("-fx-text-fill: #6b7280;");
            }
            
        } catch (Exception e) {
            txtDiferencia.setText("0");
            txtDiferencia.setStyle("-fx-text-fill: #6b7280;");
        }
    }
    
    private double parsearValor(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return 0.0;
        }
        
        try {
            // Remover comas y espacios
            String cleaned = texto.replaceAll(",", "")
                                 .replaceAll(" ", "")
                                 .trim();
            return cleaned.isEmpty() ? 0.0 : Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    public void setBilletero(Billetero billetero) {
        this.billetero = billetero;
        
        if (billetero != null) {
            lblTitulo.setText("Editar Billetero");
            cargarDatos();
        } else {
            lblTitulo.setText("Nuevo Billetero");
        }
    }
    
    private void cargarDatos() {
        if (billetero != null) {
            dpFecha.setValue(billetero.getFecha());
            
            // Cargar valores con formato SIN decimales
            long billeteroVal = billetero.getBilletero().longValue();
            long premiosVal = billetero.getPremios().longValue();
            
            txtBilletero.setText(plainFormat.format(billeteroVal));
            txtPremios.setText(plainFormat.format(premiosVal));
            
            calcularDiferencia();
        }
    }
    
    public void setOnGuardarCallback(Runnable callback) {
        this.onGuardarCallback = callback;
    }
    
    @FXML
    private void handleGuardar() {
        if (!validarCampos()) {
            return;
        }
        
        try {
            LocalDate fecha = dpFecha.getValue();
            
            // Parsear valores limpiando el formato
            double billeteroVal = parsearValor(txtBilletero.getText());
            double premiosVal = parsearValor(txtPremios.getText());
            
            BigDecimal billeteroValor = BigDecimal.valueOf(billeteroVal);
            BigDecimal premiosValor = BigDecimal.valueOf(premiosVal);
            
            if (billetero == null) {
                // Crear nuevo
                billetero = new Billetero();
            }
            
            billetero.setFecha(fecha);
            billetero.setBilletero(billeteroValor);
            billetero.setPremios(premiosValor);
            
            billeteroService.guardar(billetero);
            
            System.out.println("✅ Billetero guardado correctamente");
            
            if (onGuardarCallback != null) {
                onGuardarCallback.run();
            }
            
            cerrarModal();
            
        } catch (Exception e) {
            mostrarError("Error al guardar: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleCancelar() {
        cerrarModal();
    }
    
    private boolean validarCampos() {
        ocultarError();
        
        if (dpFecha.getValue() == null) {
            mostrarError("Debe seleccionar una fecha");
            dpFecha.requestFocus();
            return false;
        }
        
        if (txtBilletero.getText().trim().isEmpty()) {
            mostrarError("Debe ingresar el valor del billetero");
            txtBilletero.requestFocus();
            return false;
        }
        
        if (txtPremios.getText().trim().isEmpty()) {
            mostrarError("Debe ingresar el valor de los premios");
            txtPremios.requestFocus();
            return false;
        }
        
        try {
            double billetero = parsearValor(txtBilletero.getText());
            if (billetero < 0) {
                mostrarError("El valor del billetero no puede ser negativo");
                return false;
            }
            if (billetero == 0) {
                mostrarError("El valor del billetero debe ser mayor a cero");
                return false;
            }
        } catch (Exception e) {
            mostrarError("El valor del billetero no es válido");
            return false;
        }
        
        try {
            double premios = parsearValor(txtPremios.getText());
            if (premios < 0) {
                mostrarError("El valor de los premios no puede ser negativo");
                return false;
            }
        } catch (Exception e) {
            mostrarError("El valor de los premios no es válido");
            return false;
        }
        
        return true;
    }
    
    private void mostrarError(String mensaje) {
        lblError.setText("⚠️ " + mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }
    
    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
    
    private void cerrarModal() {
        Stage stage = (Stage) dpFecha.getScene().getWindow();
        stage.close();
    }
}