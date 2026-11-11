package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Einas Ashkar
 */

public class NimModelTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.NimModel, new NimModelTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        Assertions.assertEquals(
                "{\"repoType\":\"NimModel\",\"enableTokenAuthentication\":false,\"listRemoteFolderItems\":false," +
                        "\"url\":\"https://api.ngc.nvidia.com\"," +
                        "\"repoType\":\"NimModel\"}",
                new NimModelTypeSpecificConfigModel().toString());
    }

    @Test
    void getListRemoteFolderItems() {
        Assertions.assertFalse(new NimModelTypeSpecificConfigModel().getListRemoteFolderItems());
    }

    @Test
    void setListRemoteFolderItems() {
        NimModelTypeSpecificConfigModel nimModelTypeSpecificConfigModel = new NimModelTypeSpecificConfigModel();
        nimModelTypeSpecificConfigModel.setListRemoteFolderItems(true);
        Assertions.assertTrue(nimModelTypeSpecificConfigModel.getListRemoteFolderItems());
    }
}
