package org.artifactory.repo.converter.v3;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;
import static org.artifactory.repo.converter.v3.ExampleRepoLayoutConverter.REPO_LAYOUT_REF_KEY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class ExampleRepoLayoutConverterTest {

    @Test
    void testConvertExampleRepo() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        ExampleRepoLayoutConverter converter = new ExampleRepoLayoutConverter();
        Map<String, Object> repositoryConfigMap = converter.deserializeMap(localRepoConfig);
        converter.convert(repositoryConfigMap);
        Map<String, Object> baseConfig =
                converter.deserializeMap(repositoryConfigMap.get(ExampleRepoLayoutConverter.BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        assertEquals(layout, DEFAULT_REPO_LAYOUT);
    }

    @Test
    void testConvertExampleRepoWithExistingLayout() {
        String initialLayout = "layout-overridden";
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef(initialLayout);
        ExampleRepoLayoutConverter converter = new ExampleRepoLayoutConverter();
        Map<String, Object> repositoryConfigMap = converter.deserializeMap(localRepoConfig);
        converter.convert(repositoryConfigMap);
        Map<String, Object> baseConfig =
                converter.deserializeMap(repositoryConfigMap.get(ExampleRepoLayoutConverter.BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        assertEquals(layout, initialLayout);
    }

    @Test
    void testConvertNonExampleRepoWithExistingLayout() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(EXAMPLE_REPO_KEY + "-other", RepoType.Generic);
        ExampleRepoLayoutConverter converter = new ExampleRepoLayoutConverter();
        Map<String, Object> repositoryConfigMap = converter.deserializeMap(localRepoConfig);
        converter.convert(repositoryConfigMap);
        Map<String, Object> baseConfig =
                converter.deserializeMap(repositoryConfigMap.get(ExampleRepoLayoutConverter.BASE_CONFIG_KEY));
        String layout = (String) baseConfig.get(REPO_LAYOUT_REF_KEY);
        assertNull(layout);
    }
}