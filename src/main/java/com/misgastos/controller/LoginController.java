package com.misgastos.controller;

import com.misgastos.model.Usuario;
import com.misgastos.service.SesionRecordadaService;
import com.misgastos.service.UsuarioService;
import javafx.application.Platform;  // ✅ AGREGAR
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

@Component
public class LoginController implements Initializable {
    
    @FXML
    private TextField txtUsername;
    
    @FXML
    private PasswordField txtPassword;
    
    @FXML
    private CheckBox checkRecordarme;
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private SesionRecordadaService sesionService;
    
    @Autowired
    private ApplicationContext springContext;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // ✅ CORRECCIÓN: Usar Platform.runLater para esperar a que la Scene esté lista
        Platform.runLater(this::verificarSesionGuardada);
    }
    
    /**
     * Verifica si existe una sesión "Recordarme" válida
     */
    private void verificarSesionGuardada() {
        Optional<Long> usuarioIdOpt = sesionService.validarSesionLocal();
        
        if (usuarioIdOpt.isPresent()) {
            Long usuarioId = usuarioIdOpt.get();
            
            // Buscar usuario por ID
            Optional<Usuario> usuarioOpt = usuarioService.buscarPorId(usuarioId);
            
            if (usuarioOpt.isPresent()) {
                Usuario usuario = usuarioOpt.get();
                System.out.println("🚀 Auto-login para: " + usuario.getUsername());
                abrirDashboard(usuario.getUsername(), usuario.getRol());
            }
        }
    }
    
    @FXML
    public void handleLogin() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            mostrarAlerta("Error", "Por favor complete todos los campos", Alert.AlertType.WARNING);
            return;
        }
        
        if (usuarioService.autenticar(username, password)) {
            // Obtener usuario completo
            Usuario usuario = usuarioService.buscarPorUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
            // ✅ Si "Recordarme" está activado, crear sesión
            if (checkRecordarme.isSelected()) {
                sesionService.crearSesionRecordada(usuario.getId());
            }
            
            abrirDashboard(usuario.getUsername(), usuario.getRol());
        } else {
            mostrarAlerta("Error", "Usuario o contraseña incorrectos", Alert.AlertType.ERROR);
        }
    }
    
    private void abrirDashboard(String username, String rol) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            loader.setControllerFactory(springContext::getBean);
            
            Parent root = loader.load();
            
            DashboardController controller = loader.getController();
            controller.setUsuario(username);
            controller.setRol(rol);
            
            // ✅ CORRECCIÓN: Obtener Stage de forma segura
            Stage stage = null;
            
            // Intentar obtener desde txtUsername si ya está montado
            if (txtUsername.getScene() != null) {
                stage = (Stage) txtUsername.getScene().getWindow();
            } 
            // Si no, buscar el Stage activo
            else {
                stage = Stage.getWindows().stream()
                    .filter(window -> window instanceof Stage)
                    .map(window -> (Stage) window)
                    .filter(Stage::isShowing)
                    .findFirst()
                    .orElse(null);
            }
            
            // Si no se encuentra ningún Stage, crear uno nuevo
            if (stage == null) {
                stage = new Stage();
            }
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            stage.setScene(scene);
            stage.setTitle("ExpenseFlow - Dashboard");
            stage.setWidth(1200);
            stage.setHeight(700);
            stage.setResizable(true);
            stage.setMaximized(true);
            stage.centerOnScreen();
            stage.show();  // ✅ AGREGAR show() por si es un Stage nuevo
            
            System.out.println("✅ Dashboard abierto para: " + username + " | Rol: " + rol);
            
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