package org.artifactory.storage.db.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.function.BiPredicate;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class SkippableScriptFileDbConverter extends ScriptFileDbConverter {

    @SafeVarargs
    public SkippableScriptFileDbConverter(String file, BiPredicate<Connection, DbType>... conditions) {
        super(file, conditions);
    }

    @SafeVarargs
    public SkippableScriptFileDbConverter(String file, String description,
            BiPredicate<Connection, DbType>... conditions) {
        super(file, description, conditions);
    }

    public SkippableScriptFileDbConverter(String file) {
        super(file);
    }

    public SkippableScriptFileDbConverter(ScriptFileDbConverter scriptFileDbConverter) {
        super(scriptFileDbConverter.file, scriptFileDbConverter.description(), scriptFileDbConverter.conditions());
    }

    @Override
    public boolean testConditions(ChangeLogContext context) {
        if (SkippableVersions.shouldSkipConverter(context.getDbVersion())) {
            return false;
        }
        return parentTestConditions(context);
    }

    public boolean parentTestConditions(ChangeLogContext context) {
        return super.testConditions(context);
    }
}
