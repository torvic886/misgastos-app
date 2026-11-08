package com.misgastos.repository;

import com.misgastos.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, String> {

    List<Cliente> findTop8ByCedulaStartingWithOrderByCedulaAsc(String prefijo);
}