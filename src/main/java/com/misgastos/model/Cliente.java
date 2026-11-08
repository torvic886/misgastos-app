package com.misgastos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Data;

/** Cliente para los bonos: la cédula es la llave, así no se repite. */
@Data
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @Column(length = 20)
    private String cedula;

    @Column(nullable = false, length = 100)
    private String nombre;
    
    @Column(length = 20)
    private String telefono;

    @Column(length = 20)
    private String etiqueta;   // Tipo 1, Tipo 2 o Tipo 3

    private LocalDate cumpleanos;
}