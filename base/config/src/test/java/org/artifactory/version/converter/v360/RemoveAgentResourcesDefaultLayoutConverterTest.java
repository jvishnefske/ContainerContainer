package org.artifactory.version.converter.v360;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.artifactory.version.converter.v357.MigrateAgentResourcesToAgentPackagesConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RemoveAgentResourcesDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    private static final String FIXTURE = "/config/test/config.3.5.5_with_agentresources_repos.xml";

    @Test
    public void testRemovesLegacyLayoutDefinition() throws Exception {
        Document document = convertXml(FIXTURE, new RemoveAgentResourcesDefaultLayoutConverter());
        assertFalse(hasLegacyLayout(document), "Legacy agent-resources-default layout should be removed");
    }

    @Test
    public void testRewritesLegacyLayoutRefs() throws Exception {
        Document document = convertXml(FIXTURE, new RemoveAgentResourcesDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepo = getLocalRepo(rootElement, namespace, "legacy-agent-local");
        assertEquals(localRepo.getChildText("repoLayoutRef", namespace), RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME);

        Element federatedRepos = rootElement.getChild("federatedRepositories", namespace);
        Element federatedRepo = federatedRepos.getChildren("federatedRepository", namespace).stream()
                .filter(r -> "legacy-agent-federated".equals(r.getChildText("key", namespace)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("legacy-agent-federated repo not found"));
        assertEquals(federatedRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME);
    }

    @Test
    public void testCustomLayoutRefUnchanged() throws Exception {
        Document document = convertXml(FIXTURE, new RemoveAgentResourcesDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepo = getLocalRepo(rootElement, namespace, "legacy-agent-custom-layout");
        assertEquals(localRepo.getChildText("repoLayoutRef", namespace), "simple-default");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        Document document = convertXml(FIXTURE, new RemoveAgentResourcesDefaultLayoutConverter());
        new RemoveAgentResourcesDefaultLayoutConverter().convert(document);
        assertFalse(hasLegacyLayout(document), "Legacy layout should remain removed after second conversion");
    }

    @Test
    public void testV357ToV360ChainRemovesOrphanedLayout() throws Exception {
        Document document = convertXml(FIXTURE, new MigrateAgentResourcesToAgentPackagesConverter());
        assertTrue(hasLegacyLayout(document),
                "v357 should leave the legacy layout definition in repoLayouts");

        new RemoveAgentResourcesDefaultLayoutConverter().convert(document);
        assertFalse(hasLegacyLayout(document),
                "v360 should remove the orphaned agent-resources-default layout after v357 migration");
    }

    @Test
    public void testConverterIsRegisteredForV360() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v360.getConverters();
        boolean hasRemoveLayoutConverter = Arrays.stream(converters)
                .anyMatch(c -> c instanceof RemoveAgentResourcesDefaultLayoutConverter);
        assertTrue(hasRemoveLayoutConverter,
                "RemoveAgentResourcesDefaultLayoutConverter should be registered for v360");
    }

    private boolean hasLegacyLayout(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        if (repoLayoutsElement == null) {
            return false;
        }
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();
        return repoLayoutElements.stream()
                .anyMatch(element -> "agent-resources-default".equals(element.getChildText("name", namespace)));
    }

    private Element getLocalRepo(Element rootElement, Namespace namespace, String repoKey) {
        Element localRepos = rootElement.getChild("localRepositories", namespace);
        return localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> repoKey.equals(r.getChildText("key", namespace)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(repoKey + " repo not found"));
    }
}
