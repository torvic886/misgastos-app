package com.misgastos.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clave propia de la pantalla "Respaldo de datos". No depende de ningún usuario del
 * programa: solo quien la sabe puede entrar, aunque sea administrador.
 *
 * La clave NO se guarda en texto: se guarda un resumen (PBKDF2 con sal) en
 * MisGastos\respaldo.clave. La primera vez que se abre la pantalla se pide crearla.
 *
 * Sobre los logs: nunca se registra la clave escrita, ni el resumen, ni la sal,
 * ni el contenido del archivo. Solo se anotan los hechos (creada, acceso, intento fallido).
 */
public final class ClaveRespaldo {

    private static final Logger log = LoggerFactory.getLogger(ClaveRespaldo.class);

    private static final int ITERACIONES = 120_000;
    private static final int BITS = 256;
    private static final int LARGO_SAL = 16;
    private static final int LARGO_MINIMO = 4;

    private ClaveRespaldo() {
    }

    private static Path archivo() {
        return Paths.get(System.getProperty("user.home"), "MisGastos", "respaldo.clave");
    }

    /** Devuelve true si la persona puede entrar (clave correcta, o clave recién creada). */
    public static boolean pedirAcceso(Window ventana) {
        if (!Files.exists(archivo())) {
            log.debug("No existe clave de respaldo: se pide crearla");
            return crearClave(ventana);
        }
        log.debug("Se pide la clave de respaldo para entrar");
        return verificarClave(ventana);
    }

    // ==================== CREAR ====================

    private static boolean crearClave(Window ventana) {
        String aviso = "Aún no hay una clave para el respaldo. Crea una que solo tú conozcas.\n"
                + "Mínimo " + LARGO_MINIMO + " caracteres.";
        String error = null;
        while (true) {
            Optional<String[]> r = dialogo(ventana, "Crear clave de respaldo", aviso, true, error);
            if (r.isEmpty()) {
                log.debug("Creación de la clave de respaldo cancelada por el usuario");
                return false;
            }
            String uno = r.get()[0];
            String dos = r.get()[1];
            if (uno.length() < LARGO_MINIMO) {
                log.debug("Clave de respaldo rechazada: muy corta");
                error = "La clave debe tener al menos " + LARGO_MINIMO + " caracteres.";
            } else if (!uno.equals(dos)) {
                log.debug("Clave de respaldo rechazada: las dos claves no coinciden");
                error = "Las dos claves no coinciden.";
            } else {
                try {
                    guardar(uno.toCharArray());
                    log.info("Clave de respaldo creada");
                    return true;
                } catch (Exception e) {
                    log.error("No se pudo guardar la clave de respaldo", e);
                    alerta(ventana, Alert.AlertType.ERROR, "No se pudo guardar la clave", e.getMessage());
                    return false;
                }
            }
        }
    }

    private static void guardar(char[] clave) throws Exception {
        byte[] sal = new byte[LARGO_SAL];
        new SecureRandom().nextBytes(sal);
        byte[] resumen = resumir(clave, sal);
        String texto = "v1:" + Base64.getEncoder().encodeToString(sal)
                + ":" + Base64.getEncoder().encodeToString(resumen);
        Files.createDirectories(archivo().getParent());
        Files.writeString(archivo(), texto, StandardCharsets.UTF_8);
    }

    // ==================== VERIFICAR ====================

    private static boolean verificarClave(Window ventana) {
        byte[] sal;
        byte[] esperado;
        try {
            String[] partes = Files.readString(archivo(), StandardCharsets.UTF_8).trim().split(":");
            if (partes.length != 3 || !"v1".equals(partes[0])) {
                throw new IllegalStateException("formato no reconocido");
            }
            sal = Base64.getDecoder().decode(partes[1]);
            esperado = Base64.getDecoder().decode(partes[2]);
        } catch (Exception e) {
            log.error("No se pudo leer el archivo de la clave de respaldo (dañado o con formato no reconocido)", e);
            alerta(ventana, Alert.AlertType.ERROR, "Archivo de clave dañado",
                    "No se puede leer la clave guardada (" + archivo() + ").");
            return false;
        }

        String error = null;
        int intentos = 0;
        while (true) {
            Optional<String[]> r = dialogo(ventana, "Respaldo de datos",
                    "Escribe la clave de respaldo para continuar.", false, error);
            if (r.isEmpty()) {
                log.debug("Ingreso al respaldo cancelado por el usuario");
                return false;
            }
            try {
                byte[] intento = resumir(r.get()[0].toCharArray(), sal);
                if (MessageDigest.isEqual(intento, esperado)) {
                    log.info("Acceso concedido con la clave de respaldo");
                    return true;
                }
            } catch (Exception e) {
                log.error("No se pudo verificar la clave de respaldo", e);
                alerta(ventana, Alert.AlertType.ERROR, "Error", e.getMessage());
                return false;
            }
            intentos++;
            log.warn("Clave de respaldo incorrecta (intento {})", intentos);
            error = "Clave incorrecta.";
        }
    }

    private static byte[] resumir(char[] clave, byte[] sal) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(clave, sal, ITERACIONES, BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    // ==================== VENTANAS ====================

    private static Optional<String[]> dialogo(Window ventana, String titulo, String texto,
                                              boolean repetir, String error) {
        Dialog<String[]> d = new Dialog<>();
        if (ventana != null) {
            d.initOwner(ventana);
        }
        d.setTitle(titulo);
        d.setHeaderText(texto);

        PasswordField uno = new PasswordField();
        uno.setPromptText("Clave");
        PasswordField dos = new PasswordField();
        dos.setPromptText("Repite la clave");

        VBox caja = new VBox(10);
        caja.setPadding(new Insets(10, 0, 0, 0));
        caja.getChildren().add(uno);
        if (repetir) {
            caja.getChildren().add(dos);
        }
        if (error != null) {
            Label l = new Label(error);
            l.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            caja.getChildren().add(l);
        }

        ButtonType aceptar = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        d.getDialogPane().getButtonTypes().setAll(aceptar, cancelar);
        d.getDialogPane().setContent(caja);
        estilos(d.getDialogPane());
        d.setResultConverter(b -> b == aceptar ? new String[]{uno.getText(), dos.getText()} : null);

        Platform.runLater(uno::requestFocus);
        return d.showAndWait();
    }

    private static void alerta(Window ventana, Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo);
        if (ventana != null) {
            a.initOwner(ventana);
        }
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        estilos(a.getDialogPane());
        a.showAndWait();
    }

    private static void estilos(javafx.scene.control.DialogPane panel) {
        try {
            panel.getStylesheets().add(ClaveRespaldo.class.getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignorada) {
            // sin estilos, funciona igual
            log.debug("No se pudieron aplicar los estilos al diálogo de la clave de respaldo");
        }
    }
}