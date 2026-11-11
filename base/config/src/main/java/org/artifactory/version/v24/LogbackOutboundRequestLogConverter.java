package org.artifactory.version.v24;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * This converter adds the appender and logger required for the outbound request log (artifactory-request-out.log).
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class LogbackOutboundRequestLogConverter implements XmlConverter {

    protected static final String APPENDER_NAME = "REQUEST_OUT";
    protected static final String LOGGER_NAME = "org.artifactory.traffic.OutboundRequestLogger";
    private static final String APPENDER_CONTENT =
            "<appender name=\"" + APPENDER_NAME + "\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">\n" +
                    "        <File>${log.dir}/artifactory-request-out.log</File>\n" +
                    "        <encoder>\n" +
                    "            <pattern>%m%n</pattern>\n" +
                    "        </encoder>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-request-out.%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "            <compressLatest>true</compressLatest>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>25MB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "    </appender>";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" " +
            "level=\"info\" additivity=\"false\">\n" +
            "        <appender-ref ref=\"" + APPENDER_NAME + "\"/>\n" +
            "    </logger>\n";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding outbound request log (artifactory-request-out.log).");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Logback conversion completed: outbound request log was added successfully.");
    }
}
