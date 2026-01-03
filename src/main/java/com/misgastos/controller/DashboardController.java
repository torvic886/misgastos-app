package com.misgastos.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.misgastos.MisGastosApplication;

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
    
    @FXML private Label lblUsuario;
    @FXML private BorderPane mainBorderPane;
    @FXML private VBox sidebar;
    
    @Autowired
    private ApplicationContext springContext;
    
    private String username;
    private String rol;
    
    @FXML
    public void initialize() {
        System.out.println("📊 Dashboard inicializado");
        cargarVista("/fxml/inicio.fxml");
    }
    
    public void setUsuario(String username) {
        this.username = username;
        actualizarLabelUsuario();
    }
    
    public void setRol(String rol) {
        this.rol = rol;
        System.out.println("🔑 Rol recibido: " + rol);
        actualizarLabelUsuario();
        configurarMenuPorRol();
    }
    
    private void actualizarLabelUsuario() {
        if (username != null && rol != null) {
            String rolMostrar = normalizarRol(rol);
            lblUsuario.setText("Usuario: " + username + " (" + rolMostrar + ")");
        }
    }
    
    private String normalizarRol(String rol) {
        if ("ADMIN".equals(rol)) return "ADMINISTRADOR";
        if ("USER".equals(rol)) return "USUARIO";
        return rol;
    }
    
    // ✅ MÉTODOS AUXILIARES PARA VERIFICAR ROL
    private boolean esAdministrador() {
        return "ADMINISTRADOR".equals(rol) || "ADMIN".equals(rol);
    }
    
    private boolean esUsuarioNormal() {
        return "USUARIO".equals(rol) || "USER".equals(rol);
    }
    
    private void configurarMenuPorRol() {
        System.out.println("⚙️ Configurando menú para rol: " + rol);
        
        if (esUsuarioNormal()) {
            System.out.println("🚫 Ocultando opciones de administrador");
            sidebar.getChildren().removeIf(node -> {
                if (node instanceof Button) {
                    Button btn = (Button) node;
                    return btn.getText().contains("Informes") || 
                           btn.getText().contains("Usuarios");
                }
                return false;
            });
        }
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
            VBox container = new VBox(10);
            container.setStyle("-fx-padding: 20; -fx-background-color: #f7fafc;");
            
            HBox submenu = new HBox(15);
            submenu.setStyle("-fx-padding: 10;");
            
            // ✅ Botón Ver Historial (TODOS los usuarios)
            Button btnListar = new Button("📋 Ver Historial");
            btnListar.getStyleClass().add("secondary-button");
            btnListar.setOnAction(e -> cargarVista("/fxml/lista-gastos.fxml"));
            submenu.getChildren().add(btnListar);
            
            // ✅ CRÍTICO: Solo agregar "Buscar y Editar" si es ADMINISTRADOR
            if (esAdministrador()) {
                System.out.println("✅ Mostrando 'Buscar y Editar' (es administrador)");
                Button btnBuscarEditar = new Button("🔍 Buscar y Editar");
                btnBuscarEditar.getStyleClass().add("search-button");
                btnBuscarEditar.setOnAction(e -> cargarVista("/fxml/buscar-editar-gastos.fxml"));
                submenu.getChildren().add(btnBuscarEditar);
            } else {
                System.out.println("🚫 Ocultando 'Buscar y Editar' (no es administrador)");
            }
            
            container.getChildren().add(submenu);
            
            // Vista por defecto: Registro de gastos
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
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        System.out.println("Navegando a Informes");
        cargarVista("/fxml/informes.fxml");
    }
    
    @FXML
    public void handleUsuarios() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para gestionar usuarios");
            return;
        }
        System.out.println("Navegando a Gestión de Usuarios");
        cargarVista("/fxml/gestion-usuarios.fxml");
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
            Scene scene = new Scene(root, 500, 700);
            
            String css = getClass().getResource("/css/styles.css").toExternalForm();
            scene.getStylesheets().add(css);

            Stage stage = (Stage) lblUsuario.getScene().getWindow();
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
    
    private void cargarVista(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            loader.setClassLoader(getClass().getClassLoader());  
            
            Parent vista = loader.load();
            
            if (fxmlPath.contains("gestion-usuarios")) {
                GestionUsuariosController controller = loader.getController();
                controller.setUsuarioActual(username);
            }
            
            mainBorderPane.setCenter(vista);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al cargar vista: " + fxmlPath);
        }
    }
    
    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}