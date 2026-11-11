package org.artifactory.addon.docker;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DockerPreemptiveAuthHelper {

    /**
     * Returns true if preemptive auth should be used for this Docker repo config (flag on, smart
     * remote, and credentials configured).
     */
    public static boolean shouldUsePreemptiveAuth(String repoKey) {
        RemoteRepoConfig config = ContextHelper.get().beanForType(RepositoryService.class)
                .getRemoteRepoConfigByKey(repoKey);
        return shouldUsePreemptiveAuthWithConfig(config);
    }

    private static boolean shouldUsePreemptiveAuthWithConfig(RemoteRepoConfig config) {
        if (config == null || !ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled.getBoolean()) {
            return false;
        }

        return isSmartRemote(config) && hasConfiguredCredentials(config);
    }

    private static boolean isSmartRemote(@NonNull RemoteRepoConfig config) {
        return config.isSmart();
    }

    /**
     * True if username and/or password is set. Password-only (e.g. API token) is valid.
     */
    private static boolean hasConfiguredCredentials(@NonNull RemoteRepoConfig config) {
        if (config.getRepoTypeConfig() == null) {
            return false;
        }
        RemoteRepositoryRepoTypeConfig repoTypeConfig = config.getRepoTypeConfig();
        return StringUtils.isNotBlank(repoTypeConfig.getUsername())
                || StringUtils.isNotBlank(repoTypeConfig.getPassword());
    }
}
