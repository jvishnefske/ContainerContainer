package org.artifactory.storage.db.devopsagent.errorcatalog.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.TemplateRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.AgenticSearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.TemplateUpsertRequest;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * DAO for the {@code error_catalog_templates} table — bounded, deduplicated, FTS-indexed.
 * UPSERT branches per DB so on-conflict semantics use each engine's native path.
 */
@Repository
@Slf4j
public class ErrorCatalogTemplateDao extends BaseDao {

    public static final String TABLE = "error_catalog_templates";

    private static final String SELECT_COLUMNS =
            "id, signature_hash, severity, logger_class, exception_class, template_message, "
                    + "first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at";

    private final DbType dbType;

    public ErrorCatalogTemplateDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbType = dbProperties.getDbType();
    }

    /**
     * Bulk signature → id lookup used by the flush job to map raw events to {@code template_id}.
     * Single SELECT with an IN-list of the signatures from the current flush window.
     */
    public java.util.Map<String, Long> findIdsBySignatureHashes(java.util.Collection<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        // Build "?, ?, ..." placeholder list; safe because the list is small (one per signature this flush window).
        StringBuilder placeholders = new StringBuilder(hashes.size() * 2);
        for (int i = 0; i < hashes.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        String sql = "SELECT signature_hash, id FROM " + TABLE + " WHERE signature_hash IN (" + placeholders + ")";
        try (ResultSet rs = jdbcHelper.executeSelect(sql, hashes.toArray())) {
            java.util.Map<String, Long> out = new java.util.HashMap<>(hashes.size() * 2);
            while (rs.next()) {
                out.put(rs.getString("signature_hash"), rs.getLong("id"));
            }
            return out;
        } catch (SQLException e) {
            throw new StorageException("Failed to lookup template ids by signature_hash batch", e);
        }
    }

    public Optional<TemplateRow> findBySignatureHash(String signatureHash) {
        if (signatureHash == null) {
            return Optional.empty();
        }
        try (ResultSet rs = jdbcHelper.executeSelect(
                "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + " WHERE signature_hash = ?",
                signatureHash)) {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new StorageException("Failed to look up error_catalog_templates by signature_hash", e);
        }
    }

    /**
     * Atomically inserts a new row or merges into an existing one (matched by {@code signature_hash}).
     * On match: {@code occurrence_count} increments by {@code occurrenceCountDelta},
     * {@code last_occurrence_at} advances to GREATEST(existing, request), {@code first_occurrence_at} is preserved.
     */
    private void upsert(TemplateUpsertRequest req) {
        switch (dbType) {
            case POSTGRESQL -> upsertPostgres(req);
            case MYSQL, MARIADB -> upsertMysql(req);
            case ORACLE -> upsertOracle(req);
            case MSSQL -> upsertMssql(req);
            case DERBY -> upsertDerby(req);
            default -> throw new StorageException("Unsupported DB type for error_catalog_templates UPSERT: " + dbType);
        }
    }

    /**
     * Batched UPSERT entry point used by the producer flush job. Iterates {@link #upsert} per request;
     * the per-DB UPSERT statements vary too much to share a single batched PreparedStatement cleanly,
     * and the batch sizes are small (one per unique signature per flush window) so the per-row cost is fine.
     *
     * <p>Intentionally <b>not</b> {@code @Transactional}: wrapping the entire batch in one transaction
     * caused deadlocks when sibling nodes (each running their own flush) processed overlapping new
     * signatures concurrently.Callers must not wrap this in a transaction</p>
     */
    public void upsertBatch(List<TemplateUpsertRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return;
        }
        for (TemplateUpsertRequest req : reqs) {
            try {
                upsert(req);
            } catch (StorageException e) {
                log.warn("Failed to upsert error_catalog_templates for signature '{}', skipping: {}",
                        req.signatureHash(), e.getMessage());
            }
        }
    }

    private void upsertPostgres(TemplateUpsertRequest req) {
        String sql = "INSERT INTO " + TABLE + " ("
                + "signature_hash, severity, logger_class, exception_class, template_message, "
                + "first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (signature_hash) DO UPDATE SET "
                + "last_occurrence_at = GREATEST(" + TABLE + ".last_occurrence_at, EXCLUDED.last_occurrence_at), "
                + "occurrence_count = " + TABLE + ".occurrence_count + EXCLUDED.occurrence_count, "
                + "updated_at = EXCLUDED.updated_at";
        executeUpsert(sql, req);
    }

    private void upsertMysql(TemplateUpsertRequest req) {
        String sql = "INSERT INTO " + TABLE + " ("
                + "signature_hash, severity, logger_class, exception_class, template_message, "
                + "first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "last_occurrence_at = GREATEST(last_occurrence_at, VALUES(last_occurrence_at)), "
                + "occurrence_count = occurrence_count + VALUES(occurrence_count), "
                + "updated_at = VALUES(updated_at)";
        executeUpsert(sql, req);
    }

    private void upsertOracle(TemplateUpsertRequest req) {
        String sql = "MERGE INTO " + TABLE + " t USING ("
                + "  SELECT ? AS signature_hash, ? AS severity, ? AS logger_class, ? AS exception_class, "
                + "         ? AS template_message, ? AS first_occurrence_at, ? AS last_occurrence_at, "
                + "         ? AS occurrence_count, ? AS created_at, ? AS updated_at FROM dual"
                + ") s ON (t.signature_hash = s.signature_hash) "
                + "WHEN MATCHED THEN UPDATE SET "
                + "  t.last_occurrence_at = GREATEST(t.last_occurrence_at, s.last_occurrence_at), "
                + "  t.occurrence_count = t.occurrence_count + s.occurrence_count, "
                + "  t.updated_at = s.updated_at "
                + "WHEN NOT MATCHED THEN INSERT ("
                + "  signature_hash, severity, logger_class, exception_class, template_message, "
                + "  first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at) "
                + "VALUES (s.signature_hash, s.severity, s.logger_class, s.exception_class, s.template_message, "
                + "  s.first_occurrence_at, s.last_occurrence_at, s.occurrence_count, s.created_at, s.updated_at)";
        executeUpsert(sql, req);
    }

    private void upsertMssql(TemplateUpsertRequest req) {
        String sql = "MERGE INTO " + TABLE + " AS t USING ("
                + "  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + ") AS s (signature_hash, severity, logger_class, exception_class, template_message, "
                + "        first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at) "
                + "ON t.signature_hash = s.signature_hash "
                + "WHEN MATCHED THEN UPDATE SET "
                + "  t.last_occurrence_at = "
                + "    CASE WHEN s.last_occurrence_at > t.last_occurrence_at "
                + "         THEN s.last_occurrence_at ELSE t.last_occurrence_at END, "
                + "  t.occurrence_count = t.occurrence_count + s.occurrence_count, "
                + "  t.updated_at = s.updated_at "
                + "WHEN NOT MATCHED THEN INSERT ("
                + "  signature_hash, severity, logger_class, exception_class, template_message, "
                + "  first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at) "
                + "VALUES (s.signature_hash, s.severity, s.logger_class, s.exception_class, s.template_message, "
                + "  s.first_occurrence_at, s.last_occurrence_at, s.occurrence_count, s.created_at, s.updated_at);";
        executeUpsert(sql, req);
    }

    /**
     * Derby has no native UPSERT. Try an UPDATE first; if zero rows changed, INSERT.
     * Caller guarantees a transactional context so the read/write pair is atomic for HA correctness.
     */
    private void upsertDerby(TemplateUpsertRequest req) {
        try {
            int updated = jdbcHelper.executeUpdate(
                    "UPDATE " + TABLE + " SET "
                            + "last_occurrence_at = CASE WHEN ? > last_occurrence_at "
                            + "THEN ? ELSE last_occurrence_at END, "
                            + "occurrence_count = occurrence_count + ?, "
                            + "updated_at = ? "
                            + "WHERE signature_hash = ?",
                    req.latestOccurrenceAt(),
                    req.latestOccurrenceAt(),
                    req.occurrenceCountDelta(),
                    req.nowEpochMs(),
                    req.signatureHash());
            if (updated == 0) {
                insertDerbyOrRetryOnConflict(req);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to UPSERT into " + TABLE + " (Derby)", e);
        }
    }

    /**
     * Race-aware INSERT for the Derby upsert path. If a concurrent inserter beat us to the UK,
     * fall back to the UPDATE branch by re-entering {@link #upsertDerby}. Surfaces every other
     * {@link SQLException} to the caller for the outer transaction handler to translate.
     */
    private void insertDerbyOrRetryOnConflict(TemplateUpsertRequest req) throws SQLException {
        String insertSql = "INSERT INTO " + TABLE + " ("
                + "signature_hash, severity, logger_class, exception_class, template_message, "
                + "first_occurrence_at, last_occurrence_at, occurrence_count, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcHelper.executeUpdate(insertSql,
                    req.signatureHash(),
                    req.severity(),
                    req.loggerClass(),
                    req.exceptionClass(),
                    req.templateMessage(),
                    req.latestOccurrenceAt(),
                    req.latestOccurrenceAt(),
                    req.occurrenceCountDelta(),
                    req.nowEpochMs(),
                    req.nowEpochMs());
        } catch (SQLException insertEx) {
            if (isUniqueConstraintViolation(insertEx)) {
                upsertDerby(req);
                return;
            }
            throw insertEx;
        }
    }

    private void executeUpsert(String sql, TemplateUpsertRequest req) {
        try {
            jdbcHelper.executeUpdate(sql,
                    req.signatureHash(),
                    req.severity(),
                    req.loggerClass(),
                    req.exceptionClass(),
                    req.templateMessage(),
                    req.latestOccurrenceAt(),
                    req.latestOccurrenceAt(),
                    req.occurrenceCountDelta(),
                    req.nowEpochMs(),
                    req.nowEpochMs());
        } catch (SQLException e) {
            throw new StorageException("Failed to UPSERT into " + TABLE, e);
        }
    }

    /**
     * FTS-driven search with optional severity / logger_class filters and keyset pagination.
     * PG uses GIN-backed {@code message_tsv @@ websearch_to_tsquery}, MySQL uses native FULLTEXT,
     * others fall back to case-insensitive {@code LIKE}.
     */
    public List<TemplateRow> searchByMessage(SearchCriteria criteria, KeysetCursor cursor, int limit) {
        if (criteria == null) {
            criteria = new SearchCriteria(null, null, null);
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS).append(" FROM ").append(TABLE)
                .append(" WHERE 1 = 1");
        if (criteria.searchText() != null && !criteria.searchText().isBlank()) {
            switch (dbType) {
                case POSTGRESQL -> {
                    sql.append(" AND message_tsv @@ websearch_to_tsquery('english', ?)");
                    params.add(criteria.searchText());
                }
                case MYSQL, MARIADB -> {
                    sql.append(" AND MATCH(template_message) AGAINST(? IN NATURAL LANGUAGE MODE)");
                    params.add(criteria.searchText());
                }
                default -> {
                    sql.append(" AND LOWER(template_message) LIKE ?");
                    params.add("%" + criteria.searchText().toLowerCase(Locale.ROOT) + "%");
                }
            }
        }
        if (criteria.severityFilter() != null) {
            sql.append(" AND severity = ?");
            params.add(criteria.severityFilter());
        }
        if (criteria.loggerClassEq() != null) {
            sql.append(" AND logger_class = ?");
            params.add(criteria.loggerClassEq());
        }
        if (cursor != null) {
            sql.append(" AND (last_occurrence_at < ? OR (last_occurrence_at = ? AND id < ?))");
            params.add(cursor.timeValue());
            params.add(cursor.timeValue());
            params.add(cursor.id());
        }
        sql.append(" ORDER BY last_occurrence_at DESC, id DESC");
        appendPagination(sql, limit, 0);
        return runQuery(sql.toString(), params);
    }

    public List<TemplateRow> findBySeverity(String severity, KeysetCursor cursor, int limit) {
        return searchByMessage(new SearchCriteria(null, severity, null), cursor, limit);
    }

    public List<TemplateRow> findByLoggerClass(String loggerClass, KeysetCursor cursor, int limit) {
        return searchByMessage(new SearchCriteria(null, null, loggerClass), cursor, limit);
    }

    /**
     * Multi-filter search used by the agentic REST surface. Caller asks for {@code limitPlusOne =
     * userLimit + 1} so the resource layer can detect {@code hasMore}. Returns rows ordered by
     * {@code last_occurrence_at DESC, id DESC} (id tiebreaker for determinism). Empty / null lists
     * and null bounds in {@code criteria} mean "no constraint".
     *
     * <p>{@link AgenticSearchCriteria#tsqueryString()} is the pre-composed argument for PG
     * {@code to_tsquery('english', ?)}. The producer-side path uses {@link #searchByMessage}
     * which has per-DB FTS / LIKE fallbacks; the agentic path is PG-only and the service layer
     * gates non-PG callers with HTTP 501 upstream.
     */
    public List<TemplateRow> searchTemplates(AgenticSearchCriteria criteria, int limitPlusOne, int offset) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS).append(" FROM ").append(TABLE)
                .append(" WHERE 1 = 1");
        appendInClause(sql, params, "id", criteria.ids());
        appendInClause(sql, params, "severity", criteria.severities());
        appendInClause(sql, params, "logger_class", criteria.loggerClasses());
        appendInClause(sql, params, "exception_class", criteria.exceptionClasses());
        appendRange(sql, params, "occurrence_count", criteria.occurrenceCountFrom(), criteria.occurrenceCountTo());
        appendRange(sql, params, "first_occurrence_at", criteria.firstOccurrenceFrom(), criteria.firstOccurrenceTo());
        appendRange(sql, params, "last_occurrence_at", criteria.lastOccurrenceFrom(), criteria.lastOccurrenceTo());
        if (criteria.tsqueryString() != null) {
            sql.append(" AND message_tsv @@ to_tsquery('english', ?)");
            params.add(criteria.tsqueryString());
        }
        sql.append(" ORDER BY last_occurrence_at DESC, id DESC");
        appendPagination(sql, limitPlusOne, offset);
        return runQuery(sql.toString(), params);
    }

    private static void appendInClause(StringBuilder sql, List<Object> params, String column, List<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("?");
            params.add(values.get(i));
        }
        sql.append(")");
    }

    private static void appendRange(StringBuilder sql, List<Object> params, String column, Long from, Long to) {
        if (from != null) {
            sql.append(" AND ").append(column).append(" >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND ").append(column).append(" <= ?");
            params.add(to);
        }
    }

    /**
     * Removes templates whose {@code last_occurrence_at} is strictly older than the cutoff.
     * Bounded row count keeps this a safe single-statement DELETE.
     */
    public int deleteOlderThan(long cutoffMs) {
        try {
            return jdbcHelper.executeUpdate("DELETE FROM " + TABLE + " WHERE last_occurrence_at < ?", cutoffMs);
        } catch (SQLException e) {
            throw new StorageException("Failed to sweep " + TABLE + " by cutoff", e);
        }
    }

    public int deleteAll() {
        try {
            return jdbcHelper.executeUpdate("DELETE FROM " + TABLE);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete all from " + TABLE, e);
        }
    }

    private void appendPagination(StringBuilder sql, int limit, int offset) {
        // T-SQL rejects FETCH without a preceding OFFSET; MySQL/MariaDB reject OFFSET before LIMIT.
        if (dbType == DbType.MSSQL) {
            sql.append(" OFFSET ").append(offset).append(" ROWS FETCH NEXT ")
                    .append(limit).append(" ROWS ONLY");
            return;
        }
        if (dbType == DbType.ORACLE || dbType == DbType.DERBY) {
            if (offset > 0) {
                sql.append(" OFFSET ").append(offset).append(" ROWS");
            }
            sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
            return;
        }
        sql.append(" LIMIT ").append(limit);
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    private List<TemplateRow> runQuery(String sql, List<Object> params) {
        try (ResultSet rs = jdbcHelper.executeSelect(sql, params.toArray())) {
            List<TemplateRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new StorageException("Failed to query " + TABLE, e);
        }
    }

    private TemplateRow mapRow(ResultSet rs) throws SQLException {
        return new TemplateRow(
                rs.getLong("id"),
                rs.getString("signature_hash"),
                rs.getString("severity"),
                rs.getString("logger_class"),
                rs.getString("exception_class"),
                rs.getString("template_message"),
                rs.getLong("first_occurrence_at"),
                rs.getLong("last_occurrence_at"),
                rs.getLong("occurrence_count"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

}
