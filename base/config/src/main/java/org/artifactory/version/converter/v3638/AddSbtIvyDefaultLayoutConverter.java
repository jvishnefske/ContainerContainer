package org.artifactory.version.converter.v3638;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverterHelper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.util.RepoLayoutUtils.SBT_IVY_DEFAULT_NAME;

/**
 * For version 3.4.4
 * @author Natalie Berni
 */
@Slf4j
public class AddSbtIvyDefaultLayoutConverter implements XmlConverter {


    public static final String NAME = "name";
    public static final String REPO_LAYOUTS = "repoLayouts";

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add Sbt-ivy default repository layout' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild(REPO_LAYOUTS, namespace);
        if (!alreadyExists(repoLayoutsElement)) {
            addSbtIvyDefaultLayouts(repoLayoutsElement, namespace);
        }
        log.info("Finished 'Add Sbt-ivy default repository layout' conversion successfully");
    }

    private boolean alreadyExists(Element repoLayoutsElement) {
        return repoLayoutsElement.getChildren()
                .stream()
                .anyMatch(element -> element.getChildren()
                        .stream()
                        .filter(e -> NAME.equals(e.getName()))
                        .anyMatch(e -> SBT_IVY_DEFAULT_NAME.equals(
                                e.getContent().get(0).getValue())));
    }

    private void addSbtIvyDefaultLayouts(Element repoLayoutsElement, Namespace namespace) {
        repoLayoutsElement
                .addContent(AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace,
                        SBT_IVY_DEFAULT_NAME,
                        "[orgPath]/[module]/[baseRev](-[folderItegRev])/" +
                                "[module]-[baseRev](-[fileItegRev])(-[classifier]).[ext]",
                        "true",
                        "[orgPath]/[module]/[baseRev](-[folderItegRev])/" +
                                "ivy-[baseRev](-[fileItegRev])(-[classifier]).xml",
                        "\\d{14}", "\\d{14}"));
    }
}
