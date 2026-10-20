package com.misgastos;

import com.misgastos.util.SceneManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

@SpringBootApplication
public class MisGastosApplication extends Application {

    private static ConfigurableApplicationContext springContext;

    // ----- Ventana de carga -----
    private static Stage splash;
    private static ProgressBar barra;
    private static Label lblPorcentaje;
    private static Label lblEstado;
    private static Timeline animacion;
    private static double progreso = 0;
    private static volatile double meta = 0.15;
    private static volatile String mensaje = "Iniciando...";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        mostrarSplash();
        try {
            SpringApplication app = new SpringApplication(MisGastosApplication.class);
            app.addListeners((ApplicationListener<ApplicationEvent>) this::registrarEtapa);
            springContext = app.run();
        } catch (RuntimeException e) {
            cerrarSplash();
            throw e;
        }
    }

    // ==================== VENTANA DE CARGA ====================

    /** Cada etapa real del arranque sube el techo de la barra. */
    private void registrarEtapa(ApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent) {
            meta = 0.30;
            mensaje = "Preparando el entorno...";
        } else if (event instanceof ApplicationPreparedEvent) {
            meta = 0.80;
            mensaje = "Cargando base de datos y módulos...";
        } else if (event instanceof ContextRefreshedEvent) {
            meta = 0.92;
            mensaje = "Abriendo pantalla de inicio...";
        }
    }

    private void mostrarSplash() {
        Platform.runLater(() -> {
            Label titulo = new Label("ExpenseFlow");
            titulo.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;");

            lblEstado = new Label(mensaje);
            lblEstado.setStyle("-fx-font-size: 13px; -fx-text-fill: #cbd5e1;");

            barra = new ProgressBar(0);
            barra.setPrefWidth(280);

            lblPorcentaje = new Label("0 %");
            lblPorcentaje.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #93c5fd;");

            VBox caja = new VBox(12, titulo, lblEstado, barra, lblPorcentaje);
            caja.setAlignment(Pos.CENTER);
            caja.setPadding(new Insets(30));
            caja.setStyle("-fx-background-color: #1a2236; -fx-border-color: #3b82f6; -fx-border-width: 2;");

            splash = new Stage(StageStyle.UNDECORATED);
            splash.setScene(new Scene(caja, 380, 230));
            splash.setAlwaysOnTop(true);
            splash.centerOnScreen();
            splash.show();

            // Acerca la barra poco a poco al techo de la etapa actual
            animacion = new Timeline(new KeyFrame(Duration.millis(100), e -> {
                progreso += (meta - progreso) * 0.06;
                barra.setProgress(progreso);
                lblPorcentaje.setText(Math.round(progreso * 100) + " %");
                lblEstado.setText(mensaje);
            }));
            animacion.setCycleCount(Animation.INDEFINITE);
            animacion.play();
        });
    }

    private static void cerrarSplash() {
        Platform.runLater(() -> {
            if (splash == null) {
                return;
            }
            if (animacion != null) {
                animacion.stop();
            }
            barra.setProgress(1.0);
            lblPorcentaje.setText("100 %");
            PauseTransition pausa = new PauseTransition(Duration.millis(300));
            pausa.setOnFinished(e -> {
                splash.close();
                splash = null;
            });
            pausa.play();
        });
    }

    // ==================== VENTANA PRINCIPAL (LOGIN) ====================

    @Override
    public void start(Stage stage) throws Exception {
        try {
            iniciar(stage);
        } finally {
            cerrarSplash();
        }
    }

    private void iniciar(Stage stage) throws Exception {

        // 🎨 CONFIGURAR ICONO DE LA APLICACIÓN (ALTA CALIDAD)
        try {
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
                    256, 256, true, true
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

        // Login ocupa 75% del ancho y 90% del alto de la pantalla
        javafx.geometry.Rectangle2D screenBounds =
            javafx.stage.Screen.getPrimary().getVisualBounds();
        double loginWidth = screenBounds.getWidth() * 0.75;
        double loginHeight = screenBounds.getHeight() * 0.90;

        Scene scene = new Scene(root, loginWidth, loginHeight);

        SceneManager.applyStyles(scene);

        stage.setTitle("ExpenseFlow - Login");
        stage.setScene(scene);

        stage.setMinWidth(500);
        stage.setMinHeight(700);

        // Asegurar que NO esté maximizado al inicio
        stage.setMaximized(false);

        stage.setWidth(loginWidth);
        stage.setHeight(loginHeight);

        stage.centerOnScreen();
        stage.show();

        System.out.println("✅ ExpenseFlow iniciado correctamente (" +
                          (int)loginWidth + "x" + (int)loginHeight + ")");
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    public static ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }
}