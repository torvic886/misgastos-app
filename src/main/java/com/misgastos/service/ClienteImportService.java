package com.misgastos.service;

import com.misgastos.model.Cliente;
import com.misgastos.repository.ClienteRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Importa clientes (cédula en la columna A, nombre en la B) desde un Excel.
 * Paso 1: analizar() solo lee y clasifica, no guarda nada.
 * Paso 2: aplicar() guarda lo que analizar() encontró.
 */
@Service
public class ClienteImportService {

    @Autowired
    private ClienteRepository clienteRepository;

    public record VistaPrevia(List<Cliente> nuevos, List<Cliente> actualizados,
                              int sinCambios, List<String> problemas) {
        public int totalAplicables() {
            return nuevos.size() + actualizados.size();
        }
    }

    public VistaPrevia analizar(File archivo) throws Exception {
        Map<String, String> existentes = new HashMap<>();
        for (Cliente c : clienteRepository.findAll()) {
            existentes.put(c.getCedula(), c.getNombre());
        }

        List<Cliente> nuevos = new ArrayList<>();
        List<Cliente> actualizados = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        int sinCambios = 0;

        DataFormatter fmt = new DataFormatter();

        try (Workbook libro = WorkbookFactory.create(archivo, null, true)) {
            Sheet hoja = libro.getSheetAt(0);
            int primera = hoja.getFirstRowNum();

            // Busca las columnas por el título de la primera fila (Cédula / Nombre)
            int colCed = 0, colNom = 1;
            Row encabezado = hoja.getRow(primera);
            if (encabezado != null) {
                int ced = -1, nom = -1;
                for (Cell c : encabezado) {
                    String t = fmt.formatCellValue(c).trim().toLowerCase();
                    if (t.contains("ced")) ced = c.getColumnIndex();
                    else if (t.contains("nom")) nom = c.getColumnIndex();
                }
                if (ced >= 0 && nom >= 0 && ced != nom) {
                    colCed = ced;
                    colNom = nom;
                }
            }
            
            for (int i = primera; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;
                int numero = i + 1;

                Cell cCed = fila.getCell(colCed);
                Cell cNom = fila.getCell(colNom);
                String textoCed = cCed == null ? "" : fmt.formatCellValue(cCed).trim();
                String nombre = cNom == null ? ""
                        : fmt.formatCellValue(cNom).replaceAll("\\s+", " ").trim();

                if (textoCed.isEmpty() && nombre.isEmpty()) continue; // fila vacía

                // Encabezado en la primera fila
                if (i == primera && (textoCed.toLowerCase().contains("ced")
                        || nombre.toLowerCase().contains("nombre"))) {
                    continue;
                }

                String cedula = leerCedula(cCed, fmt);
                if (cedula == null) {
                    problemas.add("Fila " + numero + ": cédula vacía o inválida ('" + textoCed + "')");
                    continue;
                }
                if (cedula.length() > 20) {
                    problemas.add("Fila " + numero + ": cédula demasiado larga (" + cedula + ")");
                    continue;
                }
                if (nombre.isEmpty()) {
                    problemas.add("Fila " + numero + ": falta el nombre (cédula " + cedula + ")");
                    continue;
                }
                if (nombre.length() > 100) {
                    problemas.add("Fila " + numero + ": nombre de más de 100 letras (cédula " + cedula + ")");
                    continue;
                }
                if (!vistas.add(cedula)) {
                    problemas.add("Fila " + numero + ": cédula repetida en el archivo (" + cedula + ")");
                    continue;
                }

                Cliente c = new Cliente();
                c.setCedula(cedula);
                c.setNombre(nombre);

                String nombreActual = existentes.get(cedula);
                if (nombreActual == null) {
                    nuevos.add(c);
                } else if (nombreActual.equals(nombre)) {
                    sinCambios++;
                } else {
                    actualizados.add(c);
                }
            }
        }
        return new VistaPrevia(nuevos, actualizados, sinCambios, problemas);
    }

    @Transactional
    public void aplicar(VistaPrevia vista) {
        clienteRepository.saveAll(vista.nuevos());
        clienteRepository.saveAll(vista.actualizados());
    }

    /** Devuelve solo dígitos, o null si la cédula no se puede leer bien. */
    private String leerCedula(Cell celda, DataFormatter fmt) {
        if (celda == null) return null;
        String texto;
        if (celda.getCellType() == CellType.NUMERIC) {
            double d = celda.getNumericCellValue();
            // Evita notación científica: solo enteros positivos hasta 15 dígitos
            if (d != Math.rint(d) || d <= 0 || d >= 1e15) return null;
            texto = String.valueOf((long) d);
        } else {
            texto = fmt.formatCellValue(celda).trim().replaceAll("[\\s.,]", "");
        }
        return texto.matches("\\d+") ? texto : null;
    }
}