package org.artifactory.version.v45;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.common.ResourceUtils;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * This converter adds the appender and logger required for the remote outbound request log
 * (storage-request-out.log).
 *
 * @author Idan Dadon
 */
@Slf4j
public class LogbackStorageOutboundRequestLogConverter implements XmlConverter {
    protected static final String APPENDER_NAME = "STORAGE_REQUEST_OUT";
    protected static final String CONSOLE_APPENDER_NAME = "STORAGE_REQUEST_OUT_STDOUT";
    protected static final String LOGGER_NAME = "org.jfrog.storage.audit.request.out";
    private static final String APPENDER_CONTENT =
            ResourceUtils.getResourceAsString("/appender/storageRequestOutAppender.xml");
    private static final String CONSOLE_APPENDER_CONTENT = """
                <appender name="%s" class="ch.qos.logback.core.ConsoleAppender">
                    <filter class="org.artifactory.logging.filters.ConsoleJsonFormatFilter"/>
                    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                        <layout class="org.artifactory.logging.layout.request.StorageOutboundRequestJsonLayout">
                        </layout>
                    </encoder>
                </appender>
            """.formatted(CONSOLE_APPENDER_NAME);
    private static final String LOGGER_CONTENT = """
                <logger name="%s" level="info" additivity="false">
                    <appender-ref ref="%s"/>
                    <appender-ref ref="%s"/>
                </logger>
            """.formatted(LOGGER_NAME, APPENDER_NAME, CONSOLE_APPENDER_NAME);

    @Override
    public void convert(Document doc) {
        log.debug(
                "Starting logback conversion: adding artifactory storage outbound request log " +
                        "(artifactory-storage-request-out.log)");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addAppender(root, ns, CONSOLE_APPENDER_NAME, CONSOLE_APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Logback conversion completed: artifactory storage outbound request log was added successfully.");
    }
}
