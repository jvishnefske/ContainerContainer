package org.artifactory.storage.db.init.migration.dao;

import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.jfrog.commons.continues.util.Direction;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Uriah Levy
 */
@Repository
public class MigrationStatusDao {
    public static final String MIGRATION_STATUS_TABLE = "migration_status";
    private static final String ESCAPING_CHAR = "^";
    private static final String IDENTIFIER_COLUMN = "identifier";
    private static final String STARTED_COLUMN = "started";
    private static final String FINISHED_COLUMN = "finished";
    private static final String MIGRATION_INFO_BLOB_COLUMN = "migration_info_blob";
    private static final String INSERT_MIGRATION_SQL =
            "INSERT INTO " + MIGRATION_STATUS_TABLE + " (identifier, started," +
                    " finished, migration_info_blob) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_MIGRATION_BLOB_SQL =
            "UPDATE " + MIGRATION_STATUS_TABLE + " SET " + MIGRATION_INFO_BLOB_COLUMN + " = ? WHERE identifier = ?";
    private static final String UNFINISHED_SQL = "FROM " + MIGRATION_STATUS_TABLE
            + " WHERE " + FINISHED_COLUMN + "=0";

    private final JdbcHelper jdbcHelper;
    private final SqlDaoHelper<DbMigrationStatus> daoHelper;

    @Autowired
    public MigrationStatusDao(JdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                MIGRATION_STATUS_TABLE, this::getFromResultSet, 100);
    }

    private DbMigrationStatus getFromResultSet(ResultSet resultSet) throws SQLException {
        return new DbMigrationStatus(resultSet.getString(IDENTIFIER_COLUMN),
                resultSet.getLong(STARTED_COLUMN),
                resultSet.getLong(FINISHED_COLUMN),
                resultSet.getBytes(MIGRATION_INFO_BLOB_COLUMN));
    }

    public void insertMigration(DbMigrationStatus dbMigrationStatus) throws SQLException {
        byte[] migrationInfoBlob = dbMigrationStatus.getMigrationInfoBlob();
        jdbcHelper
                .executeUpdate(INSERT_MIGRATION_SQL, dbMigrationStatus.getIdentifier(),
                        dbMigrationStatus.getStarted(),
                        dbMigrationStatus.getFinished(), migrationInfoBlob != null ? migrationInfoBlob : new byte[] {});
    }

    public void updateFinishTimeById(String identifier, long finishTime) throws SQLException {
        daoHelper.updateByProperty(Pair.of(IDENTIFIER_COLUMN, identifier), Pair
                .of(FINISHED_COLUMN, finishTime));
    }

    public Collection<DbMigrationStatus> findAllUnfinishedIn(List<String> migrationsList) throws SQLException {
        return daoHelper.findAllByQuery("SELECT * " + UNFINISHED_SQL + " AND " + IDENTIFIER_COLUMN + " IN (#)",
                migrationsList);
    }

    public int countAllUnfinishedByPrefix(String idPrefix) throws SQLException {
        return daoHelper.executeCount("SELECT COUNT(*) " + UNFINISHED_SQL + " AND " + IDENTIFIER_COLUMN + " LIKE ?",
                idPrefix + "%");
    }

    public Collection<DbMigrationStatus> findAllUnfinishedByPrefix(String prefix) throws SQLException {
        return daoHelper.findAllByQuery(" SELECT * FROM " + MIGRATION_STATUS_TABLE +
                " WHERE " + FINISHED_COLUMN + "=0 AND " + IDENTIFIER_COLUMN + " LIKE '" + prefix + "%'");
    }

    public Collection<DbMigrationStatus> findFinishedStartedAfterDateByPrefix(String prefix, long afterEpochTime)
            throws SQLException {
        return daoHelper.findAllByQuery(" SELECT * FROM " + MIGRATION_STATUS_TABLE +
                " WHERE " + FINISHED_COLUMN + ">=" + afterEpochTime +
                " AND " + IDENTIFIER_COLUMN + " LIKE '" + prefix + "%'");
    }

    public Collection<String> findStartedBeforeDateContainsPrefix(String prefix, long afterEpochTime)
            throws SQLException {
        return daoHelper.findAllByQuery(" SELECT " + IDENTIFIER_COLUMN + "  FROM " + MIGRATION_STATUS_TABLE +
                        " WHERE " + STARTED_COLUMN + " < ? AND " + IDENTIFIER_COLUMN + " LIKE ?",
                value -> value.getString(IDENTIFIER_COLUMN),
                afterEpochTime, "%" + prefix + "%");
    }

    public Optional<DbMigrationStatus> findMigrationById(String identifier) throws SQLException {
        return daoHelper.findFirstByProperty(IDENTIFIER_COLUMN, identifier);
    }

    public Collection<DbMigrationStatus> findMigrationsByIdPrefixBetweenDates(String identifierPrefix,
            String separator, Direction direction, @Nullable Long from, @Nullable Long to) throws SQLException {
        String andClause = StringUtil.EMPTY_STRING;
        List<Object> params = new ArrayList<>();
        if (StringUtils.isNotEmpty(separator)) {
            identifierPrefix += ESCAPING_CHAR + separator;
            andClause += " ESCAPE '" + ESCAPING_CHAR + "' ";
        }
        params.add(identifierPrefix + "%");
        if (Objects.nonNull(from)) {
            andClause += " AND " + STARTED_COLUMN + " >= ?";
            params.add(from);
        }
        if (Objects.nonNull(to)) {
            andClause += " AND " + STARTED_COLUMN + " <= ?";
            params.add(to);
        }
        return daoHelper.findAllByQuery(" SELECT * FROM " + MIGRATION_STATUS_TABLE +
                " WHERE " + IDENTIFIER_COLUMN + " LIKE ?" +
                andClause +
                " ORDER BY " + STARTED_COLUMN + " " + direction.name(), params.toArray(new Object[0]));
    }

    public int delete(String identifier) throws SQLException {
        return daoHelper.deleteByProperty(IDENTIFIER_COLUMN, identifier);
    }

    public void updateMigrationInfoBlob(DbMigrationStatus dbMigrationStatus) throws SQLException {
        byte[] migrationInfoBlob = dbMigrationStatus.getMigrationInfoBlob();
        if (Objects.isNull(migrationInfoBlob)) {
            throw new IllegalArgumentException("Migration info blob cannot be null");
        }
        jdbcHelper.executeUpdate(UPDATE_MIGRATION_BLOB_SQL, migrationInfoBlob, dbMigrationStatus.getIdentifier());
    }
}
