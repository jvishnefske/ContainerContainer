package org.artifactory.version.v30;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static org.artifactory.logging.converter.LogbackConverterUtils.addFilterToAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.replaceAppenderFilterClass;

/**
 * @author Dor Solomon
 */
@Slf4j
public class ConsoleLogsFilterConverter implements XmlConverter {
    protected static final String CONSOLE_FILTER_CLASS = "org.artifactory.logging.filters.ConsoleFilter";
    protected static final String CONSOLE_JSON_FORMAT_FILTER_CLASS =
            "org.artifactory.logging.filters.ConsoleJsonFormatFilter";
    protected static final String CONSOLE_APPENDER = "CONSOLE";
    protected static final String ACCESS_APPENDER = "ACCESS_STDOUT";
    protected static final String SERVICE_APPENDER = "FILE_STDOUT";
    protected static final String REQUEST_APPENDER = "REQUEST_STDOUT";
    protected static final String REQUEST_OUT_APPENDER = "REQUEST_OUT_STDOUT";
    private static final String STANDARD_FORMAT_FILTER_CONTENT =
            "<filter class=\"org.artifactory.logging.filters.ConsoleStandardFormatFilter\"/>";


    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Console log filters");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        // Add standard format filter to console
        addFilterToAppender(root, ns, CONSOLE_APPENDER, STANDARD_FORMAT_FILTER_CONTENT);

        // artifactory-access stdout
        replaceAppenderFilterClass(root, ns, ACCESS_APPENDER, CONSOLE_FILTER_CLASS, CONSOLE_JSON_FORMAT_FILTER_CLASS);

        // artifactory-service stdout
        replaceAppenderFilterClass(root, ns, SERVICE_APPENDER, CONSOLE_FILTER_CLASS, CONSOLE_JSON_FORMAT_FILTER_CLASS);

        // artifactory-request stdout
        replaceAppenderFilterClass(root, ns, REQUEST_APPENDER, CONSOLE_FILTER_CLASS, CONSOLE_JSON_FORMAT_FILTER_CLASS);

        //artifactory-request-out stdout
        replaceAppenderFilterClass(root, ns, REQUEST_OUT_APPENDER, CONSOLE_FILTER_CLASS,
                CONSOLE_JSON_FORMAT_FILTER_CLASS);

        log.debug("Console log filter conversion has been completed.");
    }
}