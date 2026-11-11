package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class ConanTypeSpecificConfigModelTest {

    @Test
    void getRepoType() { Assertions.assertEquals(RepoType.Conan, new ConanTypeSpecificConfigModel().getRepoType()); }

    @Test
    void toStringTest() throws JSONException {
        String expectedJson = """
                {
                    "repoType": "Conan",
                    "enableTokenAuthentication": false,
                    "forceConanAuthentication": false,
                    "listRemoteFolderItems": false,
                    "virtualRetrievalCachePeriodSecs": 7200,
                    "url": "https://center.conan.io/"
                }
                """;
        JSONAssert.assertEquals(expectedJson, new ConanTypeSpecificConfigModel().toString(), false);
    }

    @Test
    void testSetListRemoteFolderItems() {
        ConanTypeSpecificConfigModel configModel = new ConanTypeSpecificConfigModel();
        configModel.setListRemoteFolderItems(true);
        Assertions.assertTrue(configModel.getListRemoteFolderItems());
    }

    @Test
    void testValidateRemoteTypeSpecific() {
        ConanTypeSpecificConfigModel configModel = new ConanTypeSpecificConfigModel();
        configModel.validateRemoteTypeSpecific();
        Assertions.assertDoesNotThrow(configModel::validateRemoteTypeSpecific);
        Assertions.assertFalse(configModel.getListRemoteFolderItems());
    }
}
