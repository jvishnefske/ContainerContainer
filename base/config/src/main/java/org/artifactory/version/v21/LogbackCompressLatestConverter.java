package org.artifactory.version.v21;

import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LogbackCompressLatestConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackCompressLatestConverter.class);

    @Override
    public void convert(Document doc) {
        log.info("Starting logback conversion: adding compressLatest tag where needed.");

        List<String> allowedLogNames = Arrays.asList("-service.", "-request.", "-access.", "-event.");
        Element root = doc.getRootElement();

        List<Element> rollingPolicies = root.getChildren().stream()
                .map(Element::getChildren)
                .flatMap(List::stream)
                .filter(element -> element.getName().contains("rollingPolicy"))
                .filter(element -> element.getAttributeValue("class").contains("FixedWindowWithDateRollingPolicy"))
                .filter(element -> allowedLogNames.stream()
                        .noneMatch(str -> element.getChildText("FileNamePattern").contains(str)))
                .collect(Collectors.toList());

        for (Element element : rollingPolicies) {
            if (element.getChild("compressLatest") == null) {
                element.addContent(new Text("    "));
                element.addContent(new Element("compressLatest").addContent("true"));
                element.addContent(new Text("\n        "));
            }
        }

        log.info("Logback conversion completed: all compressLatest tags were added.");
    }
}
