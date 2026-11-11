package org.artifactory.storage.db.aql.sql.builder.query.sql.type;

import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlFunctionTableEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlApiToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.virtualRepos;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;


/**
 * @author Uriah Levy
 */
class BasicSqlGeneratorTest {
    private static Stream<Arguments> provide() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }

    @ParameterizedTest
    @MethodSource("provide")
    void testCountQuery(boolean hasUnion) {
        ArtifactsSqlGenerator artifactsSqlGenerator = new ArtifactsSqlGenerator();
        AqlApiItem aqlApiItem = AqlApiItem.createWithEmptyResults().distinct(false)
                .sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID);
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        AqlQuery<AqlRowResult> query = aqlApiToAqlAdapter.toAqlModel(aqlApiItem);
        if (hasUnion) {
            query.putSubQuery(new ArrayList(query.getAqlElements()));
            assertEquals(artifactsSqlGenerator.results(query), " n.node_id ");
        } else {
            assertEquals(artifactsSqlGenerator.results(query), " count(n.node_id) ");
        }
    }

    @ParameterizedTest
    @MethodSource("provide")
    void testDistinctCountQuery(boolean hasUnion) {
        ArtifactsSqlGenerator artifactsSqlGenerator = new ArtifactsSqlGenerator();
        AqlApiItem aqlApiItem =
                AqlApiItem.createWithEmptyResults()
                        .sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID);
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        AqlQuery<AqlRowResult> query = aqlApiToAqlAdapter.toAqlModel(aqlApiItem);
        if (hasUnion) {
            query.putSubQuery(new ArrayList(query.getAqlElements()));
            assertEquals(artifactsSqlGenerator.results(query), " distinct n.node_id ");
        } else {
            assertEquals(artifactsSqlGenerator.results(query), " count(distinct n.node_id) ");
        }

    }

    @Test
    void testMustNotStartWithAComma() {
        ArtifactsSqlGenerator artifactsSqlGenerator = new ArtifactsSqlGenerator();
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();

        AqlApiItem includePhysicalField =
                AqlApiItem.create()
                        .filter(AqlApiItem.repo().matches("my-local"));
        AqlQuery<AqlRowResult> physicalFieldAqlModel = aqlApiToAqlAdapter.toAqlModel(includePhysicalField);

        AqlApiItem includeLogicalFieldAqlItem =
                AqlApiItem.create()
                        .filter(AqlApiItem.repo().matches("my-virtual")).include(virtualRepos());
        AqlQuery<AqlRowResult> logicalFieldAqlModel = aqlApiToAqlAdapter.toAqlModel(includeLogicalFieldAqlItem);

        AqlApiItem includeFunctionField =
                AqlApiItem.createWithEmptyResults()
                        .filter(AqlApiItem.repo().matches("my-local"))
                        .sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID);
        AqlQuery<AqlRowResult> functionFieldAqlModel = aqlApiToAqlAdapter.toAqlModel(includeFunctionField);

        assertNotEquals(artifactsSqlGenerator.results(physicalFieldAqlModel).trim().charAt(0), ',');
        assertNotEquals(artifactsSqlGenerator.results(logicalFieldAqlModel).trim().charAt(0), ',');
        assertNotEquals(artifactsSqlGenerator.results(functionFieldAqlModel).trim().charAt(0), ',');
    }

    @Test
    void testIllegalQueryConfigurationUsingFactory() {
        AqlApiItem aqlApiItem =
                AqlApiItem.create().sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID);
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            aqlApiToAqlAdapter.toAqlModel(aqlApiItem);
        }, "Expected illegal state");

        Assertions.assertTrue(thrown.getMessage().contains("function"));
    }

    @Test
    void testIllegalQueryConfigurationUsingExplicitInclude() {
        AqlApiItem aqlApiItem = AqlApiItem.createWithEmptyResults()
                .sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID)
                .include(AqlApiItem.repo());
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            aqlApiToAqlAdapter.toAqlModel(aqlApiItem);
        }, "Expected illegal state");

        Assertions.assertTrue(thrown.getMessage().contains("function"));
    }
}