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

class AlpineTypeSpecificConfigModelTest {

    @Test
    void getRepoType_shouldReturnAlpine() {
        assertEquals(RepoType.Alpine, new AlpineTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toString_shouldReturnExpectedJson() throws JSONException {
        String expectedJson = """
                {
                    "repoType": "Alpine",
                    "listRemoteFolderItems": false,
                    "virtualRetrievalCachePeriodSecs": 7200
                }
                """;
        JSONAssert.assertEquals(expectedJson, new AlpineTypeSpecificConfigModel().toString(), false);
    }

    @Test
    void isListRemoteFolderItems_shouldReturnFalseByDefault() {
        assertFalse(new AlpineTypeSpecificConfigModel().isListRemoteFolderItems());
    }

    @Test
    void setListRemoteFolderItems_shouldReturnTrueWhenSetToTrue() {
        // Arrange
        AlpineTypeSpecificConfigModel configModel = new AlpineTypeSpecificConfigModel();

        // Act
        configModel.setListRemoteFolderItems(true);

        // Assert
        assertTrue(configModel.isListRemoteFolderItems());
    }

    @Test
    void validateRemoteTypeSpecific_shouldNotThrow() {
        AlpineTypeSpecificConfigModel configModel = new AlpineTypeSpecificConfigModel();
        assertDoesNotThrow(configModel::validateRemoteTypeSpecific);
        assertFalse(configModel.isListRemoteFolderItems());
    }

    @Test
    void validateRemoteTypeSpecific_shouldDefaultToTrue_whenValueIsNull()
            throws RepoConfigException {
        // Arrange
        AlpineTypeSpecificConfigModel configModel = new AlpineTypeSpecificConfigModel();
        configModel.setListRemoteFolderItems(null);

        // Act
        configModel.validateRemoteTypeSpecific();

        // Assert - defaults to true (DEFAULT_LIST_REMOTE_ITEMS_SUPPORTED_TYPE) for new repos
        assertTrue(configModel.isListRemoteFolderItems());
    }

    @Test
    void validateRemoteTypeSpecific_shouldPreserveExplicitFalse()
            throws RepoConfigException {
        // Arrange
        AlpineTypeSpecificConfigModel configModel = new AlpineTypeSpecificConfigModel();
        configModel.setListRemoteFolderItems(false);

        // Act
        configModel.validateRemoteTypeSpecific();

        // Assert
        assertFalse(configModel.isListRemoteFolderItems());
    }
}
