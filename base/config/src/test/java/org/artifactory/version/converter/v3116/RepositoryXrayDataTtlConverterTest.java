package org.artifactory.version.converter.v3116;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
public class RepositoryXrayDataTtlConverterTest extends XmlConverterTest {

    @Test(dataProvider = "testRepositoryXrayDataTtlConverterDataProvider")
    public void testRepositoryXrayTtlConverter(String fileName, int expectedLocalRepositoryTtlElements,
            int expectedRemoteRepositoryTtlElements, int expectedFederatedRepositoryTtlElements) throws Exception {
        Document document = convertXml(fileName, new RepositoryXrayDataTtlConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> localRepositoryDataTtlElements =
                getAllXrayElements(rootElement, namespace, "localRepositories");
        assertEquals(localRepositoryDataTtlElements.size(), expectedLocalRepositoryTtlElements);
        assertTrue(isAllElementsDefault(localRepositoryDataTtlElements));
        List<Element> remoteRepositoryDataTtlElements =
                getAllXrayElements(rootElement, namespace, "remoteRepositories");
        assertEquals(remoteRepositoryDataTtlElements.size(), expectedRemoteRepositoryTtlElements);
        assertTrue(isAllElementsDefault(remoteRepositoryDataTtlElements));
        List<Element> federatedRepositoryDataTtlElements =
                getAllXrayElements(rootElement, namespace, "federatedRepositories");
        assertEquals(federatedRepositoryDataTtlElements.size(), expectedFederatedRepositoryTtlElements);
        assertTrue(isAllElementsDefault(federatedRepositoryDataTtlElements));
    }

    private List<Element> getAllXrayElements(Element rootElement, Namespace namespace, String elementName) {
        return Optional.ofNullable(rootElement.getChild(elementName, namespace))
                .orElse(new Element(elementName, namespace)).getChildren().stream()
                .map(localRepositoryElement -> localRepositoryElement.getChild("xray", namespace))
                .filter(Objects::nonNull)
                .map(dataTtlElement -> dataTtlElement.getChild("dataTtl", namespace))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean isAllElementsDefault(List<Element> list) {
        return list.stream().allMatch(element -> element.getText().equals("0"));
    }

    @DataProvider
    public static Object[][] testRepositoryXrayDataTtlConverterDataProvider() {
        return new Object[][] {
                {"/config/test/config.3.1.16_convert_xray_ttl_naive.xml", 4, 2, 1},
                {"/config/test/config.3.1.16_convert_xray_ttl_no_locals_1.xml", 0, 2, 0},
                {"/config/test/config.3.1.16_convert_xray_ttl_no_locals_2.xml", 0, 2, 0},
                {"/config/test/config.3.1.16_convert_xray_ttl_no_remotes_1.xml", 4, 0, 0},
                {"/config/test/config.3.1.16_convert_xray_ttl_no_remotes_2.xml", 4, 0, 0}
        };
    }
}
