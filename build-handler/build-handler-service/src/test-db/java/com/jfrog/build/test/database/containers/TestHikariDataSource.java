package com.jfrog.build.test.database.containers;

import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;
import org.jfrog.storage.JFrogDataSource;

public class TestHikariDataSource extends HikariDataSource implements JFrogDataSource {

    public TestHikariDataSource(@NonNull TestDatabaseProperties properties) {
        this.setPoolName("HikariCP Main");
        this.setDriverClassName(properties.getDriverClassName());
        this.setPassword(properties.getPassword());
        this.setUsername(properties.getUsername());
        this.setJdbcUrl(properties.getJdbcUrl());
    }
}
