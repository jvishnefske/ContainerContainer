package org.artifactory.version.v26;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * This converter adds an appender and a logger required for the usage metrics (aka feature adoption) log
 * (usage-metrics.log).
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
public class LogbackFeatureAdoptionLogConverter implements XmlConverter {

    static final String APPENDER_NAME = "USAGE-METRICS-APPENDER";
    static final String LOGGER_NAME = "USAGE_METRICS";
    private static final String APPENDER_CLASS = "org.jfrog.metrics.MetricTimeBasedRollingFileAppender";
    private static final String APPENDER_CONTENT =
            "<appender name=\"" + APPENDER_NAME + "\" class=\"" + APPENDER_CLASS + "\">\n" +
                    "        <File>${artifactory.usage.dir}/usage-metrics.log</File>\n" +
                    "        <intervalMs>900000</intervalMs>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <maxIndex>150</maxIndex>\n" +
                    "            <FileNamePattern>${artifactory.usage.dir.archived}/usage-metrics.%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "            <compressLatest>true</compressLatest>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>1GB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"org.jfrog.common.logging.logback.layout.BackTracePatternLayout\">\n" +
                    "                <pattern>%m%n</pattern>\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" " +
            "additivity=\"false\" level=\"INFO\">\n" +
            "        <appender-ref ref=\"" + APPENDER_NAME + "\"/>\n" +
            "    </logger>\n";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding usage metrics log (usage-metrics.log).");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Logback conversion completed: usage metrics log was added successfully.");
    }
}
