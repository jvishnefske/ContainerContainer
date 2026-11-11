package org.artifactory.version.converter.v3370;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.artifactory.version.converter.v160.AddonsDefaultLayoutConverterHelper;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.util.RepoLayoutUtils.ANSIBLE_DEFAULT_NAME;

/**
 * For version 3.3.7
 *
 * @author BarakH
 */
@Slf4j
public class AddAnsibleDefaultLayoutConverter implements XmlConverter {


    public static final String NAME = "name";
    public static final String REPO_LAYOUTS = "repoLayouts";

    @Override
    public void convert(Document doc) {
        log.info("Starting 'Add Ansible default repository layout' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element repoLayoutsElement = rootElement.getChild(REPO_LAYOUTS, namespace);
        if (!alreadyExists(repoLayoutsElement)) {
            addAnsibleDefaultLayouts(repoLayoutsElement, namespace);
        }
        log.info("Finished 'Add Ansible default repository layout' conversion successfully");
    }

    private boolean alreadyExists(Element repoLayoutsElement) {
        return repoLayoutsElement.getChildren()
                       .stream()
                       .anyMatch(element -> element.getChildren()
                                                    .stream()
                                                    .filter(e -> NAME.equals(e.getName()))
                                                    .anyMatch(e -> ANSIBLE_DEFAULT_NAME.equals(
                                                            e.getContent().get(0).getValue())));
    }

    private void addAnsibleDefaultLayouts(Element repoLayoutsElement, Namespace namespace) {
        repoLayoutsElement
                .addContent(AddonsDefaultLayoutConverterHelper.getRepoLayoutElement(repoLayoutsElement, namespace,
                         ANSIBLE_DEFAULT_NAME,
                        "collections/[namespace]/[name]/[version]/[namespace]-[name]-[version].tar.gz",
                        "false", null, ".*", ".*"));
    }
}