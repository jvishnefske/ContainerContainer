package org.artifactory.storage.db.aql.service.decorator;

import com.google.common.collect.Lists;
import lombok.NonNull;
import org.artifactory.aql.AqlFieldResolver;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlVariable;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.links.TableLink;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.List;

/**
 * @author Tamir Hadad
 */
abstract class SpecialReposDecorator implements DecorationStrategy {

    @Override
    public <T extends AqlRowResult> void decorate(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext decoratorContext) {
        if (aqlQuery.getAqlElements().isEmpty()) {
            return;
        }

        if (shouldSkipDecoration(aqlQuery)) {
            return;
        }

        if (AqlDomainEnum.items.equals(aqlQuery.getDomain())) {
            decorateItemsSearch(aqlQuery);
        } else if (AqlDomainEnum.properties.equals(aqlQuery.getDomain())) {
            decoratePropertiesSearch(aqlQuery);
        }
    }

    <T extends AqlRowResult> boolean shouldSkipDecoration(@NonNull AqlQuery<T> aqlQuery) {
        // Don't decorate in case results already filtered by repo
        return isFilteredByRepoEqual(aqlQuery) &&
                aqlQuery.getUnionSubQueries().stream().allMatch(this::isFilteredByRepoEqual);
    }

    <T extends AqlRowResult> boolean isFilteredByRepoEqual(@NonNull AqlQuery<T> aqlQuery) {
        Clause clauses = ClauseParser.parse(aqlQuery);
        return clauses.isFilteredByRepoEqual();
    }

    <T extends AqlRowResult> void decorateItemsSearch(AqlQuery<T> aqlQuery) {
        // (query) AND (not in special_repo)
        AqlField itemRepo = AqlFieldResolver.resolve(AqlPhysicalFieldEnum.itemRepo);
        AqlVariable specialRepo = AqlFieldResolver.resolve(getRepoName(), AqlVariableTypeEnum.string);
        TableLink nodesTable = AqlTableGraph.tablesLinksMap.get(SqlTableEnum.nodes);
        SimpleCriterion criteria = new SimpleCriterion(Lists.newArrayList(AqlDomainEnum.items), itemRepo,
                nodesTable.getTable(), AqlComparatorEnum.notEquals.getSignature(), specialRepo, nodesTable.getTable(),
                false);
        appendCriteria(aqlQuery, criteria);
        aqlQuery.getUnionSubQueries().forEach(query -> appendCriteria(query, criteria));
    }

    <T extends AqlRowResult> void decoratePropertiesSearch(AqlQuery<T> aqlQuery) {
        // (query) AND (trash.time not exists)
        AqlField key = AqlFieldResolver.resolve(AqlPhysicalFieldEnum.propertyKey);
        AqlVariable trashTime = AqlFieldResolver.resolve(getPropertyName(), AqlVariableTypeEnum.string);
        TableLink propsTable = AqlTableGraph.tablesLinksMap.get(SqlTableEnum.node_props);
        SimplePropertyCriterion criteria = new SimplePropertyCriterion(Lists.newArrayList(AqlDomainEnum.properties),
                key, propsTable.getTable(), AqlComparatorEnum.notEquals.getSignature(), trashTime,
                propsTable.getTable(),
                false);
        appendCriteria(aqlQuery, criteria);
    }

    abstract String getRepoName();

    abstract String getPropertyName();

    // Wrap query with additional criteria: '( QUERY ) AND CRITERIA'
    private static void appendCriteria(AqlQuery<? extends AqlRowResult> aqlQuery, Criterion criteria) {
        List<AqlQueryElement> aqlElements = aqlQuery.getAqlElements();
        aqlElements.add(0, AqlAdapter.open);
        aqlElements.add(AqlAdapter.close);
        aqlElements.add(AqlAdapter.and);
        aqlElements.add(criteria);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
