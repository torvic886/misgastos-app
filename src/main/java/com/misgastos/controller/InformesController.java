package com.misgastos.controller;

import com.misgastos.model.Categoria;
import com.misgastos.model.Gasto;
import com.misgastos.service.CategoriaService;
import com.misgastos.service.ClienteInformeService;
import com.misgastos.service.ClienteInformeService.Orden;
import com.misgastos.service.ClienteInformeService.Resultado;
import com.misgastos.service.ClienteInformeService.Tipo;
import com.misgastos.service.GastoService;
import com.misgastos.service.InformeService;
import com.misgastos.service.diseno.Diseno;
import com.misgastos.util.SelectorFecha;
import com.misgastos.service.ContadoresInformeService;
import com.misgastos.service.InformeGestionService;
import java.util.ArrayList;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import com.misgastos.util.ProductoNormalizador;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class InformesController {

	// NUEVO: texto que identifica el informe de parqueaderos y bonos
	private static final String CLAVE_CLIENTES = "Consolidado por cliente";
	private static final String CLAVE_CAT_PROD = "categoría y producto";
	private static final String CLAVE_CONTADORES = "Contadores por rango";
	private static final String CLAVE_CASINO = "Casino Victory";
	private static final String CLAVE_GESTION = "Mensual de Gestión";

	@FXML
	private ComboBox<String> cmbTipoInforme;
	@FXML
	private DatePicker dpFechaInicio;
	@FXML
	private DatePicker dpFechaFin;
	@FXML
	private ComboBox<Categoria> cmbCategoria;
	@FXML
	private TextField txtProducto;
	@FXML
	private Label lblCategoria;
	@FXML
	private Label lblProducto;

	// Sugerencias del campo Producto
	private ListView<String> listaSugerencias;
	private Popup popupProductos;
	private List<String> productosActivos = List.of();
	private boolean eligiendoProducto = false;
	@FXML
	private TextArea txtPreview;
	@FXML
	private Label lblTotalGastos;
	@FXML
	private Label lblMontoTotal;
	@FXML
	private Label lblCategorias;

	// NUEVO: filtros del informe de parqueaderos y bonos
	@FXML
	private VBox boxFiltrosClientes;
	@FXML
	private ComboBox<String> cmbTipoCliente;
	@FXML
	private ComboBox<String> cmbOrdenCliente;
	@FXML
	private TextField txtBuscarCliente;

	@Autowired
	private GastoService gastoService;
	@Autowired
	private CategoriaService categoriaService;
	@Autowired
	private InformeService informeService;
	@Autowired
	private ClienteInformeService clienteInformeService; // NUEVO
	@Autowired
	private ContadoresInformeService contadoresInformeService;
	@Autowired
	private InformeGestionService informeGestionService;

	private String informeActual = "";

	@FXML
	public void initialize() {
		configurarTiposInforme();
		configurarFechasPorDefecto();
		cargarCategorias();
		configurarSugerenciasProducto();
		configurarListeners();
		configurarFiltrosClientes(); // NUEVO
		mostrarMensajeInicial();

		log.debug("Módulo de informes cargado");
	}

	private void mostrarMensajeInicial() {
		txtPreview
				.setText("📊 Selecciona el tipo de informe, configura las fechas y presiona 'Generar Vista Previa'\n\n"
						+ "El informe aparecerá aquí antes de exportarlo a PDF o Excel.");
	}

	private void configurarTiposInforme() {
        cmbTipoInforme.getItems().addAll("📊 Informe General Mensual", "👥 Consolidado por cliente",
                        "📦 Gastos por categoría y producto", // NUEVO
                        "🎰 Contadores por rango", "🏆 Informe Casino Victory Armenia",
                        "📈 Informe Mensual de Gestión", "📂 Informe por Categoría",
                        "📦 Informe por Producto", "⚖️ Informe Comparativo", "🎯 Dashboard Ejecutivo",
                        "📋 Desglose por Subcategorías", "📅 Informe Anual");
        cmbTipoInforme.getSelectionModel().selectFirst();
}

	// NUEVO
	private void configurarFiltrosClientes() {
		cmbTipoCliente.getItems().addAll("Todos", "Solo bonos", "Solo parqueaderos");
		cmbTipoCliente.getSelectionModel().selectFirst();
		cmbOrdenCliente.getItems().addAll("Nombre", "Mayor total", "Cédula", "Apellido");
		cmbOrdenCliente.getSelectionModel().selectFirst();
	}

	private void configurarFechasPorDefecto() {
		LocalDate hoy = LocalDate.now();
		SelectorFecha.configurar(dpFechaInicio);
		SelectorFecha.configurar(dpFechaFin);
		dpFechaInicio.setValue(hoy.withDayOfMonth(1));
		dpFechaFin.setValue(hoy);
	}

	private void cargarCategorias() {
		List<Categoria> categorias = categoriaService.listarCategorias();
		cmbCategoria.getItems().clear();
		cmbCategoria.getItems().addAll(categorias);
		cmbCategoria.setPromptText("Todas las categorías");
	}

	private void configurarListeners() {
		// Mostrar/ocultar filtros según el tipo de informe
		cmbTipoInforme.setOnAction(e -> actualizarFiltrosVisibles());
		actualizarFiltrosVisibles(); // estado inicial
	}

	private void actualizarFiltrosVisibles() {
		String tipo = cmbTipoInforme.getValue();
		boolean verClientes = tipo != null && tipo.contains(CLAVE_CLIENTES);
		boolean verCategoria = !verClientes && tipo != null && tipo.contains("Categoría");
		boolean verProducto = !verClientes && !verCategoria && tipo != null && tipo.contains("Producto");

		mostrar(lblCategoria, verCategoria);
		mostrar(cmbCategoria, verCategoria);
		mostrar(lblProducto, verProducto);
		mostrar(txtProducto, verProducto);
		mostrar(boxFiltrosClientes, verClientes);

		if (!verProducto && popupProductos != null) {
			popupProductos.hide();
		}
	}

    /** Pregunta los ingresos o gastos extraordinarios del mes. Devuelve null si se cancela. */
    private List<InformeGestionService.Extra> pedirExtras(int anio, int mes) {
            List<InformeGestionService.Extra> sugeridos = informeGestionService.sugeridos(anio, mes);
            String[] efectos = {"Suma a la utilidad", "Resta a la utilidad", "Solo informativo"};

            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle("Informe Mensual de Gestión");
            dialogo.setHeaderText(null);
            dialogo.setGraphic(null);
            dialogo.setResizable(true);
            dialogo.getDialogPane().getStylesheets()
                            .add(getClass().getResource("/css/styles.css").toExternalForm());
            dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialogo.getDialogPane().setPrefWidth(760);

            // ---------- Encabezado ----------
            SVGPath iconoEnc = new SVGPath();
            iconoEnc.setContent("M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z");
            iconoEnc.getStyleClass().add("icon-white");
            Label circulo = new Label();
            circulo.setGraphic(iconoEnc);
            circulo.getStyleClass().add("icon-circle");

            Label titulo = new Label("Extraordinarios del mes");
            titulo.getStyleClass().add("panel-title");
            Label subtitulo = new Label("Revisa los valores. Déjalos en 0 si no hubo. Usa «Agregar otro» para más filas.");
            subtitulo.getStyleClass().add("panel-subtitle");
            VBox textos = new VBox(2, titulo, subtitulo);
            HBox encabezado = new HBox(10, circulo, textos);
            encabezado.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            encabezado.getStyleClass().add("panel-header");

            // ---------- Filas ----------
            GridPane rejilla = new GridPane();
            rejilla.setHgap(10);
            rejilla.setVgap(8);
            rejilla.setPadding(new Insets(4, 4, 4, 0));
            String[] titulos = {"Concepto", "Valor", "Efecto"};
            for (int c = 0; c < 3; c++) {
                    Label etiqueta = new Label(titulos[c]);
                    etiqueta.getStyleClass().add("field-caption");
                    rejilla.add(etiqueta, c, 0);
            }

            List<TextField> conceptos = new ArrayList<>();
            List<TextField> valores = new ArrayList<>();
            List<ComboBox<String>> efectosBox = new ArrayList<>();
            int[] fila = {1};

            java.util.function.BiConsumer<String, BigDecimal> agregarFila = (concepto, valorInicial) -> {
                    TextField txtConcepto = new TextField(concepto == null ? "" : concepto);
                    txtConcepto.setPromptText("Otro extraordinario");
                    txtConcepto.getStyleClass().add("search-field");
                    txtConcepto.setPrefWidth(290);
                    if (concepto != null) {
                            txtConcepto.setEditable(false); // las filas sugeridas tienen concepto fijo
                    }
                    TextField txtValor = new TextField();
                    txtValor.getStyleClass().add("search-field");
                    txtValor.setPrefWidth(170);
                    formatoPesos(txtValor);
                    txtValor.setText(valorInicial.toBigInteger().toString());
                    ComboBox<String> efecto = new ComboBox<>();
                    efecto.getItems().addAll(efectos);
                    efecto.getSelectionModel().selectFirst();
                    efecto.setPrefWidth(180);

                    rejilla.add(txtConcepto, 0, fila[0]);
                    rejilla.add(txtValor, 1, fila[0]);
                    rejilla.add(efecto, 2, fila[0]);

                    // Solo las filas libres se pueden quitar
                    if (concepto == null) {
                            SVGPath cruz = new SVGPath();
                            cruz.setContent("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z");
                            cruz.getStyleClass().add("toolbar-icon");
                            Button btnQuitar = new Button();
                            btnQuitar.setGraphic(cruz);
                            btnQuitar.getStyleClass().addAll("toolbar-button", "toolbar-neutral");
                            btnQuitar.setOnAction(ev -> {
                                    rejilla.getChildren().removeAll(txtConcepto, txtValor, efecto, btnQuitar);
                                    conceptos.remove(txtConcepto);
                                    valores.remove(txtValor);
                                    efectosBox.remove(efecto);
                                    log.debug("Informe de gestión: fila extraordinaria quitada del diálogo");
                            });
                            rejilla.add(btnQuitar, 3, fila[0]);
                    }

                    fila[0]++;
                    conceptos.add(txtConcepto);
                    valores.add(txtValor);
                    efectosBox.add(efecto);
            };

            for (InformeGestionService.Extra s : sugeridos) {
                    agregarFila.accept(s.concepto(), s.valor());
            }
            agregarFila.accept(null, BigDecimal.ZERO);
            agregarFila.accept(null, BigDecimal.ZERO);

            ScrollPane scroll = new ScrollPane(rejilla);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(250);
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

            // ---------- Botones (mismo estilo que Buscar y Editar Gastos) ----------
            SVGPath mas = new SVGPath();
            mas.setContent("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z");
            mas.getStyleClass().add("toolbar-icon");
            Button btnAgregar = new Button("Agregar otro");
            btnAgregar.setGraphic(mas);
            btnAgregar.getStyleClass().addAll("toolbar-button", "toolbar-neutral");
            btnAgregar.setOnAction(e -> agregarFila.accept(null, BigDecimal.ZERO));

            SVGPath iconoCancelar = new SVGPath();
            iconoCancelar.setContent("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z");
            iconoCancelar.getStyleClass().add("toolbar-icon");
            Button btnCancelarModal = new Button("Cancelar");
            btnCancelarModal.setGraphic(iconoCancelar);
            btnCancelarModal.getStyleClass().addAll("toolbar-button", "toolbar-neutral");
            btnCancelarModal.setOnAction(e -> dialogo.setResult(ButtonType.CANCEL));

            SVGPath iconoGenerar = new SVGPath();
            iconoGenerar.setContent("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z");
            iconoGenerar.getStyleClass().add("toolbar-icon");
            Button btnGenerarModal = new Button("Generar Excel");
            btnGenerarModal.setGraphic(iconoGenerar);
            btnGenerarModal.getStyleClass().addAll("toolbar-button", "toolbar-blue");
            btnGenerarModal.setDefaultButton(true);
            btnGenerarModal.setOnAction(e -> dialogo.setResult(ButtonType.OK));

            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox botones = new HBox(10, btnAgregar, espacio, btnCancelarModal, btnGenerarModal);
            botones.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            VBox tarjeta = new VBox(10, encabezado, scroll, botones);
            tarjeta.getStyleClass().add("table-card");
            VBox raiz = new VBox(tarjeta);
            raiz.getStyleClass().addAll("content", "gasto-screen");
            VBox.setVgrow(tarjeta, Priority.ALWAYS);
            dialogo.getDialogPane().setContent(raiz);

            // La barra de botones del diálogo se esconde: se sigue cerrando con la X de la ventana
            Button okOculto = (Button) dialogo.getDialogPane().lookupButton(ButtonType.OK);
            Button cancelOculto = (Button) dialogo.getDialogPane().lookupButton(ButtonType.CANCEL);
            okOculto.setVisible(false);
            okOculto.setManaged(false);
            cancelOculto.setVisible(false);
            cancelOculto.setManaged(false);
            Node barra = dialogo.getDialogPane().lookup(".button-bar");
            if (barra != null) {
                    barra.setVisible(false);
                    barra.setManaged(false);
            }

            Optional<ButtonType> respuesta = dialogo.showAndWait();
            if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
                    return null;
            }

            List<InformeGestionService.Extra> resultado = new ArrayList<>();
            for (int i = 0; i < conceptos.size(); i++) {
                    BigDecimal valor = leerValorEntero(valores.get(i).getText());
                    int pos = efectosBox.get(i).getSelectionModel().getSelectedIndex();
                    InformeGestionService.Tipo tipo = pos == 1 ? InformeGestionService.Tipo.RESTA
                                    : pos == 2 ? InformeGestionService.Tipo.INFORMATIVO
                                                    : InformeGestionService.Tipo.SUMA;
                    TextField txtConcepto = conceptos.get(i);
                    String concepto = txtConcepto.getText();
                    if (!txtConcepto.isEditable()) {
                            resultado.add(new InformeGestionService.Extra(concepto, valor, tipo));
                    } else if (concepto != null && !concepto.isBlank() && valor.signum() != 0) {
                            resultado.add(new InformeGestionService.Extra(concepto.trim(), valor, tipo));
                    }
            }
            log.debug("Informe de gestión: {} fila(s) extraordinarias confirmadas", resultado.size());
            return resultado;
    }

	/** Muestra lo que se escribe como pesos: $ 1.234.567 */
	private void formatoPesos(TextField campo) {
		campo.textProperty().addListener((obs, antes, ahora) -> {
			String digitos = ahora == null ? "" : ahora.replaceAll("[^0-9]", "");
			String nuevo = digitos.isEmpty() ? ""
					: "$ " + NumberFormat.getIntegerInstance(new Locale("es", "CO"))
							.format(new java.math.BigInteger(digitos));
			if (!nuevo.equals(ahora)) {
				campo.setText(nuevo);
				campo.positionCaret(nuevo.length());
			}
		});
	}

	/** Convierte "1.234.567" o "$ 1234567" en número entero; vacío = 0. */
	private BigDecimal leerValorEntero(String texto) {
		String limpio = texto == null ? "" : texto.replaceAll("[^0-9]", "");
		return limpio.isEmpty() ? BigDecimal.ZERO : new BigDecimal(limpio);
	}

	private void mostrar(Node nodo, boolean visible) {
		nodo.setVisible(visible);
		nodo.setManaged(visible);
	}

