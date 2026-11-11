package org.artifactory.version.v41;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

@Slf4j
public class LogbackCleanupAuditLogConverter implements XmlConverter {

    static final String APPENDER_NAME = "CLEANUP_AUDIT_FILE";

    static final String LOGGER_NAME =
            "org.artifactory.addon.cleanup.task.PackageCleanupRunnableImpl.cleanupAudit";

    static final String ARCHIVED_FILENAME_PATTERN = "${log.dir.archived}/cleanup/artifactory-cleanup-audit.%i.log.gz";

    static final String APPENDER_CONTENT =
            "<appender name=\"%s\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">".formatted(
                    APPENDER_NAME) + """
                        <File>${log.dir}/artifactory-cleanup-audit.log</File>
                        <rollingPolicy class="ch.qos.logback.core.rolling.FixedWindowRollingPolicy">
                            <fileNamePattern>%s</fileNamePattern>
                            <minIndex>1</minIndex>
                            <maxIndex>1000</maxIndex>
                        </rollingPolicy>
                        <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
                            <maxFileSize>1GB</maxFileSize>
                        </triggeringPolicy>
                        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                            <layout class="org.artifactory.logging.layout.cleanup.CleanupAuditJsonLayout">
                            </layout>
                        </encoder>
                    </appender>
                    """.formatted(ARCHIVED_FILENAME_PATTERN);
    static final String LOGGER_CONTENT = """
            <logger name="%s" level="INFO" additivity="false">
                <appender-ref ref="CLEANUP_AUDIT_FILE" />
            </logger>
            """.formatted(LOGGER_NAME);

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding cleanup audit logger.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Logback conversion completed: cleanup audit logger was added successfully.");
    }
}