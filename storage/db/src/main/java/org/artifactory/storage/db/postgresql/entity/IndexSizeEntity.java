package org.artifactory.storage.db.postgresql.entity;

import java.util.Objects;

/**
 * PostgreSQL index and table size metadata.
 * <p>
 * <b>Equality:</b> Based only on {@code indexName} (intentional - supports Map keying by index name).
 * <b>Note:</b> {@code tableRows} is an approximate estimate from PostgreSQL statistics.
 *
 * @param indexName the name of the index
 * @param indexSize the size of the index in bytes
 * @param tableName the name of the parent table
 * @param tableSize the size of the parent table in bytes
 * @param tableRows the approximate number of rows in the parent table
 * @author Andrey Tashlitsky
 */
public record IndexSizeEntity(
        String indexName,
        long indexSize,
        String tableName,
        long tableSize,
        long tableRows
) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IndexSizeEntity indexSizeEntity = (IndexSizeEntity) o;
        return Objects.equals(indexName, indexSizeEntity.indexName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(indexName);
    }
}
