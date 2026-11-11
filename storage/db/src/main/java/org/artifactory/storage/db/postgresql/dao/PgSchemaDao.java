package org.artifactory.storage.db.postgresql.dao;

import org.artifactory.storage.db.postgresql.entity.IndexSizeEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Data Access Object for querying PostgreSQL schema information.
 * <p>
 * Queries PostgreSQL catalog views ({@code pg_stat_all_indexes}, {@code pg_class}) to retrieve index and table sizes.
 * Designed for monitoring tens of indexes, not thousands.
 *
 * @author Andrey Tashlitsky
 */
@Repository
public class PgSchemaDao extends BaseDao {

    private static final String PG_INDEX_SIZES_QUERY = """
            SELECT pgsai.indexrelname                 AS index_name,
                   pg_relation_size(pgsai.indexrelid) AS index_size,
                   pgsai.relname                      AS table_name,
                   pg_relation_size(pgsai.relid)      AS table_size,
                   pgc.reltuples::bigint              AS table_rows
            FROM pg_stat_all_indexes pgsai
                     JOIN pg_class pgc ON pgsai.relid = pgc.oid
            WHERE pgsai.indexrelname IN (%s);""";

    @Autowired
    public PgSchemaDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Retrieves size and metadata for the specified indexes and their parent tables.
     * <p>
     * <b>Note:</b> Table row count is an approximate estimate from {@code pg_class.reltuples}.
     *
     * @param indexNames the set of index names to query (must not be empty)
     * @return index and table metadata entities
     * @throws SQLException if the database query fails
     */
    public Set<IndexSizeEntity> getIndexSizes(Set<String> indexNames) throws SQLException {
        String indexNamesIn = String.join(",", Collections.nCopies(indexNames.size(), "?"));
        String query = PG_INDEX_SIZES_QUERY.formatted(indexNamesIn);
        Set<IndexSizeEntity> indexSizes = new HashSet<>();
        try (ResultSet rs = jdbcHelper.executeSelect(query, indexNames.toArray())) {
            while (rs.next()) {
                String indexName = rs.getString("index_name");
                long indexSize = rs.getLong("index_size");
                String tableName = rs.getString("table_name");
                long tableSize = rs.getLong("table_size");
                long tableRows = rs.getLong("table_rows");
                IndexSizeEntity indexSizeEntity =
                        new IndexSizeEntity(indexName, indexSize, tableName, tableSize, tableRows);
                indexSizes.add(indexSizeEntity);
            }
        }
        return indexSizes;
    }
}
