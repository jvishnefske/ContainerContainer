package org.artifactory.ui.rest.model.admin.configuration.repository.local;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.local.LocalReplicationConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;

import java.util.List;
import java.util.Set;

import static java.util.Objects.isNull;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@JsonTypeName("localRepoConfig")
public class LocalRepositoryConfigModel extends CommonLocalRepositoryConfigModel {

    private List<LocalReplicationConfigModel> replications;

    @Setter
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private FederationConfigModel federation;

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator,
            MutableCentralConfigDescriptor configDescriptor, RepositoryService repositoryService,
            boolean fedAttrOptIn) throws RepoConfigException {
        return creator.handleLocal(this, configDescriptor, fedAttrOptIn);
    }

    @Override
    public LocalConfig toRepoConfig(RepoConfigValidator validator, RepoConfigDescriptorBuilder builder,
            MutableCentralConfigDescriptor configDescriptor)
            throws RepoConfigException {
        validator.validateLocal(this);
        return builder.buildLocalConfig(this);
    }

    @Override
    public LocalRepositoryConfigModel fromRepoConfig(RepoConfigModelBuilder builder, RepoConfig repoConfig) {
        builder.populateLocalDescriptorValuesToModel((LocalConfig) repoConfig, this);
        return this;
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater, boolean fedAttrOptIn) throws RepoConfigException {
        updater.handleLocal(this, fedAttrOptIn);
    }

    @Override
    public List<LocalReplicationConfigModel> getReplications() {
        return replications;
    }

    @Override
    public void setReplications(List<LocalReplicationConfigModel> replications) {
        this.replications = replications;
    }

    public Set<LocalReplicationDescriptor> getReplicationDescriptors(
            ReplicationConfigValidator validator,
            ReplicationConfigDescriptorBuilder builder) throws RepoConfigException {
        if (isNull(replications)) {
            return Sets.newHashSet();
        }
        validator.validateLocalModels(replications);
        return builder.buildLocalReplications(replications, general.getRepoKey());
    }
}
