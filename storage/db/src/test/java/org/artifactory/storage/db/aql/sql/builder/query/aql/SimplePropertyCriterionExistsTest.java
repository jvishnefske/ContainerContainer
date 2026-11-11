package org.artifactory.storage.db.aql.sql.builder.query.aql;

import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
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
import static org.artifactory.aql.model.AqlComparatorEnum.matches;
import static org.artifactory.aql.model.AqlComparatorEnum.matchesIgnoreCase;
import static org.artifactory.aql.model.AqlComparatorEnum.notEquals;
import static org.artifactory.aql.model.AqlComparatorEnum.notMatches;
import static org.artifactory.aql.model.AqlDomainEnum.buildProperties;
import static org.artifactory.aql.model.AqlDomainEnum.builds;
import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.aql.model.AqlDomainEnum.modules;
import static org.artifactory.aql.model.AqlDomainEnum.properties;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.propertyKey;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_props;
import static org.testng.Assert.assertEquals;

class SimplePropertyCriterionExistsTest {

    private static Stream<Arguments> provide() {
        return Stream.of(
                Arguments.of(List.of(items, properties), node_props, node_props, " ( not exists ( select 1 " +
                        "from node_props npsub " +
                        "where npsub.node_id = n.node_id and npsub.prop_key like ? ))", notMatches.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props, " ( not exists ( select 1 " +
                        "from node_props npsub " +
                        "where npsub.node_id = n.node_id and npsub.prop_key = ? ))", notEquals.getSignature()),
                Arguments.of(List.of(modules, builds,
                        buildProperties), build_props, build_props, " ( not exists ( select 1 " +
                        "from build_props bpsub " +
                        "where bpsub.build_id = b.build_id and bpsub.prop_key = ? ))", notEquals.getSignature()),
                Arguments.of(List.of(modules, builds,
                        buildProperties), build_props, build_props, " ( not exists ( select 1 " +
                        "from build_props bpsub " +
                        "where bpsub.build_id = b.build_id and bpsub.prop_key like ? ))", notMatches.getSignature()),
                // Positive
                Arguments.of(List.of(items, properties), node_props, node_props, " ( exists ( select 1 " +
                        "from node_props npsub " +
                        "where npsub.node_id = n.node_id and npsub.prop_key like ? ))", matches.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props, " ( exists ( select 1 " +
                                "from node_props npsub " +
                                "where npsub.node_id = n.node_id and LOWER(npsub.prop_key) like ? ))",
                        matchesIgnoreCase.getSignature()),
                Arguments.of(List.of(items, properties), node_props, node_props, " ( exists ( select 1 " +
                        "from node_props npsub " +
                        "where npsub.node_id = n.node_id and npsub.prop_key = ? ))", equals.getSignature()),
                Arguments.of(List.of(modules, builds,
                        buildProperties), build_props, build_props, " ( exists ( select 1 " +
                        "from build_props bpsub " +
                        "where bpsub.build_id = b.build_id and bpsub.prop_key = ? ))", equals.getSignature()),
                Arguments.of(List.of(modules, builds,
                        buildProperties), build_props, build_props, " ( exists ( select 1 " +
                        "from build_props bpsub " +
                        "where bpsub.build_id = b.build_id and bpsub.prop_key like ? ))", matches.getSignature())
        );
    }

    @ParameterizedTest
    @MethodSource("provide")
    void test(List<AqlDomainEnum> domains, SqlTableEnum from,
            SqlTableEnum to, String expected, String comparator) {
        Criterion newCriterion =
                new SimplePropertyCriterionExists(domains,
                        new AqlField(propertyKey), new SqlTable(from, 100),
                        comparator,
                        new AqlValue(AqlVariableTypeEnum.string, "value1"),
                        new SqlTable(to, 100), false);
        ArrayList param = new ArrayList();
        param.add("value1");
        assertEquals(newCriterion.toSql(param), expected);
    }
}
