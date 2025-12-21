package com.misgastos.controller;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.misgastos.MisGastosApplication;
import com.misgastos.util.SceneManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

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
        // Cargar vista de inicio por defecto
        cargarVista("/fxml/inicio.fxml");
    }
    
    @FXML
    public void handleInicio() {
        System.out.println("Navegando a Inicio");
        cargarVista("/fxml/inicio.fxml");
    }
    
    @FXML
    public void handleGastos() {
        System.out.println("Navegando a Gastos");
        cargarVistaConSubMenu();
    }

    private void cargarVistaConSubMenu() {
        try {
            // Crear un VBox para contener botones y la vista
            VBox container = new VBox(10);
            container.setStyle("-fx-padding: 20; -fx-background-color: #f7fafc;");
            
            // Botones de submenú
            HBox submenu = new HBox(15);
            submenu.setStyle("-fx-padding: 10;");
            
            Button btnRegistrar = new Button("➕ Registrar Gasto");
            btnRegistrar.getStyleClass().add("primary-button");
            btnRegistrar.setOnAction(e -> cargarVista("/fxml/registro-gasto.fxml"));
            
            Button btnListar = new Button("📋 Ver Historial");
            btnListar.getStyleClass().add("secondary-button");
            btnListar.setOnAction(e -> cargarVista("/fxml/lista-gastos.fxml"));
            
            submenu.getChildren().addAll(btnRegistrar, btnListar);
            container.getChildren().add(submenu);
            
            // Cargar vista por defecto (registro)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/registro-gasto.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent vista = loader.load();
            
            container.getChildren().add(vista);
            mainBorderPane.setCenter(container);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    public void handleInformes() {
        System.out.println("Navegando a Informes");
        cargarVista("/fxml/informes.fxml");
    }
    
    @FXML
    public void handleReportes() {
        System.out.println("Navegando a Reportes");
        cargarVista("/fxml/reportes.fxml");
    }
    
    @FXML
    private void handleCerrarSesion() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/login.fxml")
            );
            loader.setControllerFactory(
                    MisGastosApplication.getSpringContext()::getBean
            );

            Parent root = loader.load();

            // 👉 Scene NUEVA con tamaño del login
            Scene scene = new Scene(root, 500, 700);
            SceneManager.applyStyles(scene);

            // 👉 Obtener el mismo Stage
            Stage stage = (Stage) lblUsuario.getScene().getWindow();

            // 👉 Resetear completamente el Stage
            stage.setTitle("ExpenseFlow - Login");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setWidth(500);
            stage.setHeight(700);
            stage.centerOnScreen();

        } catch (Exception e) {
            e.printStackTrace();
        }
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