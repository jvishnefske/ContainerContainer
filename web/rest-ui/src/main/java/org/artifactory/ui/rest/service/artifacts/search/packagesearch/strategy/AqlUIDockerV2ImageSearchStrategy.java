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

package org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy;

import com.google.common.collect.Lists;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;

import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;
import static org.artifactory.mime.DockerNaming.MANIFEST_FILENAME;
import static org.artifactory.mime.DockerNaming.MANIFEST_LIST_FILENAME;

/**
 * @author Dan Feldman
 */
public class AqlUIDockerV2ImageSearchStrategy extends AqlUIPropertySearchStrategy {
    public static final String PREFIX = "library/";

    public AqlUIDockerV2ImageSearchStrategy(String key, AqlDomainEnum[] subdomainPath) {
        super(key, subdomainPath);
    }

    @Override
    public AqlBase.OrClause<?> toQuery() {
        AqlBase.OrClause<?> query = or();
        values.forEach(value -> {
            AqlBase.AndClause and = and();
            AqlBase.OrClause manifestClause = AqlApiItem.or();
            AqlBase.OrClause propOr = or();
            //Don't search for docker v1 tags that have the same property on a different json descriptor
            manifestClause.append(
                    new AqlBase.CriteriaClause(AqlPhysicalFieldEnum.itemName, Lists.newArrayList(AqlDomainEnum.items),
                            AqlComparatorEnum.equals, MANIFEST_FILENAME));
            manifestClause.append(
                    new AqlBase.CriteriaClause(AqlPhysicalFieldEnum.itemName, Lists.newArrayList(AqlDomainEnum.items),
                            AqlComparatorEnum.equals, MANIFEST_LIST_FILENAME));
            and.append(manifestClause);
            propOr.append(new AqlBase.PropertyCriteriaClause<>(key, AqlComparatorEnum.matches, value, subdomainPath));
            propOr.append(
                    new AqlBase.PropertyCriteriaClause<>(key, AqlComparatorEnum.matches, PREFIX + value,
                            subdomainPath));
            and.append(propOr);
            query.append(and);
        });
        return query;
    }

    @Override
    public boolean includePropsInResult() {
        return true;
    }
}