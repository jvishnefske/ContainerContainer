package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.aql.model.AqlOperatorEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent group of clauses. Example:
 * <pre>
 * (C1 AND C2 OR C3) => ClauseGroup(
 *                          joinWith = OR,
 *                          children = [ ClauseItem(C1), ClauseItem(C2), ClauseItem(C3) ]
 *                      )
 * ((C1))            => ClauseGroup(
 *                          joinWith = AND,
 *                          children = [ ClauseGroup(joinWith = AND, children = [ ClauseItem(C1) ]) ]
 *                      )
 * </pre>
 *
 * @see ClauseItem
 */
class ClauseGroup implements Clause {

    // AND is default value for groups which just wrap other clauses.
    private AqlOperatorEnum joinWith = AqlOperatorEnum.and;
    private final List<Clause> children = new ArrayList<>();

    void addJoinOperator(AqlOperatorEnum operator) {
        // saves most prioritized state without collecting all operators
        if (operator == AqlOperatorEnum.or) {
            joinWith = operator;
        }
    }

    void addChild(Clause clause) {
        children.add(clause);
    }

    @Override
    public boolean isFilteredByRepoEqual() {
        switch (joinWith) {
            case and:
                return children.stream().anyMatch(Clause::isFilteredByRepoEqual);
            case or:
                return children.stream().allMatch(Clause::isFilteredByRepoEqual);
            default:
                return false;
        }
    }
}
