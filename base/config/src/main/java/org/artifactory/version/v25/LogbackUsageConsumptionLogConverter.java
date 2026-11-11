package org.artifactory.version.v25;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * This converter adds the appender and logger required for the usage consumption log (artifactory-usage.log).
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public class LogbackUsageConsumptionLogConverter implements XmlConverter {

    protected static final String APPENDER_NAME = "USAGE";
    protected static final String APPENDER_CLASS =
            "org.artifactory.usage.appender.UsageTrafficTimeBasedRollingFileAppender";
    protected static final String LOGGER_NAME = "org.artifactory.usage.logger.UsageConsumptionLogger";
    private static final String APPENDER_CONTENT =
            "<appender name=\"" + APPENDER_NAME + "\" class=\"" + APPENDER_CLASS + "\">\n" +
                    "        <intervalMs>21600000</intervalMs>\n" +
                    "        <File>${artifactory.usage.dir}/artifactory-usage.log</File>\n" +
                    "        <encoder>\n" +
                    "            <pattern>%m%n</pattern>\n" +
                    "        </encoder>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <maxIndex>150</maxIndex>\n" +
                    "            <FileNamePattern>${artifactory.usage.dir.archived}/artifactory-usage-%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "            <compressLatest>true</compressLatest>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>10MB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "    </appender>";
    private static final String LOGGER_CONTENT = "<logger name=\"" + LOGGER_NAME + "\" " +
            "level=\"info\" additivity=\"false\">\n" +
            "        <appender-ref ref=\"" + APPENDER_NAME + "\"/>\n" +
            "    </logger>\n";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion: adding usage consumption log (artifactory-usage.log).");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, APPENDER_NAME, APPENDER_CONTENT);
        addLogger(root, ns, LOGGER_NAME, LOGGER_CONTENT);
        log.debug("Logback conversion completed: usage consumption log was added successfully.");
    }
}
