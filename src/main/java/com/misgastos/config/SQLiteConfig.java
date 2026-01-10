package com.misgastos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class SQLiteConfig {
    
    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        return new DataSourceInitializer() {
            {
                setDataSource(dataSource);
                // Habilitar Foreign Keys en SQLite
                try (Connection conn = dataSource.getConnection()) {
                    conn.createStatement().execute("PRAGMA foreign_keys = ON;");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}