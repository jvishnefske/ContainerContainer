package org.artifactory.version.v31;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * This converter adds appenders and loggers required for the federation metrics logs
 * (artifactory-federation-metrics.log and artifactory-federation-metrics_events.log).
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
public class LogbackFederationMetricsLogConverter implements XmlConverter {

    static final String APPENDER_NAME = "FEDERATION-METRICS-APPENDER";
    static final String EVENTS_APPENDER_NAME = "FEDERATION-METRICS-EVENTS-APPENDER";
    static final String LOGGER_NAME = "FEDERATION_METRICS";
    static final String EVENTS_LOGGER_NAME = "FEDERATION_METRICS_EVENTS";

    private static final String APPENDER_CONTENT = """
            <appender name="FEDERATION-METRICS-APPENDER" class="org.jfrog.metrics.MetricTimeBasedRollingFileAppender">
                    <File>${log.dir}/artifactory-federation-metrics.log</File>
                    <intervalMs>900000</intervalMs>
                    <rollingPolicy class="org.jfrog.common.logging.logback.rolling.FixedWindowWithDateRollingPolicy">
                        <maxIndex>150</maxIndex>
                        <FileNamePattern>${log.dir.archived}/artifactory-federation-metrics.%i.log.gz</FileNamePattern>
                        <compressLatest>true</compressLatest>
                    </rollingPolicy>
                    <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
                        <MaxFileSize>1GB</MaxFileSize>
                    </triggeringPolicy>
                    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                        <layout class="org.jfrog.common.logging.logback.layout.BackTracePatternLayout">
                            <pattern>%m%n</pattern>
                        </layout>
                    </encoder>
                </appender>
                """;
    private static final String EVENTS_APPENDER_CONTENT = """
            <appender name="FEDERATION-METRICS-EVENTS-APPENDER" class="ch.qos.logback.core.rolling.RollingFileAppender">
                    <File>${log.dir}/artifactory-federation-metrics_events.log</File>
                    <rollingPolicy class="org.jfrog.common.logging.logback.rolling.FixedWindowWithDateRollingPolicy">
                <FileNamePattern>${log.dir.archived}/artifactory-federation-metrics_events.%i.log.gz</FileNamePattern>
                        <compressLatest>true</compressLatest>
                    </rollingPolicy>
                    <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
                        <MaxFileSize>25MB</MaxFileSize>
                    </triggeringPolicy>
                    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                        <layout class="org.jfrog.common.logging.logback.layout.BackTracePatternLayout">
                            <pattern>%m%n</pattern>
                        </layout>
                    </encoder>
                </appender>""";

    private static final String LOGGER_CONTENT = """
            <logger name="FEDERATION_METRICS" additivity="false" level="INFO">
                    <appender-ref ref="FEDERATION-METRICS-APPENDER"/>
                </logger>
                """;
    private static final String EVENTS_LOGGER_CONTENT = """
            <logger name="FEDERATION_METRICS_EVENTS" additivity="false" level="INFO">
                    <appender-ref ref="FEDERATION-METRICS-EVENTS-APPENDER"/>
                </logger>
                """;

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding federation metrics logs (artifactory-federation-metrics.log, " +
                  "artifactory-federation-metrics_events.log).");
        Element root = doc.getRootElement();
        addAppender(root, root.getNamespace(), APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, root.getNamespace(), LOGGER_NAME, LOGGER_CONTENT);
        addAppender(root, root.getNamespace(), EVENTS_APPENDER_NAME, EVENTS_APPENDER_CONTENT);
        addLogger(root, root.getNamespace(), EVENTS_LOGGER_NAME, EVENTS_LOGGER_CONTENT);
        log.debug("Logback conversion completed: federation metrics logs was added successfully.");
    }
}
