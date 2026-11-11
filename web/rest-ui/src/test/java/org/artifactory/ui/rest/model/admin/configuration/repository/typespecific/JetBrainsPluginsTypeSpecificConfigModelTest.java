package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;


import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Volodymyr Shulga
 */
class JetBrainsPluginsTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.JetBrainsPlugins, new JetBrainsPluginsTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String actual = new JetBrainsPluginsTypeSpecificConfigModel().toString();
        Assertions.assertEquals(
                "{\"repoType\":\"JetBrainsPlugins\",\"url\":\"https://plugins.jetbrains.com\"," +
                        "\"repoType\":\"JetBrainsPlugins\",\"enableTokenAuthentication\":false}", actual);
    }
}