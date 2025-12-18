package com.misgastos;

import com.misgastos.util.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/login.fxml")
        );
        loader.setControllerFactory(springContext::getBean);

        Parent root = loader.load();
        Scene scene = new Scene(root, 500, 700);

        SceneManager.applyStyles(scene);

        stage.setTitle("ExpenseFlow - Login");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();

        System.out.println("✅ ExpenseFlow iniciado correctamente");
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
