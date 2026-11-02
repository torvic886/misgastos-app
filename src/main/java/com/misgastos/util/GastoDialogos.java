package com.misgastos.util;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.model.Subcategoria;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.GastoService;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import lombok.extern.slf4j.Slf4j;
import javafx.scene.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Formulario de edición y confirmación de eliminación de un gasto, para usarlos
 * desde cualquier tabla (clic derecho en el historial, en últimos gastos, etc.).
 * Es el mismo formulario que usa Buscar y Editar, con las mismas reglas.
 */
@Slf4j
@Component
public class GastoDialogos {

    @Autowired
    private GastoService gastoService;

    @Autowired
    private CategoriaService categoriaService;

    // Peso colombiano sin decimales
    private static final NumberFormat formatoCOP;

    static {
        formatoCOP = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        formatoCOP.setMaximumFractionDigits(0);
        formatoCOP.setMinimumFractionDigits(0);
    }

    /**
     * Abre el formulario para editar el gasto. Si se guarda correctamente,
     * avisa al usuario y ejecuta alGuardar (por ejemplo, para recargar la tabla).
     */
    public void editar(Gasto gasto, Runnable alGuardar) {
        Dialog<Gasto> dialog = new Dialog<>();
        dialog.setTitle("✏️ Editar Gasto");
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("gd-dialog");
        dialog.getDialogPane().setPrefWidth(580);

        ButtonType btnGuardar = new ButtonType("💾 Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        DatePicker dpFecha = new DatePicker(gasto.getFecha());

        Spinner<Integer> spHora = new Spinner<>(0, 23, gasto.getHora().getHour());
        Spinner<Integer> spMin = new Spinner<>(0, 59, gasto.getHora().getMinute());
        configurarSpinner(spHora, 23);
        configurarSpinner(spMin, 59);

        Button btnAhora = new Button("Ahora");
        btnAhora.getStyleClass().add("gd-boton-ahora");
        btnAhora.setOnAction(e -> {
            LocalTime ahora = LocalTime.now();
            spHora.getValueFactory().setValue(ahora.getHour());
            spMin.getValueFactory().setValue(ahora.getMinute());
        });

        Label dosPuntos = new Label(":");
        dosPuntos.getStyleClass().add("gd-label");
        HBox cajaHora = new HBox(6, spHora, dosPuntos, spMin, btnAhora);
        cajaHora.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Categoria> cmbCategoria = new ComboBox<>();
        cmbCategoria.getItems().setAll(categoriaService.listarCategorias());
        cmbCategoria.setValue(gasto.getCategoria());

        ComboBox<Subcategoria> cmbSubcategoria = new ComboBox<>();
        cmbSubcategoria.getItems().setAll(
            categoriaService.listarSubcategoriasPorCategoria(gasto.getCategoria().getId())
        );
        cmbSubcategoria.setValue(gasto.getSubcategoria());

        TextField txtProducto = new TextField(gasto.getProducto());
        TextField txtCantidad = new TextField(String.valueOf(gasto.getCantidad()));
        TextField txtValorUnitario = new TextField(gasto.getValorUnitario().toString());

        Label lblTotal = new Label(formatoCOP.format(gasto.getValorTotal()));
        lblTotal.getStyleClass().add("gd-total-valor");

        TextArea txtNotas = new TextArea(gasto.getNotas());
        txtNotas.setPrefRowCount(3);
        txtNotas.setWrapText(true);

        cmbCategoria.setOnAction(e -> {
            Categoria selected = cmbCategoria.getValue();
            if (selected != null) {
                cmbSubcategoria.getItems().setAll(
                    categoriaService.listarSubcategoriasPorCategoria(selected.getId())
                );
            }
        });

        Runnable calcularTotal = () -> {
            try {
                int cantidad = Integer.parseInt(txtCantidad.getText());
                BigDecimal valorUnit = new BigDecimal(txtValorUnitario.getText());
                BigDecimal total = valorUnit.multiply(BigDecimal.valueOf(cantidad));
                lblTotal.setText(formatoCOP.format(total));
            } catch (Exception e) {
                log.debug("Cantidad o valor incompletos en la edición; el total queda en 0");
                lblTotal.setText(formatoCOP.format(0));
            }
        };

        txtCantidad.textProperty().addListener((obs, old, newVal) -> calcularTotal.run());
        txtValorUnitario.textProperty().addListener((obs, old, newVal) -> calcularTotal.run());

        // Encabezado
        Label titulo = new Label("Editar gasto #" + gasto.getId());
        titulo.getStyleClass().add("gd-titulo");
        Label subtitulo = new Label("Modifica los datos y pulsa Guardar");
        subtitulo.getStyleClass().add("gd-subtitulo");
        VBox encabezado = new VBox(2, titulo, subtitulo);
        encabezado.getStyleClass().add("gd-encabezado");

        // Caja del total
        Label lblTotalTitulo = new Label("Valor total");
        lblTotalTitulo.getStyleClass().add("gd-total-titulo");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cajaTotal = new HBox(lblTotalTitulo, espacio, lblTotal);
        cajaTotal.setAlignment(Pos.CENTER_LEFT);
        cajaTotal.getStyleClass().add("gd-total");

        // Formulario en dos columnas
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        grid.add(campo("Fecha", dpFecha), 0, 0);
        grid.add(campo("Hora", cajaHora), 1, 0);
        grid.add(campo("Categoría", cmbCategoria), 0, 1);
        grid.add(campo("Subcategoría", cmbSubcategoria), 1, 1);
        grid.add(campo("Producto", txtProducto), 0, 2, 2, 1);
        grid.add(campo("Cantidad", txtCantidad), 0, 3);
        grid.add(campo("Valor unitario", txtValorUnitario), 1, 3);
        grid.add(cajaTotal, 0, 4, 2, 1);
        grid.add(campo("Notas", txtNotas), 0, 5, 2, 1);

        VBox contenido = new VBox(16, encabezado, grid);
        contenido.getStyleClass().add("gd-contenido");
        dialog.getDialogPane().setContent(contenido);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                try {
                    if (cmbCategoria.getValue() == null || cmbSubcategoria.getValue() == null) {
                        log.warn("Edición de gasto sin categoría o subcategoría (id={})", gasto.getId());
                        mostrarAlerta("Validación", "Seleccione la categoría y la subcategoría.",
                            Alert.AlertType.WARNING);
                        return null;
                    }

                    spHora.increment(0); // confirma lo que se haya escrito a mano
                    spMin.increment(0);
                    LocalTime hora = LocalTime.of(spHora.getValue(), spMin.getValue());

                    gastoService.actualizarGasto(
                        gasto.getId(),
                        cmbCategoria.getValue().getId(),
                        cmbSubcategoria.getValue().getId(),
                        txtProducto.getText(),
                        Integer.parseInt(txtCantidad.getText()),
                        new BigDecimal(txtValorUnitario.getText()),
                        txtNotas.getText(),
                        dpFecha.getValue(),
                        hora
                    );

                    return gasto;
                } catch (Exception e) {
                    log.error("No se pudo actualizar el gasto id={}", gasto.getId(), e);
                    mostrarAlerta("Error", "Error al guardar: " + e.getMessage(), Alert.AlertType.ERROR);
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(gastoActualizado -> {
            log.info("Gasto actualizado desde el formulario de edición (id={})", gastoActualizado.getId());
            mostrarAlerta("Éxito", "Gasto actualizado correctamente", Alert.AlertType.INFORMATION);
            if (alGuardar != null) {
                alGuardar.run();
            }
        });
    }

    private VBox campo(String etiqueta, Node control) {
        Label lbl = new Label(etiqueta);
        lbl.getStyleClass().add("gd-label");
        if (control instanceof Control c) {
            c.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(4, lbl, control);
    }

    /** Selector de 2 dígitos (07, 09...), editable, que da la vuelta al llegar al límite. */
    private void configurarSpinner(Spinner<Integer> sp, int maximo) {
        sp.setEditable(true);
        sp.setPrefWidth(78);
        sp.getEditor().setAlignment(Pos.CENTER);

        SpinnerValueFactory.IntegerSpinnerValueFactory fabrica =
            (SpinnerValueFactory.IntegerSpinnerValueFactory) sp.getValueFactory();
        fabrica.setWrapAround(true);
        fabrica.setConverter(new StringConverter<Integer>() {
            @Override
            public String toString(Integer valor) {
                return valor == null ? "00" : String.format("%02d", valor);
            }

            @Override
            public Integer fromString(String texto) {
                try {
                    int v = Integer.parseInt(texto.trim());
                    return Math.max(0, Math.min(maximo, v));
                } catch (Exception e) {
                    return fabrica.getValue();
                }
            }
        });

        // Al salir del campo se confirma lo escrito
        sp.getEditor().focusedProperty().addListener((o, antes, enfocado) -> {
            if (!enfocado) {
                sp.increment(0);
            }
        });
    }

    /**
     * Pide confirmación y envía el gasto a la papelera. Si se elimina,
     * avisa al usuario y ejecuta alEliminar.
     */
    public void eliminar(Gasto gasto, Runnable alEliminar) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar eliminación");
        confirmacion.setHeaderText("¿Eliminar este gasto?");
        confirmacion.setContentText(
            "Producto: " + gasto.getProducto() + "\n" +
            "Valor: " + formatoCOP.format(gasto.getValorTotal()) + "\n" +
            "Fecha: " + gasto.getFecha()
        );

        confirmacion.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    gastoService.eliminarGasto(gasto.getId());
                    log.info("Gasto enviado a la papelera (id={})", gasto.getId());
                    mostrarAlerta("Éxito", "Gasto enviado a la papelera", Alert.AlertType.INFORMATION);
                    if (alEliminar != null) {
                        alEliminar.run();
                    }
                } catch (Exception e) {
                    log.error("No se pudo eliminar el gasto id={}", gasto.getId(), e);
                    mostrarAlerta("Error", "No se pudo eliminar el gasto: " + e.getMessage(),
                        Alert.AlertType.ERROR);
                }
            } else {
                log.debug("Eliminación del gasto id={} cancelada por el usuario", gasto.getId());
            }
        });
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}