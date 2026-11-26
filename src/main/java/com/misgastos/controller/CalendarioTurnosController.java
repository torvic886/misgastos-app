package com.misgastos.controller;

import com.misgastos.model.Empleado;
import com.misgastos.model.Programacion;
import com.misgastos.model.Turno;
import com.misgastos.service.EmpleadoService;
import com.misgastos.service.ProgramacionService;
import com.misgastos.service.ProgramacionService.Asignacion;
import com.misgastos.service.ProgramacionService.Aviso;
import com.misgastos.service.ProgramacionService.DiaProgramado;
import com.misgastos.service.TurnoService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.misgastos.model.Novedad;
import com.misgastos.model.TipoNovedad;
import com.misgastos.service.NovedadService;
import java.util.stream.Collectors;

/** Calendario mensual de turnos (solo lectura). Es la pestaña "Calendario" de la pantalla Turnos. */
@Slf4j
@Component
public class CalendarioTurnosController {

    /** Etiqueta del medio turno (½), la misma que usa el modal de turnos. */
    private static final String ETIQUETA_MEDIO = "\u00BD";
    private static final Locale ES = new Locale("es", "CO");
    private static final String[] NOMBRES_DIA = {"LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"};

    // Colores {fondo, texto}
    private static final String[] AZUL = {"#DCE7FF", "#1D3FA8"};
    private static final String[] VERDE = {"#D5F1EB", "#0B6B5C"};
    private static final String[] NARANJA = {"#FFE6C7", "#8A4B08"};
    private static final String[] MORADO = {"#EDE9FE", "#5B21B6"};
    private static final String TEXTO_GRIS = "#5B6378";
    private static final String ROJO = "#BE123C";

    @FXML private Label lblMes;
    @FXML private HBox boxResumen;
    @FXML private HBox boxLeyenda;
    @FXML private GridPane gridMes;

    @Autowired
    private ProgramacionService programacionService;

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private TurnoService turnoService;
    
    @Autowired
    private ApplicationContext springContext;

    private YearMonth mes = YearMonth.now();

    private Map<LocalDate, List<Novedad>> novedadesPorDia = new HashMap<>();

    @Autowired
    private NovedadService novedadService;
    
