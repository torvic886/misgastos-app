package com.misgastos.util;

import javafx.scene.Scene;

public final class SceneManager {

    private static final String CSS_PATH = "/css/styles.css";

    private SceneManager() {}

    public static void applyStyles(Scene scene) {
        var css = SceneManager.class.getResource(CSS_PATH);
        if (css == null) {
            throw new RuntimeException("❌ No se encontró " + CSS_PATH);
        }
        scene.getStylesheets().clear();
        scene.getStylesheets().add(css.toExternalForm());
    }
}
