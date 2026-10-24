package com.misgastos.controller;

import com.misgastos.model.AuditoriaLog;
import com.misgastos.model.Usuario;
import com.misgastos.service.AuditoriaService;
import com.misgastos.service.UsuarioService;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pantalla "Registro de cambios": muestra quién hizo qué y cuándo (tabla auditoria_logs).
 * Solo lectura. El acceso se limita a administradores desde el Dashboard.
 */
@Component
public class RegistroCambiosController {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int MAX_FILAS = 1000;
    private static final String TODOS = "Todos";
    private static final String TODAS = "Todas";

    private static final Map<String, String> NOMBRES_ACCION = Map.of(
            "EDITAR_GASTO", "Editar gasto",
            "BORRAR_GASTO", "Borrar gasto (a la papelera)",
            "RESTAURAR_GASTO", "Restaurar gasto",
            "ELIMINAR_DEFINITIVO", "Eliminar definitivamente",
            "VACIAR_PAPELERA", "Vaciar papelera",
            "LOGIN", "Inicio de sesión",
            "CREAR_USUARIO", "Crear usuario",
            "ACTUALIZAR_USUARIO", "Modificar usuario",
            "CAMBIAR_PASSWORD", "Cambiar contraseña",
            "ELIMINAR_USUARIO", "Eliminar usuario");

    @FXML private ComboBox<String> cmbUsuario;
    @FXML private ComboBox<String> cmbAccion;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private TableView<AuditoriaLog> tablaRegistros;
    @FXML private TableColumn<AuditoriaLog, String> colFecha;
    @FXML private TableColumn<AuditoriaLog, String> colUsuario;
    @FXML private TableColumn<AuditoriaLog, String> colAccion;
    @FXML private TableColumn<AuditoriaLog, String> colDetalles;
    @FXML private Label lblResultados;
    @FXML private Label lblDetalle;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private UsuarioService usuarioService;

    // Se comparan ids y códigos (texto), nunca entidades
    private final Map<String, Long> idPorUsuario = new LinkedHashMap<>();
    private final Map<String, String> codigoPorEtiqueta = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        configurarTabla();
        cargarFiltros();
        valoresPorDefecto();
        handleBuscar();
    }

    private void configurarTabla() {
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() == null ? "" : c.getValue().getFecha().format(FECHA_HORA)));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUsuario() == null ? "" : c.getValue().getUsuario().getUsername()));
        colAccion.setCellValueFactory(c -> new SimpleStringProperty(etiquetaAccion(c.getValue().getAccion())));
        colDetalles.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDetalles() == null ? "" : c.getValue().getDetalles()));

        tablaRegistros.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                lblDetalle.setText("Selecciona una fila para ver el detalle completo.");
            } else {
                String d = ahora.getDetalles();
                lblDetalle.setText(d == null || d.isBlank() ? "(sin detalle)" : d);
            }
        });
    }

    private void cargarFiltros() {
        idPorUsuario.clear();
        List<Usuario> usuarios = usuarioService.listarTodos().stream()
                .sorted(Comparator.comparing(u -> u.getUsername().toLowerCase(Locale.ROOT)))
                .toList();
        cmbUsuario.getItems().clear();
        cmbUsuario.getItems().add(TODOS);
        for (Usuario u : usuarios) {
            idPorUsuario.put(u.getUsername(), u.getId());
            cmbUsuario.getItems().add(u.getUsername());
        }

        codigoPorEtiqueta.clear();
        cmbAccion.getItems().clear();
        cmbAccion.getItems().add(TODAS);
        for (String codigo : auditoriaService.listarAcciones()) {
            String etiqueta = etiquetaAccion(codigo);
            codigoPorEtiqueta.put(etiqueta, codigo);
            cmbAccion.getItems().add(etiqueta);
        }
    }

    private void valoresPorDefecto() {
        cmbUsuario.setValue(TODOS);
        cmbAccion.setValue(TODAS);
        dpHasta.setValue(LocalDate.now());
        dpDesde.setValue(LocalDate.now().minusDays(30));
    }

    private String etiquetaAccion(String codigo) {
        if (codigo == null) {
            return "";
        }
        String nombre = NOMBRES_ACCION.get(codigo);
        if (nombre != null) {
            return nombre;
        }
        String legible = codigo.replace('_', ' ').toLowerCase(Locale.ROOT);
        return legible.substring(0, 1).toUpperCase(Locale.ROOT) + legible.substring(1);
    }

    @FXML
    public void handleBuscar() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            alerta("Revisa las fechas", "La fecha de inicio no puede ser posterior a la fecha fin.");
            return;
        }

        String usuario = cmbUsuario.getValue();
        Long usuarioId = (usuario == null || TODOS.equals(usuario)) ? null : idPorUsuario.get(usuario);

        String accion = cmbAccion.getValue();
        String codigo = (accion == null || TODAS.equals(accion)) ? null : codigoPorEtiqueta.get(accion);

        List<AuditoriaLog> resultado = auditoriaService.buscar(usuarioId, codigo, desde, hasta);

        if (resultado.size() > MAX_FILAS) {
            lblResultados.setText("Se muestran los " + MAX_FILAS + " más recientes de " + resultado.size()
                    + ". Acota las fechas para ver el resto.");
            resultado = resultado.subList(0, MAX_FILAS);
        } else {
            lblResultados.setText(resultado.size() + " registro(s)");
        }

        tablaRegistros.getItems().setAll(resultado);
        tablaRegistros.getSelectionModel().clearSelection();
        lblDetalle.setText("Selecciona una fila para ver el detalle completo.");
    }

    @FXML
    public void handleLimpiar() {
        valoresPorDefecto();
        handleBuscar();
    }

    private void alerta(String titulo, String mensaje) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        try {
            a.getDialogPane().getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignorada) {
            // sin estilos, el diálogo funciona igual
        }
        a.showAndWait();
    }
}