package org.artifactory.version.converter.v3132;

import org.artifactory.common.repo.PackageType;
import org.artifactory.convert.XmlConverterTest;
import org.artifactory.version.converter.PackageArtifactoryRequestsCanRetrieveRemoteArtifactConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;


class PackageArtifactoryRequestsCanRetrieveRemoteArtifactsConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH =
            "/config/test/config.3.1.32_convert_artifactory_request_can_retrieve_remote_artifacts.xml";

    @Test
    void testConvert() throws Exception {
        verifyContent("false", getDocument(RESOURCE_PATH));
        Document doc = convertXml(RESOURCE_PATH,
                new PackageArtifactoryRequestsCanRetrieveRemoteArtifactConverter(PackageType.HELM));
        verifyContent("true", doc);
    }

    private void verifyContent(String expectedValue, Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element virtualRepositoriesElement = rootElement.getChild("virtualRepositories", namespace);
        List<Element> virtualRepositories = virtualRepositoriesElement.getChildren();
        for (Element virtualRepo : virtualRepositories) {
            if ("helm".equals(virtualRepo.getChild("type", namespace).getText())) {
                Assertions.assertEquals(expectedValue,
                        virtualRepo.getChild("artifactoryRequestsCanRetrieveRemoteArtifacts", namespace).getText());
            } else {
                Assertions.assertEquals("false",
                        virtualRepo.getChild("artifactoryRequestsCanRetrieveRemoteArtifacts", namespace).getText());
            }
        }
    }
}