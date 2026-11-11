package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.List;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_COMPOSER_REGISTRY;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_TERRAFORM_LOCAL_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_TERRAFORM_PROVIDERS_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_TERRAFORM_REGISTRY_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.PATTERN_GITHUB;

/**
 * @author BarakH
 */
public class TerraformTypeSpecificConfigModel extends VcsTypeSpecificConfigModel {

    //remote
    private String registryUrl = DEFAULT_TERRAFORM_REGISTRY_URL;
    private String providersUrl = DEFAULT_TERRAFORM_PROVIDERS_URL;
    @Getter
    @Setter
    private Boolean enableExternalDependencies = false;
    @Getter
    @Setter
    private List<String> externalPatterns = Lists.newArrayList(PATTERN_GITHUB);

    //local
    private String terraformType = DEFAULT_TERRAFORM_LOCAL_TYPE;

    @Override
    public RepoType getRepoType() {
        return RepoType.Terraform;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    public String getProvidersUrl() {
        return providersUrl;
    }

    public void setProvidersUrl(String providersUrl) {
        this.providersUrl = providersUrl;
    }

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        setRegistryUrl(ofNullable(getRegistryUrl()).orElse(DEFAULT_COMPOSER_REGISTRY));
        setProvidersUrl(ofNullable(getProvidersUrl()).orElse(DEFAULT_TERRAFORM_PROVIDERS_URL));
        super.validateRemoteTypeSpecific();
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) throws RepoConfigException {
        // no implementation by default
    }

    public String getTerraformType() {
        return terraformType;
    }

    public void setTerraformType(String terraformType) {
        this.terraformType = terraformType;
    }
}

