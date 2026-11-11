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

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_FORCE_CONAN_AUTH;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

/**
 * @author Yinon Avraham
 */
@Getter
@Setter
public class ConanTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    private Boolean forceConanAuthentication = DEFAULT_FORCE_CONAN_AUTH;
    private Boolean listRemoteFolderItems = DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
    private Long virtualRetrievalCachePeriodSecs = DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD;

    @Override
    public RepoType getRepoType() {
        return RepoType.Conan;
    }

    @Override
    public void validateRemoteTypeSpecific() {
        setListRemoteFolderItems(ofNullable(getListRemoteFolderItems())
                .orElse(DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE));
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) {
        setVirtualRetrievalCachePeriodSecs(ofNullable(getVirtualRetrievalCachePeriodSecs())
                .orElse(DEFAULT_VIRTUAL_RETRIEVAL_CACHE_PERIOD));
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
