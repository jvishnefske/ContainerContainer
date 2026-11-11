package org.artifactory.storage.db.aql.sql.builder.query.aql;

import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.aql.model.AqlComparatorEnum.equals;
import static org.artifactory.aql.model.AqlComparatorEnum.equalsIgnoreCase;
import static org.artifactory.aql.model.AqlComparatorEnum.equalsValueIgnoreCase;
import static org.artifactory.aql.model.AqlComparatorEnum.greater;
import static org.artifactory.aql.model.AqlComparatorEnum.greaterEquals;
import static org.artifactory.aql.model.AqlComparatorEnum.less;
import static org.artifactory.aql.model.AqlComparatorEnum.lessEquals;
import static org.artifactory.aql.model.AqlComparatorEnum.matches;
import static org.artifactory.aql.model.AqlComparatorEnum.matchesIgnoreCase;
import static org.artifactory.aql.model.AqlComparatorEnum.notEquals;
import static org.artifactory.aql.model.AqlComparatorEnum.notMatches;
import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.aql.model.AqlDomainEnum.properties;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_props;
import static org.testng.Assert.assertEquals;

/**
 * Verifies the SQL rendered by {@link ComplexPropertyCriterion} for every comparator, in particular the
 * {@code equalsValueIgnoreCase} ({@code $eqvic}) case which keeps {@code prop_key} exact (sargable) while
 * folding {@code prop_value} with {@code LOWER(...)}.
 *
 * @author Arko Jyoti De
 */
class ComplexPropertyCriterionTest {

    private static Stream<Arguments> provide() {
        return Stream.of(
                Arguments.of("( np100.prop_key = ? and  np100.prop_value = ?)",
                        equals.getSignature()),
                Arguments.of("( LOWER(np100.prop_key) = ? and  LOWER(np100.prop_value) = ?)",
                        equalsIgnoreCase.getSignature()),
                // Exact key, case-folded value: prop_key stays sargable, only prop_value is lowered.
                Arguments.of("( np100.prop_key = ? and  LOWER(np100.prop_value) = ?)",
                        equalsValueIgnoreCase.getSignature()),
                Arguments.of("( np100.prop_key = ? and  np100.prop_value like ?)",
                        matches.getSignature()),
                Arguments.of("( np100.prop_key = ? and  LOWER(np100.prop_value) like ?)",
                        matchesIgnoreCase.getSignature()),
                Arguments.of("( np100.prop_key = ? and  np100.prop_value < ?)",
                        less.getSignature()),
                Arguments.of("( np100.prop_key = ? and  np100.prop_value > ?)",
                        greater.getSignature()),
                Arguments.of("( np100.prop_key = ? and  np100.prop_value >= ?)",
                        greaterEquals.getSignature()),
                Arguments.of("( np100.prop_key = ? and  np100.prop_value <= ?)",
                        lessEquals.getSignature()),
                Arguments.of("(np100.node_id is null or not exists (select 1 from node_props where "
                                + "np100.node_id = node_id and prop_key = ? and prop_value = ?))",
                        notEquals.getSignature()),
                Arguments.of("(np100.node_id is null or not exists (select 1 from node_props where "
                                + "np100.node_id = node_id and prop_key = ? and prop_value like  ?))",
                        notMatches.getSignature())
        );
    }

    @ParameterizedTest
    @MethodSource("provide")
    void test(String expected, String comparator) {
        ComplexPropertyCriterion criterion = new ComplexPropertyCriterion(
                List.of(items, properties),
                new AqlValue(AqlVariableTypeEnum.string, "start"),
                new SqlTable(node_props, 100),
                comparator, new AqlValue(AqlVariableTypeEnum.string, "1"),
                new SqlTable(node_props, -1), false);
        assertEquals(criterion.toSql(new ArrayList()), expected);
    }
}
