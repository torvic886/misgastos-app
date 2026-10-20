package com.misgastos.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Filtro tipo Excel para una TableView: un botón ▼ en cada encabezado abre una
 * lista de valores con casillas. Mantiene el orden por columna y avisa (alCambiar)
 * con la lista que queda visible, para recalcular totales.
 *
 * Uso:
 *   filtro = new FiltroExcel<>(tabla, this::actualizarTotal);
 *   filtro.agregar(colProducto, "Producto", Gasto::getProducto);
 *   filtro.setDatos(listaDeGastos);   // en lugar de tabla.setItems(...)
 */
public class FiltroExcel<T> {

    private static final String ESTILO_NORMAL =
        "-fx-padding: 0 4 0 4; -fx-font-size: 10px; -fx-background-color: transparent; "
        + "-fx-text-fill: #64748b; -fx-cursor: hand;";
    private static final String ESTILO_ACTIVO =
        "-fx-padding: 0 4 0 4; -fx-font-size: 10px; -fx-background-color: #dbeafe; "
        + "-fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand;";

    private final ObservableList<T> maestro = FXCollections.observableArrayList();
    private final FilteredList<T> filtrados = new FilteredList<>(maestro, x -> true);
    private final Map<TableColumn<T, ?>, Function<T, String>> extractores = new LinkedHashMap<>();
    private final Map<TableColumn<T, ?>, Set<String>> seleccionados = new HashMap<>();
    private final Map<TableColumn<T, ?>, Button> botones = new HashMap<>();
    private final Consumer<List<T>> alCambiar;

    public FiltroExcel(TableView<T> tabla, Consumer<List<T>> alCambiar) {
        this.alCambiar = alCambiar;
        SortedList<T> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
    }

    /** Pone el botón ▼ en el encabezado de la columna. */
    public void agregar(TableColumn<T, ?> col, String titulo, Function<T, String> extractor) {
        Label lbl = new Label(titulo);
        lbl.setStyle("-fx-font-weight: bold;");

        Button btn = new Button("▼");
        btn.setFocusTraversable(false);
        btn.setStyle(ESTILO_NORMAL);
        btn.setOnAction(e -> mostrarMenu(col, btn));

        HBox caja = new HBox(4, lbl, btn);
        caja.setAlignment(Pos.CENTER);
        col.setText("");
        col.setGraphic(caja);

        extractores.put(col, extractor);
        botones.put(col, btn);
    }

    /** Carga los datos completos (borra los filtros activos). */
    public void setDatos(List<T> datos) {
        seleccionados.clear();
        maestro.setAll(datos);
        aplicar();
    }

    /** Quita todos los filtros activos. */
    public void limpiarFiltros() {
        seleccionados.clear();
        aplicar();
    }

    private String valorDe(TableColumn<T, ?> col, T item) {
        String v = extractores.get(col).apply(item);
        return v == null ? "" : v;
    }

    private void aplicar() {
        filtrados.setPredicate(item -> {
            for (Map.Entry<TableColumn<T, ?>, Set<String>> f : seleccionados.entrySet()) {
                if (!f.getValue().contains(valorDe(f.getKey(), item))) {
                    return false;
                }
            }
            return true;
        });
        botones.forEach((c, b) ->
            b.setStyle(seleccionados.containsKey(c) ? ESTILO_ACTIVO : ESTILO_NORMAL));
        if (alCambiar != null) {
            alCambiar.accept(new ArrayList<>(filtrados));
        }
    }

    private void mostrarMenu(TableColumn<T, ?> col, Button anchor) {
        TreeSet<String> valores = maestro.stream()
            .map(item -> valorDe(col, item))
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actuales = seleccionados.containsKey(col)
            ? new HashSet<>(seleccionados.get(col)) : new HashSet<>(valores);

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar...");
        CheckBox chkTodo = new CheckBox("(Seleccionar todo)");
        chkTodo.setSelected(actuales.containsAll(valores));

        VBox lista = new VBox(4);
        lista.setPadding(new Insets(4));
        ScrollPane sp = new ScrollPane(lista);
        sp.setFitToWidth(true);
        sp.setPrefHeight(200);
        sp.setPrefWidth(230);

        Button ok = new Button("Aceptar");
        Button limpiar = new Button("Limpiar filtro");
        VBox contenido = new VBox(8, buscar, chkTodo, sp, new HBox(8, ok, limpiar));
        contenido.setPadding(new Insets(8));

        ContextMenu menu = new ContextMenu();
        menu.getItems().add(new CustomMenuItem(contenido, false));

        Runnable refrescar = () -> {
            String q = buscar.getText() == null ? "" : buscar.getText().trim().toLowerCase();
            lista.getChildren().clear();
            for (String v : valores) {
                String etiqueta = v.isEmpty() ? "(vacío)" : v;
                if (!q.isEmpty() && !etiqueta.toLowerCase().contains(q)) continue;
                CheckBox cb = new CheckBox(etiqueta);
                cb.setSelected(actuales.contains(v));
                cb.selectedProperty().addListener((o, a, sel) -> {
                    if (sel) actuales.add(v); else actuales.remove(v);
                });
                lista.getChildren().add(cb);
            }
        };
        buscar.textProperty().addListener((o, a, b) -> refrescar.run());
        chkTodo.setOnAction(e -> {
            for (Node n : lista.getChildren()) {
                ((CheckBox) n).setSelected(chkTodo.isSelected());
            }
        });
        ok.setOnAction(e -> {
            if (actuales.containsAll(valores)) seleccionados.remove(col);
            else seleccionados.put(col, new HashSet<>(actuales));
            menu.hide();
            aplicar();
        });
        limpiar.setOnAction(e -> {
            seleccionados.remove(col);
            menu.hide();
            aplicar();
        });

        refrescar.run();
        menu.show(anchor, Side.BOTTOM, 0, 0);
    }
}