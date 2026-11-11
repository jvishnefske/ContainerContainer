package org.artifactory.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.List;

/**
 * Base class for all fix layouts convertors
 *
 * @author Asaf Zalcman
 */
@Slf4j
public class BasicFixDefaultLayoutConverter implements XmlConverter {

    private static final String NAME_ELEMENT = "name";
    private static final String ARTIFACT_PATH_PATTERN_KEY = "artifactPathPattern";
    private final String name;
    private final String artifactPathPattern;

    public BasicFixDefaultLayoutConverter(String name, String artifactPathPattern) {
        this.name = name;
        this.artifactPathPattern = artifactPathPattern;
    }

    @Override
    public void convert(Document doc) {
        log.info("Starting 'fix {} repository layout' conversion", name);
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild("repoLayouts", namespace);
        List<Element> repoLayouts = repoLayoutsElement.getChildren();

        for (Element layout : repoLayouts) {
            if (layout.getContent() != null && layout.getChild(NAME_ELEMENT, namespace) != null
                    && name.equals(layout.getChild(NAME_ELEMENT, namespace).getText())) {
                log.debug("Found {} layout", name);
                Element pathPatternElement = layout.getChild(ARTIFACT_PATH_PATTERN_KEY, namespace);
                pathPatternElement.setText(artifactPathPattern);
                log.debug("Fixed {} layout successfully", name);
            }
        }
        log.info("Finished 'fix {} repository layout' conversion successfully", name);
    }
}