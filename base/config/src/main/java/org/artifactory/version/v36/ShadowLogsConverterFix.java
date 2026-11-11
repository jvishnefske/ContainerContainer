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

package org.artifactory.version.v36;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.logging.converter.LogbackConverterUtils.addLogger;
import static org.artifactory.logging.converter.LogbackConverterUtils.replaceAppenderEncoderPattern;

/**
 * 1. Fix Pattern of appender.
 * 2. Add shadowFilterResponse to use the shadow appender
 *
 * @author asafz
 */
public class ShadowLogsConverterFix implements XmlConverter {
    private static final Logger log = LoggerFactory.getLogger(ShadowLogsConverterFix.class);
    protected static final String SHADOW_APPENDER_NAME = "SHADOW";
    protected static final String SHADOW_LOGGER_NAME = "org.artifactory.rest.common.ShadowResponseFilter";
    private static final String CURRENT_PATTERN =
            "%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [jfrt ] %highlight([%-5p]) [%-16X{uber-trace-id}] [%-30.30" +
                    "(%c{3}:%L)] [%-20.20thread] - %m%n";
    private static final String PATTERN_TO_SET =
            "%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [jfrt ] [%-5p] [%-16X{uber-trace-id}] [%-30.30(%c{3}:%L)] [%-20" +
                    ".20thread] - %m%n";

    private static final String SHADOW_LOGGER_CONTENT =
            "    <logger name=\"" + SHADOW_LOGGER_NAME + "\" additivity=\"false\">\n" +
                    "        <level value=\"info\"/>\n" +
                    "        <appender-ref ref=\"" + SHADOW_APPENDER_NAME + "\"/>\n" +
                    "    </logger>";

    @Override
    public void convert(Document doc) {
        log.info("Starting logback conversion --> Replace shadow appender pattern.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        replaceAppenderEncoderPattern(root, ns, SHADOW_APPENDER_NAME, CURRENT_PATTERN, PATTERN_TO_SET);
        addLogger(root, ns, SHADOW_LOGGER_NAME, SHADOW_LOGGER_CONTENT);
        log.info("Migration logs conversion completed.");
    }
}