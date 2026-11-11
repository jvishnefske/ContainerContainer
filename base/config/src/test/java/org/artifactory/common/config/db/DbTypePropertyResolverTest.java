package org.artifactory.common.config.db;

import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import static org.artifactory.common.config.db.DbTypePropertyResolver.BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY;
import static org.artifactory.common.config.db.DbTypePropertyResolver.BACKWARDS_COMPATIBLE_OVERRIDE_DB_PROPERTY;
import static org.artifactory.common.config.db.DbTypePropertyResolver.DB_TYPE_PROPERTY;
import static org.jfrog.storage.DbType.DERBY;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
class DbTypePropertyResolverTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(DB_TYPE_PROPERTY);
        System.clearProperty(BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY);
        System.clearProperty(BACKWARDS_COMPATIBLE_OVERRIDE_DB_PROPERTY);
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testResolveOrDefault_withSystemProperty(DbType dbType) {
        System.setProperty(DB_TYPE_PROPERTY, dbType.toString());
        DbType result = DbTypePropertyResolver.resolveOrDefault(DERBY);
        assertEquals(dbType, result);
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testResolveOrDefault_withEnvironmentVariable(DbType dbType, EnvironmentVariables environmentVariables) {
        environmentVariables.set(DB_TYPE_PROPERTY, dbType.toString());
        DbType result = DbTypePropertyResolver.resolveOrDefault(DERBY);
        assertEquals(dbType, result);
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testResolveOrDefault_withDefault(DbType dbType) {
        DbType result = DbTypePropertyResolver.resolveOrDefault(dbType);
        assertEquals(dbType, result);
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testGetBySystemProperty_withOldDbConfigName(DbType dbType) {
        System.setProperty(BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY, dbType.toString());
        DbType result = DbTypePropertyResolver.resolveOrDefault(DERBY);
        assertEquals(dbType, result);
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testGetByEnvironmentVariable_withOldOverrideDb(DbType dbType, EnvironmentVariables environmentVariables) {
        environmentVariables.set(BACKWARDS_COMPATIBLE_OVERRIDE_DB_PROPERTY, dbType.toString());
        DbType result = DbTypePropertyResolver.resolveOrDefault(DERBY);
        assertEquals(dbType, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgresql-docker", "postgresql-windows-1", "postgresql-docker-unitests"})
    void testResolveNameBySystemPropertyWithSuffix_withOldDbConfigName(String dbTypeWithSuffix) {
        System.setProperty(BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY, dbTypeWithSuffix);
        String result = DbTypePropertyResolver.resolveDbNameOrDefault("default");
        assertEquals(dbTypeWithSuffix, result);
    }

    @Test
    void testResolveByDbFilenameFallback_withOldDbConfigName() {
        System.setProperty(BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY, "derby-persist");
        DbType result = DbTypePropertyResolver.resolveOrDefault(POSTGRESQL);
        assertEquals(DERBY, result);
    }
}