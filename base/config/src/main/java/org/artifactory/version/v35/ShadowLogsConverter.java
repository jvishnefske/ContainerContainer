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

package org.artifactory.version.v35;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;

/**
 * Adds Shadow testing related loggers.
 *
 * @author Michael Akushsky
 */
public class ShadowLogsConverter implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(ShadowLogsConverter.class);
    protected static final String SHADOW_APPENDER_NAME = "SHADOW";
    protected static final String SHADOW_LOGGER_NAME = "org.artifactory.shadow.ShadowRequestService";

    private static final String SHADOW_APPENDER_CONTENT =
            " <appender name=\"" + SHADOW_APPENDER_NAME +
                    "\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">\n" +
                    "        <File>${log.dir}/artifactory-shadow.log</File>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"org.jfrog.common.logging.logback.layout.BackTracePatternLayout\">\n" +
                    "                <pattern>%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [jfrt ] %highlight([%-5p]) " +
                    "[%-16X{uber-trace-id}] [%-30.30(%c{3}:%L)] [%-20.20thread] - %m%n</pattern>\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "        <rollingPolicy class=\"org.jfrog.common.logging.logback.rolling" +
                    ".FixedWindowWithDateRollingPolicy\">\n" +
                    "            <FileNamePattern>${log.dir.archived}/artifactory-shadow.%i.log" +
                    ".gz</FileNamePattern>\n" +
                    "            <compressLatest>true</compressLatest>\n" +
                    "        </rollingPolicy>\n" +
                    "        <triggeringPolicy class=\"ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\">\n" +
                    "            <MaxFileSize>10MB</MaxFileSize>\n" +
                    "        </triggeringPolicy>\n" +
                    "    </appender>";


    private static final String SHADOW_LOGGER_CONTENT =
            "    <logger name=\"" + SHADOW_LOGGER_NAME + "\" additivity=\"false\">\n" +
                    "        <level value=\"info\"/>\n" +
                    "        <appender-ref ref=\"" + SHADOW_APPENDER_NAME + "\"/>\n" +
                    "    </logger>";

    @Override
    public void convert(Document doc) {
        log.info("Starting logback conversion --> Adding shadow logs.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        addAppender(root, ns, SHADOW_APPENDER_NAME, SHADOW_APPENDER_CONTENT);
        addLogger(root, ns, SHADOW_LOGGER_NAME, SHADOW_LOGGER_CONTENT);
        log.info("Migration logs conversion completed.");
    }
}