package com.misgastos.controller;

import com.misgastos.service.UsuarioService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class LoginController {
    
    @FXML
    private TextField txtUsername;
    
    @FXML
    private PasswordField txtPassword;
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private ApplicationContext springContext;
    
    @FXML
    public void handleLogin() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            mostrarAlerta("Error", "Por favor complete todos los campos", Alert.AlertType.WARNING);
            return;
        }
        
        if (usuarioService.autenticar(username, password)) {
            abrirDashboard(username);
        } else {
            mostrarAlerta("Error", "Usuario o contraseña incorrectos", Alert.AlertType.ERROR);
        }
    }
    
    private void abrirDashboard(String username) {
        try {
            // ✅ Obtener el usuario con su rol
            com.misgastos.model.Usuario usuario = usuarioService.buscarPorUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            loader.setControllerFactory(springContext::getBean);
            
            Parent root = loader.load();
            
            DashboardController controller = loader.getController();
            controller.setUsuario(username);
            controller.setRol(usuario.getRol()); // ✅ NUEVO
            
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            stage.setScene(scene);
            stage.setTitle("ExpenseFlow - Dashboard");
            stage.setWidth(1200);
            stage.setHeight(700);
            stage.setResizable(true);
            stage.setMaximized(true);
            stage.centerOnScreen();
            
            System.out.println("✅ Dashboard abierto para usuario: " + username + " | Rol: " + usuario.getRol());
            
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "No se pudo abrir el dashboard", Alert.AlertType.ERROR);
        }
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}