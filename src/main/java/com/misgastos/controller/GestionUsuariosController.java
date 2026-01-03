package com.misgastos.controller;

import com.misgastos.model.Usuario;
import com.misgastos.model.Rol;
import com.misgastos.service.UsuarioService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class GestionUsuariosController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ComboBox<String> cmbRol;
    @FXML private CheckBox chkActivo;
    
    @FXML private TableView<Usuario> tblUsuarios;
    @FXML private TableColumn<Usuario, Long> colId;
    @FXML private TableColumn<Usuario, String> colUsername;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, Boolean> colActivo;
    @FXML private TableColumn<Usuario, LocalDateTime> colFechaCreacion;
    
    @FXML private Label lblTotalUsuarios;
    
    @Autowired
    private UsuarioService usuarioService;
    
    private Usuario usuarioSeleccionado;
    private String usuarioActual;
    
    @FXML
    public void initialize() {
        configurarComboRol();
        configurarTabla();
        cargarUsuarios();
        configurarSeleccionTabla();
        
        System.out.println("✅ Módulo de Gestión de Usuarios cargado");
    }
    
    public void setUsuarioActual(String username) {
        this.usuarioActual = username;
    }
    
    private void configurarComboRol() {
        cmbRol.setItems(FXCollections.observableArrayList(
            Rol.ADMINISTRADOR.name(),
            Rol.USUARIO.name()
        ));
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
            }
        });
    }
    
    private void cargarDatosEnFormulario(Usuario usuario) {
        txtUsername.setText(usuario.getUsername());
        txtPassword.clear();
        cmbRol.setValue(usuario.getRol());
        chkActivo.setSelected(usuario.getActivo());
    }
    
    private void cargarUsuarios() {
        var usuarios = usuarioService.listarTodos();
        tblUsuarios.setItems(FXCollections.observableArrayList(usuarios));
        lblTotalUsuarios.setText(String.valueOf(usuarios.size()));
    }
    
    @FXML
    public void handleCrear() {
        if (!validarCampos()) return;
        
        try {
            usuarioService.crearUsuario(
                txtUsername.getText().trim(),
                txtPassword.getText(),
                cmbRol.getValue(),
                usuarioActual != null ? usuarioActual : "admin"
            );
            
            mostrarAlerta("Éxito", "Usuario creado correctamente", Alert.AlertType.INFORMATION);
            handleLimpiar();
            cargarUsuarios();
            
        } catch (Exception e) {
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
            usuarioService.actualizarUsuario(
                usuarioSeleccionado.getId(),
                txtUsername.getText().trim(),
                cmbRol.getValue(),
                chkActivo.isSelected(),
                usuarioActual != null ? usuarioActual : "admin"
            );
            
            mostrarAlerta("Éxito", "Usuario actualizado correctamente", Alert.AlertType.INFORMATION);
            handleLimpiar();
            cargarUsuarios();
            
        } catch (Exception e) {
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
            
            mostrarAlerta("Éxito", "Contraseña cambiada correctamente", Alert.AlertType.INFORMATION);
            txtPassword.clear();
            
        } catch (Exception e) {
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
                
                mostrarAlerta("Éxito", "Usuario eliminado correctamente", Alert.AlertType.INFORMATION);
                handleLimpiar();
                cargarUsuarios();
                
            } catch (Exception e) {
                mostrarAlerta("Error", "No se pudo eliminar el usuario: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
    
    @FXML
    public void handleLimpiar() {
        txtUsername.clear();
        txtPassword.clear();
        cmbRol.setValue(null);
        chkActivo.setSelected(true);
        usuarioSeleccionado = null;
        tblUsuarios.getSelectionModel().clearSelection();
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