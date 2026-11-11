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
public class AddAgentPluginsDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    private static final String FIXTURE = "/config/test/config.3.5.2_without_agentplugins_layout.xml";

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME,
                "[module]/[version]/[module]-[version].[ext]", "false", null, ".*", ".*");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());

        int countAfterFirst = countAgentPluginsLayouts(document);
        assertEquals(countAfterFirst, 1, "Should have exactly one AgentPlugins layout after first conversion");

        new AddAgentPluginsDefaultLayoutConverter().convert(document);

        int countAfterSecond = countAgentPluginsLayouts(document);
        assertEquals(countAfterSecond, 1,
                "Should still have exactly one AgentPlugins layout after second conversion (idempotent)");
    }

    @Test
    public void testAgentPluginsRepoLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        List<Element> repos = localRepos.getChildren("localRepository", namespace);
        Element agentPluginsRepo =
                repos.stream().filter(r -> "agent-plugins-local".equals(r.getChildText("key", namespace))).findFirst()
                        .orElseThrow(() -> new AssertionError("agent-plugins-local repo not found"));
        assertEquals(agentPluginsRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME,
                "agent-plugins-local repoLayoutRef should be migrated from simple-default to agent-plugins-default");
    }

    @Test
    public void testFederatedAgentPluginsRepoLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element federatedRepos = rootElement.getChild("federatedRepositories", namespace);
        Element agentPluginsRepo = federatedRepos.getChildren("federatedRepository", namespace).stream()
                .filter(r -> "agent-plugins-federated".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("agent-plugins-federated repo not found"));
        assertEquals(agentPluginsRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME,
                "agent-plugins-federated repoLayoutRef should be migrated"
                        + " from simple-default to agent-plugins-default");
    }

    @Test
    public void testAgentPluginsRepoCustomLayoutMigrated() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element agentPluginsRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "agent-plugins-local-custom".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("agent-plugins-local-custom repo not found"));
        assertEquals(agentPluginsRepo.getChildText("repoLayoutRef", namespace),
                RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME,
                "agent-plugins-local-custom repoLayoutRef should be migrated"
                        + " from maven-2-default to agent-plugins-default");
    }

    @Test
    public void testNonAgentPluginsRepoLayoutNotChanged() throws Exception {
        Document document = convertXml(FIXTURE, new AddAgentPluginsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element genericRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "generic-local".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("generic-local repo not found"));
        assertEquals(genericRepo.getChildText("repoLayoutRef", namespace), "simple-default",
                "Non-agentplugins repo layout should not be changed");
    }

    @Test
    public void testConverterRegisteredForVersion() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v352.getConverters();
        boolean hasAgentPluginsConverter =
                Arrays.stream(converters).anyMatch(c -> c instanceof AddAgentPluginsDefaultLayoutConverter);
        assertTrue(hasAgentPluginsConverter, "AddAgentPluginsDefaultLayoutConverter should be registered for v352");
    }

    private int countAgentPluginsLayouts(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);

        return (int) repoLayoutsElement.getChildren().stream()
                .filter(element -> element.getChildren().stream().filter(e -> "name".equals(e.getName())).anyMatch(
                        e -> RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME.equals(e.getContent().getFirst().getValue())))
                .count();
    }
}
