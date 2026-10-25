package com.misgastos.diagnostico;

import java.util.List;

/** Una página de resultados (más recientes primero) y los totales de la consulta. */
public record PaginaLogs(List<EntradaLog> entradas, int total, int pagina, int totalPaginas, int archivos) {
}