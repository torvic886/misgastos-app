package com.misgastos.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

@Slf4j
public class SceneManager {

    public static void applyStyles(Scene scene) {
        try {
            String css = SceneManager.class
                    .getResource("/css/styles.css")
                    .toExternalForm();

            scene.getStylesheets().clear();   // 🔑 CLAVE
            scene.getStylesheets().add(css);

        } catch (Exception e) {
            log.error("Error al cargar estilos CSS", e);
        }
    }

    public static void cambiarEscena(
            Scene currentScene,
            String fxmlPath,
            ApplicationContext springContext,
            int width,
            int height,
            String title
    ) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(fxmlPath)
            );
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();

            Scene newScene = new Scene(root); // 🔑 NO FORZAR TAMAÑO
            applyStyles(newScene);

            Stage stage = (Stage) currentScene.getWindow();
            stage.setScene(newScene);
            stage.setTitle(title);
            stage.sizeToScene();              // 🔑
            stage.centerOnScreen();

        } catch (Exception e) {
            log.error("Error al cambiar escena: {}", fxmlPath, e);
        }
    }
}