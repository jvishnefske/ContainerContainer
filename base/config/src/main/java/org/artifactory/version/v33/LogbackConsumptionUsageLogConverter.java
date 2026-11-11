package org.artifactory.version.v33;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * This converter adds appenders and loggers required for the consumption usage logs
 * (artifactory-consumption-metrics.log).
 *
 * @author BarakH
 */
@Slf4j
public class LogbackConsumptionUsageLogConverter implements XmlConverter {

    static final String APPENDER_NAME = "CONSUMPTION-USAGE-APPENDER";
    static final String LOGGER_NAME = "CONSUMPTION_USAGE";

    private static final String APPENDER_CONTENT = """
            <appender name="CONSUMPTION-USAGE-APPENDER" class="org.jfrog.metrics.MetricTimeBasedRollingFileAppender">
                    <File>${log.dir}/artifactory-consumption-usage.log</File>
                    <intervalMs>900000</intervalMs>
                    <rollingPolicy class="org.jfrog.common.logging.logback.rolling.FixedWindowWithDateRollingPolicy">
                        <maxIndex>150</maxIndex>
                        <FileNamePattern>${log.dir.archived}/artifactory-consumption-usage.%i.log.gz</FileNamePattern>
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
    private static final String LOGGER_CONTENT = """
            <logger name="CONSUMPTION_USAGE" additivity="false" level="INFO">
                <appender-ref ref="CONSUMPTION-USAGE-APPENDER"/>
            </logger>
                """;


    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding consumption usage logs (artifactory-consumption-usage.log).");
        Element root = doc.getRootElement();
        addAppender(root, root.getNamespace(), APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, root.getNamespace(), LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Logback conversion completed: consumption usage logs was added successfully.");
    }
}
