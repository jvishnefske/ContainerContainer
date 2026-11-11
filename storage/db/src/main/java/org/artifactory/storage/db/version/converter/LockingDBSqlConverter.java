package org.artifactory.storage.db.version.converter;

import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.function.BiPredicate;

public class LockingDBSqlConverter extends ScriptFileDbConverter {

    @SafeVarargs
    public LockingDBSqlConverter(String fileName, BiPredicate<Connection, DbType>... conditions) {
        super(fileName, conditions);
    }
}
