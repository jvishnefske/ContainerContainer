package org.artifactory.version.converter.v357;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MigrateAgentResourcesToAgentPackagesConverterTest extends AddonsLayoutConverterTestBase {

    private static final String FIXTURE = "/config/test/config.3.5.5_with_agentresources_repos.xml";

    @Test
    public void testMigrateLocalAgentResourcesRepo() throws Exception {
        Document document = convertXml(FIXTURE, new MigrateAgentResourcesToAgentPackagesConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepo = getLocalRepo(rootElement, namespace, "legacy-agent-local");
        assertEquals(localRepo.getChildText("type", namespace), "agentpackages");
        assertEquals(localRepo.getChildText("repoLayoutRef", namespace), RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME);
    }

    @Test
    public void testMigrateFederatedAgentResourcesRepo() throws Exception {
        Document document = convertXml(FIXTURE, new MigrateAgentResourcesToAgentPackagesConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element federatedRepos = rootElement.getChild("federatedRepositories", namespace);
        Element federatedRepo = federatedRepos.getChildren("federatedRepository", namespace).stream()
                .filter(r -> "legacy-agent-federated".equals(r.getChildText("key", namespace)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("legacy-agent-federated repo not found"));
        assertEquals(federatedRepo.getChildText("type", namespace), "agentpackages");
        assertEquals(federatedRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME);
    }

    @Test
    public void testAgentResourcesCustomLayoutRefMigratesTypeOnly() throws Exception {
        Document document = convertXml(FIXTURE, new MigrateAgentResourcesToAgentPackagesConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepo = getLocalRepo(rootElement, namespace, "legacy-agent-custom-layout");
        assertEquals(localRepo.getChildText("type", namespace), "agentpackages");
        assertEquals(localRepo.getChildText("repoLayoutRef", namespace), "simple-default");
    }

    @Test
    public void testNonAgentResourcesRepoUnchanged() throws Exception {
        Document document = convertXml(FIXTURE, new MigrateAgentResourcesToAgentPackagesConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element genericRepo = getLocalRepo(rootElement, namespace, "generic-local");
        assertEquals(genericRepo.getChildText("type", namespace), "generic");
        assertEquals(genericRepo.getChildText("repoLayoutRef", namespace), "simple-default");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        Document document = convertXml(FIXTURE, new MigrateAgentResourcesToAgentPackagesConverter());
        new MigrateAgentResourcesToAgentPackagesConverter().convert(document);
        testMigrateLocalAgentResourcesRepoOnDocument(document);
    }

    @Test
    public void testConverterIsRegisteredForV357() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v357.getConverters();
        boolean hasMigrateConverter = Arrays.stream(converters)
                .anyMatch(c -> c instanceof MigrateAgentResourcesToAgentPackagesConverter);
        assertTrue(hasMigrateConverter,
                "MigrateAgentResourcesToAgentPackagesConverter should be registered for v357");
    }

    private void testMigrateLocalAgentResourcesRepoOnDocument(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element localRepo = getLocalRepo(rootElement, namespace, "legacy-agent-local");
        assertEquals(localRepo.getChildText("type", namespace), "agentpackages");
        assertEquals(localRepo.getChildText("repoLayoutRef", namespace), RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME);
    }

    private Element getLocalRepo(Element rootElement, Namespace namespace, String repoKey) {
        Element localRepos = rootElement.getChild("localRepositories", namespace);
        return localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> repoKey.equals(r.getChildText("key", namespace)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(repoKey + " repo not found"));
    }
}
