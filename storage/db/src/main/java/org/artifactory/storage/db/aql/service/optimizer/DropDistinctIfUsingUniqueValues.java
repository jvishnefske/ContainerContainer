package org.artifactory.storage.db.aql.service.optimizer;

import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.HashSet;
import java.util.List;

/**
 * This class represents an optimization strategy for AQL queries, specifically designed for the "nodes" domain
 * with unique values considerations. It extends the base class OptimizationStrategy.
 * The optimization strategy is applied to remove the DISTINCT keyword from AQL queries if certain conditions are met.
 * The conditions for optimization include:
 * - The AQL query must belong to the "items" domain.
 * - The AQL query must be a single domain query for the "nodes" table.
 * - The AQL query should be using unique values based on specific fields ("repo", "name", "path", "node_id").
 * If these conditions are met, the DISTINCT keyword is removed from the AQL query during the optimization process.
 *
 * @author Hussein Alshqerat
 */
public class DropDistinctIfUsingUniqueValues extends OptimizationStrategy {

    private static final List<String> NODES_UNIQUE_FIELDS = List.of("repo", "name", "path");
    private static final String NODE_ID = "id";

    /**
     * Checks whether the AQL query is a single domain query involving a specific SQL table.
     *
     * @param aqlQuery The AQL query to be checked.
     * @param table    The SQL table to be considered.
     * @return {@code true} if the AQL query is a single domain query for the specified table, {@code false} otherwise.
     */
    private static <T extends AqlRowResult> boolean isSingleDomainQuery(AqlQuery<T> aqlQuery, SqlTableEnum table) {
        return aqlQuery.getAqlElements().stream()
                .filter(Criterion.class::isInstance)
                .map(Criterion.class::cast)
                .noneMatch(criterion -> (!table.equals(criterion.getTable1().getTable())) ||
                        (!table.equals(criterion.getTable2().getTable())));
    }

    /**
     * Checks whether the AQL query belongs to the "items" domain.
     *
     * @param aqlQuery The AQL query to be checked.
     * @return {@code true} if the AQL query belongs to the "items" domain, {@code false} otherwise.
     */
    private <T extends AqlRowResult> boolean isItemsDomain(AqlQuery<T> aqlQuery) {
        return AqlDomainEnum.items.equals(aqlQuery.getDomain());
    }

    /**
     * Checks whether the AQL query returns unique values based on specific fields.
     *
     * @param aqlQuery The AQL query to be checked.
     * @return {@code true} if the AQL query uses unique values, {@code false} otherwise.
     */
    private <T extends AqlRowResult> boolean isIncludeUniqueValues(AqlQuery<T> aqlQuery) {
        List<String> includeElements = aqlQuery.getResultFields().stream()
                .map(domainSensitiveField -> domainSensitiveField.getField().getSignature())
                .toList();
        return includeElements.contains(NODE_ID) || new HashSet<>(includeElements).containsAll(NODES_UNIQUE_FIELDS);
    }

    /**
     * Determines whether the optimization should be skipped based on the given AQL query.
     *
     * @param aqlQuery The AQL query to be analyzed.
     * @return {@code true} if the optimization should be done, {@code false} otherwise.
     */
    private <T extends AqlRowResult> boolean shouldOptimize(AqlQuery<T> aqlQuery) {
        return isItemsDomain(aqlQuery) && isSingleDomainQuery(aqlQuery, SqlTableEnum.nodes) &&
                isIncludeUniqueValues(aqlQuery);
    }

    /**
     * Optimizes the given AQL query by removing the DISTINCT keyword if the conditions for optimization are met.
     *
     * @param aqlQuery       The AQL query to be optimized.
     * @param transformation not used in this case.
     */
    @Override
    public <T extends AqlRowResult> void optimize(AqlQuery<T> aqlQuery, String transformation) {
        if (shouldOptimize(aqlQuery)) {
            aqlQuery.setDistinct(false);
        }
    }
}