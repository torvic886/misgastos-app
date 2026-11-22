package com.misgastos.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import lombok.extern.slf4j.Slf4j;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.misgastos.model.Billetero;
import com.misgastos.service.BilleteroService;
import com.misgastos.service.BilleteroService.Acumulado;
import com.misgastos.util.CopiarTabla;
import com.misgastos.util.SelectorFecha;
import com.misgastos.service.BilleterosImportService;
import java.io.File;
import javafx.stage.FileChooser;
import javafx.scene.control.SelectionMode;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class GestionBilleterosController {

	@FXML
	private TableView<Billetero> tablaBilleteros;
	@FXML
	private TableColumn<Billetero, String> colFecha;
	@FXML
	private TableColumn<Billetero, String> colBilletero;
	@FXML
	private TableColumn<Billetero, String> colPremios;
	@FXML
	private TableColumn<Billetero, String> colDiferencia;
	@FXML
	private TableColumn<Billetero, String> colAcumulada;
	@FXML
	private TableColumn<Billetero, String> colPromedio;
	@FXML
	private TableColumn<Billetero, Void> colAcciones;

	@FXML
	private TextField txtBuscar;
	@FXML
	private DatePicker dpFechaDesde;
	@FXML
	private DatePicker dpFechaHasta;

	@FXML
	private Label lblTotalRegistros;
	@FXML
	private Label lblTotalBilletero;
	@FXML
	private Label lblTotalPremios;
	@FXML
	private Label lblDiferenciaNeta;

	@FXML
	private Button btnAnterior;
	@FXML
	private Button btnSiguiente;
	@FXML
	private Label lblPagina;

	@Autowired
	private BilleteroService billeteroService;
	
    @Autowired
    private BilleterosImportService importService;

	@Autowired
	private ApplicationContext springContext;

	private ObservableList<Billetero> listaBilleteros;
	private ObservableList<Billetero> listaFiltrada;
	private Map<Long, Acumulado> acumulados = new java.util.HashMap<>();
	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private final NumberFormat currencyFormat;

	public GestionBilleterosController() {
		// Formato de moneda SIN decimales
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
		symbols.setGroupingSeparator(',');
		symbols.setDecimalSeparator('.');
		DecimalFormat format = new DecimalFormat("$ #,##0", symbols);
		this.currencyFormat = format;
	}

	@FXML
	public void initialize() {
		SelectorFecha.configurar(dpFechaDesde);
		SelectorFecha.configurar(dpFechaHasta);
		log.debug("Inicializando gestión de billeteros");
		configurarTabla();
		configurarBusqueda();
		cargarDatos();
		actualizarResumen();
		irAMes(YearMonth.now()); // la pantalla abre en el mes actual
	}

	private void configurarTabla() {
		// Columna Fecha
		colFecha.setCellValueFactory(cellData -> {
			LocalDate fecha = cellData.getValue().getFecha();
			return new SimpleStringProperty(fecha.format(dateFormatter));
		});

		// Columna Billetero
		colBilletero.setCellValueFactory(cellData -> {
			BigDecimal valor = cellData.getValue().getBilletero();
			return new SimpleStringProperty(formatearMoneda(valor));
		});
		colBilletero.setStyle("-fx-alignment: CENTER-RIGHT;");

		// Columna Premios
		colPremios.setCellValueFactory(cellData -> {
			BigDecimal valor = cellData.getValue().getPremios();
			return new SimpleStringProperty(formatearMoneda(valor));
		});
		colPremios.setStyle("-fx-alignment: CENTER-RIGHT;");

		// Columna Diferencia con color
		colDiferencia.setCellValueFactory(cellData -> {
			BigDecimal valor = cellData.getValue().getDiferencia();
			return new SimpleStringProperty(formatearMoneda(valor));
		});
		colDiferencia.setCellFactory(column -> new TableCell<Billetero, String>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					setText(item);

					Billetero billetero = getTableView().getItems().get(getIndex());
					BigDecimal diferencia = billetero.getDiferencia();

					if (diferencia.compareTo(BigDecimal.ZERO) > 0) {
						setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
					} else if (diferencia.compareTo(BigDecimal.ZERO) < 0) {
						setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: #ef4444; -fx-font-weight: bold;");
					} else {
						setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: #6b7280; -fx-font-weight: normal;");
					}
				}
			}
		});

		// Columnas calculadas: diferencia acumulada del mes y promedio por día
		colAcumulada.setCellValueFactory(cellData -> {
			Acumulado a = acumulados.get(cellData.getValue().getId());
			return new SimpleStringProperty(a == null ? "" : formatearMoneda(a.diferenciaAcumulada()));
		});
		colAcumulada.setSortable(false);
		colPromedio.setCellValueFactory(cellData -> {
			Acumulado a = acumulados.get(cellData.getValue().getId());
			return new SimpleStringProperty(a == null ? "" : formatearMoneda(a.promedioDia()));
		});
		colPromedio.setSortable(false);

		tablaBilleteros.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		CopiarTabla.habilitar(tablaBilleteros, fila -> true, colAcciones);

		// Columna Acciones

		colAcciones.setCellFactory(column -> new TableCell<Billetero, Void>() {
			private final Button btnEditar = new Button("✏ Editar");
			private final Button btnEliminar = new Button("🗑");
			private final HBox container = new HBox(5, btnEditar, btnEliminar);

			{
				btnEditar.getStyleClass().add("secondary-button");
				btnEditar.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");

				btnEliminar.getStyleClass().add("danger-button");
				btnEliminar.setStyle(
						"-fx-font-size: 11px; -fx-padding: 5 10; -fx-background-color: #ef4444; -fx-text-fill: white;");

				// container.setAlignment(Pos.CENTER);

				btnEditar.setOnAction(event -> {
					Billetero billetero = getTableView().getItems().get(getIndex());
					handleEditar(billetero);
				});

				btnEliminar.setOnAction(event -> {
					Billetero billetero = getTableView().getItems().get(getIndex());
					handleEliminar(billetero);
				});
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				setGraphic(empty ? null : container);
			}
		});
	}

	private void configurarBusqueda() {
		txtBuscar.textProperty().addListener((observable, oldValue, newValue) -> {
			filtrarDatos();
		});
	}

	private void cargarDatos() {
		try {
			List<Billetero> billeteros = billeteroService.listarTodos();
			acumulados = billeteroService.calcularAcumulados(billeteros);
			listaBilleteros = FXCollections.observableArrayList(billeteros);
			listaFiltrada = FXCollections.observableArrayList(billeteros);
			tablaBilleteros.setItems(listaFiltrada);

			lblTotalRegistros.setText("Total registros: " + listaFiltrada.size());

			log.debug("Billeteros cargados: {} registros", billeteros.size());
		} catch (Exception e) {
			mostrarError("Error al cargar datos", e.getMessage());
			log.error("No se pudieron cargar los billeteros", e);
		}
	}

	private void filtrarDatos() {
		String busqueda = txtBuscar.getText().toLowerCase().trim();

		if (busqueda.isEmpty()) {
			listaFiltrada.setAll(listaBilleteros);
		} else {
			listaFiltrada.clear();
			for (Billetero b : listaBilleteros) {
				String fecha = b.getFecha().format(dateFormatter);
				String billetero = formatearMoneda(b.getBilletero());
				String premios = formatearMoneda(b.getPremios());
				String diferencia = formatearMoneda(b.getDiferencia());

				if (fecha.contains(busqueda) || billetero.toLowerCase().contains(busqueda)
						|| premios.toLowerCase().contains(busqueda) || diferencia.toLowerCase().contains(busqueda)) {
					listaFiltrada.add(b);
				}
			}
		}

		lblTotalRegistros.setText("Total registros: " + listaFiltrada.size());
	}

	@FXML
	private void handleNuevo() {
		abrirModal(null);
	}

	@FXML
	private void handleRefresh() {
		cargarDatos();
		actualizarResumen();
		dpFechaDesde.setValue(null);
		dpFechaHasta.setValue(null);
		txtBuscar.clear();
	}

	@FXML
	private void handleBuscar() {
		LocalDate fechaInicio = dpFechaDesde.getValue();
		LocalDate fechaFin = dpFechaHasta.getValue();

		if (fechaInicio == null && fechaFin == null) {
			cargarDatos();
			actualizarResumen();
			return;
		}

		if (fechaInicio == null)
			fechaInicio = LocalDate.of(2000, 1, 1);
		if (fechaFin == null)
			fechaFin = LocalDate.now();

		if (fechaInicio.isAfter(fechaFin)) {
			mostrarError("Error de fechas", "La fecha inicial no puede ser mayor a la fecha final");
			return;
		}

		try {
			List<Billetero> resultados = billeteroService.buscarPorRangoFechas(fechaInicio, fechaFin);
			acumulados = billeteroService.calcularAcumulados(resultados);
			listaBilleteros.setAll(resultados);
			listaFiltrada.setAll(resultados);
			tablaBilleteros.setItems(listaFiltrada);

			lblTotalRegistros.setText("Total registros: " + listaFiltrada.size());
			actualizarResumenPorFechas(fechaInicio, fechaFin);

		} catch (Exception e) {
			mostrarError("Error al buscar", e.getMessage());
			log.error("No se pudo buscar billeteros por rango de fechas", e);
		}
	}

	@FXML
	private void handleLimpiarFiltros() {
		dpFechaDesde.setValue(null);
		dpFechaHasta.setValue(null);
		txtBuscar.clear();
		handleRefresh();
	}

	@FXML
	private void handleMesAnterior() {
		moverMes(-1);
	}

	@FXML
	private void handleEsteMes() {
		irAMes(YearMonth.now());
	}

	@FXML
	private void handleMesSiguiente() {
		moverMes(1);
	}

	/**
	 * Mueve el filtro al mes anterior o siguiente al de la fecha "Desde" (o al mes
	 * actual si está vacía).
	 */
	private void moverMes(int meses) {
		LocalDate base = dpFechaDesde.getValue();
		YearMonth actual = base != null ? YearMonth.from(base) : YearMonth.now();
		irAMes(actual.plusMonths(meses));
	}

	/** Pone Desde y Hasta en el mes completo y busca. */
	private void irAMes(YearMonth mes) {
		dpFechaDesde.setValue(mes.atDay(1));
		dpFechaHasta.setValue(mes.atEndOfMonth());
		log.debug("Billeteros: filtro por mes {}", mes);
		handleBuscar();
	}
	
    @FXML
    private void handleImportarExcel() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Elige el Excel de billeteros (un mes)");
        selector.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
        File archivo = selector.showOpenDialog(tablaBilleteros.getScene().getWindow());
        if (archivo == null) {
            return;
        }

        BilleterosImportService.VistaPrevia vista;
        try {
            vista = importService.analizar(archivo);
        } catch (Exception e) {
            log.error("No se pudo leer el Excel de billeteros", e);
            mostrarError("No se pudo leer el Excel",
                    "Revisa que el archivo no esté abierto en Excel y que tenga las columnas Fecha, Billeteros y Premios.\n\nMotivo: "
                            + e.getMessage());
            return;
        }

        String resumen = importService.textoResumen(vista);
        if (vista.nuevos().isEmpty()) {
            mostrarTexto(Alert.AlertType.INFORMATION, "Importar billeteros",
                    "No hay registros nuevos para guardar.", resumen, ButtonType.OK);
            return;
        }

        ButtonType si = new ButtonType("Sí, importar", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Optional<ButtonType> respuesta = mostrarTexto(Alert.AlertType.CONFIRMATION, "Importar billeteros",
                "Esto es lo que se va a importar.", resumen, si, no);
        if (respuesta.isEmpty() || respuesta.get() != si) {
            log.debug("Importar billeteros: cancelado por el usuario");
            return;
        }

        try {
            int guardados = importService.aplicar(vista);
            irAMes(YearMonth.from(vista.mes().atDay(1)));
            mostrarInfo("Importación terminada", guardados + " registro(s) creados en "
                    + BilleterosImportService.etiqueta(vista.mes()) + ".");
        } catch (Exception e) {
            log.error("No se pudo guardar la importación de billeteros", e);
            mostrarError("No se pudo importar", "Motivo: " + e.getMessage());
        }
    }

    private Optional<ButtonType> mostrarTexto(Alert.AlertType tipo, String titulo, String cabecera, String texto,
            ButtonType... botones) {
        Alert alert = new Alert(tipo, "", botones);
        alert.setTitle(titulo);
        alert.setHeaderText(cabecera);
        TextArea area = new TextArea(texto);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(16);
        area.setPrefColumnCount(70);
        area.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        alert.setResizable(true);
        return alert.showAndWait();
    }

	private void handleEditar(Billetero billetero) {
		abrirModal(billetero);
	}

	private void handleEliminar(Billetero billetero) {
		Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
		confirmacion.setTitle("Confirmar eliminación");
		confirmacion.setHeaderText("¿Está seguro de eliminar este registro?");
		confirmacion.setContentText("Fecha: " + billetero.getFecha().format(dateFormatter) + "\nBilletero: "
				+ formatearMoneda(billetero.getBilletero()));

		Optional<ButtonType> resultado = confirmacion.showAndWait();

		if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
			try {
				billeteroService.eliminar(billetero.getId());
				cargarDatos();
				actualizarResumen();

				mostrarInfo("Éxito", "Registro eliminado correctamente");
			} catch (Exception e) {
				mostrarError("Error al eliminar", e.getMessage());
				log.error("No se pudo eliminar el billetero {}", billetero.getId(), e);
			}
		}
	}

	private void abrirModal(Billetero billetero) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modal-billetero.fxml"));
			loader.setControllerFactory(springContext::getBean);

			Parent root = loader.load();

			ModalBilleteroController controller = loader.getController();
			controller.setBilletero(billetero);
			controller.setOnGuardarCallback(() -> {
				cargarDatos();
				actualizarResumen();
			});

			Stage stage = new Stage();
			stage.setTitle(billetero == null ? "Nuevo Billetero" : "Editar Billetero");
			stage.initModality(Modality.APPLICATION_MODAL);
			stage.setScene(new Scene(root));
			stage.setResizable(false);

			// Cargar CSS
			String css = getClass().getResource("/css/styles.css").toExternalForm();
			stage.getScene().getStylesheets().add(css);

			stage.showAndWait();

		} catch (Exception e) {
			mostrarError("Error al abrir modal", e.getMessage());
			log.error("No se pudo abrir el modal de billetero", e);
		}
	}

	private void actualizarResumen() {
		try {
			Map<String, BigDecimal> totales = billeteroService.obtenerTotales();

			lblTotalBilletero.setText(formatearMoneda(totales.get("billetero")));
			lblTotalPremios.setText(formatearMoneda(totales.get("premios")));

			BigDecimal diferencia = totales.get("diferencia");
			lblDiferenciaNeta.setText(formatearMoneda(diferencia));

			aplicarEstiloDiferencia(lblDiferenciaNeta, diferencia);

		} catch (Exception e) {
			log.error("No se pudo actualizar el resumen de billeteros", e);
		}
	}

	private void actualizarResumenPorFechas(LocalDate inicio, LocalDate fin) {
		try {
			Map<String, BigDecimal> totales = billeteroService.obtenerTotalesPorFechas(inicio, fin);

			lblTotalBilletero.setText(formatearMoneda(totales.get("billetero")));
			lblTotalPremios.setText(formatearMoneda(totales.get("premios")));

			BigDecimal diferencia = totales.get("diferencia");
			lblDiferenciaNeta.setText(formatearMoneda(diferencia));

			aplicarEstiloDiferencia(lblDiferenciaNeta, diferencia);

		} catch (Exception e) {
			log.error("No se pudo actualizar el resumen por fechas {} a {}", inicio, fin, e);
		}
	}

	private void aplicarEstiloDiferencia(Label label, BigDecimal valor) {
		if (valor.compareTo(BigDecimal.ZERO) > 0) {
			label.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 18px;");
		} else if (valor.compareTo(BigDecimal.ZERO) < 0) {
			label.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 18px;");
		} else {
			label.setStyle("-fx-text-fill: #6b7280; -fx-font-weight: normal; -fx-font-size: 18px;");
		}
	}

	private String formatearMoneda(BigDecimal valor) {
		if (valor == null)
			return "$ 0";
		return currencyFormat.format(valor.longValue());
	}

	private void mostrarError(String titulo, String mensaje) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle(titulo);
		alert.setHeaderText(null);
		alert.setContentText(mensaje);
		alert.showAndWait();
	}

	private void mostrarInfo(String titulo, String mensaje) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(titulo);
		alert.setHeaderText(null);
		alert.setContentText(mensaje);
		alert.showAndWait();
	}
}