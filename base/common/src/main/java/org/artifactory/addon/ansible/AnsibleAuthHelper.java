package org.artifactory.addon.ansible;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RemoteRepoConfig;

import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnsibleAuthHelper {

    /**
     * Returns true if Ansible SSO authentication should be used for this Ansible repository.
     * SSO is enabled when both authUrl and password (used as refresh token) are configured.
     */
    public static boolean shouldUseSsoAuth(String repoKey) {
        if (repoKey == null) {
            return false;
        }
        RemoteRepoConfig config = ContextHelper.get().beanForType(RepositoryService.class)
                .getRemoteRepoConfigByKey(repoKey);
        if (config == null) {
            return false;
        }
        Map<String, String> packageTypeConfig = config.getPackageTypeConfig();
        if (packageTypeConfig == null) {
            return false;
        }
        return StringUtils.isNotBlank(packageTypeConfig.get(AUTH_URL.getKey()))
                && StringUtils.isNotBlank(config.getRepoTypeConfig().getPassword());
    }
}