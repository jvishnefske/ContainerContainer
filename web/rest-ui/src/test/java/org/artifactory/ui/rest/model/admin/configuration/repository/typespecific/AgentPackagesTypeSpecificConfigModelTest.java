package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentPackagesTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.AgentPackages, new AgentPackagesTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String actual = new AgentPackagesTypeSpecificConfigModel().toString();
        Assertions.assertTrue(actual.contains("\"repoType\":\"AgentPackages\""));
    }
}
