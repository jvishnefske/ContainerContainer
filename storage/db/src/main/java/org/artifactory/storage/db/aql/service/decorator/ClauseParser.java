package org.artifactory.storage.db.aql.service.decorator;

import jakarta.validation.constraints.NotNull;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OpenParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;

import java.util.Iterator;

/**
 * Build clauses tree from flat AQL query model.
 * Result tree can be used for checks and validations.
 *
 * @see Clause
 * @see ClauseGroup
 */
final class ClauseParser {

    private ClauseParser() {}

    public static Clause parse(@NotNull AqlQuery<? extends AqlRowResult> aqlQuery) {
        ClauseGroup root = new ClauseGroup();
        enter(root, aqlQuery.getAqlElements().iterator());
        return root;
    }

    private static void enter(final ClauseGroup currentClause, Iterator<AqlQueryElement> it) {
        while (it.hasNext()) {
            AqlQueryElement aqlElement = it.next();
            if (aqlElement instanceof OpenParenthesisAqlElement) {
                enter(onStart(currentClause), it);
            } else if (aqlElement instanceof Criterion) {
                onCriteria(currentClause, (Criterion) aqlElement);
            } else if (aqlElement instanceof OperatorQueryElement) {
                onOperator(currentClause, (OperatorQueryElement) aqlElement);
            } else if (aqlElement instanceof CloseParenthesisAqlElement) {
                return;
            }
        }
    }

    private static ClauseGroup onStart(ClauseGroup currentClause) {
        ClauseGroup nestedGroup = new ClauseGroup();
        currentClause.addChild(nestedGroup);
        return nestedGroup;
    }

    private static void onCriteria(ClauseGroup currentClause, Criterion criteria) {
        ClauseItem nestedItem = new ClauseItem(criteria);
        currentClause.addChild(nestedItem);
    }

    private static void onOperator(ClauseGroup currentClause, OperatorQueryElement op) {
        currentClause.addJoinOperator(op.getOperatorEnum());
    }
}
