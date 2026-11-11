/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.aql.service.decorator;

import com.google.common.collect.Lists;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.TransitiveMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

/**
 * @author Yinon Avraham
 */
public class VirtualRepoCriteriaDecorator implements DecorationStrategy {
    private static final Logger log = LoggerFactory.getLogger(VirtualRepoCriteriaDecorator.class);

    @Override
    public <T extends AqlRowResult> void decorate(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext decoratorContext) {
        List<AqlQueryElement> aqlQueryElements = aqlQuery.getAqlElements();
        this.checkTransitive(aqlQuery, decoratorContext);
        int index = 0;
        while ((index = findNextFieldCriteria(index, AqlPhysicalFieldEnum.itemRepo, aqlQueryElements)) >= 0) {
            index = handleRepoCriteria(index, aqlQueryElements, decoratorContext);
        }
    }

    private <T extends AqlRowResult> void checkTransitive(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext ctx) {
        log.debug("Checking if the received query is transitive");
        if (aqlQuery.getTransitiveMetadata().isTransitive()) {
            aqlQuery.getAqlElements()
                    .stream()
                    .filter(curr -> curr instanceof SimpleCriterion)
                    .map(curr -> (Criterion) curr)
                    .filter(curr -> ((AqlField) curr.getVariable1()).getFieldEnum().getSignature().equals("repo"))
                    .findFirst()
                    .ifPresent(criterion -> this.handleTransitive(criterion, aqlQuery, ctx));
        }
    }

    private <T extends AqlRowResult> void handleTransitive(Criterion criterion, AqlQuery<T> aqlQuery,
            AqlQueryDecoratorContext ctx) {
        String repoKey = ((AqlValue) criterion.getVariable2()).getValue();
        log.debug("The current query is transitive, with repoKey: {}", repoKey);
        TransitiveMetadata details = aqlQuery.getTransitiveMetadata();

        // If the request is of transitive type, and contains remote repos that are valid
        // for a transitive query we update the aqlQuery queryType
        log.debug("Checking if the received virtual repo: {}, contains transitive valid repos", repoKey);
        if (ctx.getRepoProvider().virtualRepoContainsTransitiveValidRepos(repoKey)) {
            log.debug("The received virtual repo contains transitive valid repos, setting the base repo name: {}",
                    repoKey);
            details.setBaseRepoName(repoKey);

            // Reduce the depth of the transitive search by 1
            log.debug("Reducing the transitive depth by 1, the current depth is: {}", details.getTransitiveDepth());
            details.setTransitiveDepth(details.getTransitiveDepth() - 1);
            log.debug("Changing the queryRepoType, previous type: {}, new type: {}",
                    aqlQuery.getQueryRepoType().getName(), AqlQueryRepoType.virtual.getName());
            aqlQuery.setQueryRepoType(AqlQueryRepoType.virtual);
        }
    }

    private int handleRepoCriteria(int index, List<AqlQueryElement> aqlQueryElements,
            AqlQueryDecoratorContext decoratorContext) {
        Criterion criterion = (Criterion) aqlQueryElements.get(index);
        AqlComparatorEnum comparator = AqlComparatorEnum.value(criterion.getComparatorName());
        if (comparator != null) {
            switch (comparator) {
                case equals:
                case matches:
                case greater:
                case greaterEquals:
                case less:
                case lessEquals:
                    return handlePositive(index, aqlQueryElements, criterion, comparator, decoratorContext);
                case notEquals:
                case notMatches:
                    return handleNegative(index, aqlQueryElements, criterion, comparator, decoratorContext);
            }
        }
        throw new IllegalArgumentException("Unexpected repo comparator: " + criterion.getComparatorName());
    }

    private int handlePositive(int index, List<AqlQueryElement> aqlQueryElements, Criterion criterion,
            AqlComparatorEnum comparator, AqlQueryDecoratorContext decoratorContext) {
        String value = (String) ((AqlValue) criterion.getVariable2()).toObject();
        boolean matchesAnyRepo = comparator.equals(AqlComparatorEnum.matches) && "*".equals(value);
        if (matchesAnyRepo) {
            return index + 1; // no need to add OR clauses for each repo in virtual
        }
        Predicate<String> repoKeyPredicate = createRepoKeyPredicate(comparator, value);
        boolean includeOriginalCriteria = comparator != AqlComparatorEnum.equals;
        List<String> repoKeys =
                findVirtualReposAndResolveRepoKeys(repoKeyPredicate, decoratorContext, !includeOriginalCriteria, value);
        return adjustQueryElements(index, aqlQueryElements, criterion, AqlComparatorEnum.equals, repoKeys,
                AqlAdapter.or, includeOriginalCriteria);
    }

