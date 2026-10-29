package com.misgastos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}