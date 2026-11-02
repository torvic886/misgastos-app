package com.misgastos.controller;

import com.misgastos.model.Usuario;
import com.misgastos.model.Rol;
import com.misgastos.model.Ventana;
import com.misgastos.service.UsuarioService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class GestionUsuariosController {

    private static final String[] GRUPOS = {"GASTOS", "PERSONAS", "SISTEMA"};

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<String> cmbRol;
    @FXML private ComboBox<String> cmbAvatar;
    @FXML private CheckBox chkActivo;

    @FXML private TableView<Usuario> tblUsuarios;
    @FXML private TableColumn<Usuario, Long> colId;
    @FXML private TableColumn<Usuario, String> colUsername;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, Boolean> colActivo;
    @FXML private TableColumn<Usuario, LocalDateTime> colFechaCreacion;

    @FXML private Label lblTotalUsuarios;

    // Panel de permisos por ventana
    @FXML private VBox panelPermisos;
    @FXML private Label lblPermisosDe;
    @FXML private Label lblPermisosEstado;
    @FXML private ComboBox<String> cmbPlantilla;
    @FXML private HBox boxPermisos;

    private final Map<Ventana, CheckBox> casillas = new EnumMap<>(Ventana.class);

    @Autowired
    private UsuarioService usuarioService;

    private Usuario usuarioSeleccionado;
    private String usuarioActual;
    private Runnable alActualizarUsuarioActual;

    @FXML
    public void initialize() {
        configurarComboRol();
        configurarComboAvatar();
        configurarTabla();
        construirPanelPermisos();
        cargarUsuarios();
        configurarSeleccionTabla();
        limpiarPanelPermisos();

        log.debug("Módulo de gestión de usuarios cargado");
    }

    public void setUsuarioActual(String username) {
        this.usuarioActual = username;
    }

    // El dashboard registra aquí qué hacer cuando se actualiza el usuario que tiene la sesión abierta
    public void setAlActualizarUsuarioActual(Runnable accion) {
        this.alActualizarUsuarioActual = accion;
    }

    private void configurarComboRol() {
        cmbRol.setItems(FXCollections.observableArrayList(
            Rol.ADMINISTRADOR.name(),
            Rol.USUARIO.name()
        ));
    }

    private void configurarComboAvatar() {
        cmbAvatar.setItems(FXCollections.observableArrayList("HOMBRE", "MUJER"));
    }

    private void configurarTabla() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRol.setCellValueFactory(new PropertyValueFactory<>("rol"));

        colActivo.setCellValueFactory(new PropertyValueFactory<>("activo"));
        colActivo.setCellFactory(col -> new TableCell<Usuario, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item ? "✅ Sí" : "❌ No");
                }
            }
        });

        colFechaCreacion.setCellValueFactory(new PropertyValueFactory<>("fechaCreacion"));
        colFechaCreacion.setCellFactory(col -> new TableCell<Usuario, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                }
            }
        });
    }

    private void configurarSeleccionTabla() {
        tblUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                usuarioSeleccionado = newVal;
                cargarDatosEnFormulario(newVal);
                mostrarPermisos(newVal);
            }
        });
    }

    private void cargarDatosEnFormulario(Usuario usuario) {
        txtUsername.setText(usuario.getUsername());
        txtPassword.clear();
        cmbRol.setValue(usuario.getRol());
        cmbAvatar.setValue(usuario.getAvatar());
        chkActivo.setSelected(usuario.getActivo());
    }

    private void cargarUsuarios() {
        var usuarios = usuarioService.listarTodos();
        tblUsuarios.setItems(FXCollections.observableArrayList(usuarios));
        lblTotalUsuarios.setText(String.valueOf(usuarios.size()));
    }

    // ==================== PERMISOS POR VENTANA ====================

    /** Crea las casillas por grupo, tomando las ventanas del enum Ventana. */
    private void construirPanelPermisos() {
        cmbPlantilla.setItems(FXCollections.observableArrayList(
            Rol.USUARIO.name(),
            Rol.ADMINISTRADOR.name()
        ));
        cmbPlantilla.setValue(Rol.USUARIO.name());

        boxPermisos.getChildren().clear();
        casillas.clear();
        for (String grupo : GRUPOS) {
            VBox columna = new VBox(6);
            Label titulo = new Label(grupo);
            titulo.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
            columna.getChildren().add(titulo);
            for (Ventana v : Ventana.values()) {
                if (grupo.equals(v.getGrupo())) {
                    CheckBox casilla = new CheckBox(v.getEtiqueta());
                    casilla.setStyle("-fx-font-size: 12px;");
                    casillas.put(v, casilla);
                    columna.getChildren().add(casilla);
                }
            }
            boxPermisos.getChildren().add(columna);
        }
    }

    private void mostrarPermisos(Usuario usuario) {
        panelPermisos.setDisable(false);
        lblPermisosDe.setText("Permisos de: " + usuario.getUsername());
        marcarCasillas(Ventana.efectivas(usuario.getRol(), usuario.getPermisos()), usuario.getRol());
        lblPermisosEstado.setText(usuario.getPermisos() == null
            ? "Usa los permisos de su rol"
            : "Permisos personalizados");
    }

    /** Usuarios es exclusiva del Administrador general: marcada y bloqueada para él, desmarcada y bloqueada para los demás. */
    private void marcarCasillas(Set<Ventana> ventanas, String rol) {
        boolean general = Rol.ADMIN_GENERAL.name().equals(rol);
        for (Map.Entry<Ventana, CheckBox> e : casillas.entrySet()) {
            CheckBox casilla = e.getValue();
            if (e.getKey() == Ventana.USUARIOS) {
                casilla.setSelected(general);
                casilla.setDisable(true);
            } else {
                casilla.setSelected(ventanas.contains(e.getKey()));
                casilla.setDisable(false);
            }
        }
    }

    private Set<Ventana> leerCasillas() {
        Set<Ventana> marcadas = EnumSet.noneOf(Ventana.class);
        for (Map.Entry<Ventana, CheckBox> e : casillas.entrySet()) {
            if (e.getValue().isSelected()) {
                marcadas.add(e.getKey());
            }
        }
        return marcadas;
    }

    private void limpiarPanelPermisos() {
        panelPermisos.setDisable(true);
        lblPermisosDe.setText("Permisos: seleccione un usuario");
        lblPermisosEstado.setText("");
        for (CheckBox casilla : casillas.values()) {
            casilla.setSelected(false);
        }
    }

    private void reseleccionar(Long id) {
        for (Usuario u : tblUsuarios.getItems()) {
            if (u.getId().equals(id)) {
                tblUsuarios.getSelectionModel().select(u);
                tblUsuarios.scrollTo(u);
                return;
            }
        }
    }

    @FXML
    public void handleAplicarPlantilla() {
        if (usuarioSeleccionado == null || cmbPlantilla.getValue() == null) {
            return;
        }
        marcarCasillas(Ventana.porDefecto(cmbPlantilla.getValue()), usuarioSeleccionado.getRol());
        lblPermisosEstado.setText("Plantilla " + cmbPlantilla.getValue() + " aplicada: falta guardar");
    }

    @FXML
    public void handleGuardarPermisos() {
        if (usuarioSeleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un usuario de la tabla", Alert.AlertType.WARNING);
            return;
        }

        Set<Ventana> marcadas = leerCasillas();
        if (marcadas.isEmpty()) {
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Sin ventanas");
            confirmacion.setHeaderText("Este usuario no podrá abrir ninguna ventana");
            confirmacion.setContentText("¿Desea guardar de todos modos?");
            if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        guardarPermisos(marcadas);
    }

    @FXML
    public void handleRestaurarPermisos() {
        if (usuarioSeleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un usuario de la tabla", Alert.AlertType.WARNING);
            return;
        }
        guardarPermisos(null);
    }

    /** ventanas = null: el usuario vuelve a los permisos de su rol. */
    private void guardarPermisos(Set<Ventana> ventanas) {
        try {
            Long id = usuarioSeleccionado.getId();
            usuarioService.guardarPermisos(id, ventanas, usuarioActual != null ? usuarioActual : "admin");

            log.info("Permisos de un usuario guardados");
            log.debug("Permisos guardados: usuario id {}, ventanas {}", id, ventanas);
            mostrarAlerta("Éxito",
                "Permisos guardados. Se aplican la próxima vez que ese usuario inicie sesión.",
                Alert.AlertType.INFORMATION);

            cargarUsuarios();
            reseleccionar(id);

        } catch (Exception e) {
            log.error("No se pudieron guardar los permisos", e);
            mostrarAlerta("Error", "No se pudieron guardar los permisos: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ==================== USUARIOS ====================

    @FXML
    public void handleCrear() {
        if (!validarCampos()) return;

        try {
            usuarioService.crearUsuario(
                txtUsername.getText().trim(),
                txtPassword.getText(),
                cmbRol.getValue(),
                usuarioActual != null ? usuarioActual : "admin",
                cmbAvatar.getValue()
            );

            log.info("Usuario creado");
            log.debug("Usuario creado: nombre '{}', rol {}", txtUsername.getText().trim(), cmbRol.getValue());
            mostrarAlerta("Éxito", "Usuario creado correctamente", Alert.AlertType.INFORMATION);
            handleLimpiar();
            cargarUsuarios();

        } catch (Exception e) {
                log.error("No se pudo crear el usuario", e);
                mostrarAlerta("Error", "No se pudo crear el usuario: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleActualizar() {
        if (usuarioSeleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un usuario de la tabla", Alert.AlertType.WARNING);
            return;
        }

        if (txtUsername.getText().trim().isEmpty() || cmbRol.getValue() == null) {
            mostrarAlerta("Validación", "Complete los campos obligatorios", Alert.AlertType.WARNING);
            return;
        }

        try {
            // ¿Se está editando al usuario que tiene la sesión abierta?
            boolean esUsuarioActual = usuarioActual != null
                    && usuarioActual.equals(usuarioSeleccionado.getUsername());

            usuarioService.actualizarUsuario(
                usuarioSeleccionado.getId(),
                txtUsername.getText().trim(),
                cmbRol.getValue(),
                chkActivo.isSelected(),
                usuarioActual != null ? usuarioActual : "admin",
                cmbAvatar.getValue()
            );

            if (esUsuarioActual && alActualizarUsuarioActual != null) {
                alActualizarUsuarioActual.run();
            }

            log.info("Usuario actualizado");
            log.debug("Usuario actualizado: id {}, rol {}, activo {}, es el usuario de la sesión: {}",
                usuarioSeleccionado.getId(), cmbRol.getValue(), chkActivo.isSelected(), esUsuarioActual);
            mostrarAlerta("Éxito", "Usuario actualizado correctamente", Alert.AlertType.INFORMATION);
            handleLimpiar();
            cargarUsuarios();

        } catch (Exception e) {
                log.error("No se pudo actualizar el usuario", e);
                mostrarAlerta("Error", "No se pudo actualizar el usuario: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleCambiarPassword() {
        if (usuarioSeleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un usuario de la tabla", Alert.AlertType.WARNING);
            return;
        }

        if (txtPassword.getText().isEmpty()) {
            mostrarAlerta("Validación", "Ingrese la nueva contraseña", Alert.AlertType.WARNING);
            return;
        }

        try {
            usuarioService.cambiarPassword(
                usuarioSeleccionado.getId(),
                txtPassword.getText(),
                usuarioActual != null ? usuarioActual : "admin"
            );

            log.info("Contraseña de un usuario cambiada");
            log.debug("Contraseña cambiada: usuario id {}", usuarioSeleccionado.getId());
            mostrarAlerta("Éxito", "Contraseña cambiada correctamente", Alert.AlertType.INFORMATION);
            txtPassword.clear();

        } catch (Exception e) {
                log.error("No se pudo cambiar la contraseña de un usuario", e);
                mostrarAlerta("Error", "No se pudo cambiar la contraseña: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleEliminar() {
        if (usuarioSeleccionado == null) {
            mostrarAlerta("Advertencia", "Seleccione un usuario de la tabla", Alert.AlertType.WARNING);
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar Eliminación");
        confirmacion.setHeaderText("¿Está seguro de eliminar este usuario?");
        confirmacion.setContentText("Usuario: " + usuarioSeleccionado.getUsername());

        if (confirmacion.showAndWait().get() == ButtonType.OK) {
            try {
                usuarioService.eliminarUsuario(
                    usuarioSeleccionado.getId(),
                    usuarioActual != null ? usuarioActual : "admin"
                );

                log.info("Usuario eliminado");
                log.debug("Usuario eliminado: id {}, nombre '{}'",
                    usuarioSeleccionado.getId(), usuarioSeleccionado.getUsername());
                mostrarAlerta("Éxito", "Usuario eliminado correctamente", Alert.AlertType.INFORMATION);
                handleLimpiar();
                cargarUsuarios();

            } catch (Exception e) {
                log.error("No se pudo eliminar el usuario", e);
                mostrarAlerta("Error", "No se pudo eliminar el usuario: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    public void handleLimpiar() {
        txtUsername.clear();
        txtPassword.clear();
        cmbRol.setValue(null);
        cmbAvatar.setValue(null);
        chkActivo.setSelected(true);
        usuarioSeleccionado = null;
        tblUsuarios.getSelectionModel().clearSelection();
        limpiarPanelPermisos();
    }

    @FXML
    public void handleRefrescar() {
        cargarUsuarios();
    }

    private boolean validarCampos() {
        if (txtUsername.getText().trim().isEmpty()) {
            mostrarAlerta("Validación", "Ingrese un nombre de usuario", Alert.AlertType.WARNING);
            return false;
        }

        if (txtPassword.getText().isEmpty()) {
            mostrarAlerta("Validación", "Ingrese una contraseña", Alert.AlertType.WARNING);
            return false;
        }

        if (cmbRol.getValue() == null) {
            mostrarAlerta("Validación", "Seleccione un rol", Alert.AlertType.WARNING);
            return false;
        }

        return true;
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}