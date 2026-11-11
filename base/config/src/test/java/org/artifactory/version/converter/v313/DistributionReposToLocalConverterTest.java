package org.artifactory.version.converter.v313;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author Volodymyr Shulga
 */
@Test
public class DistributionReposToLocalConverterTest  extends AddonsLayoutConverterTestBase {
    private final DistributionReposToLocalConverter converter = new DistributionReposToLocalConverter();

    @Test
    public void testConvertForAbsentDistributionRepositories() throws Exception {
        String CONFIG_XML = "/config/test/config.3.1.3_add_basic_auth.xml";
        Document originalDoc = getDocument(CONFIG_XML);
        final Element rootElement = originalDoc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element distributionRepositories = rootElement.getChild("distributionRepositories", namespace);
        assertNull(distributionRepositories);
        Element localRepositories = rootElement.getChild("localRepositories", namespace);
        List<Element> localRepos = localRepositories.getChildren("localRepository", namespace);

        Document document = convertXml(CONFIG_XML, converter);
        Element root = document.getRootElement();
        Element localRepositoriesAfterConvert = root.getChild("localRepositories", namespace);
        List<Element> localReposAfterConvert = localRepositoriesAfterConvert.getChildren("localRepository", namespace);

        assertEquals(localReposAfterConvert.size(), localRepos.size());
    }

    @Test
    public void testConvertForDistributionRepositories() throws Exception {
        String CONFIG_XML = "/config/test/config.3.1.15_convert_distribution_repositories.xml";
        Document originalDoc = getDocument(CONFIG_XML);
        final Element rootElement = originalDoc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element distributionRepositories = rootElement.getChild("distributionRepositories", namespace);
        assertNotNull(distributionRepositories);
        List<Element> distributionRepos = distributionRepositories.getChildren("distributionRepository", namespace);
        assertEquals(distributionRepos.size(), 1);
        Element localRepositories = rootElement.getChild("localRepositories", namespace);
        List<Element> localRepos = localRepositories.getChildren("localRepository", namespace);
        assertEquals(localRepos.size(), 2);
        assertNotNull(rootElement.getChild("bintrayConfig", namespace));
        assertNotNull(rootElement.getChild("bintrayApplications", namespace));
        Element originalFederatedRepositories = rootElement.getChild("federatedRepositories", namespace);
        assertNotNull(originalFederatedRepositories);
        List<Element> originalFederatedRepos = originalFederatedRepositories.getChildren(
                "federatedRepository", namespace);
        for (Element federatedRepo : originalFederatedRepos) {
            assertNull(federatedRepo.getChild("bintrayApplicationRef"));
        }

        Document document = convertXml(CONFIG_XML, converter);
        Element root = document.getRootElement();
        // distribution repositories are removed after convert.
        Element distributionRepositoriesAfterConvert = root.getChild("distributionRepositories", namespace);
        assertNull(distributionRepositoriesAfterConvert);

        // distribution repositories were converted to local
        Element localRepositoriesAfterConvert = root.getChild("localRepositories", namespace);
        List<Element> localReposAfterConvert = localRepositoriesAfterConvert.getChildren("localRepository", namespace);

        // new repository name equals to distribution.
        assertEquals(localRepos.size() + distributionRepos.size(), localReposAfterConvert.size());
        assertEquals(
                getContent(localReposAfterConvert.get(2), "key", namespace),
                getContent(distributionRepos.get(0), "key", namespace)
        );

        assertNull(root.getChild("bintrayConfig", namespace));
        assertNull(root.getChild("bintrayApplications", namespace));

        Element federatedRepositories = root.getChild("federatedRepositories", namespace);
        assertNotNull(federatedRepositories);
        List<Element> federatedRepos = federatedRepositories.getChildren("federatedRepository", namespace);
        for (Element federatedRepo : federatedRepos) {
            assertNull(federatedRepo.getChild("bintrayApplicationRef"));
        }
    }

    private String getContent(Element repo, String attribute, Namespace namespace) {
        return repo.getChild(attribute, namespace).getText();
    }
}
