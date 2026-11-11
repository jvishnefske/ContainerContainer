package org.artifactory.version.converter.v349;

import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
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
 * For version 3.4.9
 *
 * @author Linoy Levi
 */
public class AddNixDefaultLayoutConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void testConvertFromPreviousConfigVersion() throws Exception {
        Document document = convertXml("/config/test/config.3.4.9_without_nix_layout.xml",
                                       new AddNixDefaultLayoutConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayoutElements = repoLayoutsElement.getChildren();

        checkLayout(repoLayoutElements, namespace, RepoLayoutUtils.NIX_DEFAULT_NAME,
                "binary-cache/[narinfoHash]/[narinfoHash].narinfo",
                    "false", null,
                    ".*",
                    ".*");
    }

    @Test
    public void testConverterIsIdempotent() throws Exception {
        // First conversion adds the Nix layout
        Document document = convertXml("/config/test/config.3.4.9_without_nix_layout.xml",
                new AddNixDefaultLayoutConverter());

        // Count Nix layouts after first conversion
        int countAfterFirstConversion = countNixLayouts(document);
        assertEquals(countAfterFirstConversion, 1,
                "Should have exactly one Nix layout after first conversion");

        // Second conversion should not add duplicate
        new AddNixDefaultLayoutConverter().convert(document);

        int countAfterSecondConversion = countNixLayouts(document);
        assertEquals(countAfterSecondConversion, 1,
                "Should still have exactly one Nix layout after second conversion (idempotent)");
    }

    @Test
    public void testConverterRegisteredForVersion71371() {
        // Verify converter is registered for v350 which maps to version 7.137.1
        ArtifactoryConfigVersion v350 = ArtifactoryConfigVersion.v350;

        // Check that v350 is associated with version 7.137.1
        assertEquals(v350.getVersion(), ArtifactoryServiceVersionProvider.v71371.get(),
                "v350 should be associated with version 7.137.1");

        // Check that AddNixDefaultLayoutConverter is registered for v350
        XmlConverter[] converters = v350.getConverters();
        boolean hasNixConverter = Arrays.stream(converters)
                .anyMatch(c -> c instanceof AddNixDefaultLayoutConverter);
        assertTrue(hasNixConverter, "AddNixDefaultLayoutConverter should be registered for v350 (7.137.1)");
    }

    private int countNixLayouts(Document document) {
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);

        return (int) repoLayoutsElement.getChildren().stream()
                .filter(element -> element.getChildren().stream()
                        .filter(e -> "name".equals(e.getName()))
                        .anyMatch(e -> RepoLayoutUtils.NIX_DEFAULT_NAME.equals(
                                e.getContent().getFirst().getValue())))
                .count();
    }

}