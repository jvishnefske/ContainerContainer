package org.artifactory.version.converter.v345;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * This converter will remove the archive index enabled value from the config descriptor.
 *
 * @author Evgeny Kurtser
 */
@Slf4j
public class ArchiveIndexDisableConverter implements XmlConverter {
    @Override
    public void convert(Document doc) {
        log.info("Starting 'archiveIndexDisabled' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        boolean removed = rootElement.removeChild("archiveIndexEnabled", namespace);
        log.info(removed ?
                "'archiveIndexEnabled' element was removed." :
                "'archiveIndexEnabled' element was not found. Quiting.");
        log.info("Finished removal of `archiveIndexDisabled`");
    }
}
