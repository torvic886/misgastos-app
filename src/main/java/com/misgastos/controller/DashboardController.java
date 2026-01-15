package com.misgastos.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.misgastos.MisGastosApplication;
import com.misgastos.service.SesionRecordadaService;
import com.misgastos.service.UsuarioService;
import com.misgastos.model.Usuario;
import javafx.application.Platform;

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

import java.util.Optional;

@Component
public class DashboardController {
    
    // ✅ Labels del usuario en la top bar
    @FXML private Label lblUsuario;  // Mantener por compatibilidad
    @FXML private Label lblUsuarioNombre;
    @FXML private Label lblUsuarioRol;
    @FXML private Label lblAvatar;
    
    @FXML private BorderPane mainBorderPane;
    @FXML private VBox sidebar;
    
    // Referencias a los botones del menú
    @FXML private Button btnInicio;
    @FXML private Button btnGastos;
    @FXML private Button btnInformes;
    @FXML private Button btnUsuarios;
    @FXML private Button btnBilleteros;
    @FXML private Button btnReportesBilleteros;
    
    @Autowired
    private ApplicationContext springContext;
    
    @Autowired
    private SesionRecordadaService sesionService;
    
    @Autowired
    private UsuarioService usuarioService;
    
    private String username;
    private String rol;
    private Button botonActivo = null;
    
    @FXML
    public void initialize() {
        System.out.println("📊 Dashboard inicializado");
        cargarVista("/fxml/inicio.fxml");
        
        if (btnInicio != null) {
            establecerBotonActivo(btnInicio);
        }
    }
    
    public void setUsuario(String username) {
        this.username = username;
        actualizarLabelUsuario();
        actualizarAvatar();
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
            
            // ✅ NUEVO: Actualizar los labels individuales de la top bar
            if (lblUsuarioNombre != null) {
                lblUsuarioNombre.setText(username);
            }
            
            if (lblUsuarioRol != null) {
                lblUsuarioRol.setText(rolMostrar);
            }
            
            // Mantener compatibilidad con el label original
            if (lblUsuario != null) {
                lblUsuario.setText("Usuario: " + username + " (" + rolMostrar + ")");
            }
        }
    }
    
    // ✅ NUEVO: Método para actualizar el avatar con las iniciales del usuario
    private void actualizarAvatar() {
        if (lblAvatar != null && username != null && !username.isEmpty()) {
            // Obtener la primera letra del username en mayúscula
            String inicial = username.substring(0, 1).toUpperCase();
            lblAvatar.setText(inicial);
            
            // Opcional: Cambiar color del avatar según el rol
            if (esAdministrador()) {
                lblAvatar.setStyle(
                    "-fx-background-color: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);"
                );
            } else {
                lblAvatar.setStyle(
                    "-fx-background-color: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);"
                );
            }
        }
    }
    
    private String normalizarRol(String rol) {
        if ("ADMIN".equals(rol)) return "ADMINISTRADOR";
        if ("USER".equals(rol)) return "USUARIO";
        return rol;
    }
    
    private void establecerBotonActivo(Button nuevoBotonActivo) {
        if (botonActivo != null) {
            botonActivo.getStyleClass().remove("active");
        }
        
        if (nuevoBotonActivo != null && !nuevoBotonActivo.getStyleClass().contains("active")) {
            nuevoBotonActivo.getStyleClass().add("active");
        }
        
        botonActivo = nuevoBotonActivo;
    }
    
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
        establecerBotonActivo(btnInicio);
        cargarVista("/fxml/inicio.fxml");
    }
    
    @FXML
    public void handleGastos() {
        System.out.println("Navegando a Gastos");
        establecerBotonActivo(btnGastos);
        cargarVistaConSubMenu();
    }

    private void cargarVistaConSubMenu() {
        try {
            VBox container = new VBox(10);
            container.setStyle("-fx-padding: 20; -fx-background-color: #f7fafc;");
            
            HBox submenu = new HBox(15);
            submenu.setStyle("-fx-padding: 10;");
            
            Button btnListar = new Button("📋 Ver Historial");
            btnListar.getStyleClass().add("secondary-button");
            btnListar.setOnAction(e -> cargarVista("/fxml/lista-gastos.fxml"));
            submenu.getChildren().add(btnListar);
            
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
        establecerBotonActivo(btnInformes);
        cargarVista("/fxml/informes.fxml");
    }
    
    @FXML
    public void handleUsuarios() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para gestionar usuarios");
            return;
        }
        System.out.println("Navegando a Gestión de Usuarios");
        establecerBotonActivo(btnUsuarios);
        cargarVista("/fxml/gestion-usuarios.fxml");
    }
    
    @FXML
    public void handleBilleteros() {
        System.out.println("Navegando a Gestión de Billeteros");
        establecerBotonActivo(btnBilleteros);
        cargarVista("/fxml/gestion-billeteros.fxml");
    }    
    
    @FXML
    public void handleReportesBilleteros() {
        System.out.println("Navegando a Reportes de Billeteros");
        establecerBotonActivo(btnReportesBilleteros);
        cargarVista("/fxml/reportes-billeteros.fxml");
    }
    
    @FXML
    private void handleCerrarSesion() {
        try {
            // Obtener Stage actual ANTES de cualquier cambio
            Stage stage = (Stage) mainBorderPane.getScene().getWindow();
            
            // Limpiar sesión "Recordarme"
            Optional<Usuario> usuarioOpt = usuarioService.buscarPorUsername(this.username);
            if (usuarioOpt.isPresent()) {
                sesionService.eliminarSesion(usuarioOpt.get().getId());
                System.out.println("🗑️ Sesión 'Recordarme' eliminada para: " + this.username);
            }
            
            // Cargar vista de Login
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            loader.setControllerFactory(springContext::getBean);
            
            Parent root = loader.load();
            LoginController loginController = loader.getController();
            
            // Calcular tamaño del login (55% ancho, 80% alto)
            javafx.geometry.Rectangle2D screenBounds = 
                javafx.stage.Screen.getPrimary().getVisualBounds();
            double loginWidth = screenBounds.getWidth() * 0.55;
            double loginHeight = screenBounds.getHeight() * 0.80;
            
            // Crear nueva escena con estilos
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm()
            );
            
            // 🔥 SOLUCIÓN: Primero desmaximizar, LUEGO cambiar escena y tamaño
            stage.setMaximized(false);
            
            // Cambiar escena
            stage.setTitle("ExpenseFlow - Login");
            stage.setScene(scene);
            
            // Usar Platform.runLater para que el cambio de escena se complete primero
            Platform.runLater(() -> {
                // Establecer tamaño calculado del login
                stage.setWidth(loginWidth);
                stage.setHeight(loginHeight);
                stage.centerOnScreen();
                
                System.out.println("✅ Login restaurado (" + 
                                  (int)loginWidth + "x" + (int)loginHeight + ")");
                
                // Limpiar campos del login
                if (loginController != null) {
                    loginController.restaurarVentana();
                }
            });
            
            System.out.println("🚪 Sesión cerrada correctamente");
            
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error Crítico", "No se pudo cerrar la sesión correctamente");
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
        
        // Aplicar estilos consistentes
        alert.getDialogPane().getStylesheets().add(
            getClass().getResource("/css/styles.css").toExternalForm()
        );
        
        alert.showAndWait();
    }
}