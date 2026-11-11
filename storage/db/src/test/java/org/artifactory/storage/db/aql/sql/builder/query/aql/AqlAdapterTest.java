package org.artifactory.storage.db.aql.sql.builder.query.aql;

import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlOperatorEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.aql.model.AqlComparatorEnum.equals;
import static org.artifactory.aql.model.AqlItemTypeEnum.file;
import static org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter.and;
import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.tablesLinksMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class AqlAdapterTest {
    private static Stream<Arguments> provide() {
        return Stream.of(Arguments.of(new ParserToAqlAdapter()), Arguments.of(new AqlApiToAqlAdapter()));
    }

    @ParameterizedTest
    @MethodSource("provide")
    public void testInjectDefaultValues(AqlAdapter adapter) {
        AdapterContext context = new AdapterContext<>();
        OperatorQueryElement and = new OperatorQueryElement(AqlOperatorEnum.and);
        context.getAqlQuery().setDomain(AqlDomainEnum.items);
        context.getAqlQuery().getAqlElements().add(new CloseParenthesisAqlElement());
        context.push(and);
        adapter.injectDefaultValues(context);
        SimpleCriterion criterion = (SimpleCriterion) context.getAqlQuery().getDefaultCriteria().get(1);
        assertEquals(context.getAqlQuery().getDefaultCriteria().get(0), and);
        assertEquals(criterion.getSubDomains(), List.of(AqlDomainEnum.items));
        assertEquals(criterion.getComparatorName(), equals.getSignature());
        assertEquals(((AqlValue) criterion.getVariable2()).getValue(), file.signature);
    }

    @ParameterizedTest
    @MethodSource("provide")
    public void testInjectDefaultValuesNoInject(AqlAdapter adapter) {
        AdapterContext context = new AdapterContext<>();
        OperatorQueryElement and = new OperatorQueryElement(AqlOperatorEnum.and);
        context.getAqlQuery().setDomain(AqlDomainEnum.properties);
        context.getAqlQuery().getAqlElements().add(new CloseParenthesisAqlElement());
        context.push(and);
        adapter.injectDefaultValues(context);
        assertEquals(context.getAqlQuery().getDefaultCriteria().size(), 0);
        assertEquals(context.getAqlQuery().getAqlElements().size(), 1);
    }

    @ParameterizedTest
    @MethodSource("provide")
    public void testInjectDefaultValuesNoInjectDomainItems(AqlAdapter adapter) {
        AdapterContext context = new AdapterContext<>();
        OperatorQueryElement and = new OperatorQueryElement(AqlOperatorEnum.and);
        context.getAqlQuery().setDomain(AqlDomainEnum.items);
        context.getAqlQuery().getAqlElements()
                .add(new SimpleCriterion(List.of(), new AqlField(AqlPhysicalFieldEnum.itemType),
                        tablesLinksMap.get(SqlTableEnum.nodes).getTable(), and.getOperatorEnum().signature,
                        null, tablesLinksMap.get(SqlTableEnum.nodes).getTable(), false));
        context.getAqlQuery().getAqlElements().add(new CloseParenthesisAqlElement());
        context.push(and);
        adapter.injectDefaultValues(context);
        assertEquals(context.getAqlQuery().getDefaultCriteria().size(), 0);
        assertEquals(context.getAqlQuery().getAqlElements().size(), 2);
    }

    @ParameterizedTest
    @MethodSource("provide")
    public void testAddCriterionNullExists(AqlAdapter adapter) {
        AdapterContext context = new AdapterContext<>();
        SimplePropertyCriterion simpleCriterion =
                new SimplePropertyCriterion(List.of(), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                        tablesLinksMap.get(SqlTableEnum.node_props).getTable(), and.getOperatorEnum().signature,
                        new AqlValue(AqlVariableTypeEnum.string, null),
                        tablesLinksMap.get(SqlTableEnum.node_props).getTable(), false);
        adapter.addCriteria(context, simpleCriterion, simpleCriterion);
        assertTrue(context.getAqlQuery().isNullExists());
    }

    @ParameterizedTest
    @MethodSource("provide")
    public void testAddCriterionNullNotExists(AqlAdapter adapter) {
        AdapterContext context = new AdapterContext<>();
        SimplePropertyCriterion simpleCriterion =
                new SimplePropertyCriterion(List.of(), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                        tablesLinksMap.get(SqlTableEnum.node_props).getTable(), and.getOperatorEnum().signature,
                        new AqlValue(AqlVariableTypeEnum.string, "bla"),
                        tablesLinksMap.get(SqlTableEnum.node_props).getTable(),
                        false);
        adapter.addCriteria(context, simpleCriterion, simpleCriterion);
        assertFalse(context.getAqlQuery().isNullExists());
    }

}