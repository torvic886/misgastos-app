package com.misgastos.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "maquinas")
public class Maquina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long codigo;

    // Denominación vigente: se propone al ingresar lecturas.
    // Cada lectura guardará su propia copia, así que cambiarla no altera el historial.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal denominacion;

    @Column(length = 100)
    private String nombre;

    @Column(length = 100)
    private String serial;

    @Column(length = 100)
    private String juego;

    // Contadores base de la máquina (valores crudos, sin multiplicar por la denominación)
    @Column(name = "contador_in")
    private Long contadorIn;

    @Column(name = "contador_out")
    private Long contadorOut;
    
    @Column(name = "contador_prem")
    private Long contadorPrem;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @Column(name = "motivo_baja", length = 200)
    private String motivoBaja;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCodigo() { return codigo; }
    public void setCodigo(Long codigo) { this.codigo = codigo; }

    public BigDecimal getDenominacion() { return denominacion; }
    public void setDenominacion(BigDecimal denominacion) { this.denominacion = denominacion; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }

    public String getJuego() { return juego; }
    public void setJuego(String juego) { this.juego = juego; }

    public Long getContadorIn() { return contadorIn; }
    public void setContadorIn(Long contadorIn) { this.contadorIn = contadorIn; }

    public Long getContadorOut() { return contadorOut; }
    public void setContadorOut(Long contadorOut) { this.contadorOut = contadorOut; }

    public Long getContadorPrem() { return contadorPrem; }
    public void setContadorPrem(Long contadorPrem) { this.contadorPrem = contadorPrem; }
    
    /** Diferencia In-Out de los contadores base; puede ser negativa. No se guarda, se calcula. */
    public Long getDiferenciaInOut() {
        if (contadorIn == null || contadorOut == null) return null;
        return contadorIn - contadorOut;
    }

    public LocalDate getFechaAlta() { return fechaAlta; }
    public void setFechaAlta(LocalDate fechaAlta) { this.fechaAlta = fechaAlta; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public LocalDate getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDate fechaBaja) { this.fechaBaja = fechaBaja; }

    public String getMotivoBaja() { return motivoBaja; }
    public void setMotivoBaja(String motivoBaja) { this.motivoBaja = motivoBaja; }

    @Override
    public String toString() {
        return String.valueOf(codigo);
    }
}