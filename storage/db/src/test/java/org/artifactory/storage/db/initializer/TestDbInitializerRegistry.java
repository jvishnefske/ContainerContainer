/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import org.artifactory.common.config.db.DbTypePropertyResolver;
import org.jfrog.storage.DbType;

import java.util.Map;

/**
 * @author haims
 */
public class TestDbInitializerRegistry {

    public static TestDBInitializer getDbInitializer(String name) {
        return getDbInitializer(name, false, null);
    }

    public static TestDBInitializer getDbInitializer(String name, boolean forceAnotherDbForLocking,
            Map<String, String> customDbProps) {
        try {
            DbType dbType = DbTypePropertyResolver.resolveOrDefault(DbType.DERBY);
            if (isOverrideDB(name, dbType)) {
                return new BaseTestDBInitializer(name);
            }
            TestDBInitializer dbInitializer = getDbInitializer(dbType);
            dbInitializer.setForceAnotherDbForLocking(forceAnotherDbForLocking);
            dbInitializer.setCustomDbProps(customDbProps);
            dbInitializer.setDbName(name);
            return dbInitializer;
        } catch (Exception e) {
            return new BaseTestDBInitializer(name);
        }
    }

    private static boolean isOverrideDB(String name, DbType dbType) {
        String canonicalDbName = DbTypePropertyResolver.stripOldFilenameSuffix(name);
        return !dbType.toString().equalsIgnoreCase(canonicalDbName);
    }

    private static TestDBInitializer getDbInitializer(DbType dbType) {
        return switch (dbType) {
            case DERBY -> new DerbyTestDBInitializer();
            case MARIADB, MYSQL -> new MySqlTestDBInitializer();
            case ORACLE -> new OracleTestDBInitializer();
            case MSSQL -> new MsSqlTestDBInitializer();
            case POSTGRESQL -> new PostgresqlTestDBInitializer();
        };
    }
}
