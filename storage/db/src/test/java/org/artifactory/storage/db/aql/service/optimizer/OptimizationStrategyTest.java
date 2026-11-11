package org.artifactory.storage.db.aql.service.optimizer;


import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlFunctionTableEnum;
import org.artifactory.aql.model.AqlSqlFunction;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Tomer Slutsky
 */
class OptimizationStrategyTest extends ArtifactoryHomeBoundJUnitTest {
    private static Stream<Arguments> provide() {
        return Stream.of(
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, false, null, true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, true, null, true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, false, null, false),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, true, null, true),
                Arguments.of(null, false, true, null, true),
                Arguments.of(null, false, false, null, false),
                Arguments.of(null, true, true, null, true),
                Arguments.of(null, true, false, null, false),
                Arguments.of(null, true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), true), true),
                Arguments.of(null, false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(null, true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(null, false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),

                Arguments.of(null, true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), true), true),
                Arguments.of(null, false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(null, true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(null, false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),

                Arguments.of(null, true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), true), true),
                Arguments.of(null, false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(null, true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(null, false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.builds), false), true),

                Arguments.of(null, true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), true), true),
                Arguments.of(null, false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(null, true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), false),
                Arguments.of(null, false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                        AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), false),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, false),
                        false, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, false, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), false),
                Arguments.of(
                        new AqlSqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID, true),
                        true, true, new SimpleCriterion(List.of(AqlDomainEnum.items), new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), "a", new AqlValue(
                                AqlVariableTypeEnum.string, "s"), new SqlTable(SqlTableEnum.nodes), false), true)
        );
    }

    @ParameterizedTest
    @MethodSource("provide")
    void testSkipOptimization(AqlSqlFunction function, boolean distinct, boolean isNullExists,
            SimpleCriterion criterion,
            boolean expected) {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.disableAqlOptimizationForBuilds, "true");
        AqlQuery query = new AqlQuery();
        query.getAqlElements().add(criterion);

        query.setSqlFunction(function);
        query.setDistinct(distinct);
        query.setNullExists(isNullExists);
        assertEquals(OptimizationStrategy.shouldSkipOptimization(query), expected);
    }
}