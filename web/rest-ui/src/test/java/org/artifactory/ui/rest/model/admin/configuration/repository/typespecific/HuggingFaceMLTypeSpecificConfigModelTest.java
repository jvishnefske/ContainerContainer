package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author shahafg
 */
class HuggingFaceMLTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.HuggingFaceML, new HuggingFaceMLTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        Assertions.assertEquals(
                "{\"repoType\":\"HuggingFaceML\",\"enableTokenAuthentication\":false,\"listRemoteFolderItems\":false," +
                        "\"mlRepoLayout\":false," +
                        "\"repoType\":\"HuggingFaceML\",\"url\":\"https://huggingface.co\"}",
                new HuggingFaceMLTypeSpecificConfigModel().toString());
    }

    @Test
    void getListRemoteFolderItems() {
        Assertions.assertFalse(new HuggingFaceMLTypeSpecificConfigModel().getListRemoteFolderItems());
    }

    @Test
    void isMLRepoLayout() {
        Assertions.assertFalse(new HuggingFaceMLTypeSpecificConfigModel().isMlRepoLayout());
    }

    @Test
    void setListRemoteFolderItems() {
        HuggingFaceMLTypeSpecificConfigModel mlTypeSpecificConfigModel = new HuggingFaceMLTypeSpecificConfigModel();
        mlTypeSpecificConfigModel.setListRemoteFolderItems(true);
        Assertions.assertTrue(mlTypeSpecificConfigModel.getListRemoteFolderItems());
    }
}