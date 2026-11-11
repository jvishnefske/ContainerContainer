package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MLTypeSpecificConfigModelTest {
    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.MachineLearning, new MLTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        Assertions.assertEquals(
                "{\"repoType\":\"MachineLearning\",\"enableTokenAuthentication\":false," +
                        "\"repoType\":\"MachineLearning\",\"url\":\"\"}",
                new MLTypeSpecificConfigModel().toString());
    }

    @Test
    void validateRemoteTypeSpecificTest() {
        Assertions.assertThrows(RepoConfigException.class, () ->
                new MLTypeSpecificConfigModel().validateRemoteTypeSpecific());
    }

    @Test
    void validateVirtualTypeSpecificTest() {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        Assertions.assertThrows(RepoConfigException.class, () ->
                new MLTypeSpecificConfigModel().validateVirtualTypeSpecific(addonsManager));
    }
}
