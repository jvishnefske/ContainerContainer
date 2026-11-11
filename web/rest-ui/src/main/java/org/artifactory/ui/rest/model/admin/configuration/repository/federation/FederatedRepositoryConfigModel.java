package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.CommonLocalRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;

/**
 * @author AndreiK.
 */

@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("federatedRepoConfig")
public class FederatedRepositoryConfigModel extends CommonLocalRepositoryConfigModel {

    protected FederationConfigModel federation;

    @Override
    public FederatedRepoAdvancedRepositoryConfigModel getAdvanced() {
        return (FederatedRepoAdvancedRepositoryConfigModel) advanced;
    }

    public void setAdvanced(FederatedRepoAdvancedRepositoryConfigModel advanced) {
        this.advanced = advanced;
    }

    @Override
    public RepoConfig createRepoConfig(CreateRepoConfigHelper creator,
            MutableCentralConfigDescriptor configDescriptor, RepositoryService repositoryService,
            boolean fedAttrOptIn) throws RepoConfigException {
        return creator.handleFederated(this, configDescriptor);
    }

    @Override
    public void updateRepo(UpdateRepoConfigHelper updater, boolean fedAttrOptIn) throws RepoConfigException {
        updater.handleFederated(this);
    }

    @Override
    public FederatedRepoConfig toRepoConfig(RepoConfigValidator validator, RepoConfigDescriptorBuilder builder,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        validator.validateFederated(this);
        return builder.buildFederatedRepoConfig(this);
    }

    @Override
    public FederatedRepositoryConfigModel fromRepoConfig(RepoConfigModelBuilder builder, RepoConfig repoConfig) {
        builder.populateFederatedDescriptorValuesToModel((FederatedRepoConfig) repoConfig, this);
        return this;
    }

    @Override
    public String getRepoType() {
        return RepoDetailsType.FEDERATED_REPO;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
