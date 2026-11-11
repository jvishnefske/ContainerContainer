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

package org.artifactory.ui.rest.model.admin.configuration.repository.local;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.local.LocalReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;

import java.util.List;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
public class CommonLocalRepositoryConfigModel implements RepositoryConfigModel<LocalBasicRepositoryConfigModel,
        LocalAdvancedRepositoryConfigModel, LocalReplicationConfigModel> {

    protected GeneralRepositoryConfigModel general;
    protected LocalBasicRepositoryConfigModel basic;
    protected LocalAdvancedRepositoryConfigModel advanced;
    protected TypeSpecificConfigModel typeSpecific;

    @Override
    public GeneralRepositoryConfigModel getGeneral() {
        return general;
    }

    public void setGeneral(GeneralRepositoryConfigModel general) {
        this.general = general;
    }

    @Override
    public LocalBasicRepositoryConfigModel getBasic() {
        return basic;
    }

    @Override
    public void setBasic(LocalBasicRepositoryConfigModel basic) {
        this.basic = basic;
    }

    @Override
    public LocalAdvancedRepositoryConfigModel getAdvanced() {
        return advanced;
    }

    @Override
    public void setAdvanced(LocalAdvancedRepositoryConfigModel advanced) {
        this.advanced = advanced;
    }

    @Override
    public List<LocalReplicationConfigModel> getReplications() {
        return List.of();
    }

    @Override
    public void setReplications(List<LocalReplicationConfigModel> replications) {
        //noop
    }

    @Override
    public TypeSpecificConfigModel getTypeSpecific() {
        return typeSpecific;
    }

    public void setTypeSpecific(TypeSpecificConfigModel typeSpecific) {
        this.typeSpecific = typeSpecific;
    }

    @Override
    public LocalConfig toRepoConfig(RepoConfigValidator validator, RepoConfigDescriptorBuilder builder,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        return null;
    }

    @Override
    public RepositoryConfigModel<LocalBasicRepositoryConfigModel, LocalAdvancedRepositoryConfigModel,
            LocalReplicationConfigModel> fromRepoConfig(
            RepoConfigModelBuilder builder, RepoConfig repoConfig) {
        return null;
    }

    public ReverseProxyDescriptor getReverseProxyDescriptor(RepoConfig repoConfig,
            RepoConfigDescriptorBuilder builder) {
        return builder.buildReverseProxyDescriptor(getAdvanced(), repoConfig);
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator,
            MutableCentralConfigDescriptor configDescriptor, RepositoryService repositoryService,
            boolean fedAttrOptIn) throws RepoConfigException {
        return null;
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator, MutableCentralConfigDescriptor configDescriptor,
            RepositoryService repositoryService) throws RepoConfigException {
        return null;
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater, boolean fedAttrOptIn) throws RepoConfigException {
        // noop
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater) throws RepoConfigException {
        // noop
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    @Override
    public String getRepoType() {
        return RepoDetailsType.LOCAL_REPO;
    }

    @Override
    public FederationConfigModel getFederation() {
        return RepositoryConfigModel.super.getFederation();
    }
}
