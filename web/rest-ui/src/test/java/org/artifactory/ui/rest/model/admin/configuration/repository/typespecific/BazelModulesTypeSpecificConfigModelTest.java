package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;


import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Volodymyr Shulga
 */
class BazelModulesTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.BazelModules, new BazelModulesTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String actual = new BazelModulesTypeSpecificConfigModel().toString();
        Assertions.assertEquals(
                "{\"repoType\":\"BazelModules\",\"url\":\"https://bcr.bazel.build/\"," +
                        "\"repoType\":\"BazelModules\",\"enableTokenAuthentication\":false}", actual);
    }
}