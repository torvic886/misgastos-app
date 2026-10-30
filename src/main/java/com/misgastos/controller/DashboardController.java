package com.misgastos.controller;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
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
import java.util.List;
import javafx.scene.Cursor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

@Slf4j
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
    @FXML private Button btnConsultas;
    @FXML private Button btnRespaldo;
    @FXML private Button btnRegistroCambios;
    @FXML private Button btnDiagnostico;
    @FXML private Button btnPresupuestos;
    @FXML private Button btnCorregirDatos;
    @FXML private Button btnToggleMenu;
    @FXML private Label lblGrupoGastos;
    @FXML private Label lblGrupoPersonas;
    @FXML private Label lblGrupoSistema;
    private boolean menuColapsado = false;
    private boolean sistemaAbierto = false;
    
    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private SesionRecordadaService sesionService;

    @Autowired
    private UsuarioService usuarioService;

    @FXML private Button btnClientes;
    
    private String username;
    private String rol;
    private Button botonActivo = null;

    // Tamaño (en píxeles) con el que se muestra el avatar en la barra superior
    private static final double TAM_AVATAR = 40;

    @FXML
    public void initialize() {
        log.debug("Dashboard inicializado");
        cargarVista("/fxml/inicio.fxml");
        configurarGrupoSistema();
        
        // Modo compacto (responsive): con ventana angosta se reducen menú y márgenes
        mainBorderPane.sceneProperty().addListener((obs, antes, escena) -> {
            if (escena != null) {
                escena.widthProperty().addListener((o, a, ancho) -> aplicarModoCompacto(ancho.doubleValue()));
                aplicarModoCompacto(escena.getWidth());
            }
        });

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
        log.debug("Rol recibido: {}", rol);
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
                                "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #f59e0b 0%, #d97706 100%);"
                );
            } else {
                lblAvatar.setStyle(
                    "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #3b82f6 0%, #2563eb 100%);"
                );
            }

            // Si el usuario tiene avatar (hombre/mujer), se muestra la imagen en lugar de la inicial
            aplicarImagenAvatar();
        }
    }

    private void aplicarModoCompacto(double ancho) {
        boolean compacto = ancho < 1100;
        if (compacto && !mainBorderPane.getStyleClass().contains("compact")) {
            mainBorderPane.getStyleClass().add("compact");
        } else if (!compacto) {
            mainBorderPane.getStyleClass().remove("compact");
        }
    }

    private void aplicarImagenAvatar() {
        try {
            Optional<Usuario> usuarioOpt = usuarioService.buscarPorUsername(username);
            if (usuarioOpt.isEmpty()) {
                return;
            }

            String avatar = usuarioOpt.get().getAvatar();
            String ruta = null;
            if ("HOMBRE".equals(avatar)) {
                ruta = "/images/avatar-hombre.png";
            } else if ("MUJER".equals(avatar)) {
                ruta = "/images/avatar-mujer.png";
            }
            if (ruta == null) {
                return; // sin avatar: se queda la inicial de siempre
            }

            try (InputStream in = getClass().getResourceAsStream(ruta)) {
                if (in == null) {
                    log.warn("No se encontró la imagen del avatar: {}", ruta);
                    return;
                }
                Image imagen = new Image(in, TAM_AVATAR * 2, TAM_AVATAR * 2, true, true);
                ImageView vista = new ImageView(imagen);
                vista.setFitWidth(TAM_AVATAR);
                vista.setFitHeight(TAM_AVATAR);
                vista.setPreserveRatio(true);
                vista.setClip(new Circle(TAM_AVATAR / 2, TAM_AVATAR / 2, TAM_AVATAR / 2));

                lblAvatar.setText("");
                lblAvatar.setGraphic(vista);
                lblAvatar.setStyle("-fx-background-color: transparent;");
            }
        } catch (Exception e) {
            log.warn("No se pudo mostrar el avatar del usuario '{}'", username, e);
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
        
        if (nuevoBotonActivo != null && botonesSistema().contains(nuevoBotonActivo) && !sistemaAbierto) {
            sistemaAbierto = true;
            actualizarGrupoSistema();
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
        log.debug("Configurando menú para el rol {}", rol);

        if (esUsuarioNormal()) {
            log.debug("Ocultando opciones de administrador");
            sidebar.getChildren().remove(lblGrupoSistema);
            sidebar.getChildren().removeIf(node -> {
                if (node instanceof Button) {
                    Button btn = (Button) node;
                    return btn.getText().contains("Informes") ||
                            btn.getText().contains("Usuarios") ||
                            btn.getText().contains("Consultas") ||
                            btn.getText().contains("Respaldo") ||
                            btn.getText().contains("Registro de cambios") ||
                            btn.getText().contains("Presupuestos") ||
                            btn.getText().contains("Diagnóstico") ||
                            btn.getText().contains("Registro de cambios") ||
                            btn.getText().contains("Corregir datos") ||
                            btn.getText().contains("Presupuestos");
                }
                return false;
            });
        }
    }
    
    private List<Button> botonesSistema() {
        return List.of(btnPresupuestos, btnRegistroCambios, btnCorregirDatos, btnRespaldo, btnDiagnostico);
    }

    /** Hace clicable el título SISTEMA y lo deja cerrado al iniciar. */
    private void configurarGrupoSistema() {
        if (lblGrupoSistema == null) {
            return;
        }
        lblGrupoSistema.setCursor(Cursor.HAND);
        lblGrupoSistema.setOnMouseClicked(e -> {
            sistemaAbierto = !sistemaAbierto;
            log.debug("Grupo SISTEMA {}", sistemaAbierto ? "abierto" : "cerrado");
            actualizarGrupoSistema();
        });
        actualizarGrupoSistema();
    }

    /**
     * Muestra u oculta los botones de SISTEMA según la flecha.
     * Con el menú contraído (solo íconos) siempre se muestran, porque
     * ahí no hay título que se pueda pulsar para abrirlos.
     */
    private void actualizarGrupoSistema() {
        if (lblGrupoSistema != null) {
            lblGrupoSistema.setText((sistemaAbierto ? "▾ " : "▸ ") + "SISTEMA");
        }
        boolean mostrar = sistemaAbierto || menuColapsado;
        for (Button b : botonesSistema()) {
            b.setVisible(mostrar);
            b.setManaged(mostrar);
        }
    }

    @FXML
    public void handleToggleMenu() {
        menuColapsado = !menuColapsado;
        aplicarMenuColapsado();
    }

    private void aplicarMenuColapsado() {
        if (menuColapsado) {
            if (!sidebar.getStyleClass().contains("collapsed")) {
                sidebar.getStyleClass().add("collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }

        for (Node n : sidebar.getChildren()) {
            if (n instanceof Button b) {
                b.setContentDisplay(menuColapsado ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
                b.setTooltip(menuColapsado ? new Tooltip(b.getText()) : null);
            } else if (n instanceof Label l) {
                l.setVisible(!menuColapsado);
                l.setManaged(!menuColapsado);
            }
        }
        actualizarGrupoSistema();
        btnToggleMenu.setTooltip(new Tooltip(menuColapsado ? "Expandir menú" : "Contraer menú"));
    }

    @FXML
    public void handleInicio() {
        log.debug("Navegando a Inicio");
        establecerBotonActivo(btnInicio);
        cargarVista("/fxml/inicio.fxml");
    }

    @FXML
    public void handleGastos() {
        log.debug("Navegando a Gastos");
        establecerBotonActivo(btnGastos);
        cargarVistaConSubMenu();
    }

    private void cargarVistaConSubMenu() {
        try {
            VBox container = new VBox(10);
            container.setStyle("-fx-padding: 8; -fx-background-color: #f7fafc;");

            HBox submenu = new HBox(15);
            submenu.setStyle("-fx-padding: 0 0 4 0;");

            Button btnListar = new Button("📋 Ver Historial");
            btnListar.getStyleClass().add("secondary-button");
            btnListar.setOnAction(e -> cargarVista("/fxml/lista-gastos.fxml"));
            submenu.getChildren().add(btnListar);

            if (esAdministrador()) {
                Button btnBuscarEditar = new Button("🔍 Buscar y Editar");
                btnBuscarEditar.getStyleClass().add("search-button");
                btnBuscarEditar.setOnAction(e -> cargarVista("/fxml/buscar-editar-gastos.fxml"));
                submenu.getChildren().add(btnBuscarEditar);
                Button btnPapelera = new Button("🗑 Papelera");
                btnPapelera.getStyleClass().add("secondary-button");
                btnPapelera.setOnAction(e -> cargarVista("/fxml/papelera.fxml"));
                submenu.getChildren().add(btnPapelera);
            }

            container.getChildren().add(submenu);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/registro-gasto.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent vista = loader.load();

            RegistroGastoController registroCtrl = loader.getController();
            registroCtrl.setUsuarioActual(username);
            VBox.setVgrow(vista, javafx.scene.layout.Priority.ALWAYS);
            container.getChildren().add(vista);
            mainBorderPane.setCenter(container);

        } catch (Exception e) {
            log.error("No se pudo cargar la pantalla de Mis Gastos", e);
        }
    }

    @FXML
    public void handleInformes() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        log.debug("Navegando a Informes");
        establecerBotonActivo(btnInformes);
        cargarVista("/fxml/informes.fxml");
    }

    @FXML
    public void handleConsultas() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        log.debug("Navegando a Consultas");
        establecerBotonActivo(btnConsultas);
        cargarVista("/fxml/consulta-totales.fxml");
    }

    @FXML
    public void handleRespaldo() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        // Pide la clave propia del respaldo (la primera vez, la crea)
        if (!ClaveRespaldo.pedirAcceso(mainBorderPane.getScene().getWindow())) {
            return;
        }
        log.debug("Navegando a Respaldo");
        establecerBotonActivo(btnRespaldo);
        cargarVista("/fxml/respaldo.fxml");
    }
    
    @FXML
    public void handleDiagnostico() {
        if (!esAdministrador()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        // Misma clave que el respaldo (la primera vez, la crea)
        if (!ClaveRespaldo.pedirAcceso(mainBorderPane.getScene().getWindow())) {
            return;
        }
        log.debug("Navegando a Diagnóstico y registros");
        establecerBotonActivo(btnDiagnostico);
        cargarVista("/fxml/diagnostico.fxml");
    }
    
    @FXML
    public void handleCorregirDatos() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        log.debug("Navegando a Corregir datos");
        establecerBotonActivo(btnCorregirDatos);
        cargarVista("/fxml/corregir-datos.fxml");
    }

    @FXML
    public void handleRegistroCambios() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        log.debug("Navegando a Registro de cambios");
        establecerBotonActivo(btnRegistroCambios);
        cargarVista("/fxml/registro-cambios.fxml");
    }

    @FXML
    public void handlePresupuestos() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
            return;
        }
        log.debug("Navegando a Presupuestos");
        establecerBotonActivo(btnPresupuestos);
        cargarVista("/fxml/presupuestos.fxml");
    }

    @FXML
    public void handleUsuarios() {
        if (esUsuarioNormal()) {
            mostrarAlerta("Acceso Denegado", "No tiene permisos para gestionar usuarios");
            return;
        }
        log.debug("Navegando a Gestión de Usuarios");
        establecerBotonActivo(btnUsuarios);
        cargarVista("/fxml/gestion-usuarios.fxml");
    }

    @FXML
    public void handleBilleteros() {
        log.debug("Navegando a Gestión de Billeteros");
        establecerBotonActivo(btnBilleteros);
        cargarVista("/fxml/gestion-billeteros.fxml");
    }

    @FXML
    public void handleReportesBilleteros() {
        log.debug("Navegando a Reportes de Billeteros");
        establecerBotonActivo(btnReportesBilleteros);
        cargarVista("/fxml/reportes-billeteros.fxml");
    }
    
    @FXML
    public void handleClientes() {
        log.debug("Navegando a Clientes");
        establecerBotonActivo(btnClientes);
        cargarVista("/fxml/clientes.fxml");
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
                log.debug("Sesión 'Recordarme' eliminada para el usuario '{}'", this.username);
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

                log.debug("Login restaurado ({}x{})", (int) loginWidth, (int) loginHeight);

                // Limpiar campos del login
                if (loginController != null) {
                    loginController.restaurarVentana();
                }
            });

            log.info("Sesión cerrada por el usuario '{}'", this.username);

        } catch (Exception e) {
            log.error("No se pudo cerrar la sesión correctamente", e);
            mostrarAlerta("Error Crítico", "No se pudo cerrar la sesión correctamente");
        }
    }

    private void cargarVista(String fxmlPath) {
        log.debug("Cargando vista {}", fxmlPath);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            loader.setClassLoader(getClass().getClassLoader());

            Parent vista = loader.load();

            if (fxmlPath.contains("gestion-usuarios")) {
                GestionUsuariosController controller = loader.getController();
                controller.setUsuarioActual(username);
                controller.setAlActualizarUsuarioActual(this::actualizarAvatar);
            }

            mainBorderPane.setCenter(vista);
        } catch (Exception e) {
            log.error("No se pudo cargar la pantalla {}", fxmlPath, e);
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