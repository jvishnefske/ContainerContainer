package org.artifactory.storage.db.aql.service.optimizer;

import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.aql.model.AqlComparatorEnum.matches;
import static org.artifactory.aql.model.AqlComparatorEnum.matchesIgnoreCase;
import static org.artifactory.aql.model.AqlOperatorEnum.and;

/**
 * Optimization strategy to add a lower-case condition for node names in AQL queries.
 * The strategy is applied to AQL queries that contain a match criterion for node names.
 * The strategy adds a lower-case condition for node names to the query.
 * the optimization is applied only for PostgresSQL databases.
 *
 * @author Hussein Alshqerat
 */
public class LowerNodeNameOptimizer extends OptimizationStrategy {

    /**
     * Optimizes the given AQL query by adding a lower-case condition for node names.
     *
     * @param aqlQuery       The AQL query to optimize.
     * @param transformation The transformation string (not used in this implementation).
     * @param <T>            The type of AQL row result.
     */
    @Override
    public <T extends AqlRowResult> void optimize(AqlQuery<T> aqlQuery, String transformation) {
        if (shouldOptimize(aqlQuery)) {
            List<AqlQueryElement> optimizedElements = new ArrayList<>();
            for (AqlQueryElement element : aqlQuery.getAqlElements()) {
                if (element instanceof SimpleCriterion criterion) {
                    if (isNodeNameMatchCriterion(criterion)) {
                        optimizedElements.add(criterion);
                        optimizedElements.add(new OperatorQueryElement(and));
                        optimizedElements.add(createLowerCaseCriterion(criterion));
                    } else {
                        optimizedElements.add(element);
                    }
                } else {
                    optimizedElements.add(element);
                }
            }
            aqlQuery.setAqlElements(optimizedElements);
        }
    }

    /**
     * Checks if the given AQL query should be optimized.
     *
     * @param aqlQuery The AQL query to check.
     * @param <T>      The type of AQL row result.
     * @return True if the query should be optimized, false otherwise.
     */
    private <T extends AqlRowResult> boolean shouldOptimize(AqlQuery<T> aqlQuery) {
        return aqlQuery.getAqlElements().stream()
                .filter(SimpleCriterion.class::isInstance)
                .map(SimpleCriterion.class::cast)
                .anyMatch(this::isNodeNameMatchCriterion);
    }

    /**
     * Checks if the given criterion is a match criterion for node names.
     *
     * @param criterion The criterion to check.
     * @return True if the criterion is a match criterion for node names, false otherwise.
     */
    private boolean isNodeNameMatchCriterion(SimpleCriterion criterion) {
        return matches.signature.equals(criterion.getComparatorName()) &&
                "name".equals(criterion.getVariable1().toNative()) &&
                SqlTableEnum.nodes.equals(criterion.getTable1().getTable());
    }

    /**
     * Creates a lower-case criterion based on the given criterion.
     *
     * @param criterion The original criterion.
     * @return The lower-case criterion.
     */
    private SimpleCriterion createLowerCaseCriterion(SimpleCriterion criterion) {
        return new SimpleCriterion(
                criterion.getSubDomains(),
                criterion.getVariable1(),
                criterion.getTable1(),
                matchesIgnoreCase.signature,
                new AqlValue(AqlVariableTypeEnum.string, criterion.getVariable2().toNative().toLowerCase()),
                criterion.getTable2(),
                criterion.isMspOperator()
        );
    }
}