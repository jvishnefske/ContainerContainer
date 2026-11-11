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

package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.List;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_EXTERNAL_DEPENDENCIES_PATTERN;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_USE_NAMESPACES;

/**
 * @author Nadav Yogev
 */
@Getter
@Setter
public class HelmTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    private String chartsBaseUrl;
    private Long virtualRetrievalCachePeriodSecs = DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;
    private boolean listRemoteFolderItems = DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
    private Boolean useNamespaces = DEFAULT_VIRTUAL_USE_NAMESPACES;
    private Boolean enableExternalDependencies = false;
    private List<String> externalPatterns = Lists.newArrayList(DEFAULT_EXTERNAL_DEPENDENCIES_PATTERN);
    private boolean forceMetadataNameVersion = false;
    private boolean forceNonDuplicateChart = false;


    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) {
        setVirtualRetrievalCachePeriodSecs(ofNullable(getVirtualRetrievalCachePeriodSecs())
                .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
        setListRemoteFolderItems(isListRemoteFolderItems());
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Helm;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
