package com.misgastos.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

@Component
public class DashboardController {
    
    @FXML
    private Label lblUsuario;
    
    @FXML
    public void initialize() {
        System.out.println("Dashboard cargado");
    }
    
    @FXML
    public void handleInicio() {
        System.out.println("Navegando a Inicio");
    }
    
    @FXML
    public void handleGastos() {
        System.out.println("Navegando a Gastos");
    }
    
    @FXML
    public void handleReportes() {
        System.out.println("Navegando a Reportes");
    }
    
    @FXML
    public void handleCerrarSesion() {
        System.out.println("Cerrando sesion");
    }
    
    public void setUsuario(String username) {
        lblUsuario.setText("Usuario: " + username);
    }
}