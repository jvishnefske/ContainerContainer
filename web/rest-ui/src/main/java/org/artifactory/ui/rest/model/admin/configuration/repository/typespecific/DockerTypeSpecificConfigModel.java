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
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.util.AolUtils;

import java.util.List;

import static java.util.Optional.ofNullable;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_API_VER;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_BLOCK_PUSHING_SCHEMA1;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_TAG_RETENTION;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_VIRTUAL_CACHE_FOREIGN_LAYERS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_VIRTUAL_RESOLVE_TAGS_BY_TIMESTAMP;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_EXTERNAL_DEPENDENCIES_PATTERN;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_MAX_UNIQUE_TAGS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_TOKEN_AUTH;

/**
 * @author Dan Feldman
 */
@Getter
@Setter
public class DockerTypeSpecificConfigModel implements TypeSpecificConfigModel {

    //local
    protected DockerApiVersion dockerApiVersion = DEFAULT_DOCKER_API_VER;
    protected int maxUniqueTags = DEFAULT_MAX_UNIQUE_TAGS;
    private Boolean blockPushingSchema1 = DEFAULT_DOCKER_BLOCK_PUSHING_SCHEMA1;
    private int dockerTagRetention = DEFAULT_DOCKER_TAG_RETENTION;

    //remote
    protected Boolean enableTokenAuthentication = DEFAULT_TOKEN_AUTH;
    protected Boolean listRemoteFolderItems = DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
    private Boolean enableForeignLayersCaching = false;
    private List<String> externalPatterns = Lists.newArrayList(DEFAULT_EXTERNAL_DEPENDENCIES_PATTERN);
    private Long retrievalCachePeriodSecs = DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD;
    private String dockerProjectId = StringUtils.EMPTY;

    //virtual
    private Boolean resolveDockerTagsByTimestamp = DEFAULT_DOCKER_VIRTUAL_RESOLVE_TAGS_BY_TIMESTAMP;
    private Boolean cachingLocalForeignLayersEnabled = DEFAULT_DOCKER_VIRTUAL_CACHE_FOREIGN_LAYERS;

    @Override
    public Boolean isEnableTokenAuthentication() {
        return enableTokenAuthentication;
    }

    @Override
    public void validateLocalTypeSpecific() throws RepoConfigException {
        setDockerApiVersion(ofNullable(getDockerApiVersion()).orElse(DEFAULT_DOCKER_API_VER));
        if (AolUtils.skipDockerDns() && dockerApiVersion == DockerApiVersion.V1) {
            throw new RepoConfigException("Docker V1 is not supported.", SC_BAD_REQUEST);
        }
        setMaxUniqueTags(getMaxUniqueTags());
        setDockerTagRetention(getDockerTagRetention());
    }

    @Override
    public void validateRemoteTypeSpecific() {
        setEnableTokenAuthentication(ofNullable(getEnableTokenAuthentication()).orElse(DEFAULT_TOKEN_AUTH));
        setRetrievalCachePeriodSecs(ofNullable(
                getRetrievalCachePeriodSecs()).orElse(DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD));
        setListRemoteFolderItems(ofNullable(getListRemoteFolderItems())
                .orElse(DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE));
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) {
        setResolveDockerTagsByTimestamp(ofNullable(getResolveDockerTagsByTimestamp())
                .orElse(DEFAULT_DOCKER_VIRTUAL_RESOLVE_TAGS_BY_TIMESTAMP));
        setCachingLocalForeignLayersEnabled(ofNullable(getCachingLocalForeignLayersEnabled())
                .orElse(DEFAULT_DOCKER_VIRTUAL_CACHE_FOREIGN_LAYERS));
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Docker;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    public boolean isBlockPushingSchema1() {
        return DockerApiVersion.V2 == this.getDockerApiVersion() && this.getBlockPushingSchema1();
    }
}
