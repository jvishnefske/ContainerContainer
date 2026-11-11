package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;

/**
 * Represent single query clause.
 * Keep original {@code Criterion} of AQL query.
 */
class ClauseItem implements Clause {

    private final Criterion criteria;

    ClauseItem(Criterion criteria) {
        this.criteria = criteria;
    }

    @Override
    public boolean isFilteredByRepoEqual() {
        if (criteria == null || !(criteria.getVariable1() instanceof AqlField)) {
            return false;
        }

        AqlField field = (AqlField) criteria.getVariable1();
        return field.getFieldEnum() == AqlPhysicalFieldEnum.itemRepo
                && AqlComparatorEnum.equals.equals(AqlComparatorEnum.value(criteria.getComparatorName()));
    }
}
