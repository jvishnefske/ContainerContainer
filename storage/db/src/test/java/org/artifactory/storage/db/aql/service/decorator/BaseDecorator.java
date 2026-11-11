package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OpenParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.Collections;
import java.util.List;

/**
 * @author Tomer Slutsky
 */
public class BaseDecorator {
    protected Criterion createRepoCriteria(AqlComparatorEnum comparator, String repoKey) {
        return new SimpleCriterion(
                Collections.singletonList(AqlDomainEnum.items),
                new AqlField(AqlPhysicalFieldEnum.itemRepo),
                new SqlTable(SqlTableEnum.nodes),
                comparator.getSignature(),
                new AqlValue(AqlVariableTypeEnum.string, repoKey),
                new SqlTable(SqlTableEnum.nodes), false);
    }

    protected Criterion createEqualsPathCriteria(String path) {
        return new SimpleCriterion(
                Collections.singletonList(AqlDomainEnum.items),
                new AqlField(AqlPhysicalFieldEnum.itemPath),
                new SqlTable(SqlTableEnum.nodes),
                AqlComparatorEnum.equals.getSignature(),
                new AqlValue(AqlVariableTypeEnum.string, path),
                new SqlTable(SqlTableEnum.nodes), false);
    }

    protected String toString(List<AqlQueryElement> aqlQueryElements) {
        StringBuilder sb = new StringBuilder();
        for (AqlQueryElement element : aqlQueryElements) {
            if (element instanceof Criterion) {
                Criterion criterion = (Criterion) element;
                criterion.getSubDomains().forEach(dom -> sb.append(dom.signature).append("."));
                sb.append(((AqlField) criterion.getVariable1()).getFieldEnum().getSignature());
                sb.append(" ");
                sb.append(criterion.getComparatorName());
                sb.append(" ");
                sb.append(((AqlValue) criterion.getVariable2()).toObject());
                sb.append(" ");
            } else if (element instanceof OpenParenthesisAqlElement) {
                sb.append("( ");
            } else if (element instanceof CloseParenthesisAqlElement) {
                sb.append(") ");
            } else if (element instanceof OperatorQueryElement) {
                sb.append(((OperatorQueryElement) element).getOperatorEnum().signature);
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
