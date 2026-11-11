package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class JemTypeSpecificConfigModelTest {
    JemTypeSpecificConfigModel jemTypeSpecificConfigModel;

    @BeforeEach
    void setUp() {
        jemTypeSpecificConfigModel = new JemTypeSpecificConfigModel();
    }

    @Test
    void validateRemoteTypeSpecific() {
        RepoConfigException exception = Assertions.assertThrows(RepoConfigException.class,
                () -> jemTypeSpecificConfigModel.validateRemoteTypeSpecific());
        Assertions.assertEquals("Package type Jem is unsupported in remote repositories", exception.getMessage());
    }

    @Test
    void validateVirtualTypeSpecific() {
        RepoConfigException exception = Assertions.assertThrows(RepoConfigException.class,
                () -> jemTypeSpecificConfigModel.validateVirtualTypeSpecific(null));
        Assertions.assertEquals("Package type Jem is unsupported in virtual repositories", exception.getMessage());
    }

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.Jem, jemTypeSpecificConfigModel.getRepoType());
    }

    @Test
    void testToString() {
        Assertions.assertEquals(
                "{\"repoType\":\"Jem\",\"enableTokenAuthentication\":false,\"repoType\":\"Jem\",\"url\":\"\"}",
                jemTypeSpecificConfigModel.toString());
    }
}