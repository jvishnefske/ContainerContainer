package org.artifactory.storage.db.version;

import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiPredicate;

/**
 * Oracle-specific converter conditions used by {@link ArtifactoryDBVersion}.
 *
 * @author Arko Jyoti De
 */
final class OracleDbVersionPredicates {

    private OracleDbVersionPredicates() {
    }

    /**
     * Existence guard for an Oracle function-based index, matched by name via {@code user_indexes}.
     * Column-name-based guards cannot detect these indexes: Oracle exposes system-generated {@code SYS_NCnnnnn$}
     * columns, so the column lookup never matches. That would let the converter re-run and fail with ORA-00955 on any
     * DB that already has the index (e.g. one created manually). Guarding by name via {@code user_indexes} avoids this.
     * Returns true (index absent, converter should run) on non-Oracle DBs. Catalog lookup failures propagate
     * (same as stock {@code indexNotExists}); pair with dbSpecific(ORACLE).
     */
    static BiPredicate<Connection, DbType> oracleIndexNotExistsByName(String indexName) {
        return (connection, dbType) -> {
            if (dbType != DbType.ORACLE) {
                return true;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM user_indexes WHERE index_name = ?")) {
                ps.setString(1, indexName.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return !rs.next();
                }
            } catch (SQLException e) {
                throw new DbConverterRuntimeException(
                        "Failed to check existence of Oracle index " + indexName, e);
            }
        };
    }
}
