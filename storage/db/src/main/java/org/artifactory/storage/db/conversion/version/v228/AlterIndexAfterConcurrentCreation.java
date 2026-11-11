package org.artifactory.storage.db.conversion.version.v228;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.conversion.DbConversionException;
import org.artifactory.storage.db.version.converter.ScriptFileDbConverter;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiPredicate;

/**
* @author Tomer Slutsky
*/
@Slf4j
public class AlterIndexAfterConcurrentCreation extends ScriptFileDbConverter {
    private final String indexToAlter;
    private final String indexToVerify;
    private final String VERIFICATION_QUERY =
            "SELECT count(1) FROM pg_class, pg_index WHERE pg_index.indisvalid = false AND pg_index.indexrelid = " +
                    "pg_class.oid and relname = ?";

    @SafeVarargs
    public AlterIndexAfterConcurrentCreation(String indexToAlter, String indexToVerify, String file,
            BiPredicate<Connection, DbType>... conditions) {
        super(file, file, conditions);
        this.indexToAlter = indexToAlter;
        this.indexToVerify = indexToVerify;
    }

    @VisibleForTesting
    boolean isPrerequisiteIndexInvalid(Connection conn) {
        ResultSet resultSet;
        try (PreparedStatement statement = conn.prepareStatement(VERIFICATION_QUERY)) {
            statement.setString(1, indexToVerify);
            resultSet = statement.executeQuery();
            return resultSet.next() && resultSet.getInt(1) > 0;
        } catch (SQLException e) {
            log.debug("An exception occurred in verification for converter: ALTER INDEX " + indexToAlter, e);
            log.error("An exception occurred in verification for converter: ALTER INDEX " + indexToAlter,
                    e.getMessage());

            return false;
        }
    }

    @Override
    protected void executeConverter(ChangeLogContext context) throws DbConverterExecutionException {
        try (Connection conn = context.getConnection()) {
            if (!ConstantValues.allowExternalConversionScripts.getBoolean() && isPrerequisiteIndexInvalid(conn)) {
                String msg = "Could not convert DB: ALTER INDEX " + indexToAlter + " since concurrent " +
                        "index failed to create";
                log.error("{}", msg);
                throw new DbConversionException(msg);
            }
            super.executeConverter(context);
        } catch (SQLException | DbConversionException e) {
            throw new DbConverterExecutionException(context.getDbVersion(), e);
        }
    }
}
