package org.artifactory.version.converter.v304;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Aviv Anidjar
 */
public class RemoveTaskAffinityConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(RemoveTaskAffinityConverter.class);
    static final String TASK_EXECUTOR = "taskExecutor";

    @Override
    public void convert(Document doc) {
        log.info("Started {}", this.getClass().getSimpleName());
        Element affinityConfig = doc.getRootElement().getChild(TASK_EXECUTOR, doc.getRootElement().getNamespace());
        if (affinityConfig != null) {
            doc.getRootElement().removeChild(TASK_EXECUTOR, doc.getRootElement().getNamespace());
            log.info("Successfully removed '{}'", TASK_EXECUTOR);
        }
        log.info("Finished {}", this.getClass().getSimpleName());
    }
}
