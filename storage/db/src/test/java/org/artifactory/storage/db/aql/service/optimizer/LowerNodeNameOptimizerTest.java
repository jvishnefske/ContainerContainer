package org.artifactory.storage.db.aql.service.optimizer;

import org.artifactory.aql.model.AqlValue;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.aql.model.AqlComparatorEnum.matches;
import static org.artifactory.aql.model.AqlComparatorEnum.matchesIgnoreCase;
import static org.artifactory.aql.model.AqlVariableTypeEnum.string;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.nodes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Hussein Alshqerat
 */
class LowerNodeNameOptimizerTest {

    private final LowerNodeNameOptimizer optimizer = new LowerNodeNameOptimizer();

    @Test
    void testOptimize_AddsLowerCaseCondition() {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        List<AqlQueryElement> elements = new ArrayList<>();
        SimpleCriterion criterion =
                new SimpleCriterion(null, new AqlValue(string, "name"), new SqlTable(nodes), matches.signature,
                        new AqlValue(string, "*A*"), null, false);
        elements.add(criterion);
        aqlQuery.setAqlElements(elements);

        optimizer.optimize(aqlQuery, null);

        assertEquals(3, aqlQuery.getAqlElements().size());
        assertEquals(matchesIgnoreCase.getSignature(),
                ((SimpleCriterion) aqlQuery.getAqlElements().get(2)).getComparatorName());
        assertEquals("*a*", ((SimpleCriterion) aqlQuery.getAqlElements().get(2)).getVariable2().toNative());

    }

    @Test
    void testOptimize_NoOptimizationNeeded() {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        List<AqlQueryElement> elements = new ArrayList<>();
        aqlQuery.setAqlElements(elements);
        optimizer.optimize(aqlQuery, null);
        assertEquals(elements, aqlQuery.getAqlElements());
    }
}