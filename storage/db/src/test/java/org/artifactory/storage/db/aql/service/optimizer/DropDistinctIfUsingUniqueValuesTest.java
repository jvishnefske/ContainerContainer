package org.artifactory.storage.db.aql.service.optimizer;

import com.google.common.collect.ImmutableList;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.aql.model.AqlDomainEnum.properties;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.nodes;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Hussein Alshqerat
 */
class DropDistinctIfUsingUniqueValuesTest extends ArtifactoryHomeBoundJUnitTest {

    private final DropDistinctIfUsingUniqueValues dropDistinctIfUsingUniqueValues =
            new DropDistinctIfUsingUniqueValues();

    private static Stream<Arguments> fieldsProvider() {
        return Stream.of(Arguments.of(
                List.of(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, ImmutableList.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, ImmutableList.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemName,
                                ImmutableList.of(AqlDomainEnum.items)))), Arguments.of(
                List.of(new DomainSensitiveField(AqlPhysicalFieldEnum.itemId, ImmutableList.of(AqlDomainEnum.items)))));
    }

    @Test
    void testSkipOptimizationIfNotItemsDomain() {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        aqlQuery.setDistinct(true);
        aqlQuery.setDomain(AqlDomainEnum.builds);
        dropDistinctIfUsingUniqueValues.optimize(aqlQuery, "");
        assertTrue(aqlQuery.isDistinct());
    }

    @Test
    void testSkipOptimizationIfNotSingleDomainFirstTable() {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        aqlQuery.setDistinct(true);
        aqlQuery.setDomain(AqlDomainEnum.items);
        ComplexPropertyCriterion complexPropertyCriterion =
                new ComplexPropertyCriterion(List.of(properties), new AqlValue(AqlVariableTypeEnum.string, "start"),
                        new SqlTable(node_props, 100), "$eq", new AqlValue(AqlVariableTypeEnum.string, "df"),
                        new SqlTable(node_props, 100), false);
        aqlQuery.setAqlElements(List.of(complexPropertyCriterion));
        dropDistinctIfUsingUniqueValues.optimize(aqlQuery, "");
        assertTrue(aqlQuery.isDistinct());
    }

    @Test
    void testSkipOptimizationIfNotSingleDomainSecondTable() {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        aqlQuery.setDistinct(true);
        aqlQuery.setDomain(AqlDomainEnum.items);
        ComplexPropertyCriterion complexPropertyCriterion =
                new ComplexPropertyCriterion(List.of(properties), new AqlValue(AqlVariableTypeEnum.string, "start"),
                        new SqlTable(nodes, 100), "$eq", new AqlValue(AqlVariableTypeEnum.string, "df"),
                        new SqlTable(node_props, 100), false);
        aqlQuery.setAqlElements(List.of(complexPropertyCriterion));
        dropDistinctIfUsingUniqueValues.optimize(aqlQuery, "");
        assertTrue(aqlQuery.isDistinct());
    }

    @Test
    void testSkipOptimizationIfNotUsingUniqueIndex() {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        aqlQuery.setDistinct(true);
        aqlQuery.setDomain(AqlDomainEnum.items);
        ComplexPropertyCriterion complexPropertyCriterion =
                new ComplexPropertyCriterion(List.of(items), new AqlValue(AqlVariableTypeEnum.string, "name"),
                        new SqlTable(nodes, 100), "$eq", new AqlValue(AqlVariableTypeEnum.string, "created"),
                        new SqlTable(nodes, 100), false);
        aqlQuery.setAqlElements(List.of(complexPropertyCriterion));
        aqlQuery.setResultFields(
                List.of(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, ImmutableList.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemName,
                                ImmutableList.of(AqlDomainEnum.items))));
        dropDistinctIfUsingUniqueValues.optimize(aqlQuery, "");
        assertTrue(aqlQuery.isDistinct());
    }

    @ParameterizedTest
    @MethodSource("fieldsProvider")
    void testDoOptimization(List<DomainSensitiveField> resultFields) {
        AqlQuery<?> aqlQuery = new AqlQuery<>();
        aqlQuery.setDistinct(true);
        aqlQuery.setDomain(AqlDomainEnum.items);
        ComplexPropertyCriterion complexPropertyCriterion =
                new ComplexPropertyCriterion(List.of(items), new AqlValue(AqlVariableTypeEnum.string, "name"),
                        new SqlTable(nodes, 100), "$eq", new AqlValue(AqlVariableTypeEnum.string, "created"),
                        new SqlTable(nodes, 100), false);
        aqlQuery.setAqlElements(List.of(complexPropertyCriterion));
        aqlQuery.setResultFields(resultFields);
        dropDistinctIfUsingUniqueValues.optimize(aqlQuery, "");
        assertFalse(aqlQuery.isDistinct());
    }
}