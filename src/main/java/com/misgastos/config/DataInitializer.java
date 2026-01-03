package com.misgastos.config;

import com.misgastos.service.CategoriaService;
import com.misgastos.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private CategoriaService categoriaService;
    
    @Override
    public void run(String... args) {
        // ✅ Crear usuarios (con "SYSTEM" como creador)
        if (usuarioService.buscarPorUsername("admin").isEmpty()) {
            usuarioService.crearUsuario("admin", "admin123", "ADMINISTRADOR", "SYSTEM");
            System.out.println("✅ Usuario admin creado");
        }
        
        if (usuarioService.buscarPorUsername("usuario").isEmpty()) {
            usuarioService.crearUsuario("usuario", "usuario123", "USUARIO", "SYSTEM");
            System.out.println("✅ Usuario normal creado");
        }
        
        // Crear categorías y subcategorías
        if (categoriaService.listarCategorias().isEmpty()) {
            crearCategoriasIniciales();
        }
    }
    
    private void crearCategoriasIniciales() {

        // Ahorro
        var ahorro = categoriaService.crearCategoria("Ahorro", "💰", "#FF6B6B");
        categoriaService.crearSubcategoria("Ahorro", ahorro.getId());
        categoriaService.crearSubcategoria("Ahorro Anchetas", ahorro.getId());

        // Arrendamiento
        var arrendamiento = categoriaService.crearCategoria("Arrendamiento", "🏠", "#4ECDC4");
        categoriaService.crearSubcategoria("Canon De Arrendamiento", arrendamiento.getId());
        categoriaService.crearSubcategoria("Retencion Arriendo", arrendamiento.getId());

        // Beneficios Clientes
        var beneficios = categoriaService.crearCategoria("Beneficios Clientes", "🎁", "#95E1D3");
        categoriaService.crearSubcategoria("Bingo", beneficios.getId());
        categoriaService.crearSubcategoria("Bono Especial", beneficios.getId());
        categoriaService.crearSubcategoria("Bonos Parqueos", beneficios.getId());
        categoriaService.crearSubcategoria("Efectivo", beneficios.getId());
        categoriaService.crearSubcategoria("Parking", beneficios.getId());
        categoriaService.crearSubcategoria("Parqueo", beneficios.getId());

        // Cafeteria
        var cafeteria = categoriaService.crearCategoria("Cafeteria", "☕", "#F38181");
        categoriaService.crearSubcategoria("Almacenes Super Popular", cafeteria.getId());

        // Costos Financieros
        var costosFin = categoriaService.crearCategoria("Costos Financieros", "💳", "#FFA502");
        categoriaService.crearSubcategoria("Datafono", costosFin.getId());

        // Gastos Administrativos
        var gastosAdmin = categoriaService.crearCategoria("Gastos Administrativos", "🗂️", "#FF9F1C");
        categoriaService.crearSubcategoria("Administracion", gastosAdmin.getId());
        categoriaService.crearSubcategoria("Decoracion", gastosAdmin.getId());
        categoriaService.crearSubcategoria("Pago Poliza", gastosAdmin.getId());
        categoriaService.crearSubcategoria("Papeleria", gastosAdmin.getId());
        categoriaService.crearSubcategoria("Resma", gastosAdmin.getId());
        categoriaService.crearSubcategoria("Rollos Para Boletas", gastosAdmin.getId());

        // Honorarios Profesionales
        var honorarios = categoriaService.crearCategoria("Honorarios Profesionales", "👨‍💼", "#6A89CC");
        categoriaService.crearSubcategoria("Pago Victor", honorarios.getId());
        categoriaService.crearSubcategoria("Don Elkin", honorarios.getId());

        // Impuestos y Tasas
        var impuestos = categoriaService.crearCategoria("Impuestos y Tasas", "📑", "#38ADA9");
        categoriaService.crearSubcategoria("Coljuegos Mes Anterior", impuestos.getId());
        categoriaService.crearSubcategoria("Coljuegos", impuestos.getId());

        // Insumos Operacion
        var insumos = categoriaService.crearCategoria("Insumos Operacion", "🍽️", "#78E08F");
        categoriaService.crearSubcategoria("Azucar", insumos.getId());
        categoriaService.crearSubcategoria("Café", insumos.getId());
        categoriaService.crearSubcategoria("Caneca De Azucar", insumos.getId());
        categoriaService.crearSubcategoria("Carne Para Almuerzo Clientes", insumos.getId());
        categoriaService.crearSubcategoria("Cerveza", insumos.getId());
        categoriaService.crearSubcategoria("Comida Clientes", insumos.getId());
        categoriaService.crearSubcategoria("Compra De Termos", insumos.getId());
        categoriaService.crearSubcategoria("Desechables", insumos.getId());
        categoriaService.crearSubcategoria("Gaseosa", insumos.getId());
        categoriaService.crearSubcategoria("Leche", insumos.getId());
        categoriaService.crearSubcategoria("Natilla", insumos.getId());
        categoriaService.crearSubcategoria("Pulpas", insumos.getId());
        categoriaService.crearSubcategoria("Refrigerios Clientes", insumos.getId());
        categoriaService.crearSubcategoria("Soda Bretaña", insumos.getId());
        categoriaService.crearSubcategoria("Supermercado Ara", insumos.getId());
        categoriaService.crearSubcategoria("Supermercado Benis", insumos.getId());
        categoriaService.crearSubcategoria("Supermercado El Descuento", insumos.getId());
        categoriaService.crearSubcategoria("Supermercado Ventanilla", insumos.getId());
        categoriaService.crearSubcategoria("Varios", insumos.getId());

        // Inversion Equipos
        var inversion = categoriaService.crearCategoria("Inversion Equipos", "🛠️", "#60A3BC");
        categoriaService.crearSubcategoria("Pago De Maquinas Novomatic", inversion.getId());
        categoriaService.crearSubcategoria("Pago De Ruleta", inversion.getId());

        // Mantenimiento Activos
        var mantenimiento = categoriaService.crearCategoria("Mantenimiento Activos", "🔧", "#B71540");
        categoriaService.crearSubcategoria("Arreglos", mantenimiento.getId());
        categoriaService.crearSubcategoria("Mantenimiento Aires", mantenimiento.getId());
        categoriaService.crearSubcategoria("Mantenimiento Equipos", mantenimiento.getId());
        categoriaService.crearSubcategoria("Mantenimiento Maquinas", mantenimiento.getId());
        categoriaService.crearSubcategoria("Repuestos", mantenimiento.getId());

        // Otros Gastos
        var otros = categoriaService.crearCategoria("Otros Gastos", "📉", "#576574");
        categoriaService.crearSubcategoria("Rojo Mes Anterior", otros.getId());

        // Reparacion
        var reparacion = categoriaService.crearCategoria("Reparacion", "🧾", "#C44569");
        categoriaService.crearSubcategoria("9032 Gold Club Bill", reparacion.getId());

        // Retiros Propietario
        var retiros = categoriaService.crearCategoria("Retiros Propietario", "🏦", "#F6B93B");
        categoriaService.crearSubcategoria("Pagos Manuel Ramirez", retiros.getId());

        // Servicios Contratados
        var serviciosContr = categoriaService.crearCategoria("Servicios Contratados", "🛡️", "#1E3799");
        categoriaService.crearSubcategoria("Seguridad Atlas", serviciosContr.getId());

        // Servicios Generales
        var serviciosGen = categoriaService.crearCategoria("Servicios Generales", "🧹", "#079992");
        categoriaService.crearSubcategoria("Productos De Limpieza", serviciosGen.getId());
        categoriaService.crearSubcategoria("Materiales Para Mantenimiento", serviciosGen.getId());
        categoriaService.crearSubcategoria("Aseo De Alfombra", serviciosGen.getId());
        categoriaService.crearSubcategoria("Limpido", serviciosGen.getId());
        categoriaService.crearSubcategoria("Fumigacion", serviciosGen.getId());

        // Servicios Publicos
        var serviciosPub = categoriaService.crearCategoria("Servicios Publicos", "💡", "#0A3D62");
        categoriaService.crearSubcategoria("Agua", serviciosPub.getId());
        categoriaService.crearSubcategoria("Energia", serviciosPub.getId());
        categoriaService.crearSubcategoria("Gas", serviciosPub.getId());
        categoriaService.crearSubcategoria("Internet", serviciosPub.getId());
        categoriaService.crearSubcategoria("Otros", serviciosPub.getId());
        categoriaService.crearSubcategoria("Telefonia", serviciosPub.getId());

        // Talento Humano
        var talento = categoriaService.crearCategoria("Talento Humano", "👥", "#E58E26");
        categoriaService.crearSubcategoria("Bonificaciones", talento.getId());
        categoriaService.crearSubcategoria("Cumpleaños", talento.getId());
        categoriaService.crearSubcategoria("Dotacion", talento.getId());
        categoriaService.crearSubcategoria("Dotacion Empleados", talento.getId());
        categoriaService.crearSubcategoria("Examenes Medicos", talento.getId());
        categoriaService.crearSubcategoria("Pago Nomina", talento.getId());
        categoriaService.crearSubcategoria("Pago Salud", talento.getId());

        // Transporte Empleados
        var transpEmp = categoriaService.crearCategoria("Transporte Empleados", "🚌", "#38ADA9");
        categoriaService.crearSubcategoria("Transporte", transpEmp.getId());
        categoriaService.crearSubcategoria("Viaticos", transpEmp.getId());

        // Transporte Logistica
        var transpLog = categoriaService.crearCategoria("Transporte Logistica", "🚚", "#4A69BD");
        categoriaService.crearSubcategoria("Envios", transpLog.getId());

        System.out.println("✅ Categorías y subcategorías creadas correctamente");
    }
}