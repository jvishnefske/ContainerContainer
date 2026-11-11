package org.artifactory.properties;

import org.w3c.dom.Node;

import jakarta.xml.bind.Element;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "properties")
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertiesImplWrapper {

    @XmlAnyElement
    List<Element> props = new ArrayList<>();

    public PropertiesImpl toProperties() {
        PropertiesImpl map = new PropertiesImpl();
        for (Object current : this.props) {
            if (current instanceof org.w3c.dom.Element element) {
                String key = element.getLocalName();
                String value = null;
                boolean hasChildNodes = element.hasChildNodes();
                if (hasChildNodes) {
                    Node firstChild = element.getFirstChild();
                    if (!"null".equals(firstChild.getLocalName())) {
                        value = element.getTextContent();
                    }
                } else {
                    value = "";
                }
                map.put(key, value);
            }
        }
        return map;
    }

}
