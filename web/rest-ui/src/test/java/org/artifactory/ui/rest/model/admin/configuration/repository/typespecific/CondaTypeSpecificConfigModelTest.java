package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Volodymyr Shulga
 */
class CondaTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        assertEquals(RepoType.Conda, new CondaTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() throws JSONException {
        String expectedJson = """
                {
                    "repoType": "Conda",
                    "enableTokenAuthentication": false,
                    "listRemoteFolderItems": false,
                    "virtualRetrievalCachePeriodSecs": 7200,
                    "url": "https://repo.anaconda.com/pkgs/main"
                }
                """;
        JSONAssert.assertEquals(expectedJson, new CondaTypeSpecificConfigModel().toString(), false);
    }

    @Test
    void testSetListRemoteFolderItems() {
        CondaTypeSpecificConfigModel configModel = new CondaTypeSpecificConfigModel();
        configModel.setListRemoteFolderItems(true);
        assertTrue(configModel.getListRemoteFolderItems());
    }

    @Test
    void testGetListRemoteFolderItems() {
        CondaTypeSpecificConfigModel configModel = new CondaTypeSpecificConfigModel();
        // Default should be false (DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE)
        assertFalse(configModel.getListRemoteFolderItems());
    }

    @Test
    void testValidateRemoteTypeSpecific() {
        CondaTypeSpecificConfigModel configModel = new CondaTypeSpecificConfigModel();
        assertDoesNotThrow(configModel::validateRemoteTypeSpecific);
        assertFalse(configModel.getListRemoteFolderItems());
    }

    @Test
    void testValidateRemoteTypeSpecificWithNullValue() throws RepoConfigException {
        CondaTypeSpecificConfigModel configModel = new CondaTypeSpecificConfigModel();
        configModel.setListRemoteFolderItems(null);
        configModel.validateRemoteTypeSpecific();
        // Should default to true when the previous value is null, such as for new repositories.
        assertTrue(configModel.getListRemoteFolderItems());
    }

    @Test
    void testValidateRemoteTypeSpecificPreservesExplicitValue() throws RepoConfigException {
        CondaTypeSpecificConfigModel configModel = new CondaTypeSpecificConfigModel();
        configModel.setListRemoteFolderItems(false);
        configModel.validateRemoteTypeSpecific();
        assertFalse(configModel.getListRemoteFolderItems());
    }
}