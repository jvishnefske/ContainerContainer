package org.artifactory.version.v38;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
public class FederationLogAppenderConverter implements XmlConverter {

    static final String APPENDER_NAME = "FEDERATION-APPENDER";
    static final String STDOUT_APPENDER_NAME = "FEDERATION_STDOUT";
    static final String FEDERATION_LOGGER_PACKAGE = "org.artifactory.addon.federated";
    static final String MIRROR_LOGGER_PACKAGE = "org.artifactory.addon.mirror.internal";

    private static final String APPENDER_CONTENT =
            "<appender name=\"%s\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">%n".formatted(
                    APPENDER_NAME) +
                    "                <File>${log.dir}/artifactory-service-federation.log</File>\n" +
                    "                <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "                    <FileNamePattern>${log.dir.archived}/artifactory-service-federation.%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "                    <compressLatest>true</compressLatest>\n" +
                    "                </rollingPolicy>\n" +
                    "                <triggeringPolicy class=\"org.jfrog.common.logging.logback.triggering" +
                    ".SizeAndIntervalTriggeringPolicy\">\n" +
                    "                    <MaxFileSize>25MB</MaxFileSize>\n" +
                    "                </triggeringPolicy>\n" +
                    "                <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "                    <layout class=\"org.jfrog.common.logging.logback.layout" +
                    ".BackTracePatternLayout\">\n" +
                    "                        <pattern>%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [jfrt ] [%-5p] " +
                    "[%-16X{uber-trace-id}] [%-30.30(%c{3}:%L)] [%-20.20thread] - %m%n%ex{full, " +
                    "THROTTLE_EXCEPTIONS_NO_METRICS}</pattern>\n" +
                    "                    </layout>\n" +
                    "                </encoder>\n" +
                    "            </appender>";

    private static final String STDOUT_APPENDER_CONTENT = """
                <appender name="%s" class="ch.qos.logback.core.ConsoleAppender">
                    <filter class="org.artifactory.logging.filters.ConsoleJsonFormatFilter"/>
                    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                        <layout class="org.artifactory.logging.layout.federation.ServiceFederationJsonLayout">
                        </layout>
                    </encoder>
                </appender>
            """.formatted(STDOUT_APPENDER_NAME);
    private static final String FEDERATION_LOGGER_CONTENT = """
            <logger name="%s" additivity="false" level="INFO">
                <appender-ref ref="%s"/>
                <appender-ref ref="%s"/>
            </logger>
                """.formatted(FEDERATION_LOGGER_PACKAGE, APPENDER_NAME, STDOUT_APPENDER_NAME);
    private static final String MIRROR_LOGGER_CONTENT = """
            <logger name="%s" additivity="false" level="INFO">
                <appender-ref ref="%s"/>
                <appender-ref ref="%s"/>
            </logger>
                """.formatted(MIRROR_LOGGER_PACKAGE, APPENDER_NAME, STDOUT_APPENDER_NAME);

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding federation logs (artifactory-service-federation.log).");
        Element root = doc.getRootElement();
        addAppender(root, root.getNamespace(), APPENDER_NAME, APPENDER_CONTENT);
        addAppender(root, root.getNamespace(), STDOUT_APPENDER_NAME, STDOUT_APPENDER_CONTENT);
        addLogger(root, root.getNamespace(), FEDERATION_LOGGER_PACKAGE, FEDERATION_LOGGER_CONTENT);
        addLogger(root, root.getNamespace(), MIRROR_LOGGER_PACKAGE, MIRROR_LOGGER_CONTENT);
        log.debug("Logback conversion completed: federation logs was added successfully.");
    }
}
