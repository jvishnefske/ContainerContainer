package org.artifactory.version.v28;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;

import java.util.List;

import static org.artifactory.logging.converter.LogbackConverterUtils.addAppender;
import static org.artifactory.logging.converter.LogbackConverterUtils.logError;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class ConsoleLogsConverter implements XmlConverter {
    protected static final String CONSOLE_CLASS = "ch.qos.logback.core.ConsoleAppender";

    protected static final String ACCESS_APPENDER = "ACCESS_STDOUT";
    protected static final String ACCESS_LOGGER_NAME = "org.artifactory.security.AccessLogger";
    protected static final String SERVICE_APPENDER = "FILE_STDOUT";
    protected static final String REQUEST_APPENDER = "REQUEST_STDOUT";
    protected static final String REQUEST_LOGGER_NAME = "org.artifactory.traffic.RequestLogger";
    protected static final String REQUEST_OUT_APPENDER = "REQUEST_OUT_STDOUT";
    protected static final String REQUEST_OUT_LOGGER_NAME = "org.artifactory.traffic.OutboundRequestLogger";
    private static final String ACCESS_APPENDER_LAYOUT = "org.artifactory.logging.layout.access.AccessJsonLayout";
    private static final String ACCESS_APPENDER_CONTENT =
            " <appender name=\"" + ACCESS_APPENDER + "\" class=\"" + CONSOLE_CLASS + "\">\n" +
                    "        <filter class=\"org.artifactory.logging.filters.ConsoleFilter\"/>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"" + ACCESS_APPENDER_LAYOUT + "\">\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";
    private static final String SERVICE_APPENDER_LAYOUT = "org.artifactory.logging.layout.service.ServiceJsonLayout";
    private static final String SERVICE_APPENDER_CONTENT =
            " <appender name=\"" + SERVICE_APPENDER + "\" class=\"" + CONSOLE_CLASS + "\">\n" +
                    "        <filter class=\"org.artifactory.logging.filters.ConsoleFilter\"/>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"" + SERVICE_APPENDER_LAYOUT + "\">\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";
    private static final String REQUEST_APPENDER_LAYOUT = "org.artifactory.logging.layout.request.RequestJsonLayout";
    private static final String REQUEST_APPENDER_CONTENT =
            " <appender name=\"" + REQUEST_APPENDER + "\" class=\"" + CONSOLE_CLASS + "\">\n" +
                    "        <filter class=\"org.artifactory.logging.filters.ConsoleFilter\"/>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"" + REQUEST_APPENDER_LAYOUT + "\">\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";
    private static final String REQUEST_OUT_APPENDER_LAYOUT =
            "org.artifactory.logging.layout.request.OutboundRequestJsonLayout";
    private static final String REQUEST_OUT_APPENDER_CONTENT =
            " <appender name=\"" + REQUEST_OUT_APPENDER + "\" class=\"" + CONSOLE_CLASS + "\">\n" +
                    "        <filter class=\"org.artifactory.logging.filters.ConsoleFilter\"/>\n" +
                    "        <encoder class=\"ch.qos.logback.core.encoder.LayoutWrappingEncoder\">\n" +
                    "            <layout class=\"" + REQUEST_OUT_APPENDER_LAYOUT + "\">\n" +
                    "            </layout>\n" +
                    "        </encoder>\n" +
                    "    </appender>";

    @Override
    public void convert(Document doc) {
        log.debug("Starting logback conversion --> Console log appenders and loggers layout conversion");

        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        // artifactory-access stdout
        addAppender(root, ns, ACCESS_APPENDER, ACCESS_APPENDER_CONTENT);
        addAppenderRefElement(root, ns, ACCESS_APPENDER, ACCESS_LOGGER_NAME);

        // artifactory-service stdout
        addAppender(root, ns, SERVICE_APPENDER, SERVICE_APPENDER_CONTENT);
        root.getChild("root").addContent(getAppenderRefElement(ns, SERVICE_APPENDER));

        // artifactory-request stdout
        addAppender(root, ns, REQUEST_APPENDER, REQUEST_APPENDER_CONTENT);
        addAppenderRefElement(root, ns, REQUEST_APPENDER, REQUEST_LOGGER_NAME);

        //artifactory-request-out stdout
        addAppender(root, ns, REQUEST_OUT_APPENDER, REQUEST_OUT_APPENDER_CONTENT);
        addAppenderRefElement(root, ns, REQUEST_OUT_APPENDER, REQUEST_OUT_LOGGER_NAME);

        log.debug("Console log appenders and loggers conversion completed.");
    }

    private List<Content> getAppenderRefElement(Namespace ns, String appenderName) {
        return List.of(new Element("appender-ref", ns)
                .setAttribute("ref", appenderName), new Text("\n"));
    }

    private void addAppenderRefElement(Element root, Namespace ns, String appenderName, String loggerName) {
        try {
            Element appender = root.getChildren("logger")
                    .stream().filter(curr -> curr.getAttributeValue("name").equals(loggerName)).findFirst()
                    .orElse(null);
            if (appender == null) {
                logError(new Exception("The requested logger: " + loggerName + ", doesn't exist"), appenderName);
                return;
            }
            appender.addContent(getAppenderRefElement(ns, appenderName));
        } catch (Exception e) {
            logError(e, appenderName);
        }
    }
}
