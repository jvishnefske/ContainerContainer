package org.artifactory.version.converter.v311;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.convert.XmlConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * @author AndreiK.
 */
@Test
public class DockerRemoteRepositoryCacheRetrievalConverterTest extends XmlConverterTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    /**
     * Verify that converter is skipped and dockerhub repos
     * retrievalCachePeriodSecs is not modified in AOL env
     *
     * @throws Exception
     */
    @Test
    public void convertAol() throws Exception {
        //Given
        //Create Mocks
        ArtifactoryHome.bind(Mockito.mock(ArtifactoryHome.class));
        ArtifactorySystemProperties properties = new ArtifactorySystemProperties();
        //Turn on skip converters
        properties.setProperty(ConstantValues.aolSkipSkipableConverters.getPropertyName(), Boolean.toString(true));
        Mockito.when(ArtifactoryHome.get().getArtifactoryProperties()).thenReturn(properties);
        //Spy Document and override toString
        Document document = Mockito.spy(getDocument("/config/test/config.3.1.1_docker_retrieval_period.xml"));
        Mockito.doReturn(StringUtils.EMPTY).when(document).toString();
        //Create Converter
        DockerRemoteRepositoryCacheRetrievalConverter converter = new DockerRemoteRepositoryCacheRetrievalConverter();

        //When
        converter.convert(document);

        //Then
        //Verify converter is skipped
        Mockito.verify(document, Mockito.never()).getRootElement();
        Namespace namespace = document.getRootElement().getNamespace();
        Set<String> dockerHubRepos = Stream.of("docker-remote-https", "docker-remote-http").collect(Collectors.toSet());
        List<Element> remoteRepositories = document.getRootElement().getChild("remoteRepositories", namespace)
                .getChildren()
                .stream()
                .filter(e -> dockerHubRepos.contains(e.getChild("key", namespace).getText()))
                .collect(Collectors.toList());

        for (Element remoteRepo : remoteRepositories) {
            String periodSecs = remoteRepo.getChild("retrievalCachePeriodSecs", namespace).getText();
            //Verify retrievalCachePeriodSecs was not modified
            assertNotEquals(periodSecs, "21600");
        }
    }

    @Test
    public void convert() throws Exception {
        Document document = convertXml("/config/test/config.3.1.1_docker_retrieval_period.xml",
                new DockerRemoteRepositoryCacheRetrievalConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<Element> remoteRepositories = rootElement.getChild("remoteRepositories", namespace).getChildren();
        for (Element remoteRepo : remoteRepositories) {
            String repoKey = remoteRepo.getChild("key", namespace).getText();
            String periodSecs = remoteRepo.getChild("retrievalCachePeriodSecs", namespace).getText();
            if (repoKey.equals("bintray-docker-remote")) {
                assertEquals(periodSecs, "7200");
            }
            if (repoKey.equals("docker-remote-https")) {
                assertEquals(periodSecs, "21600");
            }
            if (repoKey.equals("docker-remote-https-with-creds")) {
                assertEquals(periodSecs, "7200");
                assertEquals(remoteRepo.getChild("username", namespace).getText(), "lucky");
            }
            if (repoKey.equals("docker-remote-http")) {
                assertEquals(periodSecs, "21600");
            }
            if (repoKey.equals("high")) {
                assertEquals(periodSecs, "99999");
            }
            if (repoKey.equals("low")) {
                assertEquals(periodSecs, "500");
            }
            if (repoKey.equals("debian-remote")) {
                assertEquals(periodSecs, "7200");
            }
            if (repoKey.equals("maven-central")) {
                assertEquals(periodSecs, "7200");
            }
        }
    }
}
