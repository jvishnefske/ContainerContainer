package org.artifactory.version.converter.v352;

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

public class AddSkillsDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.5.1_without_skills_layout.xml",
                new AddSkillsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.SKILLS_DEFAULT_NAME,
                "[module]/[version]/[module]-[version].[ext]", "false", null, ".*", ".*");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        Document document = convertXml("/config/test/config.3.5.1_without_skills_layout.xml",
                new AddSkillsDefaultLayoutConverter());

        int countAfterFirst = countSkillsLayouts(document);
        assertEquals(countAfterFirst, 1, "Should have exactly one Skills layout after first " + "conversion");

        new AddSkillsDefaultLayoutConverter().convert(document);

        int countAfterSecond = countSkillsLayouts(document);
        assertEquals(countAfterSecond, 1,
                "Should still have exactly one Skills layout after " + "second conversion (idempotent)");
    }

    @Test
    public void testSkillsRepoLayoutMigrated() throws Exception {
        Document document = convertXml("/config/test/config.3.5.1_without_skills_layout.xml",
                new AddSkillsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        List<Element> repos = localRepos.getChildren("localRepository", namespace);
        Element skillsRepo =
                repos.stream().filter(r -> "skills-local".equals(r.getChildText("key", namespace))).findFirst()
                        .orElseThrow(() -> new AssertionError("skills-local repo not found"));
        assertEquals(skillsRepo.getChildText("repoLayoutRef", namespace), RepoLayoutUtils.SKILLS_DEFAULT_NAME,
                "skills-local repoLayoutRef should be migrated" + " from simple-default to skills-default");
    }

    @Test
    public void testFederatedSkillsRepoLayoutMigrated() throws Exception {
        Document document = convertXml("/config/test/config.3.5.1_without_skills_layout.xml",
                new AddSkillsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element federatedRepos = rootElement.getChild("federatedRepositories", namespace);
        Element skillsRepo = federatedRepos.getChildren("federatedRepository", namespace).stream()
                .filter(r -> "skills-federated".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("skills-federated repo not found"));
        assertEquals(skillsRepo.getChildText("repoLayoutRef", namespace), RepoLayoutUtils.SKILLS_DEFAULT_NAME,
                "skills-federated repoLayoutRef should be migrated" + " from simple-default to skills-default");
    }

    @Test
    public void testSkillsRepoCustomLayoutMigrated() throws Exception {
        Document document = convertXml("/config/test/config.3.5.1_without_skills_layout.xml",
                new AddSkillsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element skillsRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "skills-local-custom".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("skills-local-custom repo not found"));
        assertEquals(skillsRepo.getChildText("repoLayoutRef", namespace), RepoLayoutUtils.SKILLS_DEFAULT_NAME,
                "skills-local-custom repoLayoutRef should be migrated" + " from maven-2-default to skills-default");
    }

    @Test
    public void testNonSkillsRepoLayoutNotChanged() throws Exception {
        Document document = convertXml("/config/test/config.3.5.1_without_skills_layout.xml",
                new AddSkillsDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element localRepos = rootElement.getChild("localRepositories", namespace);
        Element genericRepo = localRepos.getChildren("localRepository", namespace).stream()
                .filter(r -> "generic-local".equals(r.getChildText("key", namespace))).findFirst()
                .orElseThrow(() -> new AssertionError("generic-local repo not found"));
        assertEquals(genericRepo.getChildText("repoLayoutRef", namespace), "simple-default",
                "Non-skills repo layout should not be changed");
    }

    @Test
    public void testConverterRegisteredForVersion() {
        XmlConverter[] converters = ArtifactoryConfigVersion.v351.getConverters();
        boolean hasSkillsConverter =
                Arrays.stream(converters).anyMatch(c -> c instanceof AddSkillsDefaultLayoutConverter);
        assertTrue(hasSkillsConverter, "AddSkillsDefaultLayoutConverter should be registered" + " for v351");
    }

    private int countSkillsLayouts(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);

        return (int) repoLayoutsElement.getChildren().stream()
                .filter(element -> element.getChildren().stream().filter(e -> "name".equals(e.getName())).anyMatch(
                        e -> RepoLayoutUtils.SKILLS_DEFAULT_NAME.equals(e.getContent().getFirst().getValue()))).count();
    }
}
