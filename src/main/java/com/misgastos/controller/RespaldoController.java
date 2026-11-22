package com.misgastos.controller;

import com.misgastos.MisGastosLauncher;
import com.misgastos.licencia.LicenciaService;
import com.misgastos.service.ContadoresImportService;
import com.misgastos.service.LecturasBorradoService;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import java.time.LocalDate;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Pantalla "Respaldo de datos": abrir la carpeta de datos, exportar una copia
 * de la base y importar una copia.
 *
 * Importar NO reemplaza la base mientras el programa está abierto (Windows no
 * deja reemplazar un archivo en uso). La copia se deja preparada y
 * MisGastosLauncher la aplica en el siguiente arranque, después de guardar un
 * respaldo de lo actual.
 */
@Slf4j
@Component
public class RespaldoController {

	private static final Locale CO = Locale.forLanguageTag("es-CO");
	private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter FECHA_PANTALLA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	@FXML
	private Label lblCarpeta;
	@FXML
	private Label lblBase;
	@FXML
	private Label lblRespaldos;
	@FXML
	private Label lblPendiente;
	@FXML
	private Label lblLicencia;
	@FXML
	private Button btnCancelarPendiente;

	@Autowired
	private DataSource dataSource;
	
	@Autowired
    private ContadoresImportService contadoresImportService;

	@Autowired
    private LecturasBorradoService lecturasBorradoService;
	
	private static final int DIAS_AVISO_LICENCIA = 30;
	private final LicenciaService licenciaService = new LicenciaService();

	// ==================== RUTAS ====================

	private Path carpetaDatos() {
		return Paths.get(System.getProperty("user.home"), "MisGastos");
	}

	private Path archivoBase() {
		return carpetaDatos().resolve("misgastos.db");
	}

	private Path carpetaRespaldos() {
		return carpetaDatos().resolve("respaldos");
	}

	private Path archivoPendiente() {
		return carpetaDatos().resolve(MisGastosLauncher.ARCHIVO_IMPORTACION);
	}

	// ==================== PANTALLA ====================

	@FXML
	public void initialize() {
		actualizarResumen();
		actualizarLicencia();
	}

	/**
	 * Muestra los días que quedan de licencia o de prueba; en naranja si faltan
	 * pocos.
	 */
	private void actualizarLicencia() {
		try {
			LicenciaService.Resultado r = licenciaService.evaluar();
			long dias = r.diasRestantes();
			String texto;
			String color = "#475569";
			if (r.estado() == LicenciaService.Estado.LICENCIADA) {
				texto = dias == 0 ? "Licencia: vence hoy" : "Licencia: vence en " + dias + " día(s)";
			} else if (r.estado() == LicenciaService.Estado.PRUEBA) {
				texto = "Periodo de prueba: te quedan " + dias + " día(s)";
			} else {
				texto = "Licencia vencida";
				color = "#c0392b";
			}
			if (r.estado() != LicenciaService.Estado.VENCIDA && dias <= DIAS_AVISO_LICENCIA) {
				color = "#c2410c";
			}
			lblLicencia.setText(texto);
			lblLicencia.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
		} catch (Exception e) {
			log.warn("No se pudo leer el estado de la licencia ({})", e.getClass().getSimpleName());
			lblLicencia.setText("");
		}
	}

