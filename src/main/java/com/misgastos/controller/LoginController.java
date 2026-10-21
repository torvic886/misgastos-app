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
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javafx.geometry.Bounds;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;


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
    
    private boolean dashboardAbierto = false;
    
    private boolean ajustandoVentana = false;
    
    private static final String PREF_USUARIOS = "usuarios_recientes";
    private static final int MAX_USUARIOS = 8;
    
    // Lista de usuarios usados antes (se crea de nuevo cada vez que se carga el login)
    private Popup popupUsuarios;
    private VBox listaPopup;
    private List<String> sugerencias = new ArrayList<>();
    private int indiceSugerencia = 0;
    private boolean eligiendoUsuario = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
    	dashboardAbierto = false;
    	Platform.runLater(() -> {
            configurarEventosEnter();
            configurarNavegacionTab();
            configurarUsuariosRecientes();
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
                if (stage != null && !dashboardAbierto) {
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
            KeyCode codigo = event.getCode();
            if (codigo == KeyCode.DOWN) {
                event.consume();
                if (sugerenciasVisibles()) {
                    indiceSugerencia = (indiceSugerencia + 1) % sugerencias.size();
                    resaltarSugerencia();
                } else {
                    mostrarSugerencias(true);
                }
            } else if (codigo == KeyCode.UP) {
                event.consume();
                if (sugerenciasVisibles()) {
                    indiceSugerencia = (indiceSugerencia - 1 + sugerencias.size()) % sugerencias.size();
                    resaltarSugerencia();
                }
            } else if (codigo == KeyCode.ESCAPE) {
                ocultarSugerencias();
            } else if (codigo == KeyCode.TAB || codigo == KeyCode.ENTER) {
                event.consume();
                if (sugerenciasVisibles()) {
                    // Enter/Tab eligen la sugerencia resaltada y pasan a la contraseña
                    elegirSugerencia(sugerencias.get(indiceSugerencia));
                } else if (codigo == KeyCode.ENTER
                        && !txtPassword.getText().isEmpty()
                        && !txtUsername.getText().isBlank()) {
                    handleLogin();
                } else {
                    txtPassword.requestFocus();
                }
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
    
    // ==================== USUARIOS RECIENTES ====================
    
    private List<String> leerUsuariosRecientes() {
        String raw = Preferences.userNodeForPackage(LoginController.class).get(PREF_USUARIOS, "");
        List<String> lista = new ArrayList<>();
        for (String u : raw.split("\\|")) {
            if (!u.isBlank()) {
                lista.add(u.trim());
            }
        }
        return lista;
    }

    private void guardarUsuarioReciente(String username) {
        List<String> lista = leerUsuariosRecientes();
        lista.removeIf(u -> u.equalsIgnoreCase(username));
        lista.add(0, username);
        while (lista.size() > MAX_USUARIOS) {
            lista.remove(lista.size() - 1);
        }
        Preferences.userNodeForPackage(LoginController.class)
            .put(PREF_USUARIOS, String.join("|", lista));
    }

    private void configurarUsuariosRecientes() {
        // El campo de usuario siempre empieza en blanco.
        // Los usuarios usados antes se ven al hacer clic en el campo.
        txtUsername.clear();

        // La lista se crea de nuevo cada vez que se carga el login,
        // así funciona igual después de cerrar sesión.
        if (popupUsuarios != null) {
            popupUsuarios.hide();
        }
        popupUsuarios = new Popup();
        popupUsuarios.setAutoHide(false);
        listaPopup = new VBox(2);
        listaPopup.setStyle(
            "-fx-background-color: white; -fx-background-radius: 10; "
            + "-fx-border-color: #d1d5db; -fx-border-radius: 10; -fx-padding: 6; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 4);");
        popupUsuarios.getContent().add(listaPopup);
        sugerencias = new ArrayList<>();
        indiceSugerencia = 0;

        // Al escribir: la lista se filtra por lo que se escribe
        txtUsername.textProperty().addListener((o, a, b) -> {
            if (!eligiendoUsuario) {
                mostrarSugerencias(false);
            }
        });

        // Al hacer clic con el mouse: siempre se muestra la lista completa
        txtUsername.setOnMouseClicked(e -> mostrarSugerencias(true));

        // La lista se cierra al salir del campo de usuario
        txtUsername.focusedProperty().addListener((o, a, enfocado) -> {
            if (!enfocado) {
                ocultarSugerencias();
            }
        });

        // ... y al hacer clic en cualquier otra parte de la ventana
        if (txtUsername.getScene() != null) {
            txtUsername.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (!(e.getTarget() instanceof Node) || !esDescendiente((Node) e.getTarget(), txtUsername)) {
                    ocultarSugerencias();
                }
            });
        }
    }

    private boolean esDescendiente(Node nodo, Node ancestro) {
        for (Node n = nodo; n != null; n = n.getParent()) {
            if (n == ancestro) {
                return true;
            }
        }
        return false;
    }

    private boolean sugerenciasVisibles() {
        return popupUsuarios != null && popupUsuarios.isShowing() && !sugerencias.isEmpty();
    }

    /**
     * Muestra la lista de usuarios usados antes.
     * @param todos true = muestra todos los usuarios (clic del mouse);
     *              false = filtra por lo que hay escrito (al escribir).
     */
    private void mostrarSugerencias(boolean todos) {
        String filtro = txtUsername.getText() == null ? "" : txtUsername.getText().trim().toLowerCase();
        sugerencias = leerUsuariosRecientes().stream()
            .filter(u -> todos || filtro.isEmpty() || u.toLowerCase().contains(filtro))
            .filter(u -> todos || !u.equalsIgnoreCase(filtro))
            .toList();

        if (sugerencias.isEmpty() || !txtUsername.isFocused()) {
            ocultarSugerencias();
            return;
        }

        indiceSugerencia = 0;
        pintarSugerencias();

        Bounds b = txtUsername.localToScreen(txtUsername.getBoundsInLocal());
        if (b == null) {
            return;
        }
        listaPopup.setPrefWidth(b.getWidth());
        if (popupUsuarios.isShowing()) {
            popupUsuarios.setAnchorX(b.getMinX());
            popupUsuarios.setAnchorY(b.getMaxY() + 2);
        } else {
            popupUsuarios.show(txtUsername, b.getMinX(), b.getMaxY() + 2);
        }
    }

    private void pintarSugerencias() {
        listaPopup.getChildren().clear();
        for (int i = 0; i < sugerencias.size(); i++) {
            final int pos = i;
            final String usuario = sugerencias.get(i);
            Label lbl = new Label(usuario);
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setOnMouseEntered(e -> {
                indiceSugerencia = pos;
                resaltarSugerencia();
            });
            lbl.setOnMousePressed(e -> elegirSugerencia(usuario));
            listaPopup.getChildren().add(lbl);
        }
        resaltarSugerencia();
    }

    private void resaltarSugerencia() {
        String base = "-fx-font-size: 14px; -fx-padding: 8 12; -fx-background-radius: 6; -fx-cursor: hand;";
        for (int i = 0; i < listaPopup.getChildren().size(); i++) {
            Node n = listaPopup.getChildren().get(i);
            n.setStyle(i == indiceSugerencia
                ? base + " -fx-background-color: #e0e7ff; -fx-text-fill: #1e3a8a; -fx-font-weight: bold;"
                : base + " -fx-background-color: transparent; -fx-text-fill: #1f2937;");
        }
    }

    private void elegirSugerencia(String usuario) {
        eligiendoUsuario = true;
        ocultarSugerencias();
        txtUsername.setText(usuario);
        txtUsername.positionCaret(usuario.length());
        eligiendoUsuario = false;
        txtPassword.requestFocus();
    }

    private void ocultarSugerencias() {
        if (popupUsuarios != null && popupUsuarios.isShowing()) {
            popupUsuarios.hide();
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
            
            guardarUsuarioReciente(usuario.getUsername());
            
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
            ocultarSugerencias();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();

            DashboardController controller = loader.getController();
            controller.setUsuario(username);
            controller.setRol(rol);

            Stage stage = obtenerStageActual();
            dashboardAbierto = true;

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle("ExpenseFlow - Dashboard");

            // Los mínimos nunca pueden superar el área útil de la pantalla
            Rectangle2D pantalla = Screen.getPrimary().getVisualBounds();
            stage.setMinWidth(Math.min(1200, pantalla.getWidth()));
            stage.setMinHeight(Math.min(700, pantalla.getHeight()));

            // Al desmaximizar, usar 85% de la pantalla
            stage.maximizedProperty().addListener((obs, wasMaximized, isNowMaximized) -> {
            	if (!isNowMaximized && !ajustandoVentana) {
                    Platform.runLater(() -> {
                        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
                        double width = bounds.getWidth() * 0.85;
                        double height = bounds.getHeight() * 0.85;
                        stage.setWidth(width);
                        stage.setHeight(height);
                        stage.centerOnScreen();
                        System.out.println("✅ Ventana restaurada: " + (int) width + "x" + (int) height);
                    });
                }
            });

            stage.setMaximized(true);

            if (!stage.isShowing()) {
                stage.show();
            }

            // Reforzar el maximizado cuando la ventana ya es visible (evita contenido recortado)
            ajustandoVentana = true;
            Platform.runLater(() -> {
                stage.setMaximized(false);
                Platform.runLater(() -> {
                    stage.setMaximized(true);
                    ajustandoVentana = false;
                });
            });

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
                // Al cerrar sesión, el usuario y la contraseña quedan en blanco.
                // Los usuarios usados antes se ven al hacer clic en el campo de usuario.
                ocultarSugerencias();
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