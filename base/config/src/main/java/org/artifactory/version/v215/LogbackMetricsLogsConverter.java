/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.version.v215;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * Adds Metrics Framework related loggers.
 *
 * @author leela
 */
public class LogbackMetricsLogsConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(LogbackMetricsLogsConverter.class);
    protected static final String METRICS_APPENDER_NAME = "METRICS-APPENDER";
    protected static final String METRICS_LOGGER_NAME = "METRICS";
    protected static final String METRICS_EVENTS_APPENDER_NAME = "METRICS-EVENTS-APPENDER";
    protected static final String METRICS_EVENTS_LOGGER_NAME = "METRICS_EVENTS";


    private final String METRICS_APPENDER_CONTENT =
            " <appender name=\"" + METRICS_APPENDER_NAME + "\" class=\"ch.qos.logback.core" +
                    ".rolling.RollingFileAppender\">\n" +
                    "        <File>${log.dir}/artifactory-metrics.log</File>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-metrics.%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>25MB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"org.jfrog.common.logging.logback.layout.BackTracePatternLayout\">\n" +
                    "                <pattern>%m%n</pattern>\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";


    private final String METRICS_EVENTS_APPENDER_CONTENT =
            " <appender name=\"" + METRICS_EVENTS_APPENDER_NAME + "\" class=\"ch.qos.logback.core.rolling" +
                    ".RollingFileAppender\">\n" +
                    "        <File>${log.dir}/artifactory-metrics_events.log</File>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-metrics_events.%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>25MB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"org.jfrog.common.logging.logback.layout.BackTracePatternLayout\">\n" +
                    "                <pattern>%m%n</pattern>\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";


    private final String METRICS_LOGGER_CONTENT =
            "    <logger name=\"" + METRICS_LOGGER_NAME + "\" additivity=\"false\">\n" +
                    "        <level value=\"info\"/>\n" +
                    "        <appender-ref ref=\"" + METRICS_APPENDER_NAME + "\"/>\n" +
                    "    </logger>";

    private final String METRICS_EVENTS_LOGGER_CONTENT =
            "    <logger name=\"" + METRICS_EVENTS_LOGGER_NAME + "\" additivity=\"false\">\n" +
                    "        <level value=\"info\"/>\n" +
                    "        <appender-ref ref=\"" + METRICS_EVENTS_APPENDER_NAME + "\"/>\n" +
                    "    </logger>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Adding metrics logs.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, METRICS_EVENTS_APPENDER_NAME, METRICS_EVENTS_APPENDER_CONTENT);
        addLogger(root, ns, METRICS_EVENTS_LOGGER_NAME, METRICS_EVENTS_LOGGER_CONTENT);
        addAppender(root, ns, METRICS_APPENDER_NAME, METRICS_APPENDER_CONTENT);
        addLogger(root, ns, METRICS_LOGGER_NAME, METRICS_LOGGER_CONTENT);
        log.debug("Migration logs conversion completed.");
    }
}