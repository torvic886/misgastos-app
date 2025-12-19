package com.misgastos.controller;

import com.misgastos.util.SceneManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DashboardController {
    
    @FXML
    private Label lblUsuario;
    
    @FXML
    private BorderPane mainBorderPane;
    
    @Autowired
    private ApplicationContext springContext;
    
    private String username;
    
    @FXML
    public void initialize() {
        System.out.println("Dashboard cargado");
    }
    
    @FXML
    public void handleInicio() {
        System.out.println("Navegando a Inicio");
        // Cargar vista de inicio
    }
    
    @FXML
    public void handleGastos() {
        System.out.println("Navegando a Gastos");
        cargarVista("/fxml/registro-gasto.fxml");
    }
    
    @FXML
    public void handleReportes() {
        System.out.println("Navegando a Reportes");
    }
    
    @FXML
    public void handleCerrarSesion() {
        System.out.println("Cerrando sesión");
        SceneManager.cambiarEscena(
            lblUsuario.getScene(), 
            "/fxml/login.fxml", 
            springContext, 
            500, 
            700, 
            "ExpenseFlow - Login"
        );
    }
    
    public void setUsuario(String username) {
        this.username = username;
        lblUsuario.setText("Usuario: " + username);
    }
    
    private void cargarVista(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            Parent vista = loader.load();
            mainBorderPane.setCenter(vista);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al cargar vista: " + fxmlPath);
        }
    }
}