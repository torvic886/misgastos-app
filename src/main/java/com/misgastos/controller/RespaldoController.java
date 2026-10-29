package com.misgastos.controller;

import com.misgastos.MisGastosLauncher;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Pantalla "Respaldo de datos": abrir la carpeta de datos, exportar una copia
 * de la base y importar una copia.
 *
 * Importar NO reemplaza la base mientras el programa está abierto (Windows no deja
 * reemplazar un archivo en uso). La copia se deja preparada y MisGastosLauncher la
 * aplica en el siguiente arranque, después de guardar un respaldo de lo actual.
 */
@Slf4j
@Component
public class RespaldoController {

    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter FECHA_PANTALLA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label lblCarpeta;
    @FXML private Label lblBase;
    @FXML private Label lblRespaldos;
    @FXML private Label lblPendiente;
    @FXML private Button btnCancelarPendiente;

    @Autowired
    private DataSource dataSource;

    // ==================== RUTAS ====================

    private Path carpetaDatos() {
        return Paths.get(System.getProperty("user.home"), "MisGastos");
    }

    private Path archivoBase() {
        return carpetaDatos().resolve("misgastos.db");
    }

    private Path carpetaRespaldos() {
        return carpetaDatos().resolve("respaldos");
    }

    private Path archivoPendiente() {
        return carpetaDatos().resolve(MisGastosLauncher.ARCHIVO_IMPORTACION);
    }

    // ==================== PANTALLA ====================

    @FXML
    public void initialize() {
        actualizarResumen();
    }

