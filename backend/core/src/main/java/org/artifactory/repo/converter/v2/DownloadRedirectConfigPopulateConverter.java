package org.artifactory.repo.converter.v2;

import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.converter.RepositoriesConverter;

import java.util.Map;

/**
 * converter that convert repo that was created in legacy way (model version < 2) without the downloadRedirectConfig
 * field populated to a repo with downloadRedirectConfig populated.
 * @author Rom Kurtz
 */
public class DownloadRedirectConfigPopulateConverter implements RepositoriesConverter {
    public static final String REPO_TYPE_CONFIG_KEY = "repoTypeConfig";
    public static final String DOWNLOAD_REDIRECT_CONFIG_KEY = "downloadRedirectConfig";
    public static final String TYPE_KEY = "type";

    /**
     * fill the repositoryConfigMap with new repoTypeConfig that include downloadRedirectConfig if not exist.
     * @param repositoryConfigMap a map represent repo config fields (not included nulls fields)
     */
    @Override
    public void convert(Map<String, Object> repositoryConfigMap) {
        String type = (String) repositoryConfigMap.get(TYPE_KEY);
        Map<String, Object> repoTypeConfig = deserializeMap(repositoryConfigMap.get(REPO_TYPE_CONFIG_KEY));
        // check if this is real repo (not virtual) and doesn't contain the key: DOWNLOAD_REDIRECT_CONFIG_KEY
        if (!RepoDetailsType.VIRTUAL.getConfigName().equals(type) &&
                !repoTypeConfig.containsKey(DOWNLOAD_REDIRECT_CONFIG_KEY)) {
            DownloadRedirectRepoConfig downloadRedirectRepoConfig = new DownloadRedirectRepoConfig();
            repoTypeConfig.put(DOWNLOAD_REDIRECT_CONFIG_KEY, downloadRedirectRepoConfig);
            repositoryConfigMap.put(REPO_TYPE_CONFIG_KEY, repoTypeConfig);
        }
    }
}
