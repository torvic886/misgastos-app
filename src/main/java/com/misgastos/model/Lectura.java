package com.misgastos.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lecturas")
public class Lectura {

    public static final String INICIAL = "INICIAL";
    public static final String NORMAL = "NORMAL";
    public static final String COPIA = "COPIA";
    public static final String REINICIO = "REINICIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "maquina_id", nullable = false)
    private Maquina maquina;

    @Column(nullable = false)
    private LocalDate fecha;

    // 1, 2 o 3 (ciclo de tres periodos)
    @Column(nullable = false)
    private int periodo;

    // Contadores crudos, tal como se leen de la máquina
    @Column(name = "contador_in", nullable = false)
    private long contadorIn;

    @Column(name = "contador_out", nullable = false)
    private long contadorOut;

    @Column(name = "contador_prem", nullable = false)
    private long contadorPrem;

    // Denominación vigente al registrar la lectura (no cambia si luego se edita la máquina)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal denominacion;

    @Column(nullable = false, length = 15)
    private String tipo = NORMAL;

    @Column(nullable = false)
    private boolean anulada = false;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    @Column(name = "registrada_en", nullable = false)
    private LocalDateTime registradaEn = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Maquina getMaquina() { return maquina; }
    public void setMaquina(Maquina maquina) { this.maquina = maquina; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public int getPeriodo() { return periodo; }
    public void setPeriodo(int periodo) { this.periodo = periodo; }

    public long getContadorIn() { return contadorIn; }
    public void setContadorIn(long contadorIn) { this.contadorIn = contadorIn; }

    public long getContadorOut() { return contadorOut; }
    public void setContadorOut(long contadorOut) { this.contadorOut = contadorOut; }

    public long getContadorPrem() { return contadorPrem; }
    public void setContadorPrem(long contadorPrem) { this.contadorPrem = contadorPrem; }

    public BigDecimal getDenominacion() { return denominacion; }
    public void setDenominacion(BigDecimal denominacion) { this.denominacion = denominacion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public boolean isAnulada() { return anulada; }
    public void setAnulada(boolean anulada) { this.anulada = anulada; }

    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }

    public LocalDateTime getRegistradaEn() { return registradaEn; }
    public void setRegistradaEn(LocalDateTime registradaEn) { this.registradaEn = registradaEn; }
}