    @FXML
    public void initialize() {
        log.debug("Inicializando calendario de turnos");
        gridMes.getColumnConstraints().clear();
        for (int i = 0; i < 7; i++) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(100.0 / 7);
            columna.setHgrow(Priority.ALWAYS);
            gridMes.getColumnConstraints().add(columna);
        }
        mes = YearMonth.now();
        cargar();
    }

    
    public void handleAnterior() {
        mes = mes.minusMonths(1);
        cargar();
    }

    
    public void handleSiguiente() {
        mes = mes.plusMonths(1);
        cargar();
    }

    
    public  void handleEsteMes() {
        mes = YearMonth.now();
        cargar();
    }

    
    public  void handleActualizar() {
        cargar();
    }
    
    /** Abre el ingreso de la semana: la de hoy si se ve el mes actual, o la primera del mes que se ve. */
    @FXML
    public  void handleIngresarSemana() {
        LocalDate base = mes.equals(YearMonth.now()) ? LocalDate.now() : mes.atDay(1);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-semana.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            ModalSemanaController controller = loader.getController();
            controller.setSemana(base);

            Stage stage = new Stage();
            stage.setTitle("Ingresar semana");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();
            cargar();
        } catch (Exception e) {
            log.error("No se pudo abrir el ingreso de la semana", e);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    e.getMessage() == null ? "Error inesperado" : e.getMessage());
            alert.setTitle("Error al abrir la ventana");
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    private void cargar() {
        try {
            List<Turno> turnos = turnoService.listarTodos();
            Map<Long, String[]> colores = coloresPorTurno(turnos);

            LocalDate primero = mes.atDay(1);
            LocalDate ultimo = mes.atEndOfMonth();
            // Se leen las semanas completas (de lunes a domingo) que toca el mes
            LocalDate desde = ProgramacionService.lunesDe(primero);
            LocalDate hasta = ProgramacionService.lunesDe(ultimo).plusDays(6);
            List<DiaProgramado> dias = programacionService.leerRango(desde, hasta);
            Map<LocalDate, List<String>> avisos = avisosPorDia(dias, primero, ultimo);

            cargarNovedades(primero, ultimo);
            
            lblMes.setText(nombreMes());
            dibujarResumen(dias, turnos, primero, ultimo);
            dibujarLeyenda(turnos, colores);
            dibujarCuadricula(dias, colores, avisos, primero, ultimo);
            log.debug("Calendario cargado: {}", mes);
        } catch (Exception e) {
            log.error("No se pudo cargar el calendario", e);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    e.getMessage() == null ? "Error inesperado" : e.getMessage());
            alert.setTitle("Error al cargar el calendario");
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    /** Turno completo 1 = azul, el 2 = verde, otros = morado, medio turno = naranja. */
    private Map<Long, String[]> coloresPorTurno(List<Turno> turnos) {
        Map<Long, String[]> colores = new HashMap<>();
        int indice = 0;
        for (Turno t : turnos) {
            if (ETIQUETA_MEDIO.equals(t.getEtiqueta())) {
                colores.put(t.getId(), NARANJA);
            } else {
                colores.put(t.getId(), indice == 0 ? AZUL : indice == 1 ? VERDE : MORADO);
                indice++;
            }
        }
        return colores;
    }

    /**
     * Avisos de cobertura por día, solo de las semanas que tienen algo programado (un mes sin
     * programar no se llena de avisos) y solo de los días del mes que se muestra.
     */
    private Map<LocalDate, List<String>> avisosPorDia(List<DiaProgramado> dias,
                                                      LocalDate primero, LocalDate ultimo) {
        Map<LocalDate, List<String>> resultado = new HashMap<>();
        for (int i = 0; i + 7 <= dias.size(); i += 7) {
            List<DiaProgramado> semana = dias.subList(i, i + 7);
            boolean hayDatos = semana.stream()
                    .anyMatch(d -> !d.trabajan().isEmpty() || !d.descansan().isEmpty());
            if (!hayDatos) {
                continue;
            }
            List<Asignacion> celdas = new ArrayList<>();
            for (DiaProgramado d : semana) {
                for (Programacion p : d.trabajan()) {
                    celdas.add(new Asignacion(d.fecha(), p.getEmpleado().getId(), p.getTurno().getId()));
                }
                for (Empleado e : d.descansan()) {
                    celdas.add(new Asignacion(d.fecha(), e.getId(), null));
                }
            }
            for (Aviso a : programacionService.avisos(semana.get(0).fecha(), celdas)) {
                if (!a.fecha().isBefore(primero) && !a.fecha().isAfter(ultimo)) {
                    resultado.computeIfAbsent(a.fecha(), k -> new ArrayList<>()).add(a.mensaje());
                }
            }
        }
        return resultado;
    }

    private void dibujarResumen(List<DiaProgramado> dias, List<Turno> turnos,
                                LocalDate primero, LocalDate ultimo) {
        LocalDate hoy = LocalDate.now();
        long vigentes = empleadoService.listarTodos().stream().filter(e -> e.vigenteEn(hoy)).count();
        long completos = turnos.stream()
                .filter(t -> t.isActivo() && !ETIQUETA_MEDIO.equals(t.getEtiqueta()))
                .count();
        long medios = dias.stream()
                .filter(d -> !d.fecha().isBefore(primero) && !d.fecha().isAfter(ultimo))
                .flatMap(d -> d.trabajan().stream())
                .filter(p -> ETIQUETA_MEDIO.equals(p.getTurno().getEtiqueta()))
                .count();
        boxResumen.getChildren().setAll(
                chip("Empleados", String.valueOf(vigentes)),
                chip("Turnos por día", String.valueOf(completos)),
                chip("Medios turnos al mes", String.valueOf(medios)));
    }

    private HBox chip(String titulo, String valor) {
        Label caption = new Label(titulo);
        caption.setStyle("-fx-text-fill: " + TEXTO_GRIS + "; -fx-font-size: 12px;");
        Label numero = new Label(valor);
        numero.setStyle("-fx-text-fill: #1F2437; -fx-font-size: 15px; -fx-font-weight: bold;");
        HBox caja = new HBox(8, caption, numero);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.setStyle("-fx-background-color: white; -fx-border-color: #E3E6F0; -fx-border-radius: 10; "
                + "-fx-background-radius: 10; -fx-padding: 4 10;");
        return caja;
    }

    private void dibujarLeyenda(List<Turno> turnos, Map<Long, String[]> colores) {
        boxLeyenda.getChildren().clear();
        for (Turno t : turnos) {
            if (t.isActivo()) {
                boxLeyenda.getChildren().add(itemLeyenda(colores.get(t.getId())[1], t.getNombre()));
            }
        }
        boxLeyenda.getChildren().add(itemLeyenda(ROJO, "Domingo o festivo"));

        Label negrita = new Label("Negrita");
        negrita.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label explicacion = new Label("= encargada");
        explicacion.setStyle("-fx-text-fill: " + TEXTO_GRIS + "; -fx-font-size: 12px;");
        HBox encargada = new HBox(3, negrita, explicacion);
        encargada.setAlignment(Pos.CENTER_LEFT);
        boxLeyenda.getChildren().add(encargada);
    }

    private HBox itemLeyenda(String color, String texto) {
        Circle punto = new Circle(5);
        punto.setStyle("-fx-fill: " + color + ";");
        Label etiqueta = new Label(texto);
        etiqueta.setStyle("-fx-text-fill: " + TEXTO_GRIS + "; -fx-font-size: 12px;");
        HBox item = new HBox(5, punto, etiqueta);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private void dibujarCuadricula(List<DiaProgramado> dias, Map<Long, String[]> colores,
                                   Map<LocalDate, List<String>> avisos,
                                   LocalDate primero, LocalDate ultimo) {
        gridMes.getChildren().clear();
        gridMes.getColumnConstraints().clear();
        gridMes.getRowConstraints().clear();
        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7);
            col.setHgrow(Priority.ALWAYS);
            gridMes.getColumnConstraints().add(col);
        }
        RowConstraints filaEncabezado = new RowConstraints();
        filaEncabezado.setMinHeight(18);
        filaEncabezado.setVgrow(Priority.NEVER);
        gridMes.getRowConstraints().add(filaEncabezado);
        for (int s = 0; s < dias.size() / 7; s++) {
            RowConstraints semana = new RowConstraints();
            semana.setMinHeight(78);
            semana.setPrefHeight(70);
            semana.setVgrow(Priority.ALWAYS);
            gridMes.getRowConstraints().add(semana);
        }
        for (int c = 0; c < 7; c++) {
            Label encabezado = new Label(NOMBRES_DIA[c]);
            encabezado.setMaxWidth(Double.MAX_VALUE);
            encabezado.setAlignment(Pos.CENTER);
            encabezado.setStyle("-fx-text-fill: " + TEXTO_GRIS + "; -fx-font-size: 12px; -fx-font-weight: bold;");
            gridMes.add(encabezado, c, 0);
        }
        for (int i = 0; i < dias.size(); i++) {
            DiaProgramado dia = dias.get(i);
            boolean delMes = !dia.fecha().isBefore(primero) && !dia.fecha().isAfter(ultimo);
            Node tarjeta = delMes
                    ? tarjetaDia(dia, colores, avisos.get(dia.fecha()))
                    : tarjetaVacia();
            gridMes.add(tarjeta, i % 7, 1 + i / 7);
        }
    }

    /** Casilla de un día que no es del mes (gris con borde punteado). */
    private Node tarjetaVacia() {
        VBox tarjeta = new VBox();
        tarjeta.setMinHeight(60);
        tarjeta.setStyle("-fx-background-color: #EFF0F6; -fx-border-color: #D9DCE8; "
                + "-fx-border-style: dashed; -fx-border-radius: 10; -fx-background-radius: 10;");
        return tarjeta;
    }

    private Node tarjetaDia(DiaProgramado dia, Map<Long, String[]> colores, List<String> avisosDelDia) {
        boolean especial = dia.esDomingoOFestivo();
        VBox tarjeta = new VBox(2);
        tarjeta.setMinHeight(60);
        tarjeta.setPadding(new Insets(4, 6, 4, 6));
        tarjeta.setStyle("-fx-background-color: " + (especial ? "#FFF6F8" : "white") + "; "
                + "-fx-border-color: " + (especial ? "#F5C2CE" : "#E3E6F0") + "; "
                + "-fx-border-radius: 10; -fx-background-radius: 10;");

        Label numero = new Label(String.valueOf(dia.fecha().getDayOfMonth()));
        numero.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: "
                + (especial ? ROJO : "#3A4056") + ";");
        HBox cabecera = new HBox(numero);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        if (avisosDelDia != null && !avisosDelDia.isEmpty()) {
            Region relleno = new Region();
            HBox.setHgrow(relleno, Priority.ALWAYS);
            Label aviso = new Label("⚠");
            aviso.setStyle("-fx-font-size: 13px; -fx-text-fill: #D97706;");
            cabecera.getChildren().addAll(relleno, aviso);
        }
        tarjeta.getChildren().add(cabecera);

        // Una franja por turno. El repositorio ya los entrega por turno y posición (encargada primero)
        Map<Long, List<Programacion>> porTurno = new LinkedHashMap<>();
        for (Programacion p : dia.trabajan()) {
            porTurno.computeIfAbsent(p.getTurno().getId(), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Long, List<Programacion>> e : porTurno.entrySet()) {
            String[] color = colores.getOrDefault(e.getKey(), AZUL);
            boolean esMedio = ETIQUETA_MEDIO.equals(e.getValue().get(0).getTurno().getEtiqueta());
            if (esMedio) {
                // El medio turno va en la línea del número para que la celda no crezca
                cabecera.setSpacing(6);
                cabecera.getChildren().add(1, franja(e.getValue(), color));
            } else {
                tarjeta.getChildren().add(franja(e.getValue(), color));
            }
        }

        if (!dia.descansan().isEmpty()) {
            String nombres = dia.descansan().stream()
                    .map(e -> primerNombre(e.getNombre()))
                    .collect(Collectors.joining(", "));
            Label descanso = new Label("Des. " + nombres);
            
            descanso.setStyle("-fx-text-fill: " + TEXTO_GRIS + "; -fx-font-size: 11px; -fx-padding: 0 0 0 8;");
            cabecera.getChildren().add(1, descanso);
        }
        

        // Novedades del día: máximo 2 líneas cortas y "+N más"
        List<Novedad> novedadesDia = novedadesPorDia.getOrDefault(dia.fecha(), List.of());
        int maxLineas = 2;
        for (int k = 0; k < novedadesDia.size() && k < maxLineas; k++) {
            Label l = new Label(textoCorto(novedadesDia.get(k)));
            l.setStyle("-fx-text-fill: #B45309; -fx-font-size: 10px; -fx-padding: 0 0 0 2;");
            tarjeta.getChildren().add(l);
        }
        if (novedadesDia.size() > maxLineas) {
            Label mas = new Label("+" + (novedadesDia.size() - maxLineas) + " más");
            mas.setStyle("-fx-text-fill: " + TEXTO_GRIS + "; -fx-font-size: 10px; -fx-padding: 0 0 0 2;");
            tarjeta.getChildren().add(mas);
        }

        List<String> lineas = new ArrayList<>();
        if (dia.esFestivo()) {
            lineas.add("Festivo: " + dia.nombreFestivo());
        }
        if (avisosDelDia != null) {
            for (String a : avisosDelDia) {
                lineas.add("⚠ " + a);
            }
        }
        for (Novedad n : novedadesDia) {
            lineas.add("• " + novedadService.texto(n));
        }
        if (!lineas.isEmpty()) {
            Tooltip.install(tarjeta, new Tooltip(String.join("\n", lineas)));
        }
        return tarjeta;
    }

    /** Franja de un turno: "Katherine · Marisella", con la encargada en negrita. */
    private Node franja(List<Programacion> grupo, String[] color) {
        TextFlow flujo = new TextFlow();
        flujo.setStyle("-fx-background-color: " + color[0] + "; -fx-background-radius: 6; -fx-padding: 1 5;");

        if (ETIQUETA_MEDIO.equals(grupo.get(0).getTurno().getEtiqueta())) {
            flujo.getChildren().add(texto(ETIQUETA_MEDIO + " ", color[1], false));
        }
        for (int i = 0; i < grupo.size(); i++) {
            if (i > 0) {
                flujo.getChildren().add(texto(" · ", color[1], false));
            }
            Empleado empleado = grupo.get(i).getEmpleado();
            flujo.getChildren().add(texto(primerNombre(empleado.getNombre()), color[1], empleado.isEsEncargada()));
        }
        return flujo;
    }

    private Text texto(String contenido, String color, boolean negrita) {
        Text t = new Text(contenido);
        t.setStyle("-fx-font-size: 11px; -fx-fill: " + color + ";" + (negrita ? " -fx-font-weight: bold;" : ""));
        return t;
    }

    /** Solo el primer nombre, con la primera letra en mayúscula: "KATHERINE LÓPEZ" pasa a "Katherine". */
    private String primerNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return "?";
        }
        String primero = limpio.split("\\s+")[0];
        return primero.substring(0, 1).toUpperCase(ES) + primero.substring(1).toLowerCase(ES);
    }

    private String nombreMes() {
        String nombre = mes.getMonth().getDisplayName(TextStyle.FULL, ES);
        return nombre.substring(0, 1).toUpperCase(ES) + nombre.substring(1) + " " + mes.getYear();
    }
    

    /** Llena el mapa día → novedades que cubren ese día, solo con los días del mes mostrado. */
    private void cargarNovedades(LocalDate primero, LocalDate ultimo) {
        novedadesPorDia = new HashMap<>();
        for (Novedad n : novedadService.listarEntre(primero, ultimo)) {
            LocalDate ini = n.getFechaDesde().isBefore(primero) ? primero : n.getFechaDesde();
            LocalDate fin = n.getFechaHasta().isAfter(ultimo) ? ultimo : n.getFechaHasta();
            ini.datesUntil(fin.plusDays(1)).forEach(
                    dia -> novedadesPorDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(n));
        }
        log.debug("Novedades del calendario cargadas: {} días con novedad", novedadesPorDia.size());
    }

    /** Línea corta para la tarjeta: "Inc. Cristian", o "Cambio Cristian ↔ Iván". */
    private String textoCorto(Novedad n) {
        TipoNovedad tipo = TipoNovedad.deNombre(n.getTipo());
        String abreviatura;
        if (tipo == null) {
            abreviatura = "Nov.";
        } else {
            switch (tipo) {
                case INCAPACIDAD -> abreviatura = "Inc.";
                case PERMISO -> abreviatura = "Perm.";
                case VACACIONES -> abreviatura = "Vac.";
                case FALTA -> abreviatura = "Falta";
                case CAMBIO_TURNO -> abreviatura = "Cambio";
                default -> abreviatura = "Nota";
            }
        }
        String texto = abreviatura + " " + primerNombre(n.getEmpleado().getNombre());
        if (n.getEmpleado2() != null) {
            texto += " ↔ " + primerNombre(n.getEmpleado2().getNombre());
        }
        return texto;
    }
}