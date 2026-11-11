package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;


import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Omri Cohen
 */
class VSCodeTypeSpecificConfigModelTest {

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.AIEditorExtensions,
                new AIEditorExtensionsTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void toStringTest() {
        String actual = new AIEditorExtensionsTypeSpecificConfigModel().toString();
        Assertions.assertEquals(
                "{\"repoType\":\"AIEditorExtensions\",\"url\":\"https://marketplace.visualstudio" +
                        ".com/_apis/public/gallery\"," +
                        "\"enableExternalDependencies\":true,\"externalPatterns\":[\"**/**vsassets.io/**\"]," +
                        "\"externalRemoteRepo\":\"\",\"listRemoteFolderItems\":false," +
                        "\"repoType\":\"AIEditorExtensions\"," +
                        "\"enableTokenAuthentication\":false}",
                actual);
    }
}