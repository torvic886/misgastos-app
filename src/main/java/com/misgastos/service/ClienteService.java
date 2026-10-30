package com.misgastos.service;

import com.misgastos.model.Cliente;
import com.misgastos.repository.ClienteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta y edición manual de clientes. Usa las mismas reglas que la importación
 * desde Excel (ClienteImportService): cédula solo con números (hasta 20) y
 * nombre obligatorio (hasta 100 letras, espacios normalizados).
 *
 * Al editar, opcionalmente corrige el nombre en los gastos ya registrados del
 * cliente (cada gasto guarda su propia copia del nombre) y lo deja anotado en
 * la auditoría.
 *
 * Los mensajes de IllegalArgumentException están pensados para mostrarse
 * tal cual al usuario.
 */
@Service
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @PersistenceContext
    private EntityManager entityManager;

    /** Resultado de editar: el cliente guardado y cuántos gastos se corrigieron. */
    public record ResultadoEdicion(Cliente cliente, int gastosCorregidos) {
    }

    /** Crea un cliente nuevo. Falla si la cédula ya existe. */
    @Transactional
    public Cliente crear(String cedulaTexto, String nombreTexto) {
        String cedula = normalizarCedula(cedulaTexto);
        String nombre = normalizarNombre(nombreTexto);

        if (clienteRepository.existsById(cedula)) {
            log.warn("No se creó el cliente: la cédula ya existe");
            log.debug("Cédula duplicada al crear cliente: {}", cedula);
            throw new IllegalArgumentException("Ya existe un cliente con la cédula " + cedula
                + ". Búscalo en la lista y usa «Editar cliente».");
        }

        Cliente cliente = new Cliente();
        cliente.setCedula(cedula);
        cliente.setNombre(nombre);
        Cliente guardado = clienteRepository.save(cliente);
        log.info("Cliente creado manualmente");
        log.debug("Cliente creado: cédula {}", cedula);
        return guardado;
    }

    /**
     * Cambia el nombre de un cliente existente (la cédula, que es la llave, no se cambia).
     * Si corregirGastos es true, también pone ese nombre en todos los gastos de esa cédula,
     * incluidos los de la papelera, aunque el nombre del cliente ya estuviera bien.
     * Todo ocurre en una sola transacción: si algo falla, no se guarda nada.
     */
    @Transactional
    public ResultadoEdicion actualizarNombre(String cedula, String nombreTexto, boolean corregirGastos) {
        String nombre = normalizarNombre(nombreTexto);

        Cliente cliente = clienteRepository.findById(cedula).orElseThrow(() -> {
            log.warn("No se pudo editar el cliente: ya no existe");
            log.debug("Cliente inexistente al editar: cédula {}", cedula);
            return new IllegalArgumentException("El cliente ya no existe. Cierra esta ventana y actualiza la lista.");
        });

        String nombreAnterior = cliente.getNombre();
        boolean cambioNombre = !nombreAnterior.equals(nombre);
        if (cambioNombre) {
            cliente.setNombre(nombre);
            cliente = clienteRepository.save(cliente);
            log.info("Nombre de cliente actualizado");
            log.debug("Cliente actualizado: cédula {}", cedula);
        }

        int corregidos = 0;
        if (corregirGastos) {
            corregidos = corregirNombreEnGastos(cedula, nombre);
            if (corregidos > 0) {
                String detalle = cambioNombre
                    ? "Cliente " + cedula + ": nombre '" + nombreAnterior + "' → '" + nombre
                        + "'; corregido en " + corregidos + " gasto(s)"
                    : "Cliente " + cedula + ": nombre '" + nombre + "' corregido en " + corregidos
                        + " gasto(s) ya registrados";
                auditoriaService.registrarCambioGasto("CORREGIR_NOMBRE_CLIENTE", detalle);
            }
        }

        if (!cambioNombre && corregidos == 0) {
            log.debug("Edición de cliente sin cambios (cédula {})", cedula);
        }
        return new ResultadoEdicion(cliente, corregidos);
    }

    /**
     * Pone el nombre en los gastos de esa cédula cuyo nombre es distinto.
     * Va por SQL directo para incluir también los gastos de la papelera
     * (así, al restaurar uno no vuelve el nombre mal escrito).
     */
    private int corregirNombreEnGastos(String cedula, String nombre) {
        int filas = entityManager.createNativeQuery(
                "UPDATE gastos SET nombre_persona = :nombre WHERE TRIM(cedula) = :cedula "
                + "AND (nombre_persona IS NULL OR nombre_persona <> :nombre)")
            .setParameter("nombre", nombre)
            .setParameter("cedula", cedula)
            .executeUpdate();
        log.info("Nombre corregido en {} gasto(s) ya registrados", filas);
        log.debug("Corrección de nombre en gastos: cédula {}, {} fila(s)", cedula, filas);
        return filas;
    }

    /** Solo dígitos; quita espacios, puntos y comas como hace el importador. */
    private String normalizarCedula(String texto) {
        String cedula = texto == null ? "" : texto.trim().replaceAll("[\\s.,]", "");
        if (cedula.isEmpty()) {
            throw new IllegalArgumentException("Escribe la cédula.");
        }
        if (!cedula.matches("\\d+")) {
            throw new IllegalArgumentException("La cédula debe tener solo números.");
        }
        if (cedula.length() > 20) {
            throw new IllegalArgumentException("La cédula es demasiado larga (máximo 20 dígitos).");
        }
        return cedula;
    }

    private String normalizarNombre(String texto) {
        String nombre = texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("Escribe el nombre.");
        }
        if (nombre.length() > 100) {
            throw new IllegalArgumentException("El nombre no puede pasar de 100 letras.");
        }
        return nombre;
    }
}