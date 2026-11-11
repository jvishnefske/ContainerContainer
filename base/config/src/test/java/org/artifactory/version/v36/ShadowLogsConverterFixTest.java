package org.artifactory.version.v36;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


import static org.artifactory.logging.converter.LogbackConverterUtils.ELEMENT_TYPE_APPENDER;

class ShadowLogsConverterFixTest extends LogbackConverterTest {

    @Test
    void testConvert() throws Exception {
        String expectedPattern =
                "%date{yyyy-MM-dd'T'HH:mm:ss.SSS, UTC}Z [jfrt ] [%-5p] [%-16X{uber-trace-id}] [%-30.30(%c{3}:%L)] " +
                        "[%-20.20thread] - %m%n";
        Document doc = convertXml("/org/artifactory/logging/version/v36/before_shadow_fix.xml",
                new ShadowLogsConverterFix());
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();
        assertLoggerExists(root, ns, "org.artifactory.rest.common.ShadowResponseFilter");
        Assertions.assertEquals(expectedPattern, getPatternValue(root, ns));
    }

    private String getPatternValue(Element root, Namespace ns) {
        Element foundAppender = getElement(root, ns, ELEMENT_TYPE_APPENDER, "SHADOW");
        return foundAppender.getChild("encoder").getChild("layout").getChild("pattern").getValue();
    }
}