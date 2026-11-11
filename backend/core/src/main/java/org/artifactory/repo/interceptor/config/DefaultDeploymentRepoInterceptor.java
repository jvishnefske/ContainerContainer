package org.artifactory.repo.interceptor.config;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

/**
 * @author Dor Solomon
 */
@Component
public class DefaultDeploymentRepoInterceptor implements RepoConfigChangeInterceptor {

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        List<RepoConfigChange> virtualRepoUpdateChanges =
                mutableRepoConfigChanges.getOrDefault(VIRTUAL, List.of()).stream()
                        .filter(this::isCreateOrUpdate).toList();
        validateDefaultDeploymentRepoIncludesInRepoRefs(virtualRepoUpdateChanges);
    }

    @VisibleForTesting
    void validateDefaultDeploymentRepoIncludesInRepoRefs(List<RepoConfigChange> virtualRepoUpdateChanges) {
        for (RepoConfigChange repoConfigChange : virtualRepoUpdateChanges) {
            VirtualRepoConfig oldVirtualRepoConfig = (VirtualRepoConfig) repoConfigChange.getOldRepoConfig();
            VirtualRepoConfig newVirtualRepoConfig = (VirtualRepoConfig) repoConfigChange.getNewRepoConfig();
            validateDefaultDeploymentRepoRef(oldVirtualRepoConfig, newVirtualRepoConfig);
            if (REPO_UPDATE.equals(repoConfigChange.getConfigAction())) {
                validateRepoRefRemoval(oldVirtualRepoConfig, newVirtualRepoConfig);
            }
        }
    }

    private void validateDefaultDeploymentRepoRef(VirtualRepoConfig oldVirtualRepoConfig,
            VirtualRepoConfig newVirtualRepoConfig) {
        if (newVirtualRepoConfig == null ||
                isChangeUnrelatedToDefaultDeploymentRepo(oldVirtualRepoConfig, newVirtualRepoConfig)) {
            // Do not enforce validation on unrelated repo config updates for existing (misconfigured) virtual repos
            return;
        }
        String newDefaultDeploymentRepoRef = newVirtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
        if (StringUtils.isNotBlank(newDefaultDeploymentRepoRef) &&
                !containsRepoRef(newVirtualRepoConfig, newDefaultDeploymentRepoRef)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot set repository reference '%s' as the default deployment repository for virtual repository" +
                            " '%s' since it is not included in the repository references",
                    newDefaultDeploymentRepoRef, newVirtualRepoConfig.getKey()));
        }
    }

    private boolean isChangeUnrelatedToDefaultDeploymentRepo(VirtualRepoConfig oldVirtualRepoConfig,
            VirtualRepoConfig newVirtualRepoConfig) {
        if (oldVirtualRepoConfig != null) {
            String newDefaultDeploymentRepoRef = newVirtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
            String oldDefaultDeploymentRepoRef = oldVirtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
            return StringUtils.equals(newDefaultDeploymentRepoRef, oldDefaultDeploymentRepoRef);
        }
        return false;
    }

    private void validateRepoRefRemoval(VirtualRepoConfig oldVirtualRepoConfig,
            VirtualRepoConfig newVirtualRepoConfig) {
        if (oldVirtualRepoConfig == null || newVirtualRepoConfig == null) {
            return;
        }
        String defaultDeploymentRepoRef = newVirtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
        if (StringUtils.isBlank(defaultDeploymentRepoRef)) {
            return;
        }
        // Do not enforce validation on unrelated repo config updates for existing (misconfigured) virtual repos
        boolean missingRepoRef = containsRepoRef(oldVirtualRepoConfig, defaultDeploymentRepoRef) &&
                !containsRepoRef(newVirtualRepoConfig, defaultDeploymentRepoRef);
        if (missingRepoRef) {
            throw new IllegalArgumentException(String.format(
                    "Cannot remove repository reference '%s' since it is defined as the default deployment " +
                            "repository for the virtual repository '%s'", defaultDeploymentRepoRef,
                    newVirtualRepoConfig.getKey()));
        }
    }

    private boolean isCreateOrUpdate(RepoConfigChange repoConfigChange) {
        return REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                REPO_UPDATE.equals(repoConfigChange.getConfigAction());
    }

    private boolean containsRepoRef(VirtualRepoConfig virtualRepoConfig, String repoRef) {
        return virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().contains(repoRef);
    }
}