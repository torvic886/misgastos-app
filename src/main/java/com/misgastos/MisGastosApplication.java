package com.misgastos;

import com.misgastos.util.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MisGastosApplication extends Application {

    private static ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        springContext = SpringApplication.run(MisGastosApplication.class);
    }

    @Override
    public void start(Stage stage) throws Exception {

        // 🎨 CONFIGURAR ICONO DE LA APLICACIÓN (ALTA CALIDAD)
        try {
            // Cargar múltiples tamaños para diferentes contextos
            // El sistema operativo elegirá automáticamente el más apropiado
            stage.getIcons().addAll(
                new Image(getClass().getResourceAsStream("/images/icon-16.png"), 16, 16, true, true),
                new Image(getClass().getResourceAsStream("/images/icon-32.png"), 32, 32, true, true),
                new Image(getClass().getResourceAsStream("/images/icon-48.png"), 48, 48, true, true),
                new Image(getClass().getResourceAsStream("/images/icon-64.png"), 64, 64, true, true),
                new Image(getClass().getResourceAsStream("/images/icon-128.png"), 128, 128, true, true),
                new Image(getClass().getResourceAsStream("/images/icon-256.png"), 256, 256, true, true)
            );
            
            System.out.println("✅ Iconos cargados correctamente");
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo cargar el icono: " + e.getMessage());
            // Intentar cargar un icono único de alta resolución como fallback
            try {
                Image fallbackIcon = new Image(
                    getClass().getResourceAsStream("/images/app-icon.png"),
                    256, 256, true, true  // smooth = true para mejor calidad
                );
                stage.getIcons().add(fallbackIcon);
            } catch (Exception ex) {
                System.err.println("⚠️ No se pudo cargar icono de respaldo");
            }
        }

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/login.fxml")
        );
        loader.setControllerFactory(springContext::getBean);

        Parent root = loader.load();
        
        // 🔥 Tamaño más grande para el login
        javafx.geometry.Rectangle2D screenBounds = 
            javafx.stage.Screen.getPrimary().getVisualBounds();
        
        // Login ocupa 75% del ancho y 90% del alto de la pantalla
        double loginWidth = screenBounds.getWidth() * 0.75;
        double loginHeight = screenBounds.getHeight() * 0.90;
        
        Scene scene = new Scene(root, loginWidth, loginHeight);

        SceneManager.applyStyles(scene);

        stage.setTitle("ExpenseFlow - Login");
        stage.setScene(scene);
        
        // ✅ Establecer tamaños mínimos y máximos para el login
        stage.setMinWidth(500);
        stage.setMinHeight(700);
        
        // 🔥 CRÍTICO: Asegurar que NO esté maximizado al inicio
        stage.setMaximized(false);
        
        // Establecer el tamaño calculado
        stage.setWidth(loginWidth);
        stage.setHeight(loginHeight);
        
        stage.centerOnScreen();
        stage.show();

        System.out.println("✅ ExpenseFlow iniciado correctamente (" + 
                          (int)loginWidth + "x" + (int)loginHeight + ")");
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }

    public static ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }
}