package com.misgastos.model;

import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "turno_horario_especial")
public class HorarioEspecial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    // Nombre del enum TipoDiaHorario: SABADO o DOMINGO_FESTIVO
    @Column(name = "tipo_dia", nullable = false, length = 20)
    private String tipoDia;

    @Column(name = "hora_inicio", nullable = false, length = 5)
    @Convert(converter = HoraCortaConverter.class)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false, length = 5)
    @Convert(converter = HoraCortaConverter.class)
    private LocalTime horaFin;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTurnoId() { return turnoId; }
    public void setTurnoId(Long turnoId) { this.turnoId = turnoId; }

    public String getTipoDia() { return tipoDia; }
    public void setTipoDia(String tipoDia) { this.tipoDia = tipoDia; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
}