    private int handleNegative(int index, List<AqlQueryElement> aqlQueryElements, Criterion criterion,
            AqlComparatorEnum comparator, AqlQueryDecoratorContext decoratorContext) {
        String value = (String) ((AqlValue) criterion.getVariable2()).toObject();
        Predicate<String> repoKeyPredicate = createRepoKeyPredicate(comparator, value);
        boolean includeOriginalCriteria = comparator != AqlComparatorEnum.notEquals;
        List<String> repoKeys =
                findVirtualReposAndResolveRepoKeys(repoKeyPredicate, decoratorContext, !includeOriginalCriteria, value);
        return adjustQueryElements(index, aqlQueryElements, criterion, AqlComparatorEnum.notEquals, repoKeys,
                AqlAdapter.and, includeOriginalCriteria);
    }

    private List<String> findVirtualReposAndResolveRepoKeys(Predicate<String> repoKeyPredicate,
            AqlQueryDecoratorContext decoratorContext, boolean preciseSearch, String repoKey) {
        AqlRepoProvider repoProvider = decoratorContext.getRepoProvider();
        if (preciseSearch) {
            if (!repoProvider.existsVirtualRepoKey(repoKey)) {
                return List.of();
            }
            return repoProvider.getVirtualResolvedLocalFederatedAndCacheRepoKeys(repoKey);
        }
        return repoProvider.getVirtualRepoKeys().stream()
                .filter(repoKeyPredicate)
                .flatMap(
                        virtualRepoKey -> repoProvider.getVirtualResolvedLocalFederatedAndCacheRepoKeys(virtualRepoKey)
                                .stream())
                .distinct()
                .toList();
    }

    private Predicate<String> createRepoKeyPredicate(AqlComparatorEnum comparator, String value) {
        switch (comparator) {
            case equals:
            case notEquals:
                return key -> key.equals(value);
            case matches:
            case notMatches:
                AqlMatchesPattern matchesPattern = AqlMatchesPattern.compile(value);
                return matchesPattern::matches;
            case greater:
                return key -> key.compareTo(value) > 0;
            case greaterEquals:
                return key -> key.compareTo(value) >= 0;
            case less:
                return key -> key.compareTo(value) < 0;
            case lessEquals:
                return key -> key.compareTo(value) <= 0;
        }
        throw new IllegalArgumentException("Unexpected comparator: " + comparator);
    }

    private int adjustQueryElements(int index, List<AqlQueryElement> aqlQueryElements, Criterion criterion,
            AqlComparatorEnum comparator, List<String> repoKeys, OperatorQueryElement operator,
            boolean includeOriginal) {
        if (repoKeys.size() > 0) {
            List<AqlQueryElement> newCompoundCriteria =
                    newCompoundCriteria(criterion, comparator, repoKeys, operator, includeOriginal);
            aqlQueryElements.remove(index);
            aqlQueryElements.addAll(index, newCompoundCriteria);
            return index + newCompoundCriteria.size();
        }
        return index + 1;
    }

    private List<AqlQueryElement> newCompoundCriteria(Criterion criterion, AqlComparatorEnum comparator,
            List<String> repoKeys, OperatorQueryElement operator, boolean includeOriginal) {
        List<AqlQueryElement> newCompoundCriteria = Lists.newArrayList();
        boolean first = true;
        if (includeOriginal) {
            newCompoundCriteria.add(criterion);
            first = false;
        }
        for (String repoKey : repoKeys) {
            if (!first) {
                newCompoundCriteria.add(operator);
            } else {
                first = false;
            }
            newCompoundCriteria.add(newCriteriaFrom(criterion, comparator, repoKey));
        }
        if (newCompoundCriteria.size() > 1) {
            newCompoundCriteria.add(0, AqlAdapter.open);
            newCompoundCriteria.add(AqlAdapter.close);
        }
        return newCompoundCriteria;
    }

    private Criterion newCriteriaFrom(Criterion criterion, AqlComparatorEnum comparatorEnum, String repoKey) {
        AqlValue newValue = new AqlValue(AqlVariableTypeEnum.string, repoKey);
        return new SimpleCriterion(criterion.getSubDomains(), criterion.getVariable1(), criterion.getTable1(),
                comparatorEnum.getSignature(), newValue, criterion.getTable2(), false);
    }
}