    private void actualizarResumen() {
        lblCarpeta.setText(carpetaDatos().toString());

        try {
            double mb = Files.size(archivoBase()) / 1048576.0;
            lblBase.setText(String.format(CO, "misgastos.db  (%.2f MB)", mb));
        } catch (Exception e) {
            lblBase.setText("No se encontró la base de datos");
        }

        List<Path> respaldos = listarRespaldos();
        if (respaldos.isEmpty()) {
            lblRespaldos.setText("Todavía no hay copias automáticas.");
        } else {
            Path ultimo = respaldos.get(respaldos.size() - 1);
            String cuando;
            try {
                cuando = LocalDateTime
                        .ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(ultimo).toMillis()), ZoneId.systemDefault())
                        .format(FECHA_PANTALLA);
            } catch (Exception e) {
                cuando = ultimo.getFileName().toString();
            }
            lblRespaldos.setText(respaldos.size() + " copia(s) automática(s). La última: " + cuando);
        }

        boolean hayPendiente = Files.exists(archivoPendiente());
        lblPendiente.setVisible(hayPendiente);
        lblPendiente.setManaged(hayPendiente);
        btnCancelarPendiente.setVisible(hayPendiente);
        btnCancelarPendiente.setManaged(hayPendiente);
    }

    private List<Path> listarRespaldos() {
        Path carpeta = carpetaRespaldos();
        if (!Files.isDirectory(carpeta)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("misgastos_") && n.endsWith(".db");
                    })
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    // ==================== ABRIR CARPETAS ====================

    @FXML
    public void handleAbrirCarpetaDatos() {
        abrirCarpeta(carpetaDatos());
    }

    @FXML
    public void handleAbrirCarpetaRespaldos() {
        abrirCarpeta(carpetaRespaldos());
    }

    private void abrirCarpeta(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", carpeta.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", carpeta.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", carpeta.toString());
            }
            pb.start();
        } catch (Exception e) {
            alerta(Alert.AlertType.ERROR, "No se pudo abrir la carpeta",
                    "Puedes abrirla a mano desde el Explorador de archivos:\n" + carpeta);
        }
    }

    // ==================== EXPORTAR ====================

    @FXML
    public void handleExportar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Exportar copia de MisGastos");
        selector.setInitialFileName("misgastos_" + LocalDateTime.now().format(FECHA_ARCHIVO) + ".db");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Base de datos MisGastos (*.db)", "*.db"));

        File elegido = selector.showSaveDialog(ventana());
        if (elegido == null) {
            return;
        }

        Path destino = elegido.toPath();
        if (!destino.getFileName().toString().toLowerCase().endsWith(".db")) {
            destino = destino.resolveSibling(destino.getFileName() + ".db");
        }

        try {
            if (Files.isSameFile(archivoBase(), destino)) {
                alerta(Alert.AlertType.WARNING, "Elige otro lugar",
                        "No puedes exportar encima de la base que el programa está usando.");
                return;
            }
        } catch (Exception ignorada) {
            // el destino aún no existe: es lo normal
        }

        try {
            Files.deleteIfExists(destino);
            copiarBaseConsistente(destino);
            log.info("Copia exportada: {}", destino);
            alerta(Alert.AlertType.INFORMATION, "Copia exportada",
                    "La copia quedó guardada en:\n" + destino
                            + "\n\nPuedes llevarla a otro computador o guardarla como respaldo.");
        } catch (Exception e) {
        	log.error("No se pudo exportar la copia de la base de datos", e);
            alerta(Alert.AlertType.ERROR, "No se pudo exportar", "Motivo: " + e.getMessage());
        }
    }

    /**
     * Copia la base mientras el programa está abierto. VACUUM INTO genera una copia
     * completa y consistente aunque haya datos recién guardados. Si no estuviera
     * disponible, se copia el archivo directamente.
     */
    private void copiarBaseConsistente(Path destino) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("VACUUM INTO '" + destino.toString().replace("'", "''") + "'");
        } catch (Exception e) {
        	log.warn("VACUUM INTO no disponible, se copia el archivo: {}", e.getMessage());
            Files.copy(archivoBase(), destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ==================== IMPORTAR ====================

    @FXML
    public void handleImportar() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elige la copia que quieres importar");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Base de datos MisGastos (*.db)", "*.db"));
        if (Files.isDirectory(carpetaRespaldos())) {
            selector.setInitialDirectory(carpetaRespaldos().toFile());
        }

        File elegido = selector.showOpenDialog(ventana());
        if (elegido == null) {
            return;
        }
        Path origen = elegido.toPath();

        try {
            if (Files.isSameFile(origen, archivoBase())) {
                alerta(Alert.AlertType.WARNING, "Ese es el archivo actual",
                        "Elige una copia guardada en otro lugar (por ejemplo, una de la carpeta de respaldos).");
                return;
            }
        } catch (Exception ignorada) {
            // seguimos
        }

        Path preparado = archivoPendiente();
        try {
            Files.copy(origen, preparado, StandardCopyOption.REPLACE_EXISTING);

            String problema = validarBase(preparado);
            if (problema != null) {
                Files.deleteIfExists(preparado);
                log.warn("Importación rechazada, el archivo no sirve: {}", problema);
                alerta(Alert.AlertType.ERROR, "Esa copia no sirve", problema);
                actualizarResumen();
                return;
            }

            Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION);
            confirmar.setTitle("Importar copia");
            confirmar.setHeaderText("Se reemplazarán TODOS los datos actuales");
            confirmar.setContentText(
                    "Los datos de este archivo sustituirán a los que tienes ahora.\n\n"
                  + "• El cambio se aplica la próxima vez que abras el programa.\n"
                  + "• Antes de aplicarlo se guarda una copia de lo actual en la carpeta de respaldos.\n"
                  + "• Si registras gastos antes de cerrar, quedarán en esa copia, no en los datos nuevos.\n\n"
                  + "¿Quieres continuar?");
            aplicarEstilos(confirmar);
            ButtonType si = new ButtonType("Sí, importar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            ButtonType no = new ButtonType("Cancelar", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            confirmar.getButtonTypes().setAll(si, no);

            Optional<ButtonType> respuesta = confirmar.showAndWait();
            if (respuesta.isEmpty() || respuesta.get() != si) {
                Files.deleteIfExists(preparado);
                log.info("Importación cancelada por el usuario");
                actualizarResumen();
                return;
            }

            actualizarResumen();
            log.info("Importación preparada desde {}. Se aplicará al reiniciar el programa", origen);
            alerta(Alert.AlertType.INFORMATION, "Copia lista para aplicar",
                    "Cierra el programa y vuelve a abrirlo. Al iniciar se cargarán los datos de la copia importada.");
        } catch (Exception e) {
        	log.error("No se pudo importar la copia de la base de datos", e);
            try {
                Files.deleteIfExists(preparado);
            } catch (Exception ignorada) {
                // nada más que hacer
            }
            alerta(Alert.AlertType.ERROR, "No se pudo importar", "Motivo: " + e.getMessage());
            actualizarResumen();
        }
    }

    @FXML
    public void handleCancelarPendiente() {
        try {
            Files.deleteIfExists(archivoPendiente());
            log.info("Importación pendiente cancelada");
        } catch (Exception e) {
            alerta(Alert.AlertType.ERROR, "No se pudo cancelar", "Motivo: " + e.getMessage());
        }
        actualizarResumen();
    }

    /**
     * Revisa que el archivo sea una base de MisGastos sana.
     * Devuelve null si está bien, o el motivo en palabras sencillas si no.
     */
    private String validarBase(Path archivo) {
        try (InputStream in = Files.newInputStream(archivo)) {
            byte[] cabecera = in.readNBytes(16);
            byte[] esperada = "SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!Arrays.equals(cabecera, esperada)) {
                return "El archivo no es una base de datos de MisGastos.";
            }
        } catch (Exception e) {
            return "No se pudo leer el archivo: " + e.getMessage();
        }

        String url = "jdbc:sqlite:" + archivo.toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    return "El archivo está dañado, no se puede importar.";
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'usuarios'")) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    return "El archivo es una base de datos, pero no es de MisGastos.";
                }
            }
        } catch (Exception e) {
            return "No se pudo revisar el archivo: " + e.getMessage();
        }
        return null;
    }

    // ==================== AUXILIARES ====================

    private Window ventana() {
        return lblCarpeta.getScene() == null ? null : lblCarpeta.getScene().getWindow();
    }

    private void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        aplicarEstilos(a);
        a.showAndWait();
    }

    private void aplicarEstilos(Alert a) {
        try {
            a.getDialogPane().getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignorada) {
            // sin estilos, el diálogo funciona igual
        }
    }
}
