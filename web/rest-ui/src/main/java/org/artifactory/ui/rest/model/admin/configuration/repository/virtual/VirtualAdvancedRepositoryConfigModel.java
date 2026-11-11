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

package org.artifactory.ui.rest.model.admin.configuration.repository.virtual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.PropertySetNameModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.AdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.CdnRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.DownloadRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.reverseProxy.ReverseProxyRepoModel;

import jakarta.annotation.Nullable;
import java.util.List;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_SIGNED_URL_TTL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_CAN_RETRIEVE_FROM_REMOTE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_HIDE_UNAUTHORIZED_RESOURCES;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@JsonIgnoreProperties({"propertySets", "blackedOut", "allowContentBrowsing"})
public class VirtualAdvancedRepositoryConfigModel implements AdvancedRepositoryConfigModel {

    private Boolean retrieveRemoteArtifacts = DEFAULT_VIRTUAL_CAN_RETRIEVE_FROM_REMOTE;
    private ReverseProxyRepoModel reverseProxy;
    private Boolean hideUnauthorizedResources = DEFAULT_VIRTUAL_HIDE_UNAUTHORIZED_RESOURCES;
    private long signedUrlTtl = DEFAULT_SIGNED_URL_TTL;

    public Boolean getRetrieveRemoteArtifacts() {
        return retrieveRemoteArtifacts;
    }

    public void setRetrieveRemoteArtifacts(Boolean retrieveRemoteArtifacts) {
        this.retrieveRemoteArtifacts = retrieveRemoteArtifacts;
    }

    @Override
    public List<PropertySetNameModel> getPropertySets() {
        return null;
    }

    @Override
    public void setPropertySets(List<PropertySetNameModel> propertySets) {

    }

    @Override
    public Boolean isBlackedOut() {
        return null;
    }

    @Override
    public void setBlackedOut(Boolean blackedOut) {

    }

    @Override
    public Boolean getAllowContentBrowsing() {
        return null;
    }

    @Override
    public void setAllowContentBrowsing(Boolean allowContentBrowsing) {

    }

    @Override
    public ReverseProxyRepoModel getReverseProxy() {
        return reverseProxy;
    }

    @Override
    public void setReverseProxy(ReverseProxyRepoModel reverseProxy) {
        this.reverseProxy = reverseProxy;
    }

    @Override
    public DownloadRedirectRepoConfigModel getDownloadRedirectConfig() {
        return null;
    }

    @Override
    public void setDownloadRedirectConfig(DownloadRedirectRepoConfigModel downloadRedirectRepoConfig) {
    }

    @Nullable
    @Override
    public CdnRedirectRepoConfigModel getCdnRedirectRepoConfig() {
        return null;
    }

    @Override
    public void setCdnRedirectRepoConfig(@Nullable CdnRedirectRepoConfigModel cdnRedirectRepoConfig) {
    }

    @Override
    public Boolean getPriorityResolution() {
        return Boolean.FALSE; // virtual repositories does not support priority resolutions
    }

    @Override
    public void setPriorityResolution(Boolean priorityResolution) {
        // nop
    }

    @Override
    public long getSignedUrlTtl() {
        return this.signedUrlTtl;
    }

    @Override
    public void setSignedUrlTtl(long signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }

    public Boolean getHideUnauthorizedResources() {
        return hideUnauthorizedResources;
    }

    public void setHideUnauthorizedResources(Boolean hideUnauthorizedResources) {
        this.hideUnauthorizedResources = hideUnauthorizedResources;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