// ==================== Sugerencias del campo Producto ====================

	private void configurarSugerenciasProducto() {
		listaSugerencias = new ListView<>();
		listaSugerencias.setFocusTraversable(false);

		popupProductos = new Popup();
		popupProductos.setAutoHide(true);
		popupProductos.setConsumeAutoHidingEvents(false); // el clic en el campo sigue llegando al campo
		popupProductos.getContent().add(listaSugerencias);

		// Clic en un producto de la lista
		listaSugerencias.setOnMouseClicked(e -> {
			String elegido = listaSugerencias.getSelectionModel().getSelectedItem();
			if (elegido != null) {
				elegirProducto(elegido);
			}
		});

		// Al entrar al campo se cargan los productos; al salir se cierra la lista
		txtProducto.focusedProperty().addListener((o, antes, enfocado) -> {
			if (enfocado) {
				productosActivos = gastoService.listarProductos();
			} else {
				popupProductos.hide();
			}
		});

		// Clic en el campo: muestra todos los productos
		txtProducto.setOnMouseClicked(e -> mostrarSugerencias());

		// Al escribir: filtra
		txtProducto.textProperty().addListener((o, antes, ahora) -> {
			if (!eligiendoProducto && txtProducto.isFocused()) {
				mostrarSugerencias();
			}
		});

		// Teclado: abajo/arriba para moverse, Enter para elegir, Esc para cerrar
		txtProducto.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			boolean abierta = popupProductos.isShowing();
			if (e.getCode() == KeyCode.DOWN) {
				if (abierta) {
					moverSeleccion(1);
				} else {
					mostrarSugerencias();
				}
				e.consume();
			} else if (e.getCode() == KeyCode.UP && abierta) {
				moverSeleccion(-1);
				e.consume();
			} else if (e.getCode() == KeyCode.ENTER && abierta
					&& listaSugerencias.getSelectionModel().getSelectedItem() != null) {
				elegirProducto(listaSugerencias.getSelectionModel().getSelectedItem());
				e.consume();
			} else if (e.getCode() == KeyCode.ESCAPE && abierta) {
				popupProductos.hide();
				e.consume();
			}
		});
	}

	private void mostrarSugerencias() {
		String q = ProductoNormalizador.clave(txtProducto.getText());
		List<String> coincidencias = productosActivos.stream()
				.filter(p -> q.isEmpty() || ProductoNormalizador.clave(p).contains(q)).toList();
		if (coincidencias.isEmpty() || txtProducto.getScene() == null) {
			popupProductos.hide();
			return;
		}
		listaSugerencias.getItems().setAll(coincidencias);
		listaSugerencias.getSelectionModel().clearSelection();
		listaSugerencias.setPrefWidth(txtProducto.getWidth());
		listaSugerencias.setPrefHeight(Math.min(coincidencias.size(), 8) * 24 + 2);
		Bounds b = txtProducto.localToScreen(txtProducto.getBoundsInLocal());
		if (b != null) {
			popupProductos.show(txtProducto, b.getMinX(), b.getMaxY());
		}
	}

	private void moverSeleccion(int paso) {
		int total = listaSugerencias.getItems().size();
		if (total == 0) {
			return;
		}
		int actual = listaSugerencias.getSelectionModel().getSelectedIndex();
		int nuevo = Math.max(0, Math.min(total - 1, actual + paso));
		listaSugerencias.getSelectionModel().select(nuevo);
		listaSugerencias.scrollTo(nuevo);
	}

	private void elegirProducto(String producto) {
		eligiendoProducto = true;
		try {
			txtProducto.setText(producto);
			txtProducto.positionCaret(producto.length());
		} finally {
			eligiendoProducto = false;
		}
		popupProductos.hide();
		txtProducto.requestFocus();
	}

	@FXML
	public void handleGenerarVista() {
		if (!validarFechas())
			return;

		String tipoInforme = cmbTipoInforme.getValue();
		LocalDate inicio = dpFechaInicio.getValue();
		LocalDate fin = dpFechaFin.getValue();

		log.debug("Fecha inicio: {}", inicio);
		log.debug("Fecha fin: {}", fin);

		try {
			String informe = "";
			boolean esClientes = false;

			if (tipoInforme.contains(CLAVE_GESTION)) {
				esClientes = true; // pone sus propias cifras
				var r = informeGestionService.resumen(inicio.getYear(), inicio.getMonthValue());
				informe = informeGestionService.textoVistaPrevia(inicio.getYear(), inicio.getMonthValue(), r);
				lblTotalGastos.setText(String.valueOf(r.transacciones()));
				lblMontoTotal.setText(Diseno.moneda(r.gastos()));
				lblCategorias.setText(String.valueOf(r.categorias()));
			} else if (tipoInforme.contains(CLAVE_CONTADORES)) {
				esClientes = true; // ya pone sus propias cifras abajo
				var r = contadoresInformeService.generarContadores(inicio, fin);
				informe = contadoresInformeService.textoContadores(inicio, fin);
				lblTotalGastos.setText(String.valueOf(r.lecturas()));
				lblMontoTotal.setText(Diseno.moneda(r.total()));
				lblCategorias.setText(String.valueOf(r.grupos().size()));
			} else if (tipoInforme.contains(CLAVE_CASINO)) {
				esClientes = true;
				var r = contadoresInformeService.generarCasino(inicio, fin);
				informe = contadoresInformeService.textoCasino(inicio, fin);
				lblTotalGastos.setText(String.valueOf(r.filas().size()));
				lblMontoTotal.setText(Diseno.moneda(r.total()));
				lblCategorias.setText(String.valueOf(r.filas().size()));
			} else if (tipoInforme.contains(CLAVE_CAT_PROD)) {
				informe = informeService.generarInformeCategoriaProducto(inicio, fin);
			} else if (tipoInforme.contains(CLAVE_CLIENTES)) { // NUEVO
				esClientes = true;
				Resultado r = clienteInformeService.generar(inicio, fin, tipoCliente(), txtBuscarCliente.getText(),
						ordenCliente());
				informe = clienteInformeService.textoVistaPrevia(r, periodoCliente(inicio, fin), filtrosCliente());
				lblTotalGastos.setText(String.valueOf(r.filas().size()));
				lblMontoTotal.setText(Diseno.moneda(r.total()));
				lblCategorias.setText("1");
			} else if (tipoInforme.contains("General Mensual")) {
				informe = informeService.generarInformeGeneralMensual(inicio, fin);
				log.debug("Fecha inicio InformeGeneralMensual: {}", inicio);
				log.debug("Fecha fin InformeGeneralMensual: {}", fin);
			} else if (tipoInforme.contains("Categoría")) {
				Categoria categoria = cmbCategoria.getValue();
				if (categoria == null) {
					mostrarAlerta("Validación", "Por favor seleccione una categoría", Alert.AlertType.WARNING);
					return;
				}
				informe = informeService.generarInformePorCategoria(categoria.getNombre(), inicio, fin);
				log.debug("Fecha inicio InformePorCategoria: {}", inicio);
				log.debug("Fecha fin InformePorCategoria: {}", fin);
			} else if (tipoInforme.contains("Producto")) {
				String producto = txtProducto.getText();
				if (producto == null || producto.trim().isEmpty()) {
					mostrarAlerta("Validación", "Por favor ingrese el nombre del producto", Alert.AlertType.WARNING);
					return;
				}

				log.debug("Fecha inicio InformePorProducto: {}", inicio);
				log.debug("Fecha fin InformePorProducto: {}", fin);

				informe = informeService.generarInformePorProducto(producto.trim(), inicio, fin);
			} else if (tipoInforme.contains("Comparativo")) {

				log.debug("Fecha inicio InformeComparativo: {}", inicio);
				log.debug("Fecha fin InformeComparativo: {}", fin);

				informe = informeService.generarInformeComparativo(inicio, fin);
			} else if (tipoInforme.contains("Dashboard")) {

				log.debug("Fecha inicio DashboardEjecutivo: {}", inicio);
				log.debug("Fecha fin DashboardEjecutivo: {}", fin);

				informe = informeService.generarDashboardEjecutivo(inicio, fin);
			} else if (tipoInforme.contains("Subcategorías")) {

				log.debug("Fecha inicio InformeSubcategorias: {}", inicio);
				log.debug("Fecha fin InformeSubcategorias: {}", fin);

				informe = informeService.generarInformeSubcategorias(inicio, fin);
			} else if (tipoInforme.contains("Anual")) {
				int anio = inicio.getYear();

				log.debug("Fecha inicio InformeAnual: {}", inicio);
				log.debug("Fecha fin InformeAnual: {}", fin);

				informe = informeService.generarInformeAnual(anio);
			}

			informeActual = informe;
			txtPreview.setText(informe);

			// Actualizar estadísticas (el informe de parqueaderos y bonos ya puso las
			// suyas)
			if (!esClientes) {
				actualizarEstadisticas(inicio, fin);
			}

			log.info("Informe generado: {}", tipoInforme);

		} catch (Exception e) {
			log.error("No se pudo generar el informe '{}'", tipoInforme, e);
			mostrarAlerta("Error", "No se pudo generar el informe:\n" + e.getMessage(), Alert.AlertType.ERROR);
		}
	}

	private void actualizarEstadisticas(LocalDate inicio, LocalDate fin) {
		try {
			List<Gasto> gastos = gastoService.listarPorPeriodo(inicio, fin);

			// Filtrar si hay categoría seleccionada
			Categoria categoria = cmbCategoria.getValue();
			if (categoria != null && cmbCategoria.isVisible()) {
				Long catId = categoria.getId();
				gastos = gastos.stream().filter(g -> g.getCategoria().getId().equals(catId)).toList();
			}

			// Filtrar si hay producto ingresado
			String producto = txtProducto.getText();
			if (producto != null && !producto.trim().isEmpty() && txtProducto.isVisible()) {
				gastos = gastos.stream().filter(g -> InformeService.mismoProducto(g.getProducto(), producto)).toList();
			}

			// Actualizar labels
			lblTotalGastos.setText(String.valueOf(gastos.size()));

			BigDecimal total = gastos.stream().map(Gasto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
			NumberFormat formatoCOP = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
			formatoCOP.setMaximumFractionDigits(0);
			lblMontoTotal.setText(formatoCOP.format(total));

			long categorias = gastos.stream().map(g -> g.getCategoria().getId()).distinct().count();
			lblCategorias.setText(String.valueOf(categorias));

		} catch (Exception e) {
			log.error("Error al actualizar estadísticas", e);
		}
	}

	@FXML
	public void handleExportarPDF() {
		if (informeActual.isEmpty()) {
			mostrarAlerta("Advertencia", "Primero genere un informe", Alert.AlertType.WARNING);
			return;
		}

		if (!validarFechas())
			return;

		if (cmbTipoInforme.getValue().contains(CLAVE_GESTION)) {
			mostrarAlerta("Informe Mensual de Gestión",
					"Este informe solo se exporta a Excel (4 hojas). Usa el botón Exportar Excel.",
					Alert.AlertType.INFORMATION);
			return;
		}

		try {
			File archivo = seleccionarArchivo("Exportar Informe - PDF", "pdf");
			if (archivo != null) {
				String tipoInforme = cmbTipoInforme.getValue();
				LocalDate inicio = dpFechaInicio.getValue();
				LocalDate fin = dpFechaFin.getValue();

				if (tipoInforme.contains(CLAVE_CONTADORES)) {
					contadoresInformeService.exportarContadoresPDF(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_CASINO)) {
					contadoresInformeService.exportarCasinoPDF(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_CAT_PROD)) {
					informeService.exportarCategoriaProductoPDF(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_CLIENTES)) { // NUEVO
					exportarClientes(false, archivo, inicio, fin);
				} else if (tipoInforme.contains("General Mensual")) {
					informeService.exportarInformeGeneralPDF(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Categoría")) {
					Categoria categoria = cmbCategoria.getValue();
					if (categoria == null) {
						mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
						return;
					}
					informeService.exportarInformeCategoriaPDF(categoria.getNombre(), inicio, fin,
							archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Producto")) {
					String producto = txtProducto.getText();
					if (producto == null || producto.trim().isEmpty()) {
						mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
						return;
					}
					informeService.exportarInformeProductoPDF(producto.trim(), inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Dashboard")) {
					informeService.exportarDashboardPDF(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Comparativo")) {
					informeService.exportarComparativoPDF(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Subcategorías")) {
					informeService.exportarSubCategoriasPDF(inicio, fin, archivo.getAbsolutePath());
				} else {
					// Para otros informes, usar exportación genérica de texto
					int anio = inicio.getYear();
					informeService.exportarAnualPDF(anio, archivo.getAbsolutePath());
				}

				mostrarAlerta("Éxito", "Informe PDF exportado correctamente a:\n" + archivo.getAbsolutePath(),
						Alert.AlertType.INFORMATION);
			}
		} catch (Exception e) {
			log.error("No se pudo exportar el informe a PDF", e);
			mostrarAlerta("Error", "No se pudo exportar el PDF:\n" + e.getMessage(), Alert.AlertType.ERROR);
		}
	}

	@FXML
	public void handleExportarExcel() {
		if (informeActual.isEmpty()) {
			mostrarAlerta("Advertencia", "Primero genere un informe", Alert.AlertType.WARNING);
			return;
		}

		if (!validarFechas())
			return;

		List<InformeGestionService.Extra> extrasGestion = null;
		if (cmbTipoInforme.getValue().contains(CLAVE_GESTION)) {
			extrasGestion = pedirExtras(dpFechaInicio.getValue().getYear(), dpFechaInicio.getValue().getMonthValue());
			if (extrasGestion == null) {
				log.debug("Informe de gestión: exportación cancelada en el diálogo de extraordinarios");
				return;
			}
		}

		try {
			File archivo = seleccionarArchivo("Exportar Informe - Excel", "xlsx");
			if (archivo != null) {
				String tipoInforme = cmbTipoInforme.getValue();
				LocalDate inicio = dpFechaInicio.getValue();
				LocalDate fin = dpFechaFin.getValue();

				if (tipoInforme.contains(CLAVE_CONTADORES)) {
					contadoresInformeService.exportarContadoresExcel(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_CASINO)) {
					contadoresInformeService.exportarCasinoExcel(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_CAT_PROD)) {
					informeService.exportarCategoriaProductoExcel(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_GESTION)) {
					informeGestionService.exportarExcel(inicio.getYear(), inicio.getMonthValue(), extrasGestion,
							archivo.getAbsolutePath());
				} else if (tipoInforme.contains(CLAVE_CLIENTES)) { // NUEVO
					exportarClientes(true, archivo, inicio, fin);
				} else if (tipoInforme.contains("General Mensual")) {
					informeService.exportarInformeGeneralExcel(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Categoría")) {
					Categoria categoria = cmbCategoria.getValue();
					if (categoria == null) {
						mostrarAlerta("Validación", "Seleccione una categoría", Alert.AlertType.WARNING);
						return;
					}
					informeService.exportarInformeCategoriaExcel(categoria.getNombre(), inicio, fin,
							archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Producto")) {
					String producto = txtProducto.getText();
					if (producto == null || producto.trim().isEmpty()) {
						mostrarAlerta("Validación", "Ingrese un producto", Alert.AlertType.WARNING);
						return;
					}
					informeService.exportarInformeProductoExcel(producto.trim(), inicio, fin,
							archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Dashboard")) {
					informeService.exportarDashboardExcel(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Comparativo")) {
					informeService.exportarComparativoExcel(inicio, fin, archivo.getAbsolutePath());
				} else if (tipoInforme.contains("Subcategorías")) {
					informeService.exportarSubCategoriasExcel(inicio, fin, archivo.getAbsolutePath());
				} else {
					informeService.exportarAnualExcel(inicio.getYear(), archivo.getAbsolutePath());
				}

				mostrarAlerta("Éxito", "Informe Excel exportado correctamente a:\n" + archivo.getAbsolutePath(),
						Alert.AlertType.INFORMATION);
			}
		} catch (Exception e) {
			log.error("No se pudo exportar el informe a Excel", e);
			mostrarAlerta("Error", "No se pudo exportar el Excel:\n" + e.getMessage(), Alert.AlertType.ERROR);
		}
	}

	@FXML
	public void handleLimpiar() {
		configurarFechasPorDefecto();
		cmbCategoria.setValue(null);
		txtProducto.clear();
		cmbTipoCliente.getSelectionModel().selectFirst(); // NUEVO
		cmbOrdenCliente.getSelectionModel().selectFirst(); // NUEVO
		txtBuscarCliente.clear(); // NUEVO
		informeActual = "";

		txtPreview
				.setText("📊 Selecciona el tipo de informe, configura las fechas y presiona 'Generar Vista Previa'\n\n"
						+ "El informe aparecerá aquí antes de exportarlo a PDF o Excel.");

		lblTotalGastos.setText("0");
		lblMontoTotal.setText("$ 0");
		lblCategorias.setText("0");

		cmbTipoInforme.getSelectionModel().selectFirst();
	}

	// ==================== NUEVO: parqueaderos y bonos ====================

	private Tipo tipoCliente() {
		return switch (cmbTipoCliente.getSelectionModel().getSelectedIndex()) {
		case 1 -> Tipo.BONOS;
		case 2 -> Tipo.PARQUEADEROS;
		default -> Tipo.TODOS;
		};
	}

	private Orden ordenCliente() {
		return switch (cmbOrdenCliente.getSelectionModel().getSelectedIndex()) {
		case 1 -> Orden.MAYOR_TOTAL;
		case 2 -> Orden.CEDULA;
		case 3 -> Orden.APELLIDO;
		default -> Orden.NOMBRE;
		};
	}

	private String periodoCliente(LocalDate inicio, LocalDate fin) {
		return Diseno.fecha(inicio) + " al " + Diseno.fecha(fin);
	}

	private String filtrosCliente() {
		StringBuilder sb = new StringBuilder();
		if (cmbTipoCliente.getSelectionModel().getSelectedIndex() > 0) {
			sb.append(cmbTipoCliente.getValue());
		}
		String q = txtBuscarCliente.getText() == null ? "" : txtBuscarCliente.getText().trim();
		if (!q.isEmpty()) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append("búsqueda: ").append(q);
		}
		return sb.toString();
	}

	private void exportarClientes(boolean excel, File archivo, LocalDate inicio, LocalDate fin) throws Exception {
		Resultado r = clienteInformeService.generar(inicio, fin, tipoCliente(), txtBuscarCliente.getText(),
				ordenCliente());
		if (excel) {
			clienteInformeService.exportarExcel(r, periodoCliente(inicio, fin), filtrosCliente(),
					archivo.getAbsolutePath());
		} else {
			clienteInformeService.exportarPDF(r, periodoCliente(inicio, fin), filtrosCliente(),
					archivo.getAbsolutePath());
		}
		log.info("Informe parqueaderos y bonos exportado: {} clientes", r.filas().size());
	}

	// ==================== comunes ====================

	private boolean validarFechas() {
		LocalDate inicio = dpFechaInicio.getValue();
		LocalDate fin = dpFechaFin.getValue();

		if (inicio == null || fin == null) {
			mostrarAlerta("Validación", "Por favor seleccione ambas fechas", Alert.AlertType.WARNING);
			return false;
		}

		if (inicio.isAfter(fin)) {
			mostrarAlerta("Validación", "La fecha de inicio debe ser anterior a la fecha fin", Alert.AlertType.WARNING);
			return false;
		}

		return true;
	}

	private File seleccionarArchivo(String titulo, String extension) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(titulo);

		String timestamp = String.valueOf(System.currentTimeMillis());
		fileChooser.setInitialFileName("informe_" + timestamp + "." + extension);

		String descripcion = extension.equals("xlsx") ? "Excel Files" : "PDF Files";
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(descripcion, "*." + extension));

		return fileChooser.showSaveDialog(txtPreview.getScene().getWindow());
	}

	private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
		Alert alert = new Alert(tipo);
		alert.setTitle(titulo);
		alert.setHeaderText(null);
		alert.setContentText(mensaje);
		alert.showAndWait();
	}
}