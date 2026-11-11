package org.artifactory.repo.converter.v6;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.converter.RepositoriesConverter;

import java.util.Map;

import static org.artifactory.util.RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME;

/**
 * Migrates agentpackages repositories to agent-packages-default layout during DB config loading.
 * Also handles the legacy "agentresources" package type, renaming it to "agentpackages".
 *
 * @author Igor Gumush
 */
@Slf4j
public class AgentPackagesRepoLayoutConverter implements RepositoriesConverter {

    @VisibleForTesting
    static final String BASE_CONFIG_KEY = "baseConfig";
    @VisibleForTesting
    static final String REPO_LAYOUT_REF_KEY = "repoLayoutRef";
    private static final String PACKAGE_TYPE_KEY = "packageType";
    private static final String REPO_KEY = "key";
    private static final String LEGACY_AGENTRESOURCES_TYPE = "agentresources";

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        String packageType = (String) repositoryConfigMap.get(PACKAGE_TYPE_KEY);
        boolean isAgentPackages = RepoType.AgentPackages.getType().equals(packageType);
        boolean isLegacyAgentResources = LEGACY_AGENTRESOURCES_TYPE.equals(packageType);
        if (!isAgentPackages && !isLegacyAgentResources) {
            return;
        }
        if (isLegacyAgentResources) {
            repositoryConfigMap.put(PACKAGE_TYPE_KEY, RepoType.AgentPackages.getType());
        }
        Map<String, Object> baseConfig = deserializeMap(repositoryConfigMap.get(BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        if (!AGENTPACKAGES_DEFAULT_NAME.equals(layout)) {
            baseConfig.put(REPO_LAYOUT_REF_KEY, AGENTPACKAGES_DEFAULT_NAME);
            repositoryConfigMap.put(BASE_CONFIG_KEY, baseConfig);
            String repoKey = (String) repositoryConfigMap.get(REPO_KEY);
            log.info("Migrated repo '{}' layout from '{}' to '{}'", repoKey, layout, AGENTPACKAGES_DEFAULT_NAME);
        }
    }
}
