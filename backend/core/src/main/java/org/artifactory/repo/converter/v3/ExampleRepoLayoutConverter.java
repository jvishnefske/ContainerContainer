package org.artifactory.repo.converter.v3;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.repo.converter.RepositoriesConverter;

import java.util.Map;
import java.util.Objects;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;

/**
 * Adds a default layout for the example repo if missed.
 *
 * @author voloymyrm
 */
@Slf4j
public class ExampleRepoLayoutConverter implements RepositoriesConverter {
    private static final String REPO_KEY = "key";
    @VisibleForTesting
    static final String BASE_CONFIG_KEY = "baseConfig";
    @VisibleForTesting
    static final String REPO_LAYOUT_REF_KEY = "repoLayoutRef";

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        String repoKey = (String) repositoryConfigMap.get(REPO_KEY);
        if (!Objects.equals(EXAMPLE_REPO_KEY, repoKey)) {
            return;
        }
        Map<String, Object> baseConfig = deserializeMap(repositoryConfigMap.get(BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        if (Objects.isNull(layout)) {
            baseConfig.put(REPO_LAYOUT_REF_KEY, DEFAULT_REPO_LAYOUT);
            repositoryConfigMap.put(BASE_CONFIG_KEY, baseConfig);
            log.debug("Layout for repo with key {} has been updated", repoKey);
        }
    }
}