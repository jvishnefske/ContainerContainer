package org.artifactory.repositories.migration;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.util.XmlUtils;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jfrog.common.ResourceUtils;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Objects;

import static org.testng.Assert.assertNotNull;

public class RepositoriesSchemaTest {

    @Test
    public void validateLocalRepositorySchema() {
        String configActual = getActualArtifactoryXsd();
        String config_v4 = getV4ArtifactoryXsd();
        Element actualRootElement = XmlUtils.parse(configActual).getRootElement();
        Element originalRootElement = XmlUtils.parse(config_v4).getRootElement();

        List<String> complexTypes = List.of("LocalRepositoriesType", "LocalRepoType", "FederatedRepositoriesType",
                "ReleaseBundlesRepositoriesType", "ReleaseBundlesRepoType", "FederatedRepoType", "RealRepoType",
                "RepoType", "RemoteRepositoriesType", "RemoteRepoType", "RemoteRepoBaseType", "ContentSynchronisation",
                "StatisticsContent", "PropertiesContent", "SourceContent", "AbstractRemoteRepoDelegate",
                "VirtualRepositoriesType", "VirtualRepoType", "P2ConfigurationType", "ExternalDependenciesConfigType",
                "ExternalDependenciesPatternsType", "VirtualCacheConfigType", "NuGetConfigurationType",
                "PypiConfigurationType", "BowerConfigurationType", "ComposerConfigurationType",
                "TerraformConfigurationType", "CocoaPodsConfigurationType", "HelmConfigurationType",
                "GitConfigurationType", "P2UrlsType", "RepositoryRefsType", "FormatType", "ReverseProxyRepoConfigType",
                "RepoXrayConfigType", "RepoDownloadRedirectConfigType", "RepoCdnRedirectConfigType", "CargoConfigType",
                "DownloadRedirectConfigType", "RepoLayoutType", "FederatedMembersType", "FederatedMemberType",
                "VcsType", "VcsGitType", "ReleaseBundlesConfigType", "ReleaseBundlesConfigType");

        StringBuilder errorsBuilder = new StringBuilder();

        for (String complexType : complexTypes) {
            Pair<Boolean, String> result = compareElement(actualRootElement, originalRootElement, complexType);
            if (result.getLeft()) {
                errorsBuilder = errorsBuilder.append(result.getRight());
            }
        }

        if (!errorsBuilder.toString().isEmpty()) {
            StringBuilder fullMessageBuilder =
                    new StringBuilder("Repo schema changes are not allowed because of repo migration to DB.");
            fullMessageBuilder.append(errorsBuilder);
            throw new IllegalStateException(fullMessageBuilder.toString());
        }
    }

    private Pair<Boolean, String> compareElement(Element actualElement, Element originalElement,
            String complexTypeName) {
        Element actualRepoType = findElementByAttribute(actualElement, "name", complexTypeName);
        Element originalRepoType = findElementByAttribute(originalElement, "name", complexTypeName);

        assertNotNull(actualRepoType, "Element with complex type " + complexTypeName + " is missed in the actual xsd");
        assertNotNull(originalElement,
                "Element with complex type " + complexTypeName + " is missed in the original xsd");

        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        String actual = out.outputString(actualRepoType);
        String original = out.outputString(originalRepoType);

        String diff1 = StringUtils.difference(original, actual);
        String diff2 = StringUtils.difference(actual, original);
        if (StringUtils.isNotEmpty(diff1)) {
            StringBuilder errorBuilder =
                    new StringBuilder("\n \n-->> Definition of type '")
                            .append(complexTypeName)
                            .append("' is changed: \n")
                            .append("---- actual ---- \n")
                            .append(diff1)
                            .append("\n \n---- original ----\n")
                            .append(diff2);
            return Pair.of(true, errorBuilder.toString());
        }
        return Pair.of(false, null);
    }

    private static String getActualArtifactoryXsd() {
        return ResourceUtils.getResourceAsString("/artifactory.xsd");
    }

    private static String getV4ArtifactoryXsd() {
        return ResourceUtils.getResourceAsString("/config/repositories.migration/artifactory_v321.xsd");
    }

    private static Element findElementByAttribute(Element element, String name, String value) {
        List<Element> children = element.getChildren();
        for (Element child : children) {
            if (Objects.nonNull(child.getAttribute(name)) && child.getAttribute(name).getValue().equals(value)) {
                return child;
            } else {
                Element result = findElementByAttribute(child, name, value);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}