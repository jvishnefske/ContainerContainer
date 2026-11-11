package com.jfrog.build.test.database.containers;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestDatabaseProperties {

    String jdbcUrl;
    String username;
    String password;
    String driverClassName;
}
