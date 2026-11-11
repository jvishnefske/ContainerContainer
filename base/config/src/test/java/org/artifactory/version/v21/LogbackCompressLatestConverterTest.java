package org.artifactory.version.v21;

import org.artifactory.convert.LogbackConverterTest;
import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogbackCompressLatestConverterTest extends LogbackConverterTest {

    @Test
    void testAddCompressLatestTagWhereNeeded() throws Exception {
        Document doc = convertXml("/org/artifactory/logging/version/v21/without_compressLatest.xml",
                new LogbackCompressLatestConverter());

        List<String> allowedLogNames = Arrays.asList("-service.", "-request.", "-access.", "-event.");
        Element root = doc.getRootElement();

        List<Element> allLogs = root.getChildren().stream()
                .map(Element::getChildren)
                .flatMap(List::stream)
                .filter(element -> element.getName().contains("rollingPolicy"))
                .filter(element -> element.getAttributeValue("class").contains("FixedWindowWithDateRollingPolicy"))
                .collect(Collectors.toList());

        List<Element> allArchiveExcludeLastTags = allLogs.stream()
                .map(Element::getChildren)
                .flatMap(List::stream)
                .filter(element -> element.getName().contains("compressLatest"))
                .collect(Collectors.toList());

        assertEquals(allArchiveExcludeLastTags.size(), allLogs.size() - allowedLogNames.size());
    }
}
