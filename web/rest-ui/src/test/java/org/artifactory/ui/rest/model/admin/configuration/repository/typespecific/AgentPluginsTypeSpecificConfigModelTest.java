package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;


import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Package Generator
 */
class AgentPluginsTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.AgentPlugins, new AgentPluginsTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String actual = new AgentPluginsTypeSpecificConfigModel().toString();
        Assertions.assertTrue(actual.contains("\"repoType\":\"AgentPlugins\""));
    }
}