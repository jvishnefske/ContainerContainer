package org.artifactory.version.v46;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * @author Dvir Twina
 */
@Slf4j
public class LogbackTrafficLogV3Converter implements XmlConverter {

    protected static final String APPENDER_NAME = "TRAFFIC_V3";
    private static final String APPENDER_CONTENT =
            "<appender name=\"" + APPENDER_NAME + "\" class=\"org.artifactory.traffic" +
                    ".TrafficTimeBasedRollingFileAppender\">\n" +
                    "        <intervalMs>900000</intervalMs>\n" +
                    "        <File>${log.dir}/artifactory-traffic-v3.log</File>\n" +
                    "        <encoder>\n" +
                    "            <pattern>%m%n</pattern>\n" +
                    "        </encoder>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <maxIndex>500</maxIndex>\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-traffic-v3-%i.log" +
                    "</FileNamePattern>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>100MB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "    </appender>";

    protected static final String LOGGER_NAME = "org.artifactory.traffic.v3.TrafficLoggerV3";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" " +
            "level=\"info\" additivity=\"false\">\n" +
            "        <appender-ref ref=\"" + APPENDER_NAME + "\"/>\n" +
            "    </logger>\n";

    @Override
    public void convert(Document doc) {
        log.info("Starting logback conversion --> Adding Traffic_V3 logs.");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);

        log.info("Migration logs conversion of Traffic_V3 logs completed.");
    }
}
