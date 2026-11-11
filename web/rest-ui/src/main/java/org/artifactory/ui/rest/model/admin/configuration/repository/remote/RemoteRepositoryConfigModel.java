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

package org.artifactory.ui.rest.model.admin.configuration.repository.remote;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.remote.RemoteReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;

import java.io.IOException;
import java.util.List;

/**
 * @author Dan Feldman
 * @author Aviad Shikloshi
 */
@Data
@JsonTypeName("remoteRepoConfig")
public class RemoteRepositoryConfigModel implements RepositoryConfigModel<RemoteBasicRepositoryConfigModel,
        RemoteAdvancedRepositoryConfigModel, RemoteReplicationConfigModel> {

    protected GeneralRepositoryConfigModel general;
    protected RemoteBasicRepositoryConfigModel basic;
    protected RemoteAdvancedRepositoryConfigModel advanced;
    protected List<RemoteReplicationConfigModel> replications;
    protected TypeSpecificConfigModel typeSpecific;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private FederationConfigModel federation;


    @Override
    public RemoteRepoConfig toRepoConfig(RepoConfigValidator validator, RepoConfigDescriptorBuilder builder,
            MutableCentralConfigDescriptor configDescriptor)
            throws RepoConfigException {
        validator.validateRemote(this);
        return builder.buildRemoteRepoConfig(this);
    }

    public ReverseProxyDescriptor getReverseProxyDescriptor(RepoConfig repoConfig,
            RepoConfigDescriptorBuilder builder) {
        return builder.buildReverseProxyDescriptor(this.advanced, repoConfig);
    }

    @Override
    public RemoteRepositoryConfigModel fromRepoConfig(RepoConfigModelBuilder builder, RepoConfig repoConfig) {
        builder.populateRemoteRepositoryConfigValuesToModel((RemoteRepoConfig) repoConfig, this);
        return this;
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator,
            MutableCentralConfigDescriptor configDescriptor, RepositoryService repositoryService,
            boolean fedAttrOptIn) throws RepoConfigException {
        return creator.handleRemote(this, configDescriptor, fedAttrOptIn);
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator, MutableCentralConfigDescriptor configDescriptor,
            RepositoryService repositoryService) throws RepoConfigException {
        return createRepoConfig(creator, configDescriptor, repositoryService, false);
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater, boolean fedAttrOptIn)
            throws IOException, RepoConfigException {
        updater.handleRemote(this, fedAttrOptIn);
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper update)
            throws IOException, RepoConfigException {
        updateRepo(update, false);
    }

    @Override
    public String getRepoType() {
        return RepoDetailsType.REMOTE_REPO;
    }

    public RemoteReplicationDescriptor getReplicationDescriptor(ReplicationConfigValidator validator,
            ReplicationConfigDescriptorBuilder builder) throws RepoConfigException {
        RemoteReplicationDescriptor descriptor = null;
        RemoteReplicationConfigModel validatedReplication = validator.validateRemoteModel(this);
        if (validatedReplication != null) {
            descriptor = builder.buildRemoteReplication(validatedReplication, general.getRepoKey());
        }
        return descriptor;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
