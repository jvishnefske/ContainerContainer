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
import com.google.common.collect.Sets;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlOverloadSettings;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlSortTypeEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SortDetails;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Shay Yaakov
 */
public class DefaultSortDecorator implements DecorationStrategy {

    @Override
    public <T extends AqlRowResult> void decorate(AqlQuery<T> aqlQuery, AqlQueryDecoratorContext decoratorContext) {
        useDefaultSortIfNeeded(aqlQuery, decoratorContext);
    }

    private <T extends AqlRowResult> void useDefaultSortIfNeeded(AqlQuery<T> aqlQuery,
            AqlQueryDecoratorContext decoratorContext) {
        if (isMultiDomainResult(aqlQuery) && !aqlQuery.getTransitiveMetadata().isTransitive()
                && !isColdSearch(decoratorContext)) {
            AqlDomainEnum mainDomain = aqlQuery.getDomain();
            List<AqlPhysicalFieldEnum> fieldEnums = resolveIdFieldFromDomain(aqlQuery);
            SortDetails sort = new SortDetails();
            sort.setSortType(AqlSortTypeEnum.asc);
            Set<DomainSensitiveField> set = Sets.newHashSet(aqlQuery.getResultFields());
            if (fieldEnums != null) {
                for (AqlPhysicalFieldEnum fieldEnum : fieldEnums) {
                    if (!sort.getFields().contains(fieldEnum)) {
                        sort.addField(fieldEnum);
                    }
                    boolean alreadyExist = false;
                    for (DomainSensitiveField domainSensitiveField : set) {
                        if (domainSensitiveField.getField() == fieldEnum) {
                            alreadyExist = true;
                        }
                    }
                    if (!alreadyExist) {
                        set.add(new DomainSensitiveField(fieldEnum, Lists.newArrayList(mainDomain)));
                    }
                }
            }
            aqlQuery.setSort(sort);
            aqlQuery.getResultFields().clear();
            aqlQuery.getResultFields().addAll(set);
        }
    }

    private <T extends AqlRowResult> boolean isMultiDomainResult(AqlQuery<T> aqlQuery) {
        List<DomainSensitiveField> resultFields = aqlQuery.getResultFields();
        Set<AqlDomainEnum> distinctDomains = resultFields.stream()
                .map(resultField -> resultField.getField().getDomain())
                .collect(Collectors.toSet());
        // if more than one domain and at list one domain is not injective with items(nodes table)
        return distinctDomains.size() > 1 && distinctDomains.stream().anyMatch(a -> !a.isInjective());
    }

    private <T extends AqlRowResult> List<AqlPhysicalFieldEnum> resolveIdFieldFromDomain(AqlQuery<T> aqlQuery) {
        AqlDomainEnum domain = aqlQuery.getDomain();
        switch (domain) {
            case items:
                return Lists.newArrayList(AqlPhysicalFieldEnum.itemId);
            case properties:
                return Lists.newArrayList(AqlPhysicalFieldEnum.propertyId);
            case statistics:
                return Lists.newArrayList(AqlPhysicalFieldEnum.statId);
            case artifacts:
                return Lists.newArrayList(AqlPhysicalFieldEnum.buildArtifactId);
            case dependencies:
                return Lists.newArrayList(AqlPhysicalFieldEnum.buildDependencyId);
            case modules:
                return Lists.newArrayList(AqlPhysicalFieldEnum.moduleId);
            case moduleProperties:
                return Lists.newArrayList(AqlPhysicalFieldEnum.modulePropertyId);
            case builds, buildPromotions:
                return Lists.newArrayList(AqlPhysicalFieldEnum.buildId);
            case buildProperties:
                return Lists.newArrayList(AqlPhysicalFieldEnum.buildPropertyId);
            default:
                return null;
        }
    }

    private boolean isColdSearch(AqlQueryDecoratorContext decoratorContext) {
        return Optional.ofNullable(decoratorContext)
                .map(AqlQueryDecoratorContext::getAqlOverloadSettings)
                .map(AqlOverloadSettings::getStreamFunc)
                .isPresent();
    }

}
