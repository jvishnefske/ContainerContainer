package org.artifactory.version.converter.v333;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
public class ItemsBrowserPagingSizeConvertor implements XmlConverter {

    @Override
    public void convert(Document doc) {
        log.info("Starting add items browser paging size convertor.");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element treeBrowserPagingSize = rootElement.getChild("itemsBrowserPagingSize", namespace);
        if (treeBrowserPagingSize == null) {
            addTreeBrowserPagingSize(rootElement, namespace);
        }
    }

    private void addTreeBrowserPagingSize(Element rootElement, Namespace namespace) {
        Element treeBrowserPagingSize = new Element("itemsBrowserPagingSize", namespace);
        treeBrowserPagingSize.addContent("500");
        int trashcanConfigLocation = findLastLocation(rootElement, "trashcanConfig");
        if (trashcanConfigLocation > 0) {
            rootElement.addContent(trashcanConfigLocation + 1, new Text("\n            "));
            rootElement.addContent(trashcanConfigLocation + 2, treeBrowserPagingSize);
        }
        log.info("Finished 'Add items browser paging size' conversion successfully");
    }
}