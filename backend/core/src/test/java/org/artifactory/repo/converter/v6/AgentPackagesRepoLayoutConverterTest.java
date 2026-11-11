package org.artifactory.repo.converter.v6;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.repo.converter.v6.AgentPackagesRepoLayoutConverter.BASE_CONFIG_KEY;
import static org.artifactory.repo.converter.v6.AgentPackagesRepoLayoutConverter.REPO_LAYOUT_REF_KEY;
import static org.artifactory.util.RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME;
import static org.artifactory.util.RepoLayoutUtils.SIMPLE_DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Igor Gumush
 */
class AgentPackagesRepoLayoutConverterTest {

    private final AgentPackagesRepoLayoutConverter converter = new AgentPackagesRepoLayoutConverter();

    @Test
    void testAgentPackagesRepoSimpleDefaultMigrated() {
        LocalRepoConfig config = LocalRepoConfig.create("agent-packages-local", RepoType.AgentPackages);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPACKAGES_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testAgentPackagesRepoCustomLayoutMigrated() {
        String customLayout = "my-custom-layout";
        LocalRepoConfig config = LocalRepoConfig.create("agent-packages-local", RepoType.AgentPackages);
        config.getBaseConfig().setRepoLayoutRef(customLayout);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPACKAGES_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testAgentPackagesRepoAlreadyAgentPackagesDefaultNotChanged() {
        LocalRepoConfig config = LocalRepoConfig.create("agent-packages-local", RepoType.AgentPackages);
        config.getBaseConfig().setRepoLayoutRef(AGENTPACKAGES_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPACKAGES_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testNonAgentPackagesRepoNotChanged() {
        LocalRepoConfig config = LocalRepoConfig.create("generic-local", RepoType.Generic);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(SIMPLE_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testLegacyAgentResourcesPackageTypeAndLayoutMigrated() {
        LocalRepoConfig config = LocalRepoConfig.create("legacy-agent-local", RepoType.Generic);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        map.put("packageType", "agentresources");
        converter.convert(map);
        assertEquals(RepoType.AgentPackages.getType(), map.get("packageType"));
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPACKAGES_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }
}
