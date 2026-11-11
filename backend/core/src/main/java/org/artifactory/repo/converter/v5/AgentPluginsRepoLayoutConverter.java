package org.artifactory.repo.converter.v5;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.converter.RepositoriesConverter;

import java.util.Map;

import static org.artifactory.util.RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME;

/**
 * Migrates agentplugins repositories to agent-plugins-default layout
 * during DB config loading.
 *
 * @author Linoy Levi
 */
@Slf4j
public class AgentPluginsRepoLayoutConverter implements RepositoriesConverter {

    @VisibleForTesting
    static final String BASE_CONFIG_KEY = "baseConfig";
    @VisibleForTesting
    static final String REPO_LAYOUT_REF_KEY = "repoLayoutRef";
    private static final String PACKAGE_TYPE_KEY = "packageType";
    private static final String REPO_KEY = "key";

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        String packageType = (String) repositoryConfigMap.get(PACKAGE_TYPE_KEY);
        if (!RepoType.AgentPlugins.getType().equals(packageType)) {
            return;
        }
        Map<String, Object> baseConfig = deserializeMap(repositoryConfigMap.get(BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        if (!AGENTPLUGINS_DEFAULT_NAME.equals(layout)) {
            baseConfig.put(REPO_LAYOUT_REF_KEY, AGENTPLUGINS_DEFAULT_NAME);
            repositoryConfigMap.put(BASE_CONFIG_KEY, baseConfig);
            String repoKey = (String) repositoryConfigMap.get(REPO_KEY);
            log.info("Migrated agentplugins repo '{}' layout" + " from '{}' to '{}'", repoKey, layout,
                    AGENTPLUGINS_DEFAULT_NAME);
        }
    }
}
