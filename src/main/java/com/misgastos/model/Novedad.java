package com.misgastos.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Incapacidades, permisos, vacaciones, faltas, cambios de turno y notas, con rango de fechas. */
@Entity
@Table(name = "novedad")
public class Novedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    // INCAPACIDAD, PERMISO, VACACIONES, FALTA, CAMBIO_TURNO, LLEGADA_TARDE, NOTA... se valida en el servicio
    @Column(nullable = false, length = 20)
    private String tipo;

    // Solo en cambios de turno entre dos personas
    @ManyToOne
    @JoinColumn(name = "empleado2_id")
    private Empleado empleado2;

    @Column(length = 300)
    private String motivo;

    @ManyToOne
    @JoinColumn(name = "registrado_por", nullable = false)
    private Usuario registradoPor;

    @Column(name = "registrada_en", nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime registradaEn = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getFechaDesde() { return fechaDesde; }
    public void setFechaDesde(LocalDate fechaDesde) { this.fechaDesde = fechaDesde; }

    public LocalDate getFechaHasta() { return fechaHasta; }
    public void setFechaHasta(LocalDate fechaHasta) { this.fechaHasta = fechaHasta; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Empleado getEmpleado2() { return empleado2; }
    public void setEmpleado2(Empleado empleado2) { this.empleado2 = empleado2; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario registradoPor) { this.registradoPor = registradoPor; }

    public LocalDateTime getRegistradaEn() { return registradaEn; }
    public void setRegistradaEn(LocalDateTime registradaEn) { this.registradaEn = registradaEn; }
}