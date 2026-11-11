package org.artifactory.version.converter.v349;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverterHelper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.util.RepoLayoutUtils.NIX_DEFAULT_NAME;

/**
 * For version 3.4.9
 *
 * @author Linoy Levi
 */
@Slf4j
public class AddNixDefaultLayoutConverter implements XmlConverter {
    public static final String NAME = "name";
    public static final String REPO_LAYOUTS = "repoLayouts";

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add Nix default repository layout' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild(REPO_LAYOUTS, namespace);
        if (!alreadyExists(repoLayoutsElement)) {
            addNixDefaultLayouts(repoLayoutsElement, namespace);
        }
        log.info("Finished 'Add Nix default repository layout' conversion successfully");
    }

    private boolean alreadyExists(Element repoLayoutsElement) {
        return repoLayoutsElement.getChildren()
                .stream()
                .anyMatch(element -> element.getChildren()
                        .stream()
                        .filter(e -> NAME.equals(e.getName()))
                        .anyMatch(e -> NIX_DEFAULT_NAME.equals(
                                e.getContent().getFirst().getValue())));
    }

    private void addNixDefaultLayouts(Element repoLayoutsElement, Namespace namespace) {
        log.info("Adding Nix default repository layout");
        repoLayoutsElement
                .addContent(AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace,
                        NIX_DEFAULT_NAME,
                        "binary-cache/[narinfoHash]/[narinfoHash].narinfo",
                        "false", null, ".*", ".*"));
    }
}
