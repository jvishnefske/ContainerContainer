package org.artifactory.repo.converter.v4;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.converter.RepositoriesConverter;

import java.util.Map;

import static org.artifactory.util.RepoLayoutUtils.SKILLS_DEFAULT_NAME;

/**
 * Migrates skills repositories to skills-default layout
 * during DB config loading.
 *
 * @author Oren Savich
 *
 */
@Slf4j
public class SkillsRepoLayoutConverter implements RepositoriesConverter {

    @VisibleForTesting
    static final String BASE_CONFIG_KEY = "baseConfig";
    @VisibleForTesting
    static final String REPO_LAYOUT_REF_KEY = "repoLayoutRef";
    private static final String PACKAGE_TYPE_KEY = "packageType";
    private static final String REPO_KEY = "key";

    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        String packageType = (String) repositoryConfigMap.get(PACKAGE_TYPE_KEY);
        if (!RepoType.Skills.getType().equals(packageType)) {
            return;
        }
        Map<String, Object> baseConfig = deserializeMap(repositoryConfigMap.get(BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        if (!SKILLS_DEFAULT_NAME.equals(layout)) {
            baseConfig.put(REPO_LAYOUT_REF_KEY, SKILLS_DEFAULT_NAME);
            repositoryConfigMap.put(BASE_CONFIG_KEY, baseConfig);
            String repoKey = (String) repositoryConfigMap.get(REPO_KEY);
            log.info("Migrated skills repo '{}' layout" + " from '{}' to '{}'", repoKey, layout, SKILLS_DEFAULT_NAME);
        }
    }
}
