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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.RepositoryReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;

import java.io.IOException;
import java.util.List;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@JsonTypeName("virtualRepoConfig")
@Data
public class VirtualRepositoryConfigModel implements RepositoryConfigModel<VirtualBasicRepositoryConfigModel,
        VirtualAdvancedRepositoryConfigModel, RepositoryReplicationConfigModel> {

    private GeneralRepositoryConfigModel general;
    private VirtualBasicRepositoryConfigModel basic;
    private VirtualAdvancedRepositoryConfigModel advanced;
    private TypeSpecificConfigModel typeSpecific;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private FederationConfigModel federation;

    @Override
    public List<RepositoryReplicationConfigModel> getReplications() {
        return null;
    }

    @Override
    public void setReplications(List<RepositoryReplicationConfigModel> replication) {
    }

    @Override
    public ReverseProxyDescriptor getReverseProxyDescriptor(RepoConfig repoConfig,
            RepoConfigDescriptorBuilder builder) {
        return builder.buildReverseProxyDescriptor(this.advanced, repoConfig);
    }

    @Override
    public VirtualRepoConfig toRepoConfig(RepoConfigValidator validator, RepoConfigDescriptorBuilder builder,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        validator.validateVirtual(this);
        return builder.buildVirtualRepoConfig(this);
    }

    @Override
    public RepositoryConfigModel<VirtualBasicRepositoryConfigModel, VirtualAdvancedRepositoryConfigModel,
            RepositoryReplicationConfigModel> fromRepoConfig(
            RepoConfigModelBuilder builder, RepoConfig repoConfig) {
        builder.populateVirtualRepositoryConfigValuesToModel((VirtualRepoConfig) repoConfig, this);
        return this;
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator,
            MutableCentralConfigDescriptor configDescriptor,
            RepositoryService repositoryService, boolean fedAttrOptIn) throws RepoConfigException {
        return creator.handleVirtual(this, configDescriptor, repositoryService, fedAttrOptIn);
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator,
            MutableCentralConfigDescriptor configDescriptor,
            RepositoryService repositoryService) throws RepoConfigException {
        return createRepoConfig(creator, configDescriptor, repositoryService, false);
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater, boolean fedAttrOptIn) throws RepoConfigException {
        updater.handleVirtual(this, fedAttrOptIn);
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater) throws IOException, RepoConfigException {
        updateRepo(updater, false);
    }

    @Override
    public String getRepoType() {
        return RepoDetailsType.VIRTUAL_REPO;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
