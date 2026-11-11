package org.artifactory.storage.db.aql.service.optimizer;

import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterionExists;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterionExists;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.aql.model.AqlDomainEnum.properties;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_props;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.AssertionErrors.assertTrue;

/**
 * @author Tomer Slutsky
 */
class FromLeftOuterJoinToExistsTest extends ArtifactoryHomeBoundJUnitTest {
    private static Stream<Arguments> provide() {
        return Stream.of(Arguments.of(
                        new ComplexPropertyCriterion(List.of(properties), new AqlValue(AqlVariableTypeEnum.string,
                                "start"),
                                new SqlTable(node_props, 100), "$ne", new AqlValue(AqlVariableTypeEnum.string, "df"),
                                new SqlTable(node_props, 100), false),
                        new ComplexPropertyCriterionExists(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100), "$ne",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), false),
                        "ComplexPropertyCriterionExists", ComplexPropertyCriterionExists.class),
                Arguments.of(new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100), "$eq",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), false),
                        new ComplexPropertyCriterionExists(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100), "$eq",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), false),
                        "ComplexPropertyCriterionExists", ComplexPropertyCriterionExists.class),
                Arguments.of(new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$nmatch",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), false),
                        new ComplexPropertyCriterionExists(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$nmatch", new AqlValue(AqlVariableTypeEnum.string, "df"),
                                new SqlTable(node_props, 100), false), "ComplexPropertyCriterionExists",
                        ComplexPropertyCriterionExists.class),
                Arguments.of(new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$match",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), false),
                        new ComplexPropertyCriterionExists(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$match", new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100),
                                false), "ComplexPropertyCriterionExists", ComplexPropertyCriterionExists.class),
                Arguments.of(new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$nmatch",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), true),
                        new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$nmatch", new AqlValue(AqlVariableTypeEnum.string, "df"),
                                new SqlTable(node_props, 100), true), "ComplexPropertyCriterion",
                        ComplexPropertyCriterion.class),
                Arguments.of(new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$match",
                                new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100), true),
                        new ComplexPropertyCriterion(List.of(properties),
                                new AqlValue(AqlVariableTypeEnum.string, "start"), new SqlTable(node_props, 100),
                                "$match", new AqlValue(AqlVariableTypeEnum.string, "df"), new SqlTable(node_props, 100),
                                true), "ComplexPropertyCriterion", ComplexPropertyCriterion.class),
                Arguments.of(
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$ne", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), false),
                        new SimplePropertyCriterionExists(List.of(properties),
                                new AqlField(AqlPhysicalFieldEnum.propertyKey), new SqlTable(node_props, 100), "$ne",
                                new AqlValue(AqlVariableTypeEnum.string, "*"), new SqlTable(node_props, 100), false),
                        "SimplePropertyCriterionExists", SimplePropertyCriterionExists.class),
                Arguments.of(
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$eq", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), false),
                        new SimplePropertyCriterionExists(List.of(properties),
                                new AqlField(AqlPhysicalFieldEnum.propertyKey), new SqlTable(node_props, 100), "$eq",
                                new AqlValue(AqlVariableTypeEnum.string, "*"), new SqlTable(node_props, 100), false),
                        "SimplePropertyCriterionExists", SimplePropertyCriterionExists.class),
                Arguments.of(
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$nmatch", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), false),
                        new SimplePropertyCriterionExists(List.of(properties),
                                new AqlField(AqlPhysicalFieldEnum.propertyKey), new SqlTable(node_props, 100),
                                "$nmatch", new AqlValue(AqlVariableTypeEnum.string, "*"), new SqlTable(node_props, 100),
                                false), "SimplePropertyCriterionExists", SimplePropertyCriterionExists.class),
                Arguments.of(
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$match", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), false),
                        new SimplePropertyCriterionExists(List.of(properties),
                                new AqlField(AqlPhysicalFieldEnum.propertyKey), new SqlTable(node_props, 100), "$match",
                                new AqlValue(AqlVariableTypeEnum.string, "*"), new SqlTable(node_props, 100), false),
                        "SimplePropertyCriterionExists", SimplePropertyCriterionExists.class),
                Arguments.of(
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$nmatch", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), true),
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$nmatch", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), true), "SimplePropertyCriterion",
                        SimplePropertyCriterion.class),
                Arguments.of(
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$match", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), true),
                        new SimplePropertyCriterion(List.of(properties), new AqlField(AqlPhysicalFieldEnum.propertyKey),
                                new SqlTable(node_props, 100), "$match", new AqlValue(AqlVariableTypeEnum.string, "*"),
                                new SqlTable(node_props, 100), true), "SimplePropertyCriterion",
                        SimplePropertyCriterion.class));
    }

    @ParameterizedTest
    @MethodSource("provide")
    public void OptimizeByComplexPropertyCriterionNotEquals(Criterion actual, Criterion expected, String expectedClass,
            Class actualClass) {
        AqlQuery aqlQuery = new AqlQuery();
        aqlQuery.getAqlElements().add(actual);
        new FromLeftOuterJoinToExists().doJob(aqlQuery);
        Object actualValue = aqlQuery.getAqlElements().get(0);
        assertTrue("instance of " + expectedClass, actualClass.isInstance(actualValue));
        assertThat(actualValue).usingRecursiveComparison().isEqualTo(expected);
    }
}
