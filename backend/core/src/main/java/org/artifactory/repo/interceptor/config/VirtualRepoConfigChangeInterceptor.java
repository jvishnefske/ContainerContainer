package org.artifactory.repo.interceptor.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.SpecificTypeRepositoryConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.spring.ConditionalOnProperty;
import org.jfrog.common.config.validation.NotUnicode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoConfigChange.ConfigAction;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.interceptor.config.VirtualRepoConfigChangeInterceptor.VIRTUAL_BLOCK_PACKAGE_TYPE_MISMATCH_PROPERTY_NAME;

/**
 * Interceptor to validate virtual repository configurations on create and update actions.
 * Ensures that:
 * - All referenced repositories have a compatible package type with the virtual repository
 * - The default deployment repository (if set) has a compatible package type with the virtual repository.
 * The above are for all package types excluding Generic, which has no restrictions.
 *
 * @author Mor Merhav
 */
@Slf4j
@Component
@SuppressWarnings("DataFlowIssue")
@ConditionalOnProperty(name = VIRTUAL_BLOCK_PACKAGE_TYPE_MISMATCH_PROPERTY_NAME, havingValue = "true")
public class VirtualRepoConfigChangeInterceptor implements RepoConfigChangeInterceptor {

    protected static final String VIRTUAL_BLOCK_PACKAGE_TYPE_MISMATCH_PROPERTY_NAME =
            "artifactory.virtual.block.package.type.mismatch";
    private final RepositoryService repositoryService;

    @Autowired
    public VirtualRepoConfigChangeInterceptor(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        List<RepoConfigChange> createVirtualChanges = repoConfigChanges.filterBy(ConfigAction.REPO_CREATE, VIRTUAL);
        List<RepoConfigChange> updateVirtualChanges = repoConfigChanges.filterBy(ConfigAction.REPO_UPDATE, VIRTUAL);
        List<RepoConfigChange> allVirtualChanges = new ArrayList<>(createVirtualChanges);
        allVirtualChanges.addAll(updateVirtualChanges);
        validatePackageTypeCompatibility(allVirtualChanges);
    }

    private void validatePackageTypeCompatibility(List<RepoConfigChange> virtualChanges) {
        for (RepoConfigChange virtualChange : virtualChanges) {
            validateRefReposPackageTypeCompatibility(virtualChange);
            validateDefaultDeploymentRepoPackageTypeCompatibility(virtualChange);
        }
    }

    private void validateDefaultDeploymentRepoPackageTypeCompatibility(RepoConfigChange virtualChange) {
        if (isDefaultDeploymentRepoUnchanged(virtualChange)) {
            return;
        }
        VirtualRepoConfig virtualRepoConfig = (VirtualRepoConfig) virtualChange.getNewRepoConfig();
        String newDefaultDeploymentRepoRef = virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
        LocalConfig localConfig =
                repositoryService.getLocalOrFederatedRepoConfigByKey(newDefaultDeploymentRepoRef);
        validatePackageTypeCompatibility(virtualRepoConfig, localConfig);
    }

    private boolean isDefaultDeploymentRepoUnchanged(RepoConfigChange virtualChange) {
        if (virtualChange.isCreate()) {
            VirtualRepositoryRepoTypeConfig repoTypeConfig =
                    (VirtualRepositoryRepoTypeConfig) virtualChange.getNewRepoConfig().getRepoTypeConfig();
            return StringUtils.isBlank(repoTypeConfig.getDefaultDeploymentRepoRef());
        } else if (virtualChange.isUpdate()) {
            SpecificTypeRepositoryConfig oldRepoTypeConfig = virtualChange.getOldRepoConfig().getRepoTypeConfig();
            SpecificTypeRepositoryConfig newRepoTypeConfig = virtualChange.getNewRepoConfig().getRepoTypeConfig();
            return Objects.equals(
                    ((VirtualRepositoryRepoTypeConfig) oldRepoTypeConfig).getDefaultDeploymentRepoRef(),
                    ((VirtualRepositoryRepoTypeConfig) newRepoTypeConfig).getDefaultDeploymentRepoRef());
        }
        return true;
    }

    private void validateRefReposPackageTypeCompatibility(RepoConfigChange virtualChange) {
        if (isRepositoryRefsUnchanged(virtualChange)) {
            return;
        }
        VirtualRepoConfig newRepoConfig = (VirtualRepoConfig) virtualChange.getNewRepoConfig();
        List<@NotUnicode String> repositoryRefs = newRepoConfig.getRepoTypeConfig().getRepositoryRefs();
        List<RepoConfig> refRepoConfigs =
                repositoryRefs.stream().map(repositoryService::getRepoConfigByKey).toList();
        for (RepoConfig refRepoConfig : refRepoConfigs) {
            validatePackageTypeCompatibility(newRepoConfig, refRepoConfig);
        }
    }

    private boolean isRepositoryRefsUnchanged(RepoConfigChange virtualChange) {
        if (virtualChange.isCreate()) {
            VirtualRepositoryRepoTypeConfig repoTypeConfig =
                    (VirtualRepositoryRepoTypeConfig) virtualChange.getNewRepoConfig().getRepoTypeConfig();
            return CollectionUtils.isEmpty(repoTypeConfig.getRepositoryRefs());
        } else if (virtualChange.isUpdate()) {
            SpecificTypeRepositoryConfig oldRepoTypeConfig = virtualChange.getOldRepoConfig().getRepoTypeConfig();
            SpecificTypeRepositoryConfig newRepoTypeConfig = virtualChange.getNewRepoConfig().getRepoTypeConfig();
            return Objects.equals(
                    ((VirtualRepositoryRepoTypeConfig) oldRepoTypeConfig).getRepositoryRefs(),
                    ((VirtualRepositoryRepoTypeConfig) newRepoTypeConfig).getRepositoryRefs());
        }
        return true;
    }

    private void validatePackageTypeCompatibility(VirtualRepoConfig virtualRepoConfig, RepoConfig repoConfig) {
        RepoType virtualRepoPackageType = virtualRepoConfig.getPackageType();
        if (virtualRepoPackageType == Generic) {
            log.debug("Virtual Repo: '{}' package Type is Generic. Skipping package type compatibility validation.",
                    virtualRepoConfig.getKey());
            return;
        }
        if (repoConfig == null) {
            return;
        }
        RepoType packageType = repoConfig.getPackageType();
        if (virtualRepoPackageType == packageType) {
            return;
        }
        if (virtualRepoPackageType.isMavenGroup() && packageType.isMavenGroup()) {
            return;
        }
        if (isHelmOciGroup(virtualRepoPackageType) && isHelmOciGroup(packageType)) {
            return;
        }
        throw new ConfigurationException(
                ("Virtual repository '%s' has package type '%s' but includes non-matching repository '%s' " +
                        "with package type '%s'").formatted(
                        virtualRepoConfig.getKey(),
                        virtualRepoPackageType,
                        repoConfig.getKey(),
                        packageType));
    }

    private boolean isHelmOciGroup(RepoType repoType) {
        return repoType == RepoType.OCI || repoType == RepoType.HelmOCI;
    }
}