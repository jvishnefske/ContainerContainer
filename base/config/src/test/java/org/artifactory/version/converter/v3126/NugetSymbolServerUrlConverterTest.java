package org.artifactory.version.converter.v3126;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Shahaf Golan
 */
public class NugetSymbolServerUrlConverterTest extends XmlConverterTest {

    private static final String RESOURCE_PATH = "/config/test/config.3.1.26_convert_nuget_symbol_server_url.xml";
    private static final String FIRST_REPO_NUGET = "nuget-remote";
    private static final String SECOND_REPO_NUGET = "nuget-remote-2";
    private static final String NUGET_OBJECT_DESCRIPTOR = "nuget";
    private static final String SYMBOL_SERVER_URL_FIELD = "symbolServerUrl";
    private static final int NUGET_FIELD_CONTENT_SIZE = 10;
    private static final String REMOTE_REPOSITORIES = "remoteRepositories";
    private static final String KEY_FIELD = "key";
    private static final String EXPECTED_SYMBOL_SERVER_URL_FIELD = "https://symbols.nuget.org/download/symbols";

    @Test
    public void convertNugetSymbolServerUrl() throws Exception {
        Document doc = convertXml(RESOURCE_PATH,
                new NugetSymbolServerUrlConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element remoteRepository1 = findRemoteRepositoryByKey(rootElement, namespace, FIRST_REPO_NUGET);
        Element remoteRepository2 = findRemoteRepositoryByKey(rootElement, namespace, SECOND_REPO_NUGET);
        Element nugetRemoteRepository1 = remoteRepository1.getChild(NUGET_OBJECT_DESCRIPTOR, namespace);
        Element nugetRemoteRepository2 = remoteRepository2.getChild(NUGET_OBJECT_DESCRIPTOR, namespace);
        assertNotNull(nugetRemoteRepository1);
        assertNotNull(nugetRemoteRepository2);
        assertEquals(NUGET_FIELD_CONTENT_SIZE, nugetRemoteRepository1.getContentSize());
        assertEquals(NUGET_FIELD_CONTENT_SIZE, nugetRemoteRepository2.getContentSize());
        int symbolServerLocation1 = findLastLocation(nugetRemoteRepository1, SYMBOL_SERVER_URL_FIELD);
        int symbolServerLocation2 = findLastLocation(nugetRemoteRepository2, SYMBOL_SERVER_URL_FIELD);
        assertEquals(EXPECTED_SYMBOL_SERVER_URL_FIELD,
                nugetRemoteRepository1.getContent(symbolServerLocation1).getValue());
        assertEquals(EXPECTED_SYMBOL_SERVER_URL_FIELD,
                nugetRemoteRepository2.getContent(symbolServerLocation2).getValue());
    }

    private int findLastLocation(Element parent, String... elements) {
        for (String element : elements) {
            Element child = parent.getChild(element, parent.getNamespace());
            if (child != null) {
                return parent.indexOf(child);
            }
        }
        return -1;
    }

    private Element findRemoteRepositoryByKey(Element rootElement, Namespace namespace, String replicationKey) {
        return findRepositoryByKey(rootElement.getChild(REMOTE_REPOSITORIES, namespace)
                , namespace, replicationKey);
    }

    private Element findRepositoryByKey(Element remoteRepository, Namespace namespace, String repositoryKey) {
        return remoteRepository
                .getChildren()
                .stream()
                .filter(element -> element.getChild(KEY_FIELD, namespace).getText().equals(repositoryKey))
                .findFirst().get();
    }
}