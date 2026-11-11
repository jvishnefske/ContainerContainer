package org.artifactory.storage.db.aql.service.optimizer;

import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterionExists;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterionExists;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;
import static org.artifactory.aql.model.AqlComparatorEnum.equals;
import static org.artifactory.aql.model.AqlComparatorEnum.matches;
import static org.artifactory.aql.model.AqlComparatorEnum.notEquals;
import static org.artifactory.aql.model.AqlComparatorEnum.notMatches;

public class FromLeftOuterJoinToExists extends OptimizationStrategy {

    private static final Pattern pattern = compile("[pkv]");
    private static final EnumSet<AqlComparatorEnum> COMPARATORS = EnumSet.of(matches, equals, notMatches, notEquals);

    @Override
    public <T extends AqlRowResult> void optimize(AqlQuery<T> aqlQuery, String transformation) {
        if (shouldSkipOptimization(aqlQuery)) {
            return;
        }
        // Try to find matching sub strings
        Matcher matcher = pattern.matcher(transformation);
        // Each match represent sub query that can be optimized
        while (matcher.find()) {
            // Get sub query bounds
            int start = matcher.start();
            int end = matcher.end();
            for (int i = start; i < end; i++) {
                if (transformation.charAt(i) == 'p') {
                    handleComplexPropertyCriterion(aqlQuery, i);
                }
                if (transformation.charAt(i) == 'k' || transformation.charAt(i) == 'v') {
                    handleSimplePropertyCriterion(aqlQuery, i);
                }
            }
        }
    }

    private <T extends AqlRowResult> void handleComplexPropertyCriterion(AqlQuery<T> aqlQuery, int i) {
        ComplexPropertyCriterion criteria = (ComplexPropertyCriterion) aqlQuery.getAqlElements().get(i);
        if (!(criteria.isMspOperator() || criteria.getTable1().getId() == -1)) {
            AqlComparatorEnum comparator = AqlComparatorEnum.value(criteria.getComparatorName());
            if (COMPARATORS.contains(comparator)) {
                Criterion newCriteria = buildNewComplexPropertyCriterion(criteria);
                aqlQuery.getAqlElements().remove(i);
                aqlQuery.getAqlElements().add(i, newCriteria);
            }
        }
    }

    private Criterion buildNewComplexPropertyCriterion(ComplexPropertyCriterion criteria) {
        return new ComplexPropertyCriterionExists(criteria.getSubDomains(),
                criteria.getVariable1(), criteria.getTable1(),
                criteria.getComparatorName(),
                criteria.getVariable2(),
                criteria.getTable2(), criteria.isMspOperator());
    }

    private <T extends AqlRowResult> void handleSimplePropertyCriterion(AqlQuery<T> aqlQuery, int i) {
        Criterion criterion = (Criterion) aqlQuery.getAqlElements().get(i);
        if (!criterion.isMspOperator() && (criterion instanceof SimplePropertyCriterion)) {
            AqlComparatorEnum comparator = AqlComparatorEnum.value(criterion.getComparatorName());
            if (COMPARATORS.contains(comparator)) {
                Criterion newCriteria = buildNewSimplePropertyCriterion(criterion);
                aqlQuery.getAqlElements().remove(i);
                aqlQuery.getAqlElements().add(i, newCriteria);
            }
        }
    }

    private Criterion buildNewSimplePropertyCriterion(Criterion criterion) {
        return new SimplePropertyCriterionExists(criterion.getSubDomains(),
                criterion.getVariable1(), criterion.getTable1(),
                criterion.getComparatorName(),
                criterion.getVariable2(),
                criterion.getTable2(), criterion.isMspOperator());
    }

}
