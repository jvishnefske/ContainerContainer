package org.artifactory.version.v49;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;

/**
 * Attaches the error-catalog appender to existing {@code logback.xml} files on upgrade: adds the
 * {@code ERROR_CATALOG} appender and references it from the root logger. The appender itself does
 * nothing until the {@code devops-agent.error.catalog.appender.enabled} flag is turned on, so the
 * attachment is dormant by default.
 */
@Slf4j
public class LogbackErrorCatalogAppenderConverter implements XmlConverter {

    public static final String ERROR_CATALOG_APPENDER = "ERROR_CATALOG";

    private static final String ERROR_CATALOG_APPENDER_CLASS =
            "org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogLogbackAppender";

    private static final String ERROR_CATALOG_APPENDER_CONTENT =
            "<appender name=\"" + ERROR_CATALOG_APPENDER + "\" class=\"" + ERROR_CATALOG_APPENDER_CLASS + "\"/>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting error-catalog log conversion: adding ERROR_CATALOG appender");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, ERROR_CATALOG_APPENDER, ERROR_CATALOG_APPENDER_CONTENT);
        addRootAppenderRef(root, ns, ERROR_CATALOG_APPENDER);
        log.debug("Completed error-catalog log conversion");
    }

    private void addRootAppenderRef(Element root, Namespace ns, String appenderName) {
        Element rootLogger = root.getChild("root", ns);
        if (rootLogger == null) {
            log.warn("No <root> logger found in logback.xml, skipping {} appender-ref", appenderName);
            return;
        }
        boolean alreadyReferenced = rootLogger.getChildren("appender-ref", ns).stream()
                .anyMatch(ref -> appenderName.equals(ref.getAttributeValue("ref")));
        if (alreadyReferenced) {
            log.info("appender-ref {} already present on <root>, skipping conversion", appenderName);
            return;
        }
        rootLogger.addContent(new Text("\n    "));
        rootLogger.addContent(new Element("appender-ref", ns).setAttribute("ref", appenderName));
    }
}
