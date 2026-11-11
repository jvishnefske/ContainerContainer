package org.artifactory.storage.db.aql.itest.service.optimizer;

import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.domain.sensitive.AqlApiProperty;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlOperatorEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.aql.itest.service.AqlAbstractServiceTest;
import org.artifactory.storage.db.aql.service.optimizer.TopLevelOrToUnion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlApiToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OpenParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
public class TopLevelOrToUnionTest extends AqlAbstractServiceTest {
    private final TopLevelOrToUnion optimization = new TopLevelOrToUnion();

    @BeforeMethod
    public void before() {
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.disableAqlOptimizationForBuilds, "false");
    }

    @AfterMethod
    public void after() {
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.disableAqlOptimizationForBuilds, "true");
    }

    @Test
    public void testOptimizeNoInclude() {
        AqlApiItem aqlQuery = AqlApiItem.createWithEmptyResults()
                .filter(
                        or(
                                and(
                                        AqlApiItem.property().property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        )
                );
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        AqlQuery<AqlRowResult> query = aqlApiToAqlAdapter.toAqlModel(aqlQuery);
        optimization.doJob(query);
        List<AqlQuery<AqlRowResult>> unionSubQueries = query.getUnionSubQueries();
        List<AqlQueryElement> aqlElements = query.getAqlElements();
        assertEquals(aqlElements.size(), 9);
        assertTrue(aqlElements.get(2) instanceof ComplexPropertyCriterion);
        assertTrue(aqlElements.get(3).isOperator());
        assertTrue(aqlElements.get(4) instanceof ComplexPropertyCriterion);
        assertTrue(aqlElements.get(7).isOperator());
        assertTrue(aqlElements.get(8) instanceof SimpleCriterion);
        assertEquals(unionSubQueries.size(), 1);
        assertEquals(unionSubQueries.get(0).getAqlElements().size(), 7);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(1) instanceof ComplexPropertyCriterion);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(2).isOperator());
        assertTrue(unionSubQueries.get(0).getAqlElements().get(3) instanceof ComplexPropertyCriterion);
    }

    @Test
    public void testOptimizeWithInclude() {
        SimplePropertyCriterion simple = Mockito.mock(SimplePropertyCriterion.class);
        SqlTable table = Mockito.mock(SqlTable.class);
        when(simple.getTable1()).thenReturn(table);
        when(table.getId()).thenReturn(-1);
        when(simple.getVariable1()).thenReturn(new AqlField(AqlPhysicalFieldEnum.propertyKey));
        AqlQuery<AqlRowResult> aqlQuery = createAqlQueryWithInclude(simple);
        AqlApiItem generated = AqlApiItem.create()
                .filter(
                        or(
                                and(
                                        AqlApiItem.property().property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        )
                ).include(AqlApiProperty.key());
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        AqlQuery<AqlRowResult> query = aqlApiToAqlAdapter.toAqlModel(generated);
        aqlQuery.getAqlElements().addAll(query.getAqlElements());
        optimization.doJob(aqlQuery);
        List<AqlQuery<AqlRowResult>> unionSubQueries = aqlQuery.getUnionSubQueries();
        List<AqlQueryElement> aqlElements = aqlQuery.getAqlElements();
        assertEquals(aqlElements.size(), 15);
        assertTrue(aqlElements.get(0) instanceof OpenParenthesisAqlElement);
        assertTrue(aqlElements.get(1) instanceof SimplePropertyCriterion);
        assertTrue(aqlElements.get(2).isOperator());
        assertTrue(aqlElements.get(3) instanceof SimplePropertyCriterion);
        assertTrue(aqlElements.get(4) instanceof CloseParenthesisAqlElement);
        assertTrue(aqlElements.get(5).isOperator());
        assertTrue(aqlElements.get(8) instanceof ComplexPropertyCriterion);
        assertTrue(aqlElements.get(9).isOperator());
        assertTrue(aqlElements.get(10) instanceof ComplexPropertyCriterion);
        assertEquals(unionSubQueries.size(), 1);
        assertEquals(unionSubQueries.get(0).getAqlElements().size(), 13);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(0) instanceof OpenParenthesisAqlElement);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(1) instanceof SimplePropertyCriterion);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(2).isOperator());
        assertTrue(unionSubQueries.get(0).getAqlElements().get(3) instanceof SimplePropertyCriterion);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(4) instanceof CloseParenthesisAqlElement);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(5).isOperator());
        assertTrue(unionSubQueries.get(0).getAqlElements().get(8) instanceof ComplexPropertyCriterion);
        assertTrue(unionSubQueries.get(0).getAqlElements().get(9).isOperator());
        assertTrue(unionSubQueries.get(0).getAqlElements().get(10) instanceof ComplexPropertyCriterion);
    }

    @Test
    public void testNoOptimization() {
        AqlApiItem aqlQuery = AqlApiItem.createWithEmptyResults()
                .filter(
                        and(
                                and(
                                        AqlApiItem.property().property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        )
                );
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        AqlQuery<AqlRowResult> query = aqlApiToAqlAdapter.toAqlModel(aqlQuery);
        optimization.doJob(query);
        List<AqlQuery<AqlRowResult>> unionSubQueries = query.getUnionSubQueries();
        List<AqlQueryElement> aqlElements = query.getAqlElements();
        assertEquals(aqlElements.size(), 15);
        assertTrue(aqlElements.get(2) instanceof ComplexPropertyCriterion);
        assertTrue(aqlElements.get(3).isOperator());
        assertTrue(aqlElements.get(4) instanceof ComplexPropertyCriterion);
        assertTrue(aqlElements.get(8) instanceof ComplexPropertyCriterion);
        assertTrue(aqlElements.get(9).isOperator());
        assertTrue(aqlElements.get(10) instanceof ComplexPropertyCriterion);
        assertEquals(unionSubQueries.size(), 0);
    }
    private AqlQuery<AqlRowResult> createAqlQueryWithInclude(SimplePropertyCriterion simple) {
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        aqlQuery.getAqlElements().add(new OpenParenthesisAqlElement());
        aqlQuery.getAqlElements().add(simple);
        aqlQuery.getAqlElements().add(new OperatorQueryElement(AqlOperatorEnum.or));
        aqlQuery.getAqlElements().add(simple);
        aqlQuery.getAqlElements().add(new CloseParenthesisAqlElement());
        aqlQuery.getAqlElements().add(new OperatorQueryElement(AqlOperatorEnum.and));
        return aqlQuery;
    }
}