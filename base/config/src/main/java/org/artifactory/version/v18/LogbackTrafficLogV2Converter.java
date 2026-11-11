package org.artifactory.version.v18;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

public class LogbackTrafficLogV2Converter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackTrafficLogV2Converter.class);

    protected static final String APPENDER_NAME = "TRAFFIC_V2";
    private static final String APPENDER_CONTENT =
            "<appender name=\"" + APPENDER_NAME + "\" class=\"org.artifactory.traffic" +
                    ".TrafficTimeBasedRollingFileAppender\">\n" +
                    "        <intervalMs>900000</intervalMs>\n" +
                    "        <File>${log.dir}/artifactory-traffic-v2.log</File>\n" +
                    "        <encoder>\n" +
                    "            <pattern>%m%n</pattern>\n" +
                    "        </encoder>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <maxIndex>150</maxIndex>\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-traffic-v2-%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>1GB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "    </appender>";

    protected static final String LOGGER_NAME = "org.artifactory.traffic.v2.TrafficLoggerV2";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" " +
            "level=\"info\" additivity=\"false\">\n" +
            "        <appender-ref ref=\"" + APPENDER_NAME + "\"/>\n" +
            "    </logger>\n";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Adding traffic v2 logs.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);

        log.debug("Migration logs conversion completed.");
    }
}
