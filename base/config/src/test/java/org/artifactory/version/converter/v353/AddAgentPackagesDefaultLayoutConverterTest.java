package org.artifactory.version.converter.v353;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Linoy Levi
 */
public class AddAgentPackagesDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    private static final String FIXTURE = "/config/test/config.3.5.2_without_agentplugins_layout.xml";

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        new AddAgentPackagesDefaultLayoutConverter().convert(document);
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME,
                "[scope]/[package-name]/[package-name]-[version].zip", "false", null, ".*", ".*");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        new AddAgentPackagesDefaultLayoutConverter().convert(document);

        int countAfterFirst = countAgentPackagesLayouts(document);
        assertEquals(countAfterFirst, 1, "Should have exactly one AgentPackages layout after first conversion");

        new AddAgentPackagesDefaultLayoutConverter().convert(document);

        int countAfterSecond = countAgentPackagesLayouts(document);
        assertEquals(countAfterSecond, 1,
                "Should still have exactly one AgentPackages layout after second conversion (idempotent)");
    }

    @Test
    public void testAgentPackagesRepoLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        new AddAgentPackagesDefaultLayoutConverter().convert(document);
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element agentPackagesRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "agent-packages-local".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("agent-packages-local repo not found"));
        assertEquals(agentPackagesRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME,
                "agent-packages-local repoLayoutRef should be migrated " +
                        "from simple-default to agent-packages-default");
    }

    @Test
    public void testNonAgentPackagesRepoLayoutNotChanged() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        new AddAgentPackagesDefaultLayoutConverter().convert(document);
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element genericRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "generic-local".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("generic-local repo not found"));
        assertEquals(genericRepo.getChildText("repoLayoutRef", namespace), "simple-default",
                "Non-agentpackages repo layout should not be changed");
    }

    @Test
    public void testConverterIsRegisteredForV353() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v353.getConverters();
        boolean hasAgentPackagesConverter =
                Arrays.stream(converters).anyMatch(c -> c instanceof AddAgentPackagesDefaultLayoutConverter);
        assertTrue(hasAgentPackagesConverter, "AddAgentPackagesDefaultLayoutConverter should be registered for v353");
    }

    @Test
    public void testConverterIsRegisteredForV355() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v355.getConverters();
        boolean hasAgentPackagesConverter =
                Arrays.stream(converters).anyMatch(c -> c instanceof AddAgentPackagesDefaultLayoutConverter);
        assertTrue(hasAgentPackagesConverter, "AddAgentPackagesDefaultLayoutConverter should be " +
                "registered for v355 to fix instances already past v353");
    }

    private int countAgentPackagesLayouts(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        return (int) repoLayoutsElement.getChildren().stream()
                .filter(element -> RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME.equals(
                        element.getChildText("name", namespace)))
                .count();
    }
}
