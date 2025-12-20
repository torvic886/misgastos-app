package com.misgastos.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;

public class SceneManager {

    public static void applyStyles(Scene scene) {
        try {
            String css = SceneManager.class
                    .getResource("/css/styles.css")
                    .toExternalForm();

            scene.getStylesheets().clear();   // 🔑 CLAVE
            scene.getStylesheets().add(css);

        } catch (Exception e) {
            System.err.println("Error al cargar estilos CSS");
            e.printStackTrace();
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
            e.printStackTrace();
            System.err.println("Error al cambiar escena: " + fxmlPath);
        }
    }
}
