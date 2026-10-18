package com.misgastos.controller;

import com.misgastos.model.Usuario;
import com.misgastos.service.SesionRecordadaService;
import com.misgastos.service.UsuarioService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import com.misgastos.licencia.LicenciaService;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;


@Component
public class LoginController implements Initializable {
    
    @FXML
    private TextField txtUsername;
    
    @FXML
    private PasswordField txtPassword;
    
    @FXML
    private CheckBox checkRecordarme;
    
    @FXML
    private Button btnLogin;
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private SesionRecordadaService sesionService;
    
    @Autowired
    private ApplicationContext springContext;
 
    private final LicenciaService licenciaService = new LicenciaService();    
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            configurarEventosEnter();
            configurarNavegacionTab();
            configurarVentanaLogin(); // 🔥 NUEVO: Configurar ventana de login
            if (!verificarLicencia()) {
                return;
            }
            verificarSesionGuardada();
            txtUsername.requestFocus();
        });
    }
    
    /**
     * 🔥 NUEVO: Configura el tamaño de la ventana de login
     */
    private void configurarVentanaLogin() {
        try {
            // Usar Platform.runLater para asegurar que el Stage esté completamente inicializado
            Platform.runLater(() -> {
                Stage stage = obtenerStageActual();
                if (stage != null) {
                    // Asegurar que NO esté maximizado
                    if (stage.isMaximized()) {
                        stage.setMaximized(false);
                    }
                    
                    // Calcular tamaño más grande (75% ancho, 90% alto)
                    Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                    double loginWidth = screenBounds.getWidth() * 0.75;
                    double loginHeight = screenBounds.getHeight() * 0.90;
                    
                    // Establecer tamaño del login
                    stage.setWidth(loginWidth);
                    stage.setHeight(loginHeight);
                    stage.centerOnScreen();
                    
                    System.out.println("✅ Ventana de login configurada (" + 
                                      (int)loginWidth + "x" + (int)loginHeight + ")");
                }
            });
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo configurar ventana de login: " + e.getMessage());
        }
    }
    
    /**
     * Configura Enter para login rápido
     */
    private void configurarEventosEnter() {
        txtUsername.setOnKeyPressed(this::handleKeyPressed);
        txtPassword.setOnKeyPressed(this::handleKeyPressed);
        checkRecordarme.setOnKeyPressed(this::handleKeyPressed);
    }
    
    /**
     * Configura navegación con Tab
     */
    private void configurarNavegacionTab() {
        txtUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                event.consume();
                txtPassword.requestFocus();
            } else if (event.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
        
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                event.consume();
                checkRecordarme.requestFocus();
            } else if (event.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
        
        checkRecordarme.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                event.consume();
                if (btnLogin != null) {
                    btnLogin.requestFocus();
                }
            } else if (event.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
    }
    
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleLogin();
        }
    }
    
    /**
     * Verifica si existe una sesión "Recordarme" válida
     */
    
    /**
     * Revisa la licencia antes de permitir el acceso.
     * Devuelve false si no hay licencia ni prueba vigente (la app se cierra).
     */
    private boolean verificarLicencia() {
        LicenciaService.Resultado r = licenciaService.evaluar();

        if (r.estado() == LicenciaService.Estado.LICENCIADA) {
            return true;
        }
        if (r.estado() == LicenciaService.Estado.PRUEBA) {
            if (r.diasRestantes() <= 15) {
                mostrarAlerta("Periodo de prueba",
                    "Te quedan " + r.diasRestantes() + " día(s) de prueba.",
                    Alert.AlertType.INFORMATION);
            }
            return true;
        }
        return mostrarActivacion(r.motivo());
    }

    private boolean mostrarActivacion(String motivo) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Activación requerida");
        dialog.setHeaderText(motivo + ".\nPara continuar, ingresa tu clave de activación.");

        if (txtUsername.getScene() != null && txtUsername.getScene().getWindow() != null) {
            dialog.initOwner(txtUsername.getScene().getWindow());
        }

        TextField txtCodigo = new TextField(licenciaService.codigoEquipo());
        txtCodigo.setEditable(false);

        TextArea txtClave = new TextArea();
        txtClave.setPromptText("Pega aquí la clave de activación");
        txtClave.setWrapText(true);
        txtClave.setPrefRowCount(4);

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #c0392b;");

        VBox box = new VBox(10,
            new Label("Código de tu equipo (envíalo para recibir la clave):"),
            txtCodigo,
            new Label("Clave de activación:"),
            txtClave,
            lblError);
        box.setPadding(new Insets(20));
        box.setPrefWidth(460);

        ButtonType btnActivar = new ButtonType("Activar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnSalir = new ButtonType("Salir", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnActivar, btnSalir);
        dialog.getDialogPane().setContent(box);

        Node okBtn = dialog.getDialogPane().lookupButton(btnActivar);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (!licenciaService.activar(txtClave.getText())) {
                ev.consume(); // no cierra el diálogo
                lblError.setText("Clave inválida, vencida o de otro equipo.");
            }
        });

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == btnActivar) {
            return true;
        }
        Platform.exit();
        return false;
    }    
    
    private void verificarSesionGuardada() {
        Optional<Long> usuarioIdOpt = sesionService.validarSesionLocal();
        
        if (usuarioIdOpt.isPresent()) {
            Long usuarioId = usuarioIdOpt.get();
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
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            mostrarAlerta("Error", "Por favor complete todos los campos", Alert.AlertType.WARNING);
            return;
        }
        
        if (usuarioService.autenticar(username, password)) {
            Usuario usuario = usuarioService.buscarPorUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
            if (checkRecordarme.isSelected()) {
                sesionService.crearSesionRecordada(usuario.getId());
            }
            
            abrirDashboard(usuario.getUsername(), usuario.getRol());
        } else {
            mostrarAlerta("Error de Autenticación", 
                         "Usuario o contraseña incorrectos. Por favor intente nuevamente.", 
                         Alert.AlertType.ERROR);
            txtPassword.clear();
            txtPassword.requestFocus();
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
            
            Stage stage = obtenerStageActual();
            
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            // 🔥 CONFIGURACIÓN CORRECTA PARA DASHBOARD
            stage.setScene(scene);
            stage.setTitle("ExpenseFlow - Dashboard");
            
            // Establecer tamaños mínimos para el dashboard
            stage.setMinWidth(1200);
            stage.setMinHeight(700);
            
            // 🔥 NUEVO: Listener para controlar la desmaximización
            stage.maximizedProperty().addListener((obs, wasMaximized, isNowMaximized) -> {
                if (!isNowMaximized) {
                    // Cuando se desmaximiza, establecer un tamaño más grande
                    Platform.runLater(() -> {
                        // Obtener dimensiones de la pantalla
                        javafx.geometry.Rectangle2D screenBounds = 
                            javafx.stage.Screen.getPrimary().getVisualBounds();
                        
                        // Usar 85% del ancho y alto de la pantalla
                        double width = screenBounds.getWidth() * 0.85;
                        double height = screenBounds.getHeight() * 0.85;
                        
                        stage.setWidth(width);
                        stage.setHeight(height);
                        stage.centerOnScreen();
                        System.out.println("✅ Ventana restaurada: " + (int)width + "x" + (int)height);
                    });
                }
            });
            
            // Maximizar después de configurar los límites
            stage.setMaximized(true);
            
            if (!stage.isShowing()) {
                stage.show();
            }
            
            System.out.println("✅ Dashboard abierto para: " + username + " | Rol: " + rol);
            
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error Crítico", 
                         "No se pudo abrir el dashboard. Contacte al administrador.", 
                         Alert.AlertType.ERROR);
        }
    }
    
    /**
     * Obtiene el Stage actual de forma robusta
     */
    private Stage obtenerStageActual() {
        // Prioridad 1: Desde txtUsername
        if (txtUsername != null && txtUsername.getScene() != null && txtUsername.getScene().getWindow() != null) {
            return (Stage) txtUsername.getScene().getWindow();
        }
        
        // Prioridad 2: Desde txtPassword
        if (txtPassword != null && txtPassword.getScene() != null && txtPassword.getScene().getWindow() != null) {
            return (Stage) txtPassword.getScene().getWindow();
        }
        
        // Prioridad 3: Buscar Stage activo
        Optional<Stage> stageOpt = Stage.getWindows().stream()
            .filter(window -> window instanceof Stage)
            .map(window -> (Stage) window)
            .filter(Stage::isShowing)
            .findFirst();
        
        if (stageOpt.isPresent()) {
            return stageOpt.get();
        }
        
        // Si no hay Stage, crear uno nuevo
        Stage newStage = new Stage();
        return newStage;
    }
    
    /**
     * 🔥 Método público para restaurar ventana al cerrar sesión
     */
    public void restaurarVentana() {
        Platform.runLater(() -> {
            try {
                // Solo limpiar campos, el tamaño ya fue ajustado por DashboardController
                txtUsername.clear();
                txtPassword.clear();
                checkRecordarme.setSelected(false);
                txtUsername.requestFocus();
                
                System.out.println("✅ Campos de login limpiados");
            } catch (Exception e) {
                System.err.println("⚠️ Error al limpiar campos: " + e.getMessage());
            }
        });
    }
    
    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(
            getClass().getResource("/css/styles.css").toExternalForm()
        );
        
        alert.showAndWait();
    }
}