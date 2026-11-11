package org.artifactory.version.converter.v313;

import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;


import static org.artifactory.version.converter.v313.DockerLocalRepositoryRetentionPolicyConverter.DOCKER_RETENTION_FIELD_NAME;
import static org.artifactory.version.converter.v313.DockerLocalRepositoryRetentionPolicyConverter.isDockerRepo;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test
public class DockerLocalRepositoryRetentionPolicyConverterTest extends XmlConverterTest {

    public void testAddDockerTagRetentionField() throws Exception {
        Document doc = convertXml("/config/test/config.2.2.3.with.docker.repos.xml",
                new DockerLocalRepositoryRetentionPolicyConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        assertFieldAddedToDockerRepos(rootElement, namespace);
    }

    private void assertFieldAddedToDockerRepos(Element rootElement, Namespace namespace) {
        Element repos = rootElement.getChild("localRepositories", namespace);
        assertNotNull(repos);
        List<Element> repositories = repos.getChildren("localRepository", namespace);
        assertNotNull(repositories);
        assertTrue(repositories.size() > 0);
        for (Element repository : repositories) {
            if (isDockerRepo(repository, namespace)) {
                Element element = repository.getChild(DOCKER_RETENTION_FIELD_NAME, namespace);
                assertNotNull(element);
                assertEquals(element.getText(), "1");
            }
        }
    }

}