package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_HEX_REGISTRY_URL;

/**
 * @author Leela Padmaja
 */
class HexTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.Hex, new HexTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String hexTypeModel = new HexTypeSpecificConfigModel().toString();
        Assertions.assertTrue(hexTypeModel.contains("\"repoType\":\"Hex\""));
        Assertions.assertTrue(hexTypeModel.contains("\"url\":\"https://repo.hex.pm\""));
        Assertions.assertTrue(hexTypeModel.contains("\"hexPublicKey\":\"-----BEGIN PUBLIC KEY-"));
    }

    @Test
    void testValidateRemoteTypeSpecific() {
        HexTypeSpecificConfigModel configModel = new HexTypeSpecificConfigModel();
        Assertions.assertDoesNotThrow(configModel::validateRemoteTypeSpecific);
        Assertions.assertEquals(DEFAULT_HEX_REGISTRY_URL, new HexTypeSpecificConfigModel().getUrl());
    }

}
