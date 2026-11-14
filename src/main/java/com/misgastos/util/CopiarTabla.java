package com.misgastos.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ctrl+C en una tabla: copia las filas seleccionadas con su encabezado en la
 * primera línea, separando las columnas con tabuladores (se pega en Excel en
 * columnas). Los valores en pesos salen como número limpio ("$ 92.000.000"
 * pasa a "92000000") y las cédulas con puntos también ("41.911.884" pasa a
 * "41911884"). No cambia la tabla ni los datos.
 */
public final class CopiarTabla {

    private static final Logger log = LoggerFactory.getLogger(CopiarTabla.class);
    private static final KeyCombination CTRL_C =
        new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    private CopiarTabla() { }

    /** Activa Ctrl+C en la tabla, con todas las columnas y todas las filas seleccionadas. */
    public static <T> void habilitar(TableView<T> tabla) {
        habilitar(tabla, fila -> true);
    }

    /**
     * Activa Ctrl+C en la tabla. incluirFila decide qué filas seleccionadas se copian
     * y columnasOmitidas son las que no se copian (por ejemplo, una de botones).
     */
    @SafeVarargs
    public static <T> void habilitar(TableView<T> tabla, Predicate<T> incluirFila,
                                     TableColumn<T, ?>... columnasOmitidas) {
        List<TableColumn<T, ?>> omitidas = List.of(columnasOmitidas);
        tabla.addEventHandler(KeyEvent.KEY_PRESSED, ev -> {
            if (!CTRL_C.match(ev)) {
                return;
            }
            // Si se está escribiendo en un campo de texto, Ctrl+C es del campo
            if (tabla.getScene() != null
                    && tabla.getScene().getFocusOwner() instanceof TextInputControl) {
                return;
            }
            copiar(tabla, incluirFila, omitidas);
            ev.consume();
        });
    }

    private static <T> void copiar(TableView<T> tabla, Predicate<T> incluirFila,
                                   List<TableColumn<T, ?>> omitidas) {
        List<Integer> filas = new ArrayList<>(tabla.getSelectionModel().getSelectedIndices());
        if (filas.isEmpty()) {
            return;
        }
        Collections.sort(filas);

        List<TableColumn<T, ?>> columnas = new ArrayList<>();
        for (TableColumn<T, ?> c : tabla.getVisibleLeafColumns()) {
            if (!omitidas.contains(c)) {
                columnas.add(c);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < columnas.size(); j++) {
            if (j > 0) {
                sb.append('\t');
            }
            sb.append(limpiar(columnas.get(j).getText()));
        }

        int copiadas = 0;
        for (int fila : filas) {
            T item = tabla.getItems().get(fila);
            if (item == null || !incluirFila.test(item)) {
                continue;
            }
            sb.append('\n');
            for (int j = 0; j < columnas.size(); j++) {
                if (j > 0) {
                    sb.append('\t');
                }
                sb.append(valor(columnas.get(j).getCellData(fila)));
            }
            copiadas++;
        }
        if (copiadas == 0) {
            return;
        }

        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(contenido);
        log.debug("Copiadas {} fila(s) de la tabla", copiadas);
    }

    /** Texto de la celda; los valores en pesos y las cédulas con puntos salen como número. */
    private static String valor(Object dato) {
        if (dato == null) {
            return "";
        }
        String s = limpiar(dato.toString());
        if (s.matches("^-?\\s*\\$\\s*-?[\\d.]+(,\\d+)?$")) {
            return s.replaceAll("[^0-9,\\-]", "").replaceAll(",0+$", "");
        }
        if (s.matches("^\\d{1,3}(\\.\\d{3})+$")) {
            return s.replace(".", "");
        }
        return s;
    }

    /** Sin tabuladores, saltos de línea ni espacios especiales, que romperían las columnas al pegar. */
    private static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\u00A0', ' ').replace('\u202F', ' ')
            .replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }
}