package org.artifactory.storage.db.aql.sql.builder.query.aql;

import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.aql.model.AqlComparatorEnum.equals;
import static org.artifactory.aql.model.AqlComparatorEnum.equalsValueIgnoreCase;
import static org.artifactory.aql.model.AqlComparatorEnum.matches;
import static org.artifactory.aql.model.AqlComparatorEnum.matchesIgnoreCase;
import static org.artifactory.aql.model.AqlComparatorEnum.notEquals;
import static org.artifactory.aql.model.AqlComparatorEnum.notMatches;
import static org.artifactory.aql.model.AqlDomainEnum.buildProperties;
import static org.artifactory.aql.model.AqlDomainEnum.builds;
import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.aql.model.AqlDomainEnum.moduleProperties;
import static org.artifactory.aql.model.AqlDomainEnum.modules;
import static org.artifactory.aql.model.AqlDomainEnum.properties;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_modules;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.module_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_props;
import static org.testng.Assert.assertEquals;

class ComplexPropertyCriterionExistsTest {
    private static Stream<Arguments> provide() {
        return Stream.of(
                Arguments.of(List.of(modules, builds,
                                buildProperties), build_props, build_modules,
                        " ( not exists ( select 1 from build_props bpsub where bpsub.build_id = b" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value like ? ))",
                        notMatches.getSignature()),
                Arguments.of(List.of(moduleProperties), module_props,
                        module_props,
                        " ( not exists ( select 1 from module_props bmpsub where bmpsub.module_id = bmp.module_id and" +
                                " " +
                                "bmpsub" +
                                ".prop_key = ? and bmpsub.prop_value like ? ))", notMatches.getSignature()),
                Arguments.of(List.of(buildProperties), build_props, build_props,
                        " ( not exists ( select 1 from build_props bpsub where bpsub.build_id = bp" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value like ? ))",
                        notMatches.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props,
                        " ( not exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                                "npsub" +
                                ".prop_key = ? and npsub.prop_value like ? ))", notMatches.getSignature()),
                Arguments.of(List.of(modules, builds,
                                buildProperties), build_props, build_props,
                        " ( not exists ( select 1 from build_props bpsub where bpsub.build_id = b" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value = ? ))",
                        notEquals.getSignature()),
                Arguments.of(List.of(buildProperties), build_props, build_props,
                        " ( not exists ( select 1 from build_props bpsub where bpsub.build_id = bp" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value = ? ))",
                        notEquals.getSignature()),
                Arguments.of(List.of(properties), node_props, node_props,
                        " ( not exists ( select 1 from node_props npsub where npsub.node_id = np" +
                                ".node_id and npsub.prop_key = ? and npsub.prop_value = ? ))",
                        notEquals.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props,
                        " ( not exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                                "npsub" +
                                ".prop_key = ? and npsub.prop_value = ? ))", notEquals.getSignature()),
                // Positive
                Arguments.of(List.of(modules, builds,
                                buildProperties), build_props, build_modules,
                        " ( exists ( select 1 from build_props bpsub where bpsub.build_id = b" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value like ? ))",
                        matches.getSignature()),
                Arguments.of(List.of(moduleProperties), module_props,
                        module_props,
                        " ( exists ( select 1 from module_props bmpsub where bmpsub.module_id = bmp.module_id and " +
                                "bmpsub.prop_key = ? and LOWER(bmpsub.prop_value) like ? ))",
                        matchesIgnoreCase.getSignature()),
                Arguments.of(List.of(moduleProperties), module_props,
                        module_props,
                        " ( exists ( select 1 from module_props bmpsub where bmpsub.module_id = bmp.module_id and " +
                                "bmpsub" +
                                ".prop_key = ? and bmpsub.prop_value like ? ))", matches.getSignature()),
                Arguments.of(List.of(buildProperties), build_props, build_props,
                        " ( exists ( select 1 from build_props bpsub where bpsub.build_id = bp" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value like ? ))",
                        matches.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props,
                        " ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                                "npsub" +
                                ".prop_key = ? and npsub.prop_value like ? ))", matches.getSignature()),
                Arguments.of(List.of(modules, builds,
                                buildProperties), build_props, build_props,
                        " ( exists ( select 1 from build_props bpsub where bpsub.build_id = b" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value = ? ))",
                        equals.getSignature()),
                Arguments.of(List.of(buildProperties), build_props, build_props,
                        " ( exists ( select 1 from build_props bpsub where bpsub.build_id = bp" +
                                ".build_id and bpsub.prop_key = ? and bpsub.prop_value = ? ))",
                        equals.getSignature()),
                Arguments.of(List.of(properties), node_props, node_props,
                        " ( exists ( select 1 from node_props npsub where npsub.node_id = np" +
                                ".node_id and npsub.prop_key = ? and npsub.prop_value = ? ))",
                        equals.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props,
                        " ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                                "npsub" +
                                ".prop_key = ? and npsub.prop_value = ? ))", equals.getSignature()),
                // Exact key, case-folded value: prop_key stays sargable, only prop_value is lowered.
                Arguments.of(List.of(items, properties), node_props, node_props,
                        " ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                                "npsub.prop_key = ? and LOWER(npsub.prop_value) = ? ))",
                        equalsValueIgnoreCase.getSignature())

        );
    }

    @ParameterizedTest
    @MethodSource("provide")
    void test(List<AqlDomainEnum> domains, SqlTableEnum from,
            SqlTableEnum to, String expected, String comparator) {
        ComplexPropertyCriterionExists newCriteria =
                new ComplexPropertyCriterionExists(
                        domains,
                        new AqlValue(AqlVariableTypeEnum.string, "start"),
                        new SqlTable(from, 100),
                        comparator, new AqlValue(AqlVariableTypeEnum.string, "1"),
                        new SqlTable(to, -1), false);
        assertEquals(newCriteria.toSql(new ArrayList()), expected);
    }
}
