package org.artifactory.repo.converter.v5;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.repo.converter.v5.AgentPluginsRepoLayoutConverter.BASE_CONFIG_KEY;
import static org.artifactory.repo.converter.v5.AgentPluginsRepoLayoutConverter.REPO_LAYOUT_REF_KEY;
import static org.artifactory.util.RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME;
import static org.artifactory.util.RepoLayoutUtils.SIMPLE_DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Linoy Levi
 */
class AgentPluginsRepoLayoutConverterTest {

    private final AgentPluginsRepoLayoutConverter converter = new AgentPluginsRepoLayoutConverter();

    @Test
    void testAgentPluginsRepoSimpleDefaultMigrated() {
        LocalRepoConfig config = LocalRepoConfig.create("agent-plugins-local", RepoType.AgentPlugins);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPLUGINS_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testAgentPluginsRepoCustomLayoutMigrated() {
        String customLayout = "my-custom-layout";
        LocalRepoConfig config = LocalRepoConfig.create("agent-plugins-local", RepoType.AgentPlugins);
        config.getBaseConfig().setRepoLayoutRef(customLayout);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPLUGINS_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testAgentPluginsRepoAlreadyAgentPluginsDefaultNotChanged() {
        LocalRepoConfig config = LocalRepoConfig.create("agent-plugins-local", RepoType.AgentPlugins);
        config.getBaseConfig().setRepoLayoutRef(AGENTPLUGINS_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(AGENTPLUGINS_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testNonAgentPluginsRepoNotChanged() {
        LocalRepoConfig config = LocalRepoConfig.create("generic-local", RepoType.Generic);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(SIMPLE_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }
}
