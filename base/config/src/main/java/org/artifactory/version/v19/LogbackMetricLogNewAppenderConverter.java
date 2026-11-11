package org.artifactory.version.v19;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.removeAppenderIfExists;

public class LogbackMetricLogNewAppenderConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackMetricLogNewAppenderConverter.class);

    protected static final String APPENDER_NAME = "METRICS-APPENDER";
    private static final String APPENDER_CONTENT =
            "<appender name=\"" + APPENDER_NAME +
                    "\" class=\"org.jfrog.metrics.MetricTimeBasedRollingFileAppender\">\n" +
                    "        <File>${log.dir}/artifactory-metrics.log</File>\n" +
                    "        <intervalMs>900000</intervalMs>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <maxIndex>150</maxIndex>\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-metrics.%i.log" +
                    ".gz</FileNamePattern>\n" +
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

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Replace appender for metric log.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        removeAppenderIfExists(root, ns, APPENDER_NAME);
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);

        log.debug("Migration logs conversion completed.");
    }

}
