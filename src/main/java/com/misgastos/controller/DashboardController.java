package com.misgastos.controller;

import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import com.misgastos.MisGastosApplication;
import com.misgastos.service.SesionRecordadaService;
import com.misgastos.service.UsuarioService;
import com.misgastos.model.Usuario;
import com.misgastos.model.Ventana;
import javafx.application.Platform;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

    // Labels del usuario en la top bar
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

    // Ventanas que este usuario puede abrir. Mientras no se carguen (permisosListos = false) no se bloquea nada.
    private Set<Ventana> permisos = EnumSet.noneOf(Ventana.class);
    private boolean permisosListos = false;
    private boolean permisosPersonalizados = false;

    // Tamaño (en píxeles) con el que se muestra el avatar en la barra superior
    private static final double TAM_AVATAR = 40;

    @FXML
    public void initialize() {
        // El controlador es un singleton: se borran los permisos de la sesión anterior
        permisos = EnumSet.noneOf(Ventana.class);
        permisosListos = false;
        permisosPersonalizados = false;

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

        cargarPermisos();
        if (permisosPersonalizados) {
            aplicarPermisosPersonalizados();
        } else {
            // Sin permisos guardados: el menú de siempre, según el rol
            configurarMenuPorRol();
        }
    }

    // ==================== PERMISOS POR VENTANA ====================

    /** Lee los permisos del usuario. Si algo falla, se usan los de su rol (el comportamiento de siempre). */
    private void cargarPermisos() {
        permisosPersonalizados = false;
        permisos = Ventana.porDefecto(rol);
        try {
            Optional<Usuario> usuarioOpt = usuarioService.buscarPorUsername(username);
            if (usuarioOpt.isPresent() && usuarioOpt.get().getPermisos() != null) {
                permisos = Ventana.efectivas(rol, usuarioOpt.get().getPermisos());
                permisosPersonalizados = true;
            }
        } catch (Exception e) {
            log.warn("No se pudieron leer los permisos de '{}'; se usan los de su rol", username, e);
            permisosPersonalizados = false;
            permisos = Ventana.porDefecto(rol);
        }
        permisosListos = true;
        log.debug("Permisos de '{}': {} (personalizados: {})", username, permisos, permisosPersonalizados);
    }

    private boolean permitida(Ventana ventana) {
        return !permisosListos || permisos.contains(ventana);
    }

    /** true (y avisa) si el usuario no puede abrir esa ventana. */
    private boolean sinPermiso(Ventana ventana) {
        if (permitida(ventana)) {
            return false;
        }
        log.warn("Ventana bloqueada: el usuario '{}' no tiene permiso para {}", username, ventana);
        mostrarAlerta("Acceso Denegado", "No tiene permisos para acceder a esta sección");
        return true;
    }

    private Button botonDe(Ventana ventana) {
        return switch (ventana) {
            case INICIO -> btnInicio;
            case GASTOS -> btnGastos;
            case INFORMES -> btnInformes;
            case CONSULTAS -> btnConsultas;
            case CLIENTES -> btnClientes;
            case BILLETEROS -> btnBilleteros;
            case REPORTES_BILLETEROS -> btnReportesBilleteros;
            case USUARIOS -> btnUsuarios;
            case PRESUPUESTOS -> btnPresupuestos;
            case REGISTRO_CAMBIOS -> btnRegistroCambios;
            case CORREGIR_DATOS -> btnCorregirDatos;
            case RESPALDO -> btnRespaldo;
            case DIAGNOSTICO -> btnDiagnostico;
        };
    }

    /** Menú según los permisos guardados: solo quedan los botones permitidos. */
    private void aplicarPermisosPersonalizados() {
        log.debug("Menú según los permisos personalizados de '{}'", username);

        for (Ventana v : Ventana.values()) {
            Button boton = botonDe(v);
            if (boton != null && !permisos.contains(v)) {
                sidebar.getChildren().remove(boton);
            }
        }
        quitarGrupoSiVacio(lblGrupoGastos, "GASTOS");
        quitarGrupoSiVacio(lblGrupoPersonas, "PERSONAS");
        quitarGrupoSiVacio(lblGrupoSistema, "SISTEMA");

        if (!permisos.contains(Ventana.INICIO)) {
            abrirPrimeraVentanaPermitida();
        }
    }

    private void quitarGrupoSiVacio(Label titulo, String grupo) {
        if (titulo == null) {
            return;
        }
        boolean hayAlguna = false;
        for (Ventana v : Ventana.values()) {
            if (grupo.equals(v.getGrupo()) && permisos.contains(v)) {
                hayAlguna = true;
                break;
            }
        }
        if (!hayAlguna) {
            sidebar.getChildren().remove(titulo);
        }
    }

    /** Si el usuario no tiene Inicio, al entrar se abre la primera ventana que sí tenga. */
    private void abrirPrimeraVentanaPermitida() {
        // Respaldo y Diagnóstico quedan fuera: piden su clave y no deben saltar al iniciar sesión
        Ventana[] orden = {
            Ventana.GASTOS, Ventana.INFORMES, Ventana.CONSULTAS, Ventana.CLIENTES, Ventana.BILLETEROS,
            Ventana.REPORTES_BILLETEROS, Ventana.PRESUPUESTOS, Ventana.REGISTRO_CAMBIOS,
            Ventana.CORREGIR_DATOS, Ventana.USUARIOS
        };
        for (Ventana v : orden) {
            if (permisos.contains(v)) {
                abrirVentana(v);
                return;
            }
        }

        Label aviso = new Label("No tienes ventanas asignadas. Pide al administrador que te dé acceso.");
        aviso.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
        VBox contenedor = new VBox(aviso);
        contenedor.setAlignment(Pos.CENTER);
        contenedor.setStyle("-fx-background-color: #f1f5f9;");
        mainBorderPane.setCenter(contenedor);
    }

    private void abrirVentana(Ventana ventana) {
        switch (ventana) {
            case GASTOS -> handleGastos();
            case INFORMES -> handleInformes();
            case CONSULTAS -> handleConsultas();
            case CLIENTES -> handleClientes();
            case BILLETEROS -> handleBilleteros();
            case REPORTES_BILLETEROS -> handleReportesBilleteros();
            case PRESUPUESTOS -> handlePresupuestos();
            case REGISTRO_CAMBIOS -> handleRegistroCambios();
            case CORREGIR_DATOS -> handleCorregirDatos();
            case USUARIOS -> handleUsuarios();
            default -> handleInicio();
        }
    }

    // ==================== USUARIO Y AVATAR ====================

    private void actualizarLabelUsuario() {
        if (username != null && rol != null) {
            String rolMostrar = normalizarRol(rol);

            // Labels individuales de la top bar
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

    // Actualiza el avatar con la inicial del usuario
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
        if ("ADMIN_GENERAL".equals(rol)) return "ADMINISTRADOR GENERAL";
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
        return "ADMINISTRADOR".equals(rol) || "ADMIN".equals(rol) || esAdministradorGeneral();
    }

    private boolean esAdministradorGeneral() {
        return "ADMIN_GENERAL".equals(rol);
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
                            btn.getText().contains("Corregir datos");
                }
                return false;
            });
        }

        if (!esAdministradorGeneral() && btnUsuarios != null) {
            btnUsuarios.setVisible(false);
            btnUsuarios.setManaged(false);
            log.debug("Opción Usuarios oculta: el rol {} no es Administrador general", rol);
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
        if (sinPermiso(Ventana.INICIO)) {
            return;
        }
        log.debug("Navegando a Inicio");
        establecerBotonActivo(btnInicio);
        cargarVista("/fxml/inicio.fxml");
    }

    @FXML
    public void handleGastos() {
        if (sinPermiso(Ventana.GASTOS)) {
            return;
        }
        log.debug("Navegando a Gastos");
        establecerBotonActivo(btnGastos);
        cargarVistaConSubMenu();
    }

    private void cargarVistaConSubMenu() {
        try {
            StackPane container = new StackPane();
            container.setStyle("-fx-padding: 8; -fx-background-color: #f7fafc;");

            HBox submenu = new HBox(8);
            submenu.setAlignment(Pos.CENTER_RIGHT);
            submenu.setPickOnBounds(false);
            submenu.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

            Button btnListar = botonBarra("Ver Historial",
                    "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z",
                    "toolbar-neutral");
            btnListar.setOnAction(e -> cargarVistaConRegreso("/fxml/lista-gastos.fxml"));
            submenu.getChildren().add(btnListar);

            if (esAdministrador()) {
                Button btnBuscarEditar = botonBarra("Buscar y Editar",
                        "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
                        "toolbar-blue");
                btnBuscarEditar.setOnAction(e -> cargarVistaConRegreso("/fxml/buscar-editar-gastos.fxml"));
                submenu.getChildren().add(btnBuscarEditar);

                Button btnPapelera = botonBarra("Papelera",
                        "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
                        "toolbar-neutral");
                btnPapelera.setOnAction(e -> cargarVista("/fxml/papelera.fxml"));
                submenu.getChildren().add(btnPapelera);
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/registro-gasto.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent vista = loader.load();

            RegistroGastoController registroCtrl = loader.getController();
            registroCtrl.setUsuarioActual(username);
            registroCtrl.setEdicionPermitida(esAdministrador());

            // La vista ocupa todo el espacio; los botones quedan encima, en la esquina del encabezado de la tabla
            container.getChildren().addAll(vista, submenu);
            StackPane.setAlignment(submenu, Pos.TOP_RIGHT);
            StackPane.setMargin(submenu, new Insets(20, 16, 0, 0));
            mainBorderPane.setCenter(container);

        } catch (Exception e) {
            log.error("No se pudo cargar la pantalla de Mis Gastos", e);
        }
    }

    /** Botón compacto de la barra de Mis Gastos: icono vectorial + texto. */
    private Button botonBarra(String texto, String icono, String claseColor) {
        Button b = new Button(texto);
        b.getStyleClass().addAll("toolbar-button", claseColor);
        SVGPath ic = new SVGPath();
        ic.setContent(icono);
        ic.getStyleClass().add("toolbar-icon");
        b.setGraphic(ic);
        return b;
    }

    @FXML
    public void handleInformes() {
        if (sinPermiso(Ventana.INFORMES)) {
            return;
        }
        log.debug("Navegando a Informes");
        establecerBotonActivo(btnInformes);
        cargarVista("/fxml/informes.fxml");
    }

    @FXML
    public void handleConsultas() {
        if (sinPermiso(Ventana.CONSULTAS)) {
            return;
        }
        log.debug("Navegando a Consultas");
        establecerBotonActivo(btnConsultas);
        cargarVista("/fxml/consulta-totales.fxml");
    }

    @FXML
    public void handleRespaldo() {
        if (sinPermiso(Ventana.RESPALDO)) {
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
        if (sinPermiso(Ventana.DIAGNOSTICO)) {
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
        if (sinPermiso(Ventana.CORREGIR_DATOS)) {
            return;
        }
        log.debug("Navegando a Corregir datos");
        establecerBotonActivo(btnCorregirDatos);
        cargarVista("/fxml/corregir-datos.fxml");
    }

    @FXML
    public void handleRegistroCambios() {
        if (sinPermiso(Ventana.REGISTRO_CAMBIOS)) {
            return;
        }
        log.debug("Navegando a Registro de cambios");
        establecerBotonActivo(btnRegistroCambios);
        cargarVista("/fxml/registro-cambios.fxml");
    }

    @FXML
    public void handlePresupuestos() {
        if (sinPermiso(Ventana.PRESUPUESTOS)) {
            return;
        }
        log.debug("Navegando a Presupuestos");
        establecerBotonActivo(btnPresupuestos);
        cargarVista("/fxml/presupuestos.fxml");
    }

    @FXML
    public void handleUsuarios() {
        if (!permitida(Ventana.USUARIOS)) {
            log.warn("Intento de abrir Gestión de usuarios sin ser Administrador general: usuario '{}'", username);
            mostrarAlerta("Acceso Denegado", "Solo el Administrador general puede gestionar usuarios");
            return;
        }
        log.debug("Navegando a Gestión de Usuarios");
        establecerBotonActivo(btnUsuarios);
        cargarVista("/fxml/gestion-usuarios.fxml");
    }

    @FXML
    public void handleBilleteros() {
        if (sinPermiso(Ventana.BILLETEROS)) {
            return;
        }
        log.debug("Navegando a Gestión de Billeteros");
        establecerBotonActivo(btnBilleteros);
        cargarVista("/fxml/gestion-billeteros.fxml");
    }

    @FXML
    public void handleReportesBilleteros() {
        if (sinPermiso(Ventana.REPORTES_BILLETEROS)) {
            return;
        }
        log.debug("Navegando a Reportes de Billeteros");
        establecerBotonActivo(btnReportesBilleteros);
        cargarVista("/fxml/reportes-billeteros.fxml");
    }

    @FXML
    public void handleClientes() {
        if (sinPermiso(Ventana.CLIENTES)) {
            return;
        }
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

            // Primero desmaximizar, LUEGO cambiar escena y tamaño
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
        // Bloqueo central: aunque alguien llegue aquí sin pasar por el menú, sin permiso no se abre
        Ventana destino = Ventana.deRuta(fxmlPath);
        if (destino != null && sinPermiso(destino)) {
            return;
        }

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
            
            if (loader.getController() instanceof InicioController inicio) {
                boolean puedeGastos = permitida(Ventana.GASTOS);
                inicio.configurarAcciones(
                        puedeGastos,
                        puedeGastos && esAdministrador(),
                        () -> cargarVistaConRegreso("/fxml/lista-gastos.fxml"),
                        () -> cargarVistaConRegreso("/fxml/buscar-editar-gastos.fxml"));
            }

            mainBorderPane.setCenter(vista);
        } catch (Exception e) {
            log.error("No se pudo cargar la pantalla {}", fxmlPath, e);
        }
    }

    /** Carga una pantalla de Mis Gastos con una barra superior para volver a Mis Gastos. */
    private void cargarVistaConRegreso(String fxmlPath) {
        Ventana destino = Ventana.deRuta(fxmlPath);
        if (destino != null && sinPermiso(destino)) {
            return;
        }

        log.debug("Cargando vista {} con botón de regreso", fxmlPath);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            loader.setClassLoader(getClass().getClassLoader());
            Parent vista = loader.load();

            if (loader.getController() instanceof ListaGastosController historial) {
                historial.setEdicionPermitida(esAdministrador());
            }

            Button btnVolver = botonBarra("Volver a Mis Gastos",
                    "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
                    "toolbar-neutral");
            btnVolver.setOnAction(e -> {
                log.debug("Volviendo a Mis Gastos");
                handleGastos();
            });

            HBox barra = new HBox(btnVolver);
            barra.setAlignment(Pos.CENTER_LEFT);
            barra.setStyle("-fx-padding: 8 8 0 8; -fx-background-color: #f7fafc;");

            VBox contenedor = new VBox(barra, vista);
            contenedor.setStyle("-fx-background-color: #f7fafc;");
            VBox.setVgrow(vista, Priority.ALWAYS);

            mainBorderPane.setCenter(contenedor);
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