	/** Permite pegar una clave nueva sin esperar a que la licencia venza. */
	@FXML
	public void handleActivarLicencia() {
		Dialog<ButtonType> dialogo = new Dialog<>();
		dialogo.setTitle("Activar / renovar licencia");
		dialogo.setHeaderText("Pega la clave que te entregó tu proveedor.");
		if (ventana() != null) {
			dialogo.initOwner(ventana());
		}

		TextField txtCodigo = new TextField(licenciaService.codigoEquipo());
		txtCodigo.setEditable(false);

		TextArea txtClave = new TextArea();
		txtClave.setPromptText("Pega aquí la clave de activación");
		txtClave.setWrapText(true);
		txtClave.setPrefRowCount(4);

		Label lblError = new Label();
		lblError.setStyle("-fx-text-fill: #c0392b;");

		VBox caja = new VBox(10, new Label("Código de tu equipo (envíalo para recibir la clave):"), txtCodigo,
				new Label("Clave de activación:"), txtClave, lblError);
		caja.setPadding(new Insets(20));
		caja.setPrefWidth(460);

		ButtonType btnActivar = new ButtonType("Activar", ButtonBar.ButtonData.OK_DONE);
		ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialogo.getDialogPane().getButtonTypes().addAll(btnActivar, btnCancelar);
		dialogo.getDialogPane().setContent(caja);
		aplicarEstilos(dialogo);

		Node okBtn = dialogo.getDialogPane().lookupButton(btnActivar);
		okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
			if (!licenciaService.activar(txtClave.getText())) {
				ev.consume(); // no cierra el diálogo
				lblError.setText("Clave inválida, vencida o de otro equipo.");
			}
		});

		Optional<ButtonType> res = dialogo.showAndWait();
		if (res.isPresent() && res.get() == btnActivar) {
			log.info("Licencia activada desde la pantalla de Respaldo");
			actualizarLicencia();
			alerta(Alert.AlertType.INFORMATION, "Licencia activada", lblLicencia.getText());
		}
	}

	private void actualizarResumen() {
		lblCarpeta.setText(carpetaDatos().toString());

		try {
			double mb = Files.size(archivoBase()) / 1048576.0;
			lblBase.setText(String.format(CO, "misgastos.db  (%.2f MB)", mb));
		} catch (Exception e) {
			log.warn("No se pudo leer el tamaño de la base de datos: {}", e.getMessage());
			lblBase.setText("No se encontró la base de datos");
		}

		List<Path> respaldos = listarRespaldos();
		if (respaldos.isEmpty()) {
			lblRespaldos.setText("Todavía no hay copias automáticas.");
		} else {
			Path ultimo = respaldos.get(respaldos.size() - 1);
			String cuando;
			try {
				cuando = LocalDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(ultimo).toMillis()),
						ZoneId.systemDefault()).format(FECHA_PANTALLA);
			} catch (Exception e) {
				log.debug("No se pudo leer la fecha de la última copia automática: {}", e.getMessage());
				cuando = ultimo.getFileName().toString();
			}
			lblRespaldos.setText(respaldos.size() + " copia(s) automática(s). La última: " + cuando);
		}

		boolean hayPendiente = Files.exists(archivoPendiente());
		lblPendiente.setVisible(hayPendiente);
		lblPendiente.setManaged(hayPendiente);
		btnCancelarPendiente.setVisible(hayPendiente);
		btnCancelarPendiente.setManaged(hayPendiente);
	}

	private List<Path> listarRespaldos() {
		Path carpeta = carpetaRespaldos();
		if (!Files.isDirectory(carpeta)) {
			return List.of();
		}
		try (Stream<Path> archivos = Files.list(carpeta)) {
			return archivos.filter(p -> {
				String n = p.getFileName().toString();
				return n.startsWith("misgastos_") && n.endsWith(".db");
			}).sorted().toList();
		} catch (Exception e) {
			log.warn("No se pudo listar la carpeta de respaldos", e);
			return List.of();
		}
	}

	// ==================== ABRIR CARPETAS ====================

	@FXML
	public void handleAbrirCarpetaDatos() {
		abrirCarpeta(carpetaDatos());
	}

	@FXML
	public void handleAbrirCarpetaRespaldos() {
		abrirCarpeta(carpetaRespaldos());
	}

	private void abrirCarpeta(Path carpeta) {
		try {
			Files.createDirectories(carpeta);
			String os = System.getProperty("os.name", "").toLowerCase();
			ProcessBuilder pb;
			if (os.contains("win")) {
				pb = new ProcessBuilder("explorer.exe", carpeta.toString());
			} else if (os.contains("mac")) {
				pb = new ProcessBuilder("open", carpeta.toString());
			} else {
				pb = new ProcessBuilder("xdg-open", carpeta.toString());
			}
			pb.start();
		} catch (Exception e) {
			log.error("No se pudo abrir la carpeta {}", carpeta, e);
			alerta(Alert.AlertType.ERROR, "No se pudo abrir la carpeta",
					"Puedes abrirla a mano desde el Explorador de archivos:\n" + carpeta);
		}
	}

	// ==================== EXPORTAR ====================

	@FXML
	public void handleExportar() {
		FileChooser selector = new FileChooser();
		selector.setTitle("Exportar copia de MisGastos");
		selector.setInitialFileName("misgastos_" + LocalDateTime.now().format(FECHA_ARCHIVO) + ".db");
		selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos MisGastos (*.db)", "*.db"));

		File elegido = selector.showSaveDialog(ventana());
		if (elegido == null) {
			return;
		}

		Path destino = elegido.toPath();
		if (!destino.getFileName().toString().toLowerCase().endsWith(".db")) {
			destino = destino.resolveSibling(destino.getFileName() + ".db");
		}

		try {
			if (Files.isSameFile(archivoBase(), destino)) {
				alerta(Alert.AlertType.WARNING, "Elige otro lugar",
						"No puedes exportar encima de la base que el programa está usando.");
				return;
			}
		} catch (Exception ignorada) {
			// el destino aún no existe: es lo normal
		}

		try {
			Files.deleteIfExists(destino);
			copiarBaseConsistente(destino);
			log.info("Copia exportada: {}", destino);
			alerta(Alert.AlertType.INFORMATION, "Copia exportada", "La copia quedó guardada en:\n" + destino
					+ "\n\nPuedes llevarla a otro computador o guardarla como respaldo.");
		} catch (Exception e) {
			log.error("No se pudo exportar la copia de la base de datos", e);
			alerta(Alert.AlertType.ERROR, "No se pudo exportar", "Motivo: " + e.getMessage());
		}
	}

	/**
	 * Copia la base mientras el programa está abierto. VACUUM INTO genera una copia
	 * completa y consistente aunque haya datos recién guardados. Si no estuviera
	 * disponible, se copia el archivo directamente.
	 */
	private void copiarBaseConsistente(Path destino) throws Exception {
		try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
			st.execute("VACUUM INTO '" + destino.toString().replace("'", "''") + "'");
		} catch (Exception e) {
			log.warn("VACUUM INTO no disponible, se copia el archivo: {}", e.getMessage());
			Files.copy(archivoBase(), destino, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// ==================== IMPORTAR ====================

	@FXML
	public void handleImportar() {
		FileChooser selector = new FileChooser();
		selector.setTitle("Elige la copia que quieres importar");
		selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos MisGastos (*.db)", "*.db"));
		if (Files.isDirectory(carpetaRespaldos())) {
			selector.setInitialDirectory(carpetaRespaldos().toFile());
		}

		File elegido = selector.showOpenDialog(ventana());
		if (elegido == null) {
			return;
		}
		Path origen = elegido.toPath();

		try {
			if (Files.isSameFile(origen, archivoBase())) {
				alerta(Alert.AlertType.WARNING, "Ese es el archivo actual",
						"Elige una copia guardada en otro lugar (por ejemplo, una de la carpeta de respaldos).");
				return;
			}
		} catch (Exception ignorada) {
			// seguimos
		}

		Path preparado = archivoPendiente();
		try {
			Files.copy(origen, preparado, StandardCopyOption.REPLACE_EXISTING);

			String problema = validarBase(preparado);
			if (problema != null) {
				Files.deleteIfExists(preparado);
				log.warn("Importación rechazada, el archivo no sirve: {}", problema);
				alerta(Alert.AlertType.ERROR, "Esa copia no sirve", problema);
				actualizarResumen();
				return;
			}

			Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION);
			confirmar.setTitle("Importar copia");
			confirmar.setHeaderText("Se reemplazarán TODOS los datos actuales");
			confirmar.setContentText("Los datos de este archivo sustituirán a los que tienes ahora.\n\n"
					+ "• El cambio se aplica la próxima vez que abras el programa.\n"
					+ "• Antes de aplicarlo se guarda una copia de lo actual en la carpeta de respaldos.\n"
					+ "• Si registras gastos antes de cerrar, quedarán en esa copia, no en los datos nuevos.\n\n"
					+ "¿Quieres continuar?");
			aplicarEstilos(confirmar);
			ButtonType si = new ButtonType("Sí, importar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
			ButtonType no = new ButtonType("Cancelar", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
			confirmar.getButtonTypes().setAll(si, no);

			Optional<ButtonType> respuesta = confirmar.showAndWait();
			if (respuesta.isEmpty() || respuesta.get() != si) {
				Files.deleteIfExists(preparado);
				log.info("Importación cancelada por el usuario");
				actualizarResumen();
				return;
			}

			actualizarResumen();
			log.info("Importación preparada desde {}. Se aplicará al reiniciar el programa", origen);
			alerta(Alert.AlertType.INFORMATION, "Copia lista para aplicar",
					"Cierra el programa y vuelve a abrirlo. Al iniciar se cargarán los datos de la copia importada.");
		} catch (Exception e) {
			log.error("No se pudo importar la copia de la base de datos", e);
			try {
				Files.deleteIfExists(preparado);
			} catch (Exception ignorada) {
				// nada más que hacer
			}
			alerta(Alert.AlertType.ERROR, "No se pudo importar", "Motivo: " + e.getMessage());
			actualizarResumen();
		}
	}

	@FXML
	public void handleCancelarPendiente() {
		try {
			Files.deleteIfExists(archivoPendiente());
			log.info("Importación pendiente cancelada");
		} catch (Exception e) {
			log.error("No se pudo cancelar la importación pendiente", e);
			alerta(Alert.AlertType.ERROR, "No se pudo cancelar", "Motivo: " + e.getMessage());
		}
		actualizarResumen();
	}

	/**
	 * Revisa que el archivo sea una base de MisGastos sana. Devuelve null si está
	 * bien, o el motivo en palabras sencillas si no.
	 */
	private String validarBase(Path archivo) {
		try (InputStream in = Files.newInputStream(archivo)) {
			byte[] cabecera = in.readNBytes(16);
			byte[] esperada = "SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
			if (!Arrays.equals(cabecera, esperada)) {
				return "El archivo no es una base de datos de MisGastos.";
			}
		} catch (Exception e) {
			log.warn("No se pudo leer el archivo de la copia a importar", e);
			return "No se pudo leer el archivo: " + e.getMessage();
		}

		String url = "jdbc:sqlite:" + archivo.toString().replace('\\', '/');
		try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
			try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
				if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
					return "El archivo está dañado, no se puede importar.";
				}
			}
			try (ResultSet rs = st
					.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'usuarios'")) {
				if (!rs.next() || rs.getInt(1) == 0) {
					return "El archivo es una base de datos, pero no es de MisGastos.";
				}
			}
		} catch (Exception e) {
			log.warn("No se pudo revisar la copia a importar", e);
			return "No se pudo revisar el archivo: " + e.getMessage();
		}
		return null;
	}
	
    // ==================== IMPORTAR CONTADORES DESDE EXCEL ====================

    private static final String[] MESES = { "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
                    "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre" };

    @FXML
    public void handleImportarContadores() {
            FileChooser selector = new FileChooser();
            selector.setTitle("Elige el Excel de contadores");
            selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
            File archivo = selector.showOpenDialog(ventana());
            if (archivo == null) {
                    return;
            }

            int[] anioMes = pedirMesYAnio();
            if (anioMes == null) {
                    return;
            }
            int anio = anioMes[0];
            int mes = anioMes[1];

            ContadoresImportService.VistaPrevia vista;
            try {
                    vista = contadoresImportService.analizar(archivo, anio, mes);
            } catch (Exception e) {
                    log.error("No se pudo leer el Excel de contadores", e);
                    alerta(Alert.AlertType.ERROR, "No se pudo leer el Excel", e.getMessage());
                    return;
            }

            String resumen = textoVista(vista, anio, mes);
            if (vista.porInsertar().isEmpty()) {
                    mostrarTexto(Alert.AlertType.INFORMATION, "Importar contadores", "No hay lecturas nuevas para guardar.", resumen);
                    return;
            }

            ButtonType si = new ButtonType("Sí, importar", ButtonBar.ButtonData.OK_DONE);
            ButtonType no = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            Optional<ButtonType> respuesta = mostrarTexto(Alert.AlertType.CONFIRMATION, "Importar contadores",
                            "Esto es lo que se va a importar. Antes se guarda una copia de tus datos actuales.", resumen, si, no);
            if (respuesta.isEmpty() || respuesta.get() != si) {
                    log.info("Importación de contadores cancelada por el usuario");
                    return;
            }

            Path respaldo = carpetaRespaldos()
                            .resolve("antes-importar-excel_" + LocalDateTime.now().format(FECHA_ARCHIVO) + ".db");
            try {
                    Files.createDirectories(carpetaRespaldos());
                    copiarBaseConsistente(respaldo);
                    log.debug("Copia previa a importar contadores: {}", respaldo);
            } catch (Exception e) {
                    log.error("No se pudo guardar la copia previa a importar contadores", e);
                    alerta(Alert.AlertType.ERROR, "No se importó nada",
                                    "No se pudo guardar la copia de seguridad previa. Motivo: " + e.getMessage());
                    return;
            }

            ContadoresImportService.Resultado r = contadoresImportService.aplicar(vista);

            StringBuilder fin = new StringBuilder();
            fin.append("Lecturas importadas: ").append(r.importadas());
            if (r.reinicios() > 0) {
                    fin.append(" (").append(r.reinicios()).append(" como reinicio de contador)");
            }
            if (r.denominacionesAjustadas() > 0) {
                    fin.append("\nLecturas con la denominación del Excel (distinta a la de la máquina): ")
                                    .append(r.denominacionesAjustadas());
            }
            agregarLista(fin, "Errores al guardar", r.errores());
            fin.append("\n\nCopia guardada antes de importar:\n").append(respaldo);
            log.info("Importación de contadores terminada desde la pantalla de Respaldo");
            mostrarTexto(r.errores().isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                            "Importar contadores", "Importación terminada. Revisa la pantalla Contadores.", fin.toString());
    }

    /** Devuelve {año, mes} o null si cancela. */
    private int[] pedirMesYAnio() {
            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle("Importar contadores");
            dialogo.setHeaderText("¿De qué mes son las lecturas del Excel?");
            if (ventana() != null) {
                    dialogo.initOwner(ventana());
            }

            ComboBox<String> cmbMes = new ComboBox<>(FXCollections.observableArrayList(MESES));
            LocalDate hoy = LocalDate.now();
            cmbMes.getSelectionModel().select(hoy.getMonthValue() - 1);
            TextField txtAnio = new TextField(String.valueOf(hoy.getYear()));
            Label lblAyuda = new Label("fecha_1 = último día del mes anterior, fecha_2 = día 16, fecha_3 = último día del mes.");
            lblAyuda.setWrapText(true);
            Label lblError = new Label();
            lblError.setStyle("-fx-text-fill: #c0392b;");

            VBox caja = new VBox(10, new Label("Mes:"), cmbMes, new Label("Año:"), txtAnio, lblAyuda, lblError);
            caja.setPadding(new Insets(20));
            caja.setPrefWidth(420);

            ButtonType btnOk = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogo.getDialogPane().getButtonTypes().addAll(btnOk, btnCancelar);
            dialogo.getDialogPane().setContent(caja);
            aplicarEstilos(dialogo);

            int[] resultado = new int[2];
            Node ok = dialogo.getDialogPane().lookupButton(btnOk);
            ok.addEventFilter(ActionEvent.ACTION, ev -> {
                    try {
                            int a = Integer.parseInt(txtAnio.getText().trim());
                            if (a < 2000 || a > 2100) {
                                    throw new NumberFormatException();
                            }
                            resultado[0] = a;
                            resultado[1] = cmbMes.getSelectionModel().getSelectedIndex() + 1;
                    } catch (NumberFormatException ex) {
                            ev.consume();
                            lblError.setText("Escribe un año válido (por ejemplo 2026).");
                    }
            });

            Optional<ButtonType> res = dialogo.showAndWait();
            return res.isPresent() && res.get() == btnOk ? resultado : null;
    }

    private String textoVista(ContadoresImportService.VistaPrevia v, int anio, int mes) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            StringBuilder sb = new StringBuilder();
            sb.append(MESES[mes - 1]).append(" ").append(anio).append(":\n");
            for (int p = 1; p <= 3; p++) {
                    sb.append("  Periodo ").append(p).append(" = ")
                                    .append(ContadoresImportService.fechaPeriodo(p, anio, mes).format(f)).append("\n");
            }
            sb.append("\nLecturas nuevas por guardar: ").append(v.porInsertar().size());
            if (v.reinicios() > 0) {
                    sb.append(" (").append(v.reinicios()).append(" como reinicio de contador)");
            }
            sb.append("\nYa existen y son iguales (se dejan): ").append(v.iguales());
            agregarLista(sb, "Máquinas del Excel que NO existen (no se importan)",
                            v.noEncontradas().stream().map(String::valueOf).toList());
            agregarLista(sb, "Contador bajo (revisar)", v.bajos());
            agregarLista(sb, "Ya existen con valores distintos (no se tocan)", v.conflictos());
            agregarLista(sb, "Otros problemas", v.problemas());
            return sb.toString();
    }

    private void agregarLista(StringBuilder sb, String titulo, List<String> items) {
            if (items.isEmpty()) {
                    return;
            }
            int max = 15;
            sb.append("\n\n").append(titulo).append(" (").append(items.size()).append("):");
            for (int i = 0; i < Math.min(max, items.size()); i++) {
                    sb.append("\n  • ").append(items.get(i));
            }
            if (items.size() > max) {
                    sb.append("\n  … y ").append(items.size() - max).append(" más");
            }
    }

    private Optional<ButtonType> mostrarTexto(Alert.AlertType tipo, String titulo, String encabezado, String texto,
                    ButtonType... botones) {
            Alert a = new Alert(tipo);
            a.setTitle(titulo);
            a.setHeaderText(encabezado);
            TextArea area = new TextArea(texto);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(16);
            area.setPrefColumnCount(60);
            a.getDialogPane().setContent(area);
            if (botones.length > 0) {
                    a.getButtonTypes().setAll(botones);
            }
            aplicarEstilos(a);
            return a.showAndWait();
    }
    
    // ==================== BORRAR LECTURAS DE CONTADORES ====================

    @FXML
    public void handleBorrarLecturas() {
            // 1) Qué borrar
            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle("Borrar lecturas de contadores");
            dialogo.setHeaderText("¿Qué lecturas quieres borrar? Las máquinas NO se borran.");
            if (ventana() != null) {
                    dialogo.initOwner(ventana());
            }

            ToggleGroup grupo = new ToggleGroup();
            RadioButton rbMes = new RadioButton("Un mes");
            RadioButton rbDesde = new RadioButton("Desde un mes hasta el final");
            RadioButton rbTodo = new RadioButton("Todo (menos la primera lectura de cada máquina)");
            rbMes.setToggleGroup(grupo);
            rbDesde.setToggleGroup(grupo);
            rbTodo.setToggleGroup(grupo);
            rbMes.setSelected(true);

            ComboBox<String> cmbMes = new ComboBox<>(FXCollections.observableArrayList(MESES));
            LocalDate hoy = LocalDate.now();
            cmbMes.getSelectionModel().select(hoy.getMonthValue() - 1);
            TextField txtAnio = new TextField(String.valueOf(hoy.getYear()));
            grupo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
                    boolean pideMes = ahora != rbTodo;
                    cmbMes.setDisable(!pideMes);
                    txtAnio.setDisable(!pideMes);
            });

            Label lblNota = new Label("La primera lectura de cada máquina (la fila gris del periodo 1) nunca se borra.");
            lblNota.setWrapText(true);
            Label lblError = new Label();
            lblError.setStyle("-fx-text-fill: #c0392b;");

            VBox caja = new VBox(10, rbMes, rbDesde, rbTodo, new Label("Mes:"), cmbMes, new Label("Año:"), txtAnio,
                            lblNota, lblError);
            caja.setPadding(new Insets(20));
            caja.setPrefWidth(440);

            ButtonType btnContinuar = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogo.getDialogPane().getButtonTypes().addAll(btnContinuar, btnCancelar);
            dialogo.getDialogPane().setContent(caja);
            aplicarEstilos(dialogo);

            LecturasBorradoService.Modo[] modo = new LecturasBorradoService.Modo[1];
            int[] anioMes = new int[2];
            Node okPaso1 = dialogo.getDialogPane().lookupButton(btnContinuar);
            okPaso1.addEventFilter(ActionEvent.ACTION, ev -> {
                    if (grupo.getSelectedToggle() == rbTodo) {
                            modo[0] = LecturasBorradoService.Modo.TODO;
                            return;
                    }
                    try {
                            int a = Integer.parseInt(txtAnio.getText().trim());
                            if (a < 2000 || a > 2100) {
                                    throw new NumberFormatException();
                            }
                            anioMes[0] = a;
                            anioMes[1] = cmbMes.getSelectionModel().getSelectedIndex() + 1;
                            modo[0] = grupo.getSelectedToggle() == rbMes ? LecturasBorradoService.Modo.MES
                                            : LecturasBorradoService.Modo.DESDE_MES;
                    } catch (NumberFormatException ex) {
                            ev.consume();
                            lblError.setText("Escribe un año válido (por ejemplo 2026).");
                    }
            });

            Optional<ButtonType> paso1 = dialogo.showAndWait();
            if (paso1.isEmpty() || paso1.get() != btnContinuar) {
                    return;
            }

            // 2) Cuántas se borrarían
            LecturasBorradoService.Seleccion seleccion = lecturasBorradoService.seleccionar(modo[0], anioMes[0],
                            anioMes[1]);
            if (seleccion.ids().isEmpty()) {
                    alerta(Alert.AlertType.INFORMATION, "Borrar lecturas", "No hay lecturas para borrar con ese criterio.");
                    return;
            }
            String que = switch (modo[0]) {
                    case MES -> MESES[anioMes[1] - 1] + " " + anioMes[0] + " (solo ese mes)";
                    case DESDE_MES -> "desde " + MESES[anioMes[1] - 1] + " " + anioMes[0] + " hasta el final";
                    case TODO -> "todas las fechas";
            };

            // 3) Confirmar escribiendo BORRAR
            Dialog<ButtonType> conf = new Dialog<>();
            conf.setTitle("Confirmar borrado");
            conf.setHeaderText("Se borrarán " + seleccion.ids().size() + " lectura(s) de " + seleccion.maquinas()
                            + " máquina(s)");
            if (ventana() != null) {
                    conf.initOwner(ventana());
            }
            Label detalle = new Label("Rango: " + que + ".\n\nLas máquinas y su primera lectura se conservan.\n"
                            + "Antes de borrar se guarda una copia de tus datos.\n"
                            + "Esto no se puede deshacer desde el programa.");
            detalle.setWrapText(true);
            TextField txtConfirmar = new TextField();
            txtConfirmar.setPromptText("Escribe BORRAR");
            Label lblErrorConf = new Label();
            lblErrorConf.setStyle("-fx-text-fill: #c0392b;");
            VBox cajaConf = new VBox(10, detalle, new Label("Para continuar escribe BORRAR:"), txtConfirmar, lblErrorConf);
            cajaConf.setPadding(new Insets(20));
            cajaConf.setPrefWidth(440);

            ButtonType btnBorrar = new ButtonType("Borrar", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnNo = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            conf.getDialogPane().getButtonTypes().addAll(btnBorrar, btnNo);
            conf.getDialogPane().setContent(cajaConf);
            aplicarEstilos(conf);
            Node okPaso3 = conf.getDialogPane().lookupButton(btnBorrar);
            okPaso3.addEventFilter(ActionEvent.ACTION, ev -> {
                    if (!"BORRAR".equals(txtConfirmar.getText().trim())) {
                            ev.consume();
                            lblErrorConf.setText("Escribe la palabra BORRAR en mayúsculas.");
                    }
            });

            Optional<ButtonType> paso3 = conf.showAndWait();
            if (paso3.isEmpty() || paso3.get() != btnBorrar) {
                    log.info("Borrado de lecturas cancelado por el usuario");
                    return;
            }

            // 4) Copia de seguridad y borrado
            Path respaldo = carpetaRespaldos()
                            .resolve("antes-borrar-lecturas_" + LocalDateTime.now().format(FECHA_ARCHIVO) + ".db");
            try {
                    Files.createDirectories(carpetaRespaldos());
                    copiarBaseConsistente(respaldo);
            } catch (Exception e) {
                    log.error("No se pudo guardar la copia previa a borrar lecturas", e);
                    alerta(Alert.AlertType.ERROR, "No se borró nada",
                                    "No se pudo guardar la copia de seguridad previa. Motivo: " + e.getMessage());
                    return;
            }

            try {
                    int borradas = lecturasBorradoService.borrar(seleccion, "Rango: " + que);
                    alerta(Alert.AlertType.INFORMATION, "Lecturas borradas", "Lecturas borradas: " + borradas
                                    + "\n\nCopia guardada antes de borrar:\n" + respaldo);
            } catch (Exception e) {
                    log.error("No se pudieron borrar las lecturas", e);
                    alerta(Alert.AlertType.ERROR, "No se pudo borrar", "Motivo: " + e.getMessage()
                                    + "\n\nLa copia previa quedó en:\n" + respaldo);
            }
    }

	// ==================== AUXILIARES ====================

	private Window ventana() {
		return lblCarpeta.getScene() == null ? null : lblCarpeta.getScene().getWindow();
	}

	private void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
		Alert a = new Alert(tipo);
		a.setTitle(titulo);
		a.setHeaderText(null);
		a.setContentText(mensaje);
		aplicarEstilos(a);
		a.showAndWait();
	}

	private void aplicarEstilos(javafx.scene.control.Dialog<?> a) {
		try {
			a.getDialogPane().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
		} catch (Exception ignorada) {
			// sin estilos, el diálogo funciona igual
			log.debug("No se pudieron aplicar los estilos al diálogo");
		}
	}
}
