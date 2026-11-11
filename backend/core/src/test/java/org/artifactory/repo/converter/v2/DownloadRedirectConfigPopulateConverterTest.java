package org.artifactory.repo.converter.v2;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;

class DownloadRedirectConfigPopulateConverterTest {
    @Test
    void testConvert() {
        DownloadRedirectConfigPopulateConverter converter = new DownloadRedirectConfigPopulateConverter();
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setDownloadRedirectConfig(null);
        Map<String, Object> repositoryConfigMap = converter.deserializeMap(localRepoConfig);
        converter.convert(repositoryConfigMap);
        Map<String, Object> repoTypeConfig = converter.deserializeMap(
                repositoryConfigMap.get(DownloadRedirectConfigPopulateConverter.REPO_TYPE_CONFIG_KEY));
        assertTrue(repoTypeConfig.containsKey(DownloadRedirectConfigPopulateConverter.DOWNLOAD_REDIRECT_CONFIG_KEY));
    }

